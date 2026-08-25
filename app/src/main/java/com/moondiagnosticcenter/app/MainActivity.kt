package com.moondiagnosticcenter.app

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private class PullToRefreshScrollView(context: android.content.Context) : ScrollView(context) {
    var onPullToRefresh: (() -> Unit)? = null
    private var downY = 0f
    private var triggered = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.rawY
                triggered = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val pulledDistance = event.rawY - downY
                val threshold = 110f * resources.displayMetrics.density
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    scrollY <= 0 && pulledDistance >= threshold && !triggered) {
                    triggered = true
                    onPullToRefresh?.invoke()
                }
                downY = 0f
            }
        }
        return super.onTouchEvent(event)
    }
}

class MainActivity : AppCompatActivity() {

    private data class CareOfOption(
        val id: String,
        val name: String,
        val address: String
    ) {
        override fun toString(): String {
            return if (address.isBlank()) {
                name
            } else {
                "$name\n$address"
            }
        }
    }

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var lastListTitle = ""
    private var lastListFilterField = ""
    private var lastListFilterValue = ""
    private var lastListDate = ""

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private var currentRole: String = ""
    private var currentUserDisplayName: String = ""

    // =========================================================
    // SCREEN STATE
    // =========================================================

    private var currentScreen = "login"

    private var serialParentScreen = "dashboard"

    companion object {

        private const val SCREEN_LOGIN = "login"
        private const val SCREEN_DASHBOARD = "dashboard"
        private const val SCREEN_DOCTOR_LIST = "doctor_list"
        private const val SCREEN_CAREOF_LIST = "careof_list"
        private const val SCREEN_TOTAL_SERIAL = "total_serial"
        private const val SCREEN_REPORTS = "reports"
        private const val SCREEN_DOCTOR_SERIAL = "doctor_serial"
        private const val SCREEN_CAREOF_SERIAL = "careof_serial"
        private const val SCREEN_ADD_DOCTOR = "add_doctor"
        private const val SCREEN_ADD_CAREOF = "add_careof"
        private const val SCREEN_ADD_SERIAL = "add_serial"

        private const val REQUEST_GALLERY = 501
        private const val REQUEST_CAMERA = 502
        private const val REQUEST_REPORT_PDF = 503

        private const val MAX_IMAGE_WIDTH = 900
        private const val MAX_IMAGE_HEIGHT = 900
    }

    // =========================================================
    // COLORS
    // =========================================================

    private val topBarColor = Color.rgb(13, 110, 110)
    private val primaryColor = Color.rgb(21, 101, 192)
    private val backgroundColor = Color.rgb(245, 247, 250)

    private val whiteColor = Color.WHITE
    private val darkText = Color.rgb(45, 45, 45)
    private val lightText = Color.rgb(90, 90, 90)

    private val totalSerialColor = Color.rgb(224, 247, 250)
    private val addSerialColor = Color.rgb(227, 242, 253)
    private val addDoctorColor = Color.rgb(232, 245, 233)
    private val addCareOfColor = Color.rgb(243, 229, 245)

    private val searchColor = Color.rgb(255, 243, 224)
    private val doctorsColor = Color.rgb(224, 247, 250)
    private val careOfColor = Color.rgb(232, 234, 246)
    private val reportsColor = Color.rgb(251, 233, 231)

    // =========================================================
    // ADD SERIAL TEMPORARY DATA
    // =========================================================

    private var selectedAttachmentBase64: String? = null
    private var selectedAttachmentName: String = ""
    private var selectedAttachmentMimeType: String = ""
    private var pendingReportPdfFile: java.io.File? = null

    private var addSerialPatientInput: EditText? = null
    private var addSerialDoctorSpinner: Spinner? = null
    private var addSerialCareOfSpinner: AutoCompleteTextView? = null
    private var addSerialDateText: TextView? = null
    private var addSerialNumberText: TextView? = null
    private var addSerialStarCheck: CheckBox? = null
    private var addSerialDescriptionInput: EditText? = null
    private var addSerialAttachmentText: TextView? = null

    private val doctorIds = mutableListOf<String>()
    private val doctorNames = mutableListOf<String>()

    private val careOfIds = mutableListOf<String>()
    private val careOfNames = mutableListOf<String>()
    private val careOfAddresses = mutableListOf<String>()
    private val careOfOptions = mutableListOf<CareOfOption>()

    private var selectedDoctorId: String = ""
    private var selectedDoctorName: String = ""

    private var selectedCareOfId: String = ""
    private var selectedCareOfName: String = ""
    private var selectedCareOfAddress: String = ""

    private var selectedSerialDate: String =
        SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Calendar.getInstance().time)

    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSystemBars()

        auth = FirebaseAuth.getInstance()

        setupBackButton()

        if (auth.currentUser != null) {
            checkUserAccess()
        } else {
            showLogin()
        }
    }

    // =========================================================
    // BACK BUTTON
    // =========================================================

    private fun setupBackButton() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (
                        ::drawerLayout.isInitialized &&
                        drawerLayout.isDrawerOpen(Gravity.START)
                    ) {
                        drawerLayout.closeDrawer(Gravity.START)
                        return
                    }

                    when (currentScreen) {

                        SCREEN_LOGIN -> {
                            finish()
                        }

                        SCREEN_DASHBOARD -> {

                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("অ্যাপ বন্ধ করুন")
                                .setMessage(
                                    "আপনি কি অ্যাপটি বন্ধ করতে চান?"
                                )
                                .setNegativeButton(
                                    "না",
                                    null
                                )
                                .setPositiveButton(
                                    "হ্যাঁ"
                                ) { _, _ ->
                                    finish()
                                }
                                .show()
                        }

                        SCREEN_DOCTOR_LIST -> {
                            showDashboard(currentRole)
                        }

                        SCREEN_CAREOF_LIST -> {
                            showDashboard(currentRole)
                        }

                        SCREEN_TOTAL_SERIAL -> {
                            showDashboard(currentRole)
                        }

                        SCREEN_REPORTS -> {
                            showDashboard(currentRole)
                        }

                        SCREEN_DOCTOR_SERIAL -> {
                            showDoctorList()
                        }

                        SCREEN_CAREOF_SERIAL -> {
                            showCareOfList()
                        }

                        SCREEN_ADD_DOCTOR -> {
                            showDashboard(currentRole)
                        }

                        SCREEN_ADD_CAREOF -> {
                            showDashboard(currentRole)
                        }

                        SCREEN_ADD_SERIAL -> {
                            showDashboard(currentRole)
                        }

                        else -> {
                            showDashboard(currentRole)
                        }
                    }
                }
            }
        )
    }

    // =========================================================
    // DP
    // =========================================================

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // =========================================================
    // SYSTEM BAR
    // =========================================================

    private fun setupSystemBars() {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            true
        )

        window.statusBarColor = topBarColor
        window.navigationBarColor = backgroundColor

        window.decorView.systemUiVisibility = 0
    }

    // =========================================================
    // STATUS BAR HEIGHT
    // =========================================================

    private fun getStatusBarHeight(): Int {

        val resourceId =
            resources.getIdentifier(
                "status_bar_height",
                "dimen",
                "android"
            )

        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    // =========================================================
    // LOGIN
    // =========================================================

    private fun showLogin() {

        currentScreen = SCREEN_LOGIN

        setupSystemBars()

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(30),
                    dp(30),
                    dp(30),
                    dp(30)
                )

                setBackgroundColor(
                    whiteColor
                )
            }

        val logo =
            TextView(this).apply {

                text = "MDC"

                textSize = 46f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setTextColor(
                    primaryColor
                )
            }

        val title =
            TextView(this).apply {

                text =
                    "মুন ডায়াগনস্টিক সেন্টার"

                textSize = 25f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(30)
                )

                setTextColor(
                    darkText
                )
            }

        val loginTitle =
            TextView(this).apply {

                text = "Login"

                textSize = 26f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(22)
                )
            }

        val email =
            EditText(this).apply {

                hint = "Email"

                textSize = 18f

                setSingleLine(true)

                setPadding(
                    dp(15),
                    dp(12),
                    dp(15),
                    dp(12)
                )
            }

        val passwordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val password =
            EditText(this).apply {
                hint = "Password"
                textSize = 18f
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(dp(15), dp(12), dp(8), dp(12))
                background = roundedCardDrawable(Color.WHITE, dp(12))
            }

        val passwordEye = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            contentDescription = "Show password"
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        var passwordVisible = false
        passwordEye.setOnClickListener {
            passwordVisible = !passwordVisible
            val position = password.selectionStart.coerceAtLeast(0)
            password.inputType = if (passwordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            password.setSelection(position.coerceAtMost(password.text.length))
            passwordEye.contentDescription = if (passwordVisible) "Hide password" else "Show password"
        }

        passwordRow.addView(password, LinearLayout.LayoutParams(0, dp(60), 1f))
        passwordRow.addView(passwordEye, LinearLayout.LayoutParams(dp(58), dp(60)))

        val loginButton =
            Button(this).apply {

                text = "LOGIN"

                textSize = 18f

                setPadding(
                    0,
                    dp(12),
                    0,
                    dp(12)
                )
            }

        val status =
            TextView(this).apply {

                textSize = 15f

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(15),
                    0,
                    0
                )
            }

        loginButton.setOnClickListener {

            val emailText =
                email.text.toString().trim()

            val passwordText =
                password.text.toString()

            if (
                emailText.isEmpty() ||
                passwordText.isEmpty()
            ) {

                Toast.makeText(
                    this,
                    "Email এবং Password দিন",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            loginButton.isEnabled = false

            status.text =
                "Login হচ্ছে..."

            auth.signInWithEmailAndPassword(
                emailText,
                passwordText
            )
                .addOnSuccessListener {
                    checkUserAccess()
                }
                .addOnFailureListener { error ->

                    loginButton.isEnabled = true

                    status.text = ""

                    Toast.makeText(
                        this,
                        "Login ব্যর্থ: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        root.addView(logo)
        root.addView(title)
        root.addView(loginTitle)

        root.addView(
            email,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            ).apply {
                setMargins(
                    0,
                    dp(5),
                    0,
                    dp(10)
                )
            }
        )

        root.addView(
            passwordRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            ).apply {
                setMargins(0, 0, 0, dp(15))
            }
        )

        root.addView(
            loginButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )

        root.addView(status)

        setContentView(root)
    }

    // =========================================================
    // CHECK USER ACCESS
    // =========================================================

    private fun checkUserAccess() {

        val user = auth.currentUser

        if (user == null) {
            showLogin()
            return
        }

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    auth.signOut()

                    Toast.makeText(
                        this,
                        "আপনার User profile পাওয়া যায়নি",
                        Toast.LENGTH_LONG
                    ).show()

                    showLogin()
                    return@addOnSuccessListener
                }

                val active =
                    document.getBoolean("active")
                        ?: false

                val role =
                    document.getString("role")
                        ?: ""

                if (!active) {

                    auth.signOut()

                    Toast.makeText(
                        this,
                        "আপনার account বর্তমানে বন্ধ আছে",
                        Toast.LENGTH_LONG
                    ).show()

                    showLogin()
                    return@addOnSuccessListener
                }

                currentRole =
                    role.lowercase()

                currentUserDisplayName =
                    document.getString("name")
                        ?: document.getString("username")
                        ?: document.getString("displayName")
                        ?: user.email
                        ?: user.uid

                showDashboard(
                    currentRole
                )
            }
            .addOnFailureListener { error ->

                auth.signOut()

                Toast.makeText(
                    this,
                    "User profile যাচাই করা যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()

                showLogin()
            }
    }

    // =========================================================
    // DASHBOARD
    // =========================================================

    private fun clearActiveListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    private fun refreshCurrentScreen() {
        when (currentScreen) {
            SCREEN_DASHBOARD -> showDashboard(currentRole)
            SCREEN_TOTAL_SERIAL -> showTotalSerial()
            SCREEN_REPORTS -> showReports()
            SCREEN_DOCTOR_LIST -> showDoctorList()
            SCREEN_CAREOF_LIST -> showCareOfList()
            SCREEN_DOCTOR_SERIAL, SCREEN_CAREOF_SERIAL -> {
                if (lastListFilterField.isNotEmpty()) {
                    showSerialListPage(lastListTitle, lastListFilterField, lastListFilterValue, lastListDate)
                } else {
                    showDashboard(currentRole)
                }
            }
            else -> showDashboard(currentRole)
        }
    }

    private fun showDashboard(role: String) {

        clearActiveListeners()
        currentScreen = SCREEN_DASHBOARD

        setupSystemBars()

        drawerLayout =
            DrawerLayout(this).apply {
                setBackgroundColor(
                    backgroundColor
                )
            }

        val mainLayout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    backgroundColor
                )
            }

        val topBar =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(8),
                    getStatusBarHeight() + dp(8),
                    dp(15),
                    dp(12)
                )

                setBackgroundColor(
                    topBarColor
                )

                elevation =
                    dp(8).toFloat()
            }

        val menuButton =
            ImageButton(this).apply {

                setImageResource(
                    android.R.drawable.ic_menu_sort_by_size
                )

                setBackgroundColor(
                    Color.TRANSPARENT
                )

                setColorFilter(
                    Color.WHITE
                )

                contentDescription =
                    "Open Menu"
            }

        topBar.addView(
            menuButton,
            LinearLayout.LayoutParams(
                dp(64),
                dp(64)
            )
        )

        val title =
            TextView(this).apply {

                text =
                    "Moon Diagnostic Center"

                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(10),
                    0,
                    0,
                    0
                )
            }

        topBar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        val roleText =
            TextView(this).apply {

                text =
                    role.uppercase()

                textSize = 14f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER
            }

        topBar.addView(
            roleText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )


        mainLayout.addView(
            topBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(88)
            )
        )

        val scrollView =
            PullToRefreshScrollView(this).apply {
                isFillViewport = true
                onPullToRefresh = { refreshCurrentScreen() }
            }

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(14),
                    dp(16),
                    dp(30)
                )
            }

        val messageBanner =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedCardDrawable(Color.rgb(198, 40, 40), dp(12))
                visibility = View.GONE
            }

        content.addView(
            messageBanner,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(12))
            }
        )

        loadTodayDashboardMessages(messageBanner)

        val welcome =
            TextView(this).apply {

                text = "স্বাগতম"

                textSize = 25f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(5),
                    dp(4),
                    dp(5),
                    dp(14)
                )
            }

        content.addView(welcome)

        val row1 =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        val totalSerial =
            createDashboardCard(
                "📋",
                "Total Serial",
                totalSerialColor
            )

        val addSerial =
            createDashboardCard(
                "➕",
                "Add Serial",
                addSerialColor
            )

        row1.addView(
            totalSerial,
            gridParams()
        )

        row1.addView(
            addSerial,
            gridParams()
        )

        content.addView(row1)

        val row2 =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        val addDoctor =
            createDashboardCard(
                "👨‍⚕️",
                "Add Doctor",
                addDoctorColor
            )

        val addCareOf =
            createDashboardCard(
                "👤",
                "Add Care Of",
                addCareOfColor
            )

        row2.addView(
            addDoctor,
            gridParams()
        )

        row2.addView(
            addCareOf,
            gridParams()
        )

        content.addView(row2)

        val summaryTitle =
            TextView(this).apply {

                text = "আজকের Serial Summary"

                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(5),
                    dp(24),
                    dp(5),
                    dp(10)
                )
            }

        content.addView(summaryTitle)

        val summaryRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        summaryRow.addView(
            createSummaryCard(
                "মোট",
                "0"
            ),
            summaryParams()
        )

        summaryRow.addView(
            createSummaryCard(
                "অপেক্ষমাণ",
                "0"
            ),
            summaryParams()
        )

        summaryRow.addView(
            createSummaryCard(
                "সম্পন্ন",
                "0"
            ),
            summaryParams()
        )

        summaryRow.addView(
            createSummaryCard(
                "উপস্থিত",
                "0"
            ),
            summaryParams()
        )

        content.addView(summaryRow)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        activeListeners.add(
            db.collection("serials").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val todayDocuments = snapshot.documents.filter {
                    (it.getString("createdDate") ?: "") == today &&
                        !((it.getString("status") ?: "Waiting").equals("Cancelled", true))
                }
                var waiting = 0
                var present = 0
                var completed = 0
                todayDocuments.forEach { doc ->
                    when ((doc.getString("status") ?: "Waiting").lowercase(Locale.getDefault())) {
                        "present" -> present++
                        "completed" -> completed++
                        else -> waiting++
                    }
                }
                val values = listOf(todayDocuments.size, waiting, completed, present)
                values.forEachIndexed { index, value ->
                    val card = summaryRow.getChildAt(index) as? LinearLayout ?: return@forEachIndexed
                    val valueView = card.getChildAt(1) as? TextView ?: return@forEachIndexed
                    valueView.text = value.toString()
                }
            }
        )

        val quickTitle =
            TextView(this).apply {

                text = "Quick Access"

                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(5),
                    dp(24),
                    dp(5),
                    dp(10)
                )
            }

        content.addView(quickTitle)

        val row3 =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        val search =
            createDashboardCard(
                "🔎",
                "Search",
                searchColor
            )

        val doctors =
            createDashboardCard(
                "👨‍⚕️",
                "Doctors",
                doctorsColor
            )

        row3.addView(
            search,
            gridParams()
        )

        row3.addView(
            doctors,
            gridParams()
        )

        content.addView(row3)

        val row4 =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        val careOf =
            createDashboardCard(
                "👤",
                "Care Of",
                careOfColor
            )

        val reports =
            createDashboardCard(
                "📊",
                "Reports",
                reportsColor
            )

        row4.addView(
            careOf,
            gridParams()
        )

        row4.addView(
            reports,
            gridParams()
        )

        content.addView(row4)

        scrollView.addView(content)

        mainLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        drawerLayout.addView(
            mainLayout,
            DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
            )
        )

        navigationView =
            NavigationView(this).apply {
                setBackgroundColor(Color.WHITE)
            }

        val drawerWidth =
            (
                resources.displayMetrics.widthPixels * 0.82
            ).toInt()

        val drawerParams =
            DrawerLayout.LayoutParams(
                drawerWidth,
                DrawerLayout.LayoutParams.MATCH_PARENT
            )

        drawerParams.gravity =
            Gravity.START

        drawerLayout.addView(
            navigationView,
            drawerParams
        )

        createNavigationMenu(
            navigationView,
            role
        )

        setContentView(drawerLayout)

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(
                Gravity.START
            )
        }

        totalSerial.setOnClickListener {
            showTotalSerial()
        }

        addSerial.setOnClickListener {
            showAddSerial()
        }

        addDoctor.setOnClickListener {

            if (role.lowercase() == "admin") {
                showAddDoctor()
            } else {
                Toast.makeText(
                    this,
                    "শুধুমাত্র Admin Doctor যোগ করতে পারবেন",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        addCareOf.setOnClickListener {
            // Any authenticated user may add a Care Of.
            // Edit/Delete is restricted to the creator or Admin.
            showAddCareOf()
        }

        search.setOnClickListener {
            showSerialSearch()
        }

        doctors.setOnClickListener {
            showDoctorList()
        }

        careOf.setOnClickListener {
            showCareOfList()
        }

        reports.setOnClickListener {
            showReports()
        }
    }

    // =========================================================
    // DATE-WISE MESSAGE
    // =========================================================

    private fun loadTodayDashboardMessages(container: LinearLayout) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        db.collection("messages")
            .whereEqualTo("date", today)
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { result ->
                container.removeAllViews()
                if (result.isEmpty) {
                    container.visibility = View.GONE
                    return@addOnSuccessListener
                }
                container.visibility = View.VISIBLE
                container.addView(TextView(this).apply {
                    text = "📢 আজকের গুরুত্বপূর্ণ বার্তা"
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                })
                val prefs = getSharedPreferences("MDC_MESSAGE_DISMISS", MODE_PRIVATE)
                result.documents.forEach { doc ->
                    if (prefs.getBoolean("hidden_${doc.id}", false)) return@forEach
                    val message = doc.getString("message") ?: return@forEach
                    val sender = doc.getString("createdByName") ?: ""
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    val text = TextView(this).apply {
                        text = if (sender.isBlank()) message else "$message\n— $sender"
                        textSize = 15f; setTextColor(Color.WHITE); setPadding(0, dp(7), dp(8), dp(7))
                    }
                    val close = TextView(this).apply { this.text = "✕"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(4), 0) }
                    close.setOnClickListener { prefs.edit().putBoolean("hidden_${doc.id}", true).apply(); loadTodayDashboardMessages(container) }
                    row.addView(text, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(close, LinearLayout.LayoutParams(dp(45), -2))
                    container.addView(row)
                }
            }
            .addOnFailureListener {
                container.visibility = View.GONE
            }
    }

    private fun showMessages() {
        setupSystemBars()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        root.addView(createInnerTopBar("💬 Message") { showDashboard(currentRole) })

        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(30))
        }

        content.addView(TextView(this).apply {
            text = "💬 তারিখভিত্তিক Message"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
        })
        content.addView(TextView(this).apply {
            text = "যে তারিখ নির্বাচন করবেন, সেই তারিখে প্রত্যেক User / Operator / Admin-এর Dashboard-এর উপরে লাল রঙে Message দেখা যাবে।"
            textSize = 14f
            setTextColor(lightText)
            setPadding(0, dp(6), 0, dp(14))
        })

        content.addView(createPrimaryButton("＋  নতুন Message") .apply {
            setBackgroundColor(Color.rgb(21, 101, 192))
            setTextColor(Color.WHITE)
            setOnClickListener { showCreateMessageDialog() }
        }, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, 0, 0, dp(14)) })

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        db.collection("messages")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    content.addView(createEmptyText("কোনো Message নেই"))
                } else {
                    result.documents.forEach { doc ->
                        val date = doc.getString("date") ?: ""
                        val message = doc.getString("message") ?: ""
                        val sender = doc.getString("createdByName") ?: ""
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(14), dp(12), dp(14), dp(12))
                            background = roundedCardDrawable(if (date == today) Color.rgb(255, 235, 238) else Color.WHITE, dp(14))
                            elevation = dp(2).toFloat()
                        }
                        card.addView(TextView(this).apply {
                            text = "📅 $date"
                            textSize = 16f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(if (date == today) Color.rgb(198, 40, 40) else darkText)
                        })
                        card.addView(TextView(this).apply {
                            text = message
                            textSize = 16f
                            setTextColor(darkText)
                            setPadding(0, dp(7), 0, 0)
                        })
                        if (sender.isNotBlank()) {
                            card.addView(TextView(this).apply {
                                text = "দিয়েছেন: $sender"
                                textSize = 13f
                                setTextColor(lightText)
                                setPadding(0, dp(5), 0, 0)
                            })
                        }
                        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })
                    }
                }
            }
            .addOnFailureListener { e ->
                content.addView(createEmptyText("Message পড়া যায়নি: ${e.message}"))
            }

        scroll.addView(content)
        scroll.onPullToRefresh = { refreshCurrentScreen() }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showCreateMessageDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        val dateInput = createFormInput("তারিখ নির্বাচন করুন")
        dateInput.isFocusable = false
        dateInput.isClickable = true
        val messageInput = createFormInput("Message লিখুন")
        messageInput.setSingleLine(false)
        messageInput.minLines = 5
        messageInput.gravity = Gravity.TOP

        val selectedDate = Calendar.getInstance()
        dateInput.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time))
        dateInput.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDate.set(year, month, day)
                    dateInput.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time))
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        box.addView(TextView(this).apply { text = "কোন দিনের জন্য Message?"; textSize = 14f; setTextColor(lightText); setPadding(0, dp(8), 0, dp(4)) })
        box.addView(dateInput, formParams())
        box.addView(TextView(this).apply { text = "Message"; textSize = 14f; setTextColor(lightText); setPadding(0, dp(10), 0, dp(4)) })
        box.addView(messageInput, LinearLayout.LayoutParams(-1, dp(130)).apply { setMargins(0, 0, 0, dp(6)) })

        AlertDialog.Builder(this)
            .setTitle("নতুন Message")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val date = dateInput.text.toString().trim()
                        val message = messageInput.text.toString().trim()
                        if (date.isEmpty() || message.isEmpty()) {
                            Toast.makeText(this, "তারিখ ও Message দিন", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val data = hashMapOf(
                            "date" to date,
                            "message" to message,
                            "createdByUid" to (auth.currentUser?.uid ?: ""),
                            "createdByName" to currentUserDisplayName,
                            "createdByRole" to currentRole,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "active" to true
                        )
                        db.collection("messages")
                            .add(data)
                            .addOnSuccessListener {
                                dialog.dismiss()
                                Toast.makeText(this, "Message সংরক্ষণ হয়েছে", Toast.LENGTH_SHORT).show()
                                showMessages()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Message সংরক্ষণ করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
            }.show()
    }

    // =========================================================
    // ADD SERIAL
    // =========================================================

    private fun showAddSerial() {

        currentScreen = SCREEN_ADD_SERIAL

        setupSystemBars()

        selectedAttachmentBase64 = null
        selectedAttachmentName = ""
        selectedAttachmentMimeType = ""

        doctorIds.clear()
        doctorNames.clear()

        careOfIds.clear()
        careOfNames.clear()
        careOfAddresses.clear()
        careOfOptions.clear()

        selectedDoctorId = ""
        selectedDoctorName = ""

        selectedCareOfId = ""
        selectedCareOfName = ""
        selectedCareOfAddress = ""

        selectedSerialDate =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            ).format(
                Calendar.getInstance().time
            )

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    backgroundColor
                )
            }

        root.addView(
            createInnerTopBar(
                "Add Serial"
            ) {
                showDashboard(currentRole)
            }
        )

        val scroll =
            PullToRefreshScrollView(this).apply {
                isFillViewport = true
            }

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(18),
                    dp(20),
                    dp(35)
                )
            }

        val heading =
            TextView(this).apply {

                text =
                    "নতুন রোগীর Serial যোগ করুন"

                textSize = 24f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(18)
                )
            }

        content.addView(heading)

        // =====================================================
        // PATIENT NAME + STAR
        // =====================================================

        addFormLabel(
            content,
            "রোগীর নাম *"
        )

        val patientRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val patientInput =
            createFormInput(
                "রোগীর নাম লিখুন"
            )

        patientInput.textSize = 18f

        addSerialPatientInput =
            patientInput

        patientRow.addView(
            patientInput,
            LinearLayout.LayoutParams(
                0,
                dp(62),
                1f
            ).apply {
                setMargins(
                    0,
                    0,
                    dp(8),
                    dp(14)
                )
            }
        )

        val starCheck =
            CheckBox(this).apply {

                text = "☆"
                textSize = 34f
                gravity = Gravity.CENTER
                buttonDrawable = null
                setTextColor(Color.rgb(145, 145, 145))

                setPadding(
                    dp(8),
                    0,
                    dp(8),
                    0
                )

                contentDescription =
                    "VIP Patient"

                setOnCheckedChangeListener { button, checked ->
                    button.text = if (checked) "★" else "☆"
                    button.setTextColor(
                        if (checked) {
                            Color.rgb(255, 193, 7)
                        } else {
                            Color.rgb(145, 145, 145)
                        }
                    )
                }
            }

        addSerialStarCheck =
            starCheck

        val starContainer =
            LinearLayout(this).apply {

                gravity =
                    Gravity.CENTER

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(14)
                    )

                elevation =
                    dp(2).toFloat()
            }

        starContainer.addView(
            starCheck,
            LinearLayout.LayoutParams(
                dp(70),
                dp(62)
            )
        )

        patientRow.addView(
            starContainer,
            LinearLayout.LayoutParams(
                dp(72),
                dp(62)
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }
        )

        content.addView(patientRow)

        // =====================================================
        // DOCTOR
        // =====================================================

        addFormLabel(
            content,
            "ডাক্তার নির্বাচন করুন *"
        )

        val doctorRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val doctorSpinner =
            Spinner(this)

        addSerialDoctorSpinner =
            doctorSpinner

        doctorRow.addView(
            doctorSpinner,
            LinearLayout.LayoutParams(
                0,
                dp(60),
                1f
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }
        )

        content.addView(doctorRow)

        // =====================================================
        // CARE OF + PLUS
        // =====================================================

        addFormLabel(
            content,
            "কেয়ার অফ"
        )

        val careRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val careSpinner =
            AutoCompleteTextView(this).apply {

                hint = "কেয়ার অফের নাম লিখুন"
                textSize = 17f
                setTextColor(darkText)
                setHintTextColor(Color.rgb(140, 140, 140))
                setSingleLine(true)
                threshold = 2
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(16),
                    dp(10),
                    dp(16),
                    dp(10)
                )
                background = roundedCardDrawable(
                    Color.WHITE,
                    dp(12)
                )
                setDropDownVerticalOffset(dp(4))
            }

        addSerialCareOfSpinner =
            careSpinner

        careRow.addView(
            careSpinner,
            LinearLayout.LayoutParams(
                0,
                dp(60),
                1f
            ).apply {
                setMargins(
                    0,
                    0,
                    dp(8),
                    dp(14)
                )
            }
        )

        // =====================================================
        // CARE OF DROPDOWN ARROW
        // =====================================================

        val careOfArrowButton =
            Button(this).apply {

                text = "▼"
                textSize = 18f
                setAllCaps(false)
                setTextColor(primaryColor)
                gravity = Gravity.CENTER
                background = roundedCardDrawable(
                    Color.WHITE,
                    dp(14)
                )
                contentDescription =
                    "Care Of নির্বাচন করুন"
                setPadding(0, 0, 0, 0)

                setOnClickListener {
                    // Show every active Care Of without requiring typing.
                    careSpinner.requestFocus()
                    careSpinner.showDropDown()
                }
            }

        careRow.addView(
            careOfArrowButton,
            LinearLayout.LayoutParams(
                dp(58),
                dp(60)
            ).apply {
                setMargins(
                    0,
                    0,
                    dp(8),
                    dp(14)
                )
            }
        )

        val plusButton =
            Button(this).apply {

                text = "＋"

                textSize = 30f

                setAllCaps(false)

                setTextColor(
                    primaryColor
                )

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(14)
                    )

                contentDescription =
                    "Add Care Of"
            }

        careRow.addView(
            plusButton,
            LinearLayout.LayoutParams(
                dp(70),
                dp(60)
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }
        )

        content.addView(careRow)

        // =====================================================
        // SERIAL NUMBER
        // =====================================================

        addFormLabel(
            content,
            "সিরিয়াল নম্বর"
        )

        val serialNumber =
            TextView(this).apply {

                text =
                    "ডাক্তার ও তারিখ নির্বাচন করুন"

                textSize = 21f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER_VERTICAL

                setTextColor(
                    primaryColor
                )

                setPadding(
                    dp(18),
                    0,
                    dp(18),
                    0
                )

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(12)
                    )
            }

        addSerialNumberText =
            serialNumber

        content.addView(
            serialNumber,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }
        )

        // =====================================================
        // DATE
        // =====================================================

        addFormLabel(
            content,
            "তারিখ নির্বাচন করুন *"
        )

        val dateButton =
            Button(this).apply {

                text =
                    formatDisplayDate(
                        selectedSerialDate
                    )

                textSize = 18f

                setAllCaps(false)

                gravity =
                    Gravity.CENTER_VERTICAL

                setTextColor(
                    darkText
                )

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(12)
                    )

                setPadding(
                    dp(16),
                    0,
                    dp(16),
                    0
                )
            }

        addSerialDateText =
            dateButton

        content.addView(
            dateButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }
        )

        // =====================================================
        // DESCRIPTION
        // =====================================================

        addFormLabel(
            content,
            "বিবরণ — Optional"
        )

        val description =
            createFormInput(
                "রোগী সম্পর্কে বিবরণ লিখুন"
            )

        description.setSingleLine(false)
        description.minLines = 5
        description.gravity = Gravity.TOP

        addSerialDescriptionInput =
            description

        content.addView(
            description,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(125)
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }
        )

        // =====================================================
        // ATTACHMENT
        // =====================================================

        addFormLabel(
            content,
            "ডকুমেন্ট / ছবি — Optional"
        )

        val attachmentRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val attachmentText =
            TextView(this).apply {

                text =
                    "কোনো ছবি নির্বাচন করা হয়নি"

                textSize = 15f

                setTextColor(
                    lightText
                )

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(12),
                    0,
                    dp(8),
                    0
                )

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(12)
                    )
            }

        addSerialAttachmentText =
            attachmentText

        attachmentRow.addView(
            attachmentText,
            LinearLayout.LayoutParams(
                0,
                dp(60),
                1f
            ).apply {
                setMargins(
                    0,
                    0,
                    dp(8),
                    dp(14)
                )
            }
        )

        val cameraButton =
            Button(this).apply {

                text = "📷"

                textSize = 24f

                setAllCaps(false)

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(12)
                    )
            }

        attachmentRow.addView(
            cameraButton,
            LinearLayout.LayoutParams(
                dp(65),
                dp(60)
            ).apply {
                setMargins(
                    0,
                    0,
                    dp(5),
                    dp(14)
                )
            }
        )

        val galleryButton =
            Button(this).apply {

                text = "📎"

                textSize = 24f

                setAllCaps(false)

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(12)
                    )
            }

        attachmentRow.addView(
            galleryButton,
            LinearLayout.LayoutParams(
                dp(65),
                dp(60)
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }
        )

        content.addView(attachmentRow)

        // =====================================================
        // STATUS
        // =====================================================

        val status =
            createStatusText()

        content.addView(status)

        // =====================================================
        // ADD SERIAL BUTTON
        // =====================================================

        val saveButton =
            createPrimaryButton(
                "সিরিয়াল যোগ করুন"
            )

        content.addView(
            saveButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            )
        )

        // =====================================================
        // LOAD DOCTORS
        // =====================================================

        loadDoctorsForAddSerial()

        // =====================================================
        // LOAD CARE OF
        // =====================================================

        loadCareOfForAddSerial()

        // =====================================================
        // DATE CLICK
        // =====================================================

        dateButton.setOnClickListener {

            showAddSerialDatePicker()
        }

        // =====================================================
        // DOCTOR CHANGE
        // =====================================================

        doctorSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (
                        position >= 0 &&
                        position < doctorIds.size
                    ) {

                        selectedDoctorId =
                            doctorIds[position]

                        selectedDoctorName =
                            doctorNames[position]

                        updateNextSerialPreview()
                    }
                }
            }

        // =====================================================
        // CARE OF SEARCH / CHANGE
        // =====================================================

        careSpinner.setOnItemClickListener { parent, _, position, _ ->

            val selected =
                parent.getItemAtPosition(position) as? CareOfOption
                    ?: return@setOnItemClickListener

            selectedCareOfId = selected.id
            selectedCareOfName = selected.name
            selectedCareOfAddress = selected.address
        }

        careSpinner.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    val text = s?.toString()?.trim().orEmpty()

                    if (text.length < 2) {
                        selectedCareOfId = ""
                        selectedCareOfName = ""
                        selectedCareOfAddress = ""
                    }
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )

        // =====================================================
        // PLUS CARE OF
        // =====================================================

        plusButton.setOnClickListener {

            showQuickAddCareOfDialog {
                loadCareOfForAddSerial()
            }
        }

        // =====================================================
        // CAMERA
        // =====================================================

        cameraButton.setOnClickListener {

            openCamera()
        }

        // =====================================================
        // GALLERY
        // =====================================================

        galleryButton.setOnClickListener {

            openGallery()
        }

        // =====================================================
        // SAVE SERIAL
        // =====================================================

        saveButton.setOnClickListener {

            saveNewSerial(
                patientInput,
                starCheck,
                description,
                saveButton,
                status
            )
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    // =========================================================
    // LOAD DOCTORS FOR ADD SERIAL
    // =========================================================

    private fun loadDoctorsForAddSerial() {

        val spinner =
            addSerialDoctorSpinner
                ?: return

        db.collection("doctors")
            .whereEqualTo(
                "active",
                true
            )
            .get()
            .addOnSuccessListener { result ->

                doctorIds.clear()
                doctorNames.clear()

                val sorted =
                    result.documents.sortedBy {

                        (
                            it.getString("name")
                                ?: ""
                        ).lowercase()
                    }

                for (doc in sorted) {

                    doctorIds.add(
                        doc.id
                    )

                    doctorNames.add(
                        doc.getString("name")
                            ?: "Unknown"
                    )
                }

                if (doctorNames.isEmpty()) {

                    doctorNames.add(
                        "কোনো Doctor পাওয়া যায়নি"
                    )

                    doctorIds.add("")

                } else {

                    selectedDoctorId =
                        doctorIds.first()

                    selectedDoctorName =
                        doctorNames.first()
                }

                val adapter =
                    ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        doctorNames
                    )

                spinner.adapter =
                    adapter

                if (doctorIds.firstOrNull()
                        ?.isNotEmpty() == true
                ) {
                    updateNextSerialPreview()
                }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Doctor List পাওয়া যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // LOAD CARE OF
    // =========================================================

    private fun loadCareOfForAddSerial() {

        val input =
            addSerialCareOfSpinner
                ?: return

        db.collection("careOf")
            .whereEqualTo(
                "active",
                true
            )
            .get()
            .addOnSuccessListener { result ->

                careOfIds.clear()
                careOfNames.clear()
                careOfAddresses.clear()
                careOfOptions.clear()

                val sorted =
                    result.documents.sortedBy {
                        (it.getString("name") ?: "").lowercase()
                    }

                for (doc in sorted) {

                    val name =
                        doc.getString("name") ?: "Unknown"

                    val address =
                        doc.getString("address") ?: ""

                    careOfIds.add(doc.id)
                    careOfNames.add(name)
                    careOfAddresses.add(address)
                    careOfOptions.add(
                        CareOfOption(
                            doc.id,
                            name,
                            address
                        )
                    )
                }

                selectedCareOfId = ""
                selectedCareOfName = ""
                selectedCareOfAddress = ""

                val adapter =
                    object : ArrayAdapter<CareOfOption>(
                        this,
                        android.R.layout.simple_list_item_1,
                        careOfOptions
                    ) {

                        override fun getView(
                            position: Int,
                            convertView: View?,
                            parent: android.view.ViewGroup
                        ): View {

                            val view =
                                super.getView(
                                    position,
                                    convertView,
                                    parent
                                ) as TextView

                            val option =
                                getItem(position)

                            view.text =
                                if (option?.address.isNullOrBlank()) {
                                    option?.name ?: ""
                                } else {
                                    "${option?.name}\n${option?.address}"
                                }

                            view.setSingleLine(false)
                            view.maxLines = 3
                            view.setPadding(
                                dp(16),
                                dp(10),
                                dp(16),
                                dp(10)
                            )
                            view.textSize = 16f

                            return view
                        }
                    }

                input.setAdapter(adapter)

                input.setOnItemClickListener { parent, _, position, _ ->

                    val selected =
                        parent.getItemAtPosition(position) as? CareOfOption
                            ?: return@setOnItemClickListener

                    selectedCareOfId = selected.id
                    selectedCareOfName = selected.name
                    selectedCareOfAddress = selected.address

                    input.setText(
                        selected.toString(),
                        false
                    )
                    input.error = null
                }

                // If the user types an existing Care Of name exactly, treat it as a
                // valid selection as well. Otherwise the mandatory check in
                // saveNewSerial() will stop the serial from being created.
                input.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val typed = normalizeCareOfValue(input.text.toString().substringBefore("\n"))
                        val match = careOfOptions.firstOrNull {
                            normalizeCareOfValue(it.name) == typed
                        }
                        if (match != null) {
                            selectedCareOfId = match.id
                            selectedCareOfName = match.name
                            selectedCareOfAddress = match.address
                            input.setText(match.toString(), false)
                            input.error = null
                        } else if (typed.isNotBlank()) {
                            selectedCareOfId = ""
                            selectedCareOfName = ""
                            selectedCareOfAddress = ""
                            input.error = "Arrow থেকে একটি Care Of নির্বাচন করুন"
                        }
                    }
                }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Care Of List পাওয়া যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // CARE OF DUPLICATE NORMALIZATION
    // =========================================================

    private fun normalizeCareOfValue(value: String): String {
        return value
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.getDefault())
    }

    private fun findDuplicateCareOf(
        careName: String,
        careAddress: String,
        onFound: (DocumentSnapshot) -> Unit,
        onNotFound: () -> Unit
    ) {
        val normalizedName = normalizeCareOfValue(careName)
        val normalizedAddress = normalizeCareOfValue(careAddress)

        if (normalizedName.isBlank()) {
            Toast.makeText(
                this,
                "Care Of নাম দিন",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // First check the deterministic document ID. This prevents two users
        // from creating the same Care Of at the same time.
        val deterministicId = careOfDocumentId(
            normalizedName,
            normalizedAddress
        )

        db.collection("careOf")
            .document(deterministicId)
            .get()
            .addOnSuccessListener { direct ->

                if (direct.exists()) {
                    onFound(direct)
                    return@addOnSuccessListener
                }

                // Also scan older/random-ID documents so Care Of records
                // created before this protection are recognized as duplicates.
                db.collection("careOf")
                    .whereEqualTo("active", true)
                    .get()
                    .addOnSuccessListener { result ->

                        val duplicate = result.documents.firstOrNull { doc ->
                            val existingName = normalizeCareOfValue(
                                doc.getString("name") ?: ""
                            )
                            val existingAddress = normalizeCareOfValue(
                                doc.getString("address") ?: ""
                            )

                            existingName == normalizedName &&
                                    existingAddress == normalizedAddress
                        }

                        if (duplicate != null) {
                            onFound(duplicate)
                        } else {
                            onNotFound()
                        }
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            this,
                            "Care Of যাচাই করা যায়নি: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Care Of যাচাই করা যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun careOfDocumentId(
        normalizedName: String,
        normalizedAddress: String
    ): String {
        val raw = "$normalizedName|$normalizedAddress"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))

        return "co_" + digest.joinToString("") { byte ->
            "%02x".format(byte)
        }
    }

    private fun saveCareOfWithoutDuplicate(
        careName: String,
        careAddress: String,
        onSaved: () -> Unit,
        onDuplicate: (DocumentSnapshot) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val normalizedName = normalizeCareOfValue(careName)
        val normalizedAddress = normalizeCareOfValue(careAddress)
        val deterministicId = careOfDocumentId(
            normalizedName,
            normalizedAddress
        )

        findDuplicateCareOf(
            careName,
            careAddress,
            onFound = onDuplicate,
            onNotFound = {
                val data = hashMapOf(
                    "name" to careName.trim().replace(Regex("\\s+"), " "),
                    "address" to careAddress.trim().replace(Regex("\\s+"), " "),
                    "normalizedName" to normalizedName,
                    "normalizedAddress" to normalizedAddress,
                    "active" to true,
                    "createdByUid" to (auth.currentUser?.uid ?: ""),
                    "createdAt" to FieldValue.serverTimestamp()
                )

                // set() with a deterministic ID is the final duplicate guard:
                // concurrent saves resolve to the same document instead of
                // creating two random documents with add().
                db.collection("careOf")
                    .document(deterministicId)
                    .set(data)
                    .addOnSuccessListener { onSaved() }
                    .addOnFailureListener { error -> onError(error) }
            }
        )
    }

    // =========================================================
    // QUICK ADD CARE OF
    // =========================================================

    private fun showQuickAddCareOfDialog(
        onSaved: () -> Unit
    ) {

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(5),
                    dp(20),
                    0
                )
            }

        val name =
            createFormInput(
                "Care Of Name"
            )

        val address =
            createFormInput(
                "Address / ঠিকানা"
            )

        address.setSingleLine(false)
        address.minLines = 3
        address.gravity = Gravity.TOP

        container.addView(
            name,
            formParams()
        )

        container.addView(
            address,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(90)
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(10)
                )
            }
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "＋ নতুন Care Of যোগ করুন"
                )
                .setView(container)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Save",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val careName =
                    name.text.toString().trim()

                val careAddress =
                    address.text.toString().trim()

                if (careName.isEmpty()) {

                    name.error =
                        "Care Of নাম দিন"

                    return@setOnClickListener
                }

                val saveButton =
                    dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                    )

                saveButton.isEnabled = false

                findDuplicateCareOf(
                    careName,
                    careAddress,
                    onFound = { existing ->

                        saveButton.isEnabled = true

                        val existingName =
                            existing.getString("name") ?: careName

                        val existingAddress =
                            existing.getString("address") ?: careAddress

                        AlertDialog.Builder(this)
                            .setTitle("Care Of আগে থেকেই আছে")
                            .setMessage(
                                if (existingAddress.isBlank()) {
                                    existingName
                                } else {
                                    "$existingName\n$existingAddress"
                                }
                            )
                            .setPositiveButton("ঠিক আছে", null)
                            .show()
                    },
                    onNotFound = {

                        saveCareOfWithoutDuplicate(
                            careName,
                            careAddress,
                            onSaved = {
                                Toast.makeText(
                                    this,
                                    "Care Of সফলভাবে যোগ হয়েছে",
                                    Toast.LENGTH_SHORT
                                ).show()
                                dialog.dismiss()
                                onSaved()
                            },
                            onDuplicate = { existing ->
                                saveButton.isEnabled = true
                                val existingName = existing.getString("name") ?: careName
                                val existingAddress = existing.getString("address") ?: careAddress
                                AlertDialog.Builder(this)
                                    .setTitle("Care Of আগে থেকেই আছে")
                                    .setMessage(if (existingAddress.isBlank()) existingName else "$existingName\n$existingAddress")
                                    .setPositiveButton("ঠিক আছে", null)
                                    .show()
                            },
                            onError = { error ->
                                saveButton.isEnabled = true
                                Toast.makeText(
                                    this,
                                    "Care Of যোগ করা যায়নি: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                )
            }
        }

        dialog.show()
    }

    // =========================================================
    // DATE PICKER FOR ADD SERIAL
    // =========================================================

    private fun showAddSerialDatePicker(onDateSelected: (() -> Unit)? = null) {

        val calendar =
            Calendar.getInstance()

        try {

            val parsed =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).parse(
                    selectedSerialDate
                )

            if (parsed != null) {
                calendar.time = parsed
            }

        } catch (_: Exception) {
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->

                val selected =
                    Calendar.getInstance()

                selected.set(
                    year,
                    month,
                    day
                )

                selectedSerialDate =
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                    ).format(
                        selected.time
                    )

                addSerialDateText?.text =
                    formatDisplayDate(
                        selectedSerialDate
                    )

                updateNextSerialPreview()
                onDateSelected?.invoke()
            },
            calendar.get(
                Calendar.YEAR
            ),
            calendar.get(
                Calendar.MONTH
            ),
            calendar.get(
                Calendar.DAY_OF_MONTH
            )
        ).apply {

            setTitle(
                "তারিখ নির্বাচন করুন"
            )

            show()
        }
    }

    // =========================================================
    // SERIAL PREVIEW
    // =========================================================

    private fun updateNextSerialPreview() {

        val doctorId =
            selectedDoctorId

        if (doctorId.isEmpty()) {

            addSerialNumberText?.text =
                "Doctor নির্বাচন করুন"

            return
        }

        val counterId =
            makeCounterId(
                selectedSerialDate,
                doctorId
            )

        db.collection("serialCounters")
            .document(counterId)
            .get()
            .addOnSuccessListener { document ->

                val lastNumber =
                    document.getLong(
                        "lastNumber"
                    ) ?: 0L

                val next =
                    lastNumber + 1L

                addSerialNumberText?.text =
                    "পরবর্তী Serial: $next"
            }
            .addOnFailureListener {

                addSerialNumberText?.text =
                    "পরবর্তী Serial: 1"
            }
    }

    // =========================================================
    // SAVE NEW SERIAL
    // =========================================================

    private fun saveNewSerial(
        patientInput: EditText,
        starCheck: CheckBox,
        description: EditText,
        saveButton: Button,
        statusText: TextView
    ) {

        val patient =
            patientInput.text.toString().trim()

        if (patient.isEmpty()) {

            patientInput.error =
                "রোগীর নাম দিন"

            patientInput.requestFocus()

            return
        }

        if (selectedDoctorId.isEmpty()) {

            Toast.makeText(
                this,
                "প্রথমে Doctor নির্বাচন করুন",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        // Care Of is mandatory for every role (Admin, Operator and User).
        // Typing/searching alone is not enough; an existing Care Of must be selected.
        if (selectedCareOfId.isBlank()) {
            addSerialCareOfSpinner?.error = "Care Of নির্বাচন করা বাধ্যতামূলক"
            addSerialCareOfSpinner?.requestFocus()
            Toast.makeText(
                this,
                "সিরিয়াল দেওয়ার আগে Care Of নির্বাচন করুন",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val loggedInUser = auth.currentUser

        if (loggedInUser == null) {
            saveButton.isEnabled = true
            statusText.text = ""
            Toast.makeText(
                this,
                "আপনার Login session পাওয়া যায়নি। আবার Login করুন।",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val loggedInUid = loggedInUser.uid

        val doctorId =
            selectedDoctorId

        val doctorName =
            selectedDoctorName

        val date =
            selectedSerialDate

        saveButton.isEnabled = false

        statusText.text =
            "Serial তৈরি হচ্ছে..."

        // =====================================================
        // IMPORTANT
        //
        // Date + Doctor অনুযায়ী unique counter document.
        //
        // Firestore transaction ব্যবহার করার কারণে
        // একই সময়ে একাধিক user Serial নিলেও duplicate
        // number তৈরি হবে না।
        // =====================================================

        val counterId =
            makeCounterId(
                date,
                doctorId
            )

        val counterRef =
            db.collection(
                "serialCounters"
            ).document(counterId)

        val serialRef =
            db.collection(
                "serials"
            ).document()

        db.runTransaction { transaction ->

            val counterSnapshot =
                transaction.get(
                    counterRef
                )

            val lastNumber =
                counterSnapshot.getLong(
                    "lastNumber"
                ) ?: 0L

            val nextNumber =
                lastNumber + 1L

            val counterData =
                hashMapOf<String, Any>(
                    "date" to date,
                    "doctorId" to doctorId,
                    "doctorName" to doctorName,
                    "lastNumber" to nextNumber,
                    "updatedAt" to
                            FieldValue.serverTimestamp()
                )

            transaction.set(
                counterRef,
                counterData,
                SetOptions.merge()
            )

            val serialData =
                hashMapOf<String, Any>(
                    "number" to nextNumber,
                    "patient" to patient,

                    "patientVip" to
                            starCheck.isChecked,

                    "doctorId" to doctorId,
                    "doctorName" to doctorName,

                    "careOfId" to
                            selectedCareOfId,

                    "careOfName" to
                            selectedCareOfName,

                    "careOfAddress" to
                            selectedCareOfAddress,

                    "createdDate" to date,

                    "description" to
                            description.text.toString().trim(),

                    "status" to "Waiting",

                    "createdByUid" to
                            loggedInUid,

                    "createdByName" to
                            (
                                currentUserDisplayName.ifBlank {
                                    auth.currentUser?.email
                                        ?: auth.currentUser?.uid
                                        ?: ""
                                }
                            ),

                    "createdByRole" to
                            currentRole,

                    "createdAt" to
                            FieldValue.serverTimestamp()
                )

            if (
                !selectedAttachmentBase64.isNullOrEmpty()
            ) {

                serialData["attachmentBase64"] =
                    selectedAttachmentBase64 ?: ""

                serialData["attachmentName"] =
                    selectedAttachmentName

                serialData["attachmentMimeType"] =
                    selectedAttachmentMimeType

                serialData["hasAttachment"] =
                    true

            } else {

                serialData["attachmentMimeType"] =
                    ""

                serialData["hasAttachment"] =
                    false
            }

            transaction.set(
                serialRef,
                serialData
            )

            nextNumber

        }.addOnSuccessListener { number ->

            saveButton.isEnabled = true

            statusText.text =
                "Serial #$number সফলভাবে যোগ হয়েছে ✓"

            Toast.makeText(
                this,
                "Serial #$number সফলভাবে যোগ হয়েছে",
                Toast.LENGTH_LONG
            ).show()

            patientInput.text.clear()

            starCheck.isChecked = false

            description.text.clear()

            selectedAttachmentBase64 = null
            selectedAttachmentName = ""
            selectedAttachmentMimeType = ""
            selectedCareOfId = ""
            selectedCareOfName = ""
            selectedCareOfAddress = ""
            addSerialCareOfSpinner?.setText("", false)

            addSerialAttachmentText?.text =
                "কোনো ছবি নির্বাচন করা হয়নি"

            updateNextSerialPreview()

        }.addOnFailureListener { error ->

            saveButton.isEnabled = true

            statusText.text = ""

            Toast.makeText(
                this,
                "Serial তৈরি করা যায়নি: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // COUNTER ID
    // =========================================================

    private fun makeCounterId(
        date: String,
        doctorId: String
    ): String {

        return "${date}_${doctorId}"
    }

    // =========================================================
    // DISPLAY DATE
    // =========================================================

    private fun formatDisplayDate(
        date: String
    ): String {

        return try {

            val parsed =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).parse(date)

            if (parsed != null) {

                SimpleDateFormat(
                    "dd-MM-yyyy",
                    Locale.getDefault()
                ).format(parsed)

            } else {
                date
            }

        } catch (_: Exception) {
            date
        }
    }

    // =========================================================
    // GALLERY
    // =========================================================

    private fun openGallery() {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT
            ).apply {

                type = "*/*"

                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "image/*",
                        "application/pdf"
                    )
                )

                addCategory(
                    Intent.CATEGORY_OPENABLE
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        startActivityForResult(
            Intent.createChooser(
                intent,
                "ছবি / PDF ডকুমেন্ট নির্বাচন করুন"
            ),
            REQUEST_GALLERY
        )
    }

    // =========================================================
    // CAMERA
    // =========================================================

    private fun openCamera() {

        try {

            val intent =
                Intent(
                    MediaStore.ACTION_IMAGE_CAPTURE
                )

            startActivityForResult(
                intent,
                REQUEST_CAMERA
            )

        } catch (error: Exception) {

            Toast.makeText(
                this,
                "Camera চালু করা যায়নি",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // ACTIVITY RESULT
    // =========================================================

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (resultCode != RESULT_OK) {
            return
        }

        if (requestCode == REQUEST_REPORT_PDF) {

            val source = pendingReportPdfFile
            val destination = data?.data

            if (resultCode == RESULT_OK && source != null && destination != null) {
                try {
                    contentResolver.openOutputStream(destination)?.use { output ->
                        source.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    source.delete()
                    pendingReportPdfFile = null
                    Toast.makeText(
                        this,
                        "PDF সফলভাবে Save হয়েছে",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (error: Exception) {
                    Toast.makeText(
                        this,
                        "PDF Save করা যায়নি: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            return
        }

        when (requestCode) {

            REQUEST_GALLERY -> {

                val uri =
                    data?.data

                if (uri != null) {

                    processSelectedAttachment(
                        uri
                    )
                }
            }

            REQUEST_CAMERA -> {

                val bitmap =
                    data?.extras?.get(
                        "data"
                    ) as? Bitmap

                if (bitmap != null) {

                    processCameraBitmap(
                        bitmap
                    )
                }
            }
        }
    }

    // =========================================================
    // PROCESS GALLERY / DOCUMENT ATTACHMENT
    // =========================================================

    private fun processSelectedAttachment(
        uri: Uri
    ) {

        try {

            val mimeType =
                contentResolver.getType(uri)
                    ?: ""

            if (mimeType.startsWith("image/")) {

                val inputStream =
                    contentResolver.openInputStream(
                        uri
                    )

                val bitmap =
                    BitmapFactory.decodeStream(
                        inputStream
                    )

                inputStream?.close()

                if (bitmap == null) {

                    Toast.makeText(
                        this,
                        "ছবিটি পড়া যায়নি",
                        Toast.LENGTH_LONG
                    ).show()

                    return
                }

                selectedAttachmentBase64 =
                    bitmapToBase64(
                        bitmap
                    )

                selectedAttachmentName =
                    "gallery_${System.currentTimeMillis()}.jpg"

                selectedAttachmentMimeType =
                    "image/jpeg"

                addSerialAttachmentText?.text =
                    "📎 ছবি সংযুক্ত হয়েছে"

                return
            }

            if (mimeType == "application/pdf") {

                val bytes =
                    contentResolver.openInputStream(
                        uri
                    )?.use {
                        it.readBytes()
                    }

                if (bytes == null || bytes.isEmpty()) {

                    Toast.makeText(
                        this,
                        "ডকুমেন্টটি পড়া যায়নি",
                        Toast.LENGTH_LONG
                    ).show()

                    return
                }

                // Firestore document size is limited.
                // Keep direct PDF attachments safely below that limit.
                if (bytes.size > 500_000) {

                    Toast.makeText(
                        this,
                        "PDF ফাইলটি খুব বড়। 500 KB-এর মধ্যে PDF দিন।",
                        Toast.LENGTH_LONG
                    ).show()

                    return
                }

                selectedAttachmentBase64 =
                    Base64.encodeToString(
                        bytes,
                        Base64.NO_WRAP
                    )

                selectedAttachmentName =
                    "document_${System.currentTimeMillis()}.pdf"

                selectedAttachmentMimeType =
                    "application/pdf"

                addSerialAttachmentText?.text =
                    "📎 PDF ডকুমেন্ট সংযুক্ত হয়েছে"

                return
            }

            Toast.makeText(
                this,
                "শুধু ছবি অথবা PDF ডকুমেন্ট নির্বাচন করুন",
                Toast.LENGTH_LONG
            ).show()

        } catch (error: Exception) {

            Toast.makeText(
                this,
                "ডকুমেন্ট নির্বাচন করা যায়নি: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // PROCESS CAMERA BITMAP
    // =========================================================

    private fun processCameraBitmap(
        bitmap: Bitmap
    ) {

        try {

            selectedAttachmentBase64 =
                bitmapToBase64(
                    bitmap
                )

            selectedAttachmentName =
                "camera_${System.currentTimeMillis()}.jpg"

            addSerialAttachmentText?.text =
                "📷 Camera ছবি সংযুক্ত হয়েছে"

        } catch (error: Exception) {

            Toast.makeText(
                this,
                "Camera ছবি সংরক্ষণ করা যায়নি",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // BITMAP TO BASE64
    // =========================================================

    private fun bitmapToBase64(
        original: Bitmap
    ): String {

        val scaled =
            scaleBitmap(
                original,
                MAX_IMAGE_WIDTH,
                MAX_IMAGE_HEIGHT
            )

        val outputStream =
            ByteArrayOutputStream()

        scaled.compress(
            Bitmap.CompressFormat.JPEG,
            45,
            outputStream
        )

        val bytes =
            outputStream.toByteArray()

        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP
        )
    }

    // =========================================================
    // SCALE BITMAP
    // =========================================================

    private fun scaleBitmap(
        bitmap: Bitmap,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap {

        val width =
            bitmap.width.toFloat()

        val height =
            bitmap.height.toFloat()

        val ratio =
            minOf(
                maxWidth / width,
                maxHeight / height,
                1f
            )

        if (ratio >= 1f) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(
            bitmap,
            (width * ratio).toInt(),
            (height * ratio).toInt(),
            true
        )
    }

    // =========================================================
    // ADD DOCTOR
    // =========================================================

    private fun showAddDoctor() {

        if (currentRole != "admin") {

            Toast.makeText(
                this,
                "শুধুমাত্র Admin এই কাজ করতে পারবেন",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        currentScreen = SCREEN_ADD_DOCTOR

        setupSystemBars()

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    backgroundColor
                )
            }

        root.addView(
            createInnerTopBar(
                "Add Doctor"
            ) {
                showDashboard(currentRole)
            }
        )

        val scroll =
            PullToRefreshScrollView(this)

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(30)
                )
            }

        val heading =
            TextView(this).apply {

                text =
                    "নতুন ডাক্তার যোগ করুন"

                textSize = 25f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(20)
                )
            }

        content.addView(heading)

        addFormLabel(
            content,
            "ডাক্তারের নাম *"
        )

        val name =
            createFormInput(
                "যেমন: ডাঃ আব্দুল্লাহ আল মুজাহিদ"
            )

        content.addView(
            name,
            formParams()
        )

        addFormLabel(
            content,
            "শিক্ষাগত যোগ্যতা"
        )

        val qualification =
            createFormInput(
                "যেমন: MBBS, BCS (Health), MD"
            )

        content.addView(
            qualification,
            formParams()
        )

        addFormLabel(
            content,
            "বিশেষজ্ঞতা"
        )

        val specialty =
            createFormInput(
                "যেমন: Medicine Specialist"
            )

        content.addView(
            specialty,
            formParams()
        )

        addFormLabel(
            content,
            "রোগী দেখার সময়"
        )

        val visitingTime =
            createFormInput(
                "যেমন: শুক্রবার সন্ধ্যা ৬টা - রাত ১০টা"
            )

        content.addView(
            visitingTime,
            formParams()
        )

        addFormLabel(
            content,
            "মোবাইল নম্বর"
        )

        val mobile =
            createFormInput(
                "01XXXXXXXXX"
            )

        mobile.inputType =
            InputType.TYPE_CLASS_PHONE

        content.addView(
            mobile,
            formParams()
        )

        val status =
            createStatusText()

        content.addView(status)

        val save =
            createPrimaryButton(
                "💾  SAVE DOCTOR"
            )

        content.addView(
            save,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        )

        save.setOnClickListener {

            val doctorName =
                name.text.toString().trim()

            if (doctorName.isEmpty()) {

                name.error =
                    "ডাক্তারের নাম দিন"

                name.requestFocus()

                return@setOnClickListener
            }

            save.isEnabled = false

            status.text =
                "Doctor সংরক্ষণ হচ্ছে..."

            val data =
                hashMapOf(
                    "name" to doctorName,
                    "qualification" to
                            qualification.text.toString().trim(),
                    "specialty" to
                            specialty.text.toString().trim(),
                    "visitingTime" to
                            visitingTime.text.toString().trim(),
                    "mobile" to
                            mobile.text.toString().trim(),
                    "active" to true,
                    "createdByUid" to
                            (
                                auth.currentUser?.uid
                                    ?: ""
                            ),
                    "createdAt" to
                            FieldValue.serverTimestamp()
                )

            db.collection("doctors")
                .add(data)
                .addOnSuccessListener {

                    save.isEnabled = true

                    status.text =
                        "Doctor সফলভাবে যোগ হয়েছে ✓"

                    Toast.makeText(
                        this,
                        "Doctor সফলভাবে যোগ হয়েছে",
                        Toast.LENGTH_LONG
                    ).show()

                    name.text.clear()
                    qualification.text.clear()
                    specialty.text.clear()
                    visitingTime.text.clear()
                    mobile.text.clear()
                }
                .addOnFailureListener { error ->

                    save.isEnabled = true

                    status.text = ""

                    Toast.makeText(
                        this,
                        "Doctor যোগ করা যায়নি: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    // =========================================================
    // DOCTOR LIST
    // =========================================================

    private fun showDoctorList() {

        currentScreen = SCREEN_DOCTOR_LIST

        setupSystemBars()

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    backgroundColor
                )
            }

        root.addView(
            createInnerTopBar(
                "Doctor List"
            ) {
                showDashboard(currentRole)
            }
        )

        val scroll =
            PullToRefreshScrollView(this)

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(30)
                )
            }

        val heading =
            TextView(this).apply {

                text =
                    "👨‍⚕️ Doctor List"

                textSize = 25f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(15)
                )
            }

        content.addView(heading)

        val progress =
            ProgressBar(this)

        content.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(50)
            ).apply {
                gravity =
                    Gravity.CENTER
            }
        )

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        db.collection("doctors")
            .whereEqualTo(
                "active",
                true
            )
            .get()
            .addOnSuccessListener { result ->

                progress.visibility =
                    View.GONE

                if (result.isEmpty) {

                    content.addView(
                        createEmptyText(
                            "কোনো Doctor পাওয়া যায়নি"
                        )
                    )

                    return@addOnSuccessListener
                }

                val doctors =
                    result.documents.sortedBy {

                        (
                            it.getString("name")
                                ?: ""
                        ).lowercase()
                    }

                for (document in doctors) {

                    val doctorName =
                        document.getString(
                            "name"
                        ) ?: "Unknown Doctor"

                    val specialty =
                        document.getString(
                            "specialty"
                        ) ?: ""

                    val qualification =
                        document.getString(
                            "qualification"
                        ) ?: ""

                    val visitingTime =
                        document.getString(
                            "visitingTime"
                        ) ?: ""

                    val mobile =
                        document.getString(
                            "mobile"
                        ) ?: ""

                    val card =
                        createDoctorListCard(
                            document.id,
                            doctorName,
                            specialty,
                            qualification,
                            visitingTime,
                            mobile
                        )

                    card.setOnClickListener {

                        showDoctorDateSelection(
                            document.id,
                            doctorName
                        )
                    }

                    content.addView(card)
                }
            }
            .addOnFailureListener { error ->

                progress.visibility =
                    View.GONE

                Toast.makeText(
                    this,
                    "Doctor List পাওয়া যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // DOCTOR CARD
    // =========================================================

    private fun createDoctorListCard(
        doctorId: String,
        name: String,
        specialty: String,
        qualification: String,
        visitingTime: String,
        mobile: String
    ): LinearLayout {

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(16),
                    dp(18),
                    dp(16)
                )

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(16)
                    )

                elevation =
                    dp(3).toFloat()
            }

        val title =
            TextView(this).apply {

                text =
                    "👨‍⚕️ $name"

                textSize = 20f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    primaryColor
                )
            }

        card.addView(title)

        if (qualification.isNotEmpty()) {
            card.addView(
                createInfoText(
                    "🎓 Qualification",
                    qualification
                )
            )
        }

        if (specialty.isNotEmpty()) {
            card.addView(
                createInfoText(
                    "🩺 Specialty",
                    specialty
                )
            )
        }

        if (visitingTime.isNotEmpty()) {
            card.addView(
                createInfoText(
                    "🕐 Visiting",
                    visitingTime
                )
            )
        }

        if (mobile.isNotEmpty()) {
            card.addView(
                createInfoText(
                    "📱 Mobile",
                    mobile
                )
            )
        }

        if (currentRole == "admin") {

            val buttonRow =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.END

                    setPadding(
                        0,
                        dp(12),
                        0,
                        0
                    )
                }

            val editButton =
                createSmallButton(
                    "✏ Edit"
                )

            editButton.setOnClickListener {

                showEditDoctorDialog(
                    doctorId
                )
            }

            val deleteButton =
                createSmallButton(
                    "🗑 Delete"
                )

            deleteButton.setOnClickListener {

                confirmDeleteDoctor(
                    doctorId,
                    name
                )
            }

            buttonRow.addView(editButton)
            buttonRow.addView(deleteButton)

            card.addView(buttonRow)
        }

        card.addView(
            createInfoText(
                "",
                "Tap করুন → তারিখ নির্বাচন করে Serial দেখুন"
            )
        )

        card.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                setMargins(
                    0,
                    dp(6),
                    0,
                    dp(6)
                )
            }

        return card
    }

    // =========================================================
    // EDIT DOCTOR
    // =========================================================

    private fun showEditDoctorDialog(
        doctorId: String
    ) {

        if (currentRole != "admin") {

            Toast.makeText(
                this,
                "শুধুমাত্র Admin Doctor Edit করতে পারবেন",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        db.collection("doctors")
            .document(doctorId)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    Toast.makeText(
                        this,
                        "Doctor পাওয়া যায়নি",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                val container =
                    LinearLayout(this).apply {

                        orientation =
                            LinearLayout.VERTICAL

                        setPadding(
                            dp(20),
                            dp(5),
                            dp(20),
                            0
                        )
                    }

                val name =
                    createFormInput(
                        "ডাক্তারের নাম"
                    )

                name.setText(
                    document.getString("name")
                        ?: ""
                )

                val qualification =
                    createFormInput(
                        "Qualification"
                    )

                qualification.setText(
                    document.getString(
                        "qualification"
                    ) ?: ""
                )

                val specialty =
                    createFormInput(
                        "Specialty"
                    )

                specialty.setText(
                    document.getString(
                        "specialty"
                    ) ?: ""
                )

                val visitingTime =
                    createFormInput(
                        "Visiting Time"
                    )

                visitingTime.setText(
                    document.getString(
                        "visitingTime"
                    ) ?: ""
                )

                val mobile =
                    createFormInput(
                        "Mobile"
                    )

                mobile.inputType =
                    InputType.TYPE_CLASS_PHONE

                mobile.setText(
                    document.getString(
                        "mobile"
                    ) ?: ""
                )

                container.addView(
                    name,
                    formParams()
                )

                container.addView(
                    qualification,
                    formParams()
                )

                container.addView(
                    specialty,
                    formParams()
                )

                container.addView(
                    visitingTime,
                    formParams()
                )

                container.addView(
                    mobile,
                    formParams()
                )

                AlertDialog.Builder(this)
                    .setTitle(
                        "✏ Edit Doctor"
                    )
                    .setView(container)
                    .setNegativeButton(
                        "Cancel",
                        null
                    )
                    .setPositiveButton(
                        "Save"
                    ) { _: DialogInterface, _: Int ->

                        val doctorName =
                            name.text.toString().trim()

                        if (
                            doctorName.isEmpty()
                        ) {

                            Toast.makeText(
                                this,
                                "Doctor Name দিন",
                                Toast.LENGTH_SHORT
                            ).show()

                            return@setPositiveButton
                        }

                        val updateData =
                            hashMapOf<String, Any>(
                                "name" to doctorName,
                                "qualification" to
                                        qualification.text.toString().trim(),
                                "specialty" to
                                        specialty.text.toString().trim(),
                                "visitingTime" to
                                        visitingTime.text.toString().trim(),
                                "mobile" to
                                        mobile.text.toString().trim()
                            )

                        db.collection("doctors")
                            .document(doctorId)
                            .update(
                                updateData
                            )
                            .addOnSuccessListener {

                                Toast.makeText(
                                    this,
                                    "Doctor Update হয়েছে",
                                    Toast.LENGTH_SHORT
                                ).show()

                                showDoctorList()
                            }
                            .addOnFailureListener { error ->

                                Toast.makeText(
                                    this,
                                    "Update ব্যর্থ: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .show()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Doctor তথ্য পাওয়া যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // DELETE DOCTOR
    // =========================================================

    private fun confirmDeleteDoctor(
        doctorId: String,
        doctorName: String
    ) {

        if (currentRole != "admin") {

            Toast.makeText(
                this,
                "শুধুমাত্র Admin Doctor Delete করতে পারবেন",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        AlertDialog.Builder(this)
            .setTitle(
                "Doctor Delete"
            )
            .setMessage(
                "আপনি কি \"$doctorName\" Doctor-কে Delete করতে চান?"
            )
            .setNegativeButton(
                "না",
                null
            )
            .setPositiveButton(
                "হ্যাঁ, Delete"
            ) { _, _ ->

                db.collection("doctors")
                    .document(doctorId)
                    .delete()
                    .addOnSuccessListener {

                        Toast.makeText(
                            this,
                            "Doctor Delete হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()

                        showDoctorList()
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            this,
                            "Delete ব্যর্থ: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    // =========================================================
    // DOCTOR DATE
    // =========================================================

    private fun showDoctorDateSelection(
        doctorId: String,
        doctorName: String
    ) {

        val calendar =
            Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->

                val selected =
                    Calendar.getInstance()

                selected.set(
                    year,
                    month,
                    day
                )

                val date =
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                    ).format(
                        selected.time
                    )

                showDoctorSerials(
                    doctorId,
                    doctorName,
                    date
                )
            },
            calendar.get(
                Calendar.YEAR
            ),
            calendar.get(
                Calendar.MONTH
            ),
            calendar.get(
                Calendar.DAY_OF_MONTH
            )
        ).apply {

            setTitle(
                "তারিখ নির্বাচন করুন"
            )

            show()
        }
    }

    // =========================================================
    // DOCTOR SERIAL
    // =========================================================

    private fun showDoctorSerials(
        doctorId: String,
        doctorName: String,
        date: String
    ) {

        serialParentScreen =
            SCREEN_DOCTOR_LIST

        showSerialListPage(
            title =
                "Doctor: $doctorName",
            filterField =
                "doctorId",
            filterValue =
                doctorId,
            date =
                date
        )
    }

    // =========================================================
    // CARE OF LIST
    // =========================================================

    private fun showCareOfList() {

        currentScreen = SCREEN_CAREOF_LIST

        setupSystemBars()

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    backgroundColor
                )
            }

        root.addView(
            createInnerTopBar(
                "Care Of List"
            ) {
                showDashboard(currentRole)
            }
        )

        val scroll =
            PullToRefreshScrollView(this)

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(30)
                )
            }

        val heading =
            TextView(this).apply {

                text =
                    "👤 Care Of List"

                textSize = 25f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(15)
                )
            }

        content.addView(heading)

        val progress =
            ProgressBar(this)

        content.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(50)
            ).apply {
                gravity =
                    Gravity.CENTER
            }
        )

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        db.collection("careOf")
            .whereEqualTo(
                "active",
                true
            )
            .get()
            .addOnSuccessListener { result ->

                progress.visibility =
                    View.GONE

                if (result.isEmpty) {

                    content.addView(
                        createEmptyText(
                            "কোনো Care Of পাওয়া যায়নি"
                        )
                    )

                    return@addOnSuccessListener
                }

                val careOfList =
                    result.documents.sortedBy {

                        (
                            it.getString("name")
                                ?: ""
                        ).lowercase()
                    }

                for (document in careOfList) {

                    val name =
                        document.getString(
                            "name"
                        ) ?: "Unknown"

                    val address =
                        document.getString(
                            "address"
                        ) ?: ""

                    val card =
                        createCareOfListCard(
                            document.id,
                            name,
                            address,
                            document.getString("createdByUid") ?: ""
                        )

                    card.setOnClickListener {

                        showCareOfDateSelection(
                            document.id,
                            name
                        )
                    }

                    content.addView(card)
                }
            }
            .addOnFailureListener { error ->

                progress.visibility =
                    View.GONE

                Toast.makeText(
                    this,
                    "Care Of List পাওয়া যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // CARE OF CARD
    // =========================================================

    private fun createCareOfListCard(
        careOfId: String,
        name: String,
        address: String,
        creatorUid: String
    ): LinearLayout {

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(16),
                    dp(18),
                    dp(16)
                )

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(16)
                    )

                elevation =
                    dp(3).toFloat()
            }

        val title =
            TextView(this).apply {

                text =
                    "👤 $name"

                textSize = 20f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    primaryColor
                )
            }

        card.addView(title)

        if (address.isNotEmpty()) {

            card.addView(
                createInfoText(
                    "📍 Address",
                    address
                )
            )
        }

        val buttonRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.END

                setPadding(
                    0,
                    dp(12),
                    0,
                    0
                )
            }

        val currentUid = auth.currentUser?.uid ?: ""
        val canEditDelete = currentRole == "admin" || currentUid == creatorUid

        if (canEditDelete) {
            val editButton = createSmallButton("✏ Edit")
            editButton.setOnClickListener { showEditCareOfDialog(careOfId) }
            buttonRow.addView(editButton)

            val deleteButton = createSmallButton("🗑 Delete")
            deleteButton.setOnClickListener { confirmDeleteCareOf(careOfId, name) }
            buttonRow.addView(deleteButton)
        }

        card.addView(buttonRow)

        card.addView(
            createInfoText(
                "",
                "Tap করুন → তারিখ নির্বাচন করে Serial দেখুন"
            )
        )

        card.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                setMargins(
                    0,
                    dp(6),
                    0,
                    dp(6)
                )
            }

        return card
    }

    // =========================================================
    // EDIT CARE OF
    // =========================================================

    private fun showEditCareOfDialog(
        careOfId: String
    ) {

        db.collection("careOf")
            .document(careOfId)
            .get()
            .addOnSuccessListener { document ->

                val ownerUid = document.getString("createdByUid") ?: ""
                if (currentRole != "admin" && ownerUid != (auth.currentUser?.uid ?: "")) {
                    Toast.makeText(this, "এই Care Of শুধু যিনি যোগ করেছেন তিনি Edit করতে পারবেন", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                if (!document.exists()) {

                    Toast.makeText(
                        this,
                        "Care Of পাওয়া যায়নি",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                val container =
                    LinearLayout(this).apply {

                        orientation =
                            LinearLayout.VERTICAL

                        setPadding(
                            dp(20),
                            dp(5),
                            dp(20),
                            0
                        )
                    }

                val name =
                    createFormInput(
                        "Care Of Name"
                    )

                name.setText(
                    document.getString(
                        "name"
                    ) ?: ""
                )

                val address =
                    createFormInput(
                        "Address"
                    )

                address.setSingleLine(false)

                address.minLines = 3

                address.gravity =
                    Gravity.TOP

                address.setText(
                    document.getString(
                        "address"
                    ) ?: ""
                )

                container.addView(
                    name,
                    formParams()
                )

                container.addView(
                    address,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(90)
                    ).apply {
                        setMargins(
                            0,
                            0,
                            0,
                            dp(14)
                        )
                    }
                )

                AlertDialog.Builder(this)
                    .setTitle(
                        "✏ Edit Care Of"
                    )
                    .setView(container)
                    .setNegativeButton(
                        "Cancel",
                        null
                    )
                    .setPositiveButton(
                        "Save"
                    ) { _: DialogInterface, _: Int ->

                        val newName =
                            name.text.toString().trim()

                        val newAddress =
                            address.text.toString().trim()

                        if (newName.isEmpty()) {

                            Toast.makeText(
                                this,
                                "Care Of Name দিন",
                                Toast.LENGTH_SHORT
                            ).show()

                            return@setPositiveButton
                        }

                        db.collection("careOf")
                            .document(careOfId)
                            .update(
                                mapOf(
                                    "name" to newName,
                                    "address" to newAddress
                                )
                            )
                            .addOnSuccessListener {

                                Toast.makeText(
                                    this,
                                    "Care Of Update হয়েছে",
                                    Toast.LENGTH_SHORT
                                ).show()

                                showCareOfList()
                            }
                            .addOnFailureListener { error ->

                                Toast.makeText(
                                    this,
                                    "Update ব্যর্থ: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .show()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Care Of তথ্য পাওয়া যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // DELETE CARE OF
    // =========================================================

    private fun confirmDeleteCareOf(
        careOfId: String,
        careOfName: String
    ) {

        val currentUid = auth.currentUser?.uid ?: ""
        if (currentRole != "admin") {
            db.collection("careOf").document(careOfId).get().addOnSuccessListener { doc ->
                if ((doc.getString("createdByUid") ?: "") != currentUid) {
                    Toast.makeText(this, "এই Care Of শুধু যিনি যোগ করেছেন তিনি Delete করতে পারবেন", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                confirmDeleteCareOfAfterCheck(careOfId, careOfName)
            }
            return
        }

        confirmDeleteCareOfAfterCheck(careOfId, careOfName)
    }

    private fun confirmDeleteCareOfAfterCheck(
        careOfId: String,
        careOfName: String
    ) {
        AlertDialog.Builder(this)
            .setTitle(
                "Care Of Delete"
            )
            .setMessage(
                "আপনি কি \"$careOfName\" Care Of-কে Delete করতে চান?"
            )
            .setNegativeButton(
                "না",
                null
            )
            .setPositiveButton(
                "হ্যাঁ, Delete"
            ) { _, _ ->

                db.collection("careOf")
                    .document(careOfId)
                    .delete()
                    .addOnSuccessListener {

                        Toast.makeText(
                            this,
                            "Care Of Delete হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()

                        showCareOfList()
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            this,
                            "Delete ব্যর্থ: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    // =========================================================
    // ADD CARE OF
    // =========================================================

    private fun showAddCareOf() {

        if (auth.currentUser == null) {
            Toast.makeText(this, "Login প্রয়োজন", Toast.LENGTH_LONG).show()
            return
        }

        currentScreen = SCREEN_ADD_CAREOF

        setupSystemBars()

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    backgroundColor
                )
            }

        root.addView(
            createInnerTopBar(
                "Add Care Of"
            ) {
                showDashboard(currentRole)
            }
        )

        val scroll =
            PullToRefreshScrollView(this)

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(30)
                )
            }

        val heading =
            TextView(this).apply {

                text =
                    "নতুন Care Of যোগ করুন"

                textSize = 25f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(20)
                )
            }

        content.addView(heading)

        addFormLabel(
            content,
            "Care Of নাম *"
        )

        val name =
            createFormInput(
                "Care Of Name"
            )

        content.addView(
            name,
            formParams()
        )

        addFormLabel(
            content,
            "ঠিকানা / Address"
        )

        val address =
            createFormInput(
                "যেমন: ফতুল্লা, নারায়ণগঞ্জ"
            )

        address.setSingleLine(false)

        address.minLines = 3

        address.gravity =
            Gravity.TOP

        content.addView(
            address,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(90)
            ).apply {

                setMargins(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }
        )

        val status =
            createStatusText()

        content.addView(status)

        val save =
            createPrimaryButton(
                "💾  SAVE CARE OF"
            )

        content.addView(
            save,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        )

        save.setOnClickListener {

            val careName =
                name.text.toString().trim()

            val careAddress =
                address.text.toString().trim()

            if (careName.isEmpty()) {

                name.error =
                    "Care Of নাম দিন"

                name.requestFocus()

                return@setOnClickListener
            }

            save.isEnabled = false

            status.text =
                "Care Of সংরক্ষণ হচ্ছে..."

            findDuplicateCareOf(
                careName,
                careAddress,
                onFound = { existing ->

                    save.isEnabled = true

                    val existingName =
                        existing.getString("name") ?: careName

                    val existingAddress =
                        existing.getString("address") ?: careAddress

                    status.text =
                        "এই Care Of আগে থেকেই আছে"

                    AlertDialog.Builder(this)
                        .setTitle("Care Of আগে থেকেই আছে")
                        .setMessage(
                            if (existingAddress.isBlank()) {
                                existingName
                            } else {
                                "$existingName\n$existingAddress"
                            }
                        )
                        .setPositiveButton("ঠিক আছে", null)
                        .show()
                },
                onNotFound = {

                    saveCareOfWithoutDuplicate(
                        careName,
                        careAddress,
                        onSaved = {
                            save.isEnabled = true
                            status.text = "Care Of সফলভাবে যোগ হয়েছে ✓"
                            Toast.makeText(
                                this,
                                "Care Of সফলভাবে যোগ হয়েছে",
                                Toast.LENGTH_SHORT
                            ).show()
                            name.text.clear()
                            address.text.clear()
                        },
                        onDuplicate = { existing ->
                            save.isEnabled = true
                            status.text = "এই Care Of আগে থেকেই আছে"
                            val existingName = existing.getString("name") ?: careName
                            val existingAddress = existing.getString("address") ?: careAddress
                            AlertDialog.Builder(this)
                                .setTitle("Care Of আগে থেকেই আছে")
                                .setMessage(if (existingAddress.isBlank()) existingName else "$existingName\n$existingAddress")
                                .setPositiveButton("ঠিক আছে", null)
                                .show()
                        },
                        onError = { error ->
                            save.isEnabled = true
                            status.text = ""
                            Toast.makeText(
                                this,
                                "Care Of যোগ করা যায়নি: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            )
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    // =========================================================
    // CARE OF DATE
    // =========================================================

    private fun showCareOfDateSelection(
        careOfId: String,
        careOfName: String
    ) {

        val calendar =
            Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->

                val selected =
                    Calendar.getInstance()

                selected.set(
                    year,
                    month,
                    day
                )

                val date =
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                    ).format(
                        selected.time
                    )

                showCareOfSerials(
                    careOfId,
                    careOfName,
                    date
                )
            },
            calendar.get(
                Calendar.YEAR
            ),
            calendar.get(
                Calendar.MONTH
            ),
            calendar.get(
                Calendar.DAY_OF_MONTH
            )
        ).apply {

            setTitle(
                "তারিখ নির্বাচন করুন"
            )

            show()
        }
    }

    // =========================================================
    // CARE OF SERIAL
    // =========================================================

    private fun showCareOfSerials(
        careOfId: String,
        careOfName: String,
        date: String
    ) {

        serialParentScreen =
            SCREEN_CAREOF_LIST

        showSerialListPage(
            title =
                "Care Of: $careOfName",
            filterField =
                "careOfId",
            filterValue =
                careOfId,
            date =
                date
        )
    }

    // =========================================================
    // SERIAL LIST PAGE
    // =========================================================

    private fun showSerialListPage(
        title: String,
        filterField: String,
        filterValue: String,
        date: String
    ) {
        clearActiveListeners()
        lastListTitle = title
        lastListFilterField = filterField
        lastListFilterValue = filterValue
        lastListDate = date
        currentScreen = if (filterField == "doctorId") SCREEN_DOCTOR_SERIAL else SCREEN_CAREOF_SERIAL
        setupSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        root.addView(createInnerTopBar("Serial List") {
            if (filterField == "doctorId") showDoctorList() else showCareOfList()
        })

        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(30))
        }
        content.addView(TextView(this).apply {
            text = title
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
        })
        content.addView(TextView(this).apply {
            text = "📅 তারিখ: ${formatDisplayDate(date)}"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryColor)
            setPadding(dp(5), dp(5), dp(5), dp(15))
        })
        val progress = ProgressBar(this)
        content.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(50)).apply { gravity = Gravity.CENTER })
        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listContainer)
        scroll.addView(content)
        scroll.onPullToRefresh = { refreshCurrentScreen() }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        activeListeners.add(
            db.collection("serials")
                .whereEqualTo(filterField, filterValue)
                .whereEqualTo("createdDate", date)
                .addSnapshotListener { result, error ->
                    progress.visibility = View.GONE
                    listContainer.removeAllViews()
                    if (error != null || result == null) {
                        if (error != null) Toast.makeText(this, "Serial List পাওয়া যায়নি: ${error.message}", Toast.LENGTH_LONG).show()
                        return@addSnapshotListener
                    }
                    val visibleDocuments = result.documents.filterNot {
                        (it.getString("status") ?: "Waiting") == "Cancelled"
                    }
                    if (visibleDocuments.isEmpty()) {
                        listContainer.addView(createEmptyText("এই তারিখে কোনো Serial পাওয়া যায়নি"))
                        return@addSnapshotListener
                    }
                    visibleDocuments.sortedWith(
                        compareByDescending<DocumentSnapshot> { it.getBoolean("patientVip") ?: false }
                            .thenBy { it.getLong("number") ?: 0L }
                    ).forEach { listContainer.addView(createSerialCard(it)) }
                }
        )
    }

    // =========================================================
    // SERIAL CARD
    // =========================================================

    private fun createSerialCard(
        document: DocumentSnapshot,
        displayNumber: Long? = null
    ): LinearLayout {

        val isVip =
            document.getBoolean(
                "patientVip"
            ) ?: false

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(14),
                    dp(16),
                    dp(14)
                )

                background =
                    roundedCardDrawable(
                        if (isVip) {
                            Color.rgb(
                                255,
                                248,
                                225
                            )
                        } else {
                            Color.WHITE
                        },
                        dp(15)
                    )

                elevation =
                    dp(3).toFloat()
            }

        val number =
            displayNumber?.toString()
                ?: document.getLong("number")
                    ?.toString()
                ?: document.getString(
                    "number"
                )
                ?: "-"

        val patient =
            document.getString(
                "patient"
            ) ?: "-"

        val careOf =
            document.getString(
                "careOfName"
            )
                ?: document.getString(
                    "careOf"
                )
                ?: "-"

        val careOfAddress =
            document.getString(
                "careOfAddress"
            ) ?: ""

        val doctor =
            document.getString(
                "doctorName"
            )
                ?: document.getString(
                    "doctor"
                )
                ?: "-"

        val status =
            document.getString(
                "status"
            ) ?: "Waiting"

        val description =
            document.getString(
                "description"
            ) ?: ""

        val createdByRole =
            document.getString(
                "createdByRole"
            )
                ?: "-"

        val createdByName =
            document.getString(
                "createdByName"
            )
                ?: document.getString(
                    "createdBy"
                )
                ?: "-"

        val title =
            TextView(this).apply {

                text =
                    if (isVip) {
                        "⭐ VIP  •  Serial #$number"
                    } else {
                        "Serial #$number"
                    }

                textSize = 21f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    if (isVip) {
                        Color.rgb(
                            190,
                            120,
                            0
                        )
                    } else {
                        primaryColor
                    }
                )
            }

        card.addView(title)

        card.addView(
            createInfoText(
                "👤 Patient",
                patient
            )
        )

        card.addView(
            createInfoText(
                "👨‍⚕️ Doctor",
                doctor
            )
        )

        if (careOf != "-") {

            val careText =
                if (careOfAddress.isBlank()) {
                    careOf
                } else {
                    "$careOf\n$careOfAddress"
                }

            card.addView(
                createInfoText(
                    "👤 Care Of",
                    careText
                )
            )
        }

        if (description.isNotEmpty()) {

            card.addView(
                createInfoText(
                    "📝 বিবরণ",
                    description
                )
            )
        }

        if (document.getBoolean("hasAttachment") == true) {
            val attachmentButton = createSmallButton(
                "📎 Open: ${document.getString("attachmentName") ?: "সংযুক্ত ফাইল"}"
            )
            attachmentButton.setOnClickListener { showAttachmentPreview(document) }
            card.addView(attachmentButton, LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(6), 0, 0) })
        }

        card.addView(
            createInfoText(
                "✍ Created By",
                "$createdByName ($createdByRole)"
            )
        )

        card.addView(
            createInfoText(
                "📌 Status",
                status
            )
        )

        // Show exactly who marked this serial as Completed.
        // This is displayed only while the current status is Completed.
        if (status.equals("Completed", true)) {
            val completedByName =
                document.getString("completedByName")
                    ?: document.getString("completedBy")
                    ?: "-"
            val completedByRole =
                document.getString("completedByRole")
                    ?: ""
            val completedByText =
                if (completedByRole.isBlank()) {
                    completedByName
                } else {
                    "$completedByName ($completedByRole)"
                }

            card.addView(
                createInfoText(
                    "✅ Completed By",
                    completedByText
                )
            )
        }

        val buttonRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.END

                setPadding(
                    0,
                    dp(10),
                    0,
                    0
                )
            }

        val currentUid =
            auth.currentUser?.uid
                ?: ""

        val creatorUid =
            document.getString(
                "createdByUid"
            ) ?: ""

        val canEditDelete =
            currentUid == creatorUid ||
                    currentRole == "admin"

        if (isVip) {
            val vipLabel = TextView(this).apply {
                text = "⭐ VIP"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(190, 120, 0))
                setPadding(0, dp(6), 0, dp(6))
            }
            buttonRow.addView(vipLabel)
        }

        val statusButton =
            createSmallButton(
                "📌 $status"
            )

        when (status.lowercase(Locale.getDefault())) {
            "completed" -> {
                statusButton.background = roundedCardDrawable(Color.rgb(46, 125, 50), dp(10))
                statusButton.setTextColor(Color.WHITE)
            }
            "present" -> {
                statusButton.background = roundedCardDrawable(Color.rgb(251, 192, 45), dp(10))
                statusButton.setTextColor(Color.BLACK)
            }
            "waiting" -> {
                statusButton.background = roundedCardDrawable(Color.rgb(198, 40, 40), dp(10))
                statusButton.setTextColor(Color.WHITE)
            }
            else -> {
                statusButton.background = roundedCardDrawable(Color.rgb(117, 117, 117), dp(10))
                statusButton.setTextColor(Color.WHITE)
            }
        }

        // Only Admin/Operator may change serial status. Normal Users cannot use this button.
        if (currentRole == "admin" || currentRole == "operator") {
            statusButton.setOnClickListener { showStatusDialog(document.id, status) }
            buttonRow.addView(statusButton)
        }

        if (canEditDelete) {

            val edit =
                createSmallButton(
                    "✏ Edit"
                )

            edit.setOnClickListener {

                showEditSerialDialog(
                    document
                )
            }

            buttonRow.addView(edit)

            val delete =
                createSmallButton(
                    "🗑 Delete"
                )

            delete.setOnClickListener {

                confirmDeleteSerial(
                    document.id
                )
            }

            buttonRow.addView(delete)
        }
card.addView(buttonRow)

        card.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                setMargins(
                    0,
                    dp(6),
                    0,
                    dp(6)
                )
            }

        return card
    }

    // =========================================================
    // ATTACHMENT PREVIEW (VIEW ONLY)
    // =========================================================

    private fun showAttachmentPreview(document: DocumentSnapshot) {
        val base64 = document.getString("attachmentBase64") ?: ""
        val mime = document.getString("attachmentMimeType") ?: ""
        if (base64.isBlank()) {
            Toast.makeText(this, "সংযুক্ত ফাইলটি পাওয়া যায়নি", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (mime.startsWith("image/")) {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) throw IllegalStateException("ছবিটি পড়া যায়নি")
                val image = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                AlertDialog.Builder(this)
                    .setTitle(document.getString("attachmentName") ?: "ছবি")
                    .setView(image)
                    .setPositiveButton("বন্ধ করুন", null)
                    .show()
                return
            }

            if (mime == "application/pdf") {
                val file = java.io.File.createTempFile("mdc_preview_", ".pdf", cacheDir)
                file.outputStream().use { it.write(bytes) }
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                if (renderer.pageCount == 0) throw IllegalStateException("PDF-এ কোনো পৃষ্ঠা নেই")
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                descriptor.close()
                file.delete()
                val image = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                AlertDialog.Builder(this)
                    .setTitle((document.getString("attachmentName") ?: "PDF") + " • প্রথম পৃষ্ঠা")
                    .setView(image)
                    .setPositiveButton("বন্ধ করুন", null)
                    .show()
                return
            }

            Toast.makeText(this, "এই ফাইলের Preview সমর্থিত নয়", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ফাইলটি খোলা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================
    // EDIT SERIAL
    // =========================================================

    private fun showEditSerialDialog(
        document: DocumentSnapshot
    ) {
        val uid = auth.currentUser?.uid ?: ""
        val owner = document.getString("createdByUid") ?: ""
        if (currentRole != "admin" && uid != owner) {
            Toast.makeText(this, "এই Serial শুধু যিনি দিয়েছেন তিনি Edit করতে পারবেন", Toast.LENGTH_LONG).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(5), dp(20), dp(5))
        }

        val patientInput = createFormInput("Patient Name")
        patientInput.setText(document.getString("patient") ?: "")
        container.addView(patientInput, formParams())

        val doctorLabel = TextView(this).apply {
            text = "ডাক্তার নির্বাচন করুন"
            textSize = 16f
            setTextColor(darkText)
            setPadding(0, dp(8), 0, dp(5))
        }
        container.addView(doctorLabel)

        val doctorSpinner = Spinner(this)
        val doctorEntries = mutableListOf<String>()
        val doctorEntryIds = mutableListOf<String>()
        doctorEntries.add("বর্তমান ডাক্তার")
        doctorEntryIds.add(document.getString("doctorId") ?: "")
        for (i in doctorNames.indices) {
            if (doctorNames[i].isNotBlank() && !doctorEntryIds.contains(doctorIds.getOrNull(i) ?: "")) {
                doctorEntries.add(doctorNames[i])
                doctorEntryIds.add(doctorIds.getOrNull(i) ?: "")
            }
        }
        val currentDoctorId = document.getString("doctorId") ?: ""
        val currentDoctorName = document.getString("doctorName") ?: document.getString("doctor") ?: ""
        if (currentDoctorName.isNotBlank() && doctorEntries[0] == "বর্তমান ডাক্তার") doctorEntries[0] = currentDoctorName
        doctorSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, doctorEntries)
        var editedDoctorId = currentDoctorId
        var editedDoctorName = currentDoctorName
        val initialDoctorIndex = doctorEntryIds.indexOf(currentDoctorId).coerceAtLeast(0)
        doctorSpinner.setSelection(initialDoctorIndex)
        doctorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                editedDoctorId = doctorEntryIds.getOrNull(position).orEmpty()
                editedDoctorName = doctorEntries.getOrNull(position).orEmpty()
            }
        }
        container.addView(doctorSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)))

        val careLabel = TextView(this).apply {
            text = "কেয়ার অফ নির্বাচন করুন"
            textSize = 16f
            setTextColor(darkText)
            setPadding(0, dp(8), 0, dp(5))
        }
        container.addView(careLabel)

        val careInput = AutoCompleteTextView(this).apply {
            hint = "কেয়ার অফের নাম লিখুন / নির্বাচন করুন"
            threshold = 0
            textSize = 17f
            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedCardDrawable(Color.WHITE, dp(12))
        }
        val editCareOptions = careOfOptions.toList()
        val careAdapter = object : ArrayAdapter<CareOfOption>(
            this,
            android.R.layout.simple_list_item_1,
            editCareOptions.toMutableList()
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val option = getItem(position)
                view.text = if (option?.address.isNullOrBlank()) option?.name ?: "" else "${option?.name}\n${option?.address}"
                view.setSingleLine(false)
                view.maxLines = 3
                view.setPadding(dp(14), dp(10), dp(14), dp(10))
                view.textSize = 16f
                return view
            }
        }
        careInput.setAdapter(careAdapter)
        val currentCareId = document.getString("careOfId") ?: ""
        val currentCareName = document.getString("careOfName") ?: document.getString("careOf") ?: ""
        val currentCareAddress = document.getString("careOfAddress") ?: ""
        var editedCareId = currentCareId
        var editedCareName = currentCareName
        var editedCareAddress = currentCareAddress
        val currentCare = editCareOptions.firstOrNull { it.id == currentCareId }
        careInput.setText((currentCare ?: CareOfOption(currentCareId, currentCareName, currentCareAddress)).toString(), false)
        careInput.setOnClickListener { careInput.showDropDown() }
        careInput.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as? CareOfOption ?: return@setOnItemClickListener
            editedCareId = selected.id
            editedCareName = selected.name
            editedCareAddress = selected.address
            careInput.setText(selected.toString(), false)
            careInput.error = null
        }
        container.addView(careInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)))

        val dateLabel = TextView(this).apply {
            text = "তারিখ নির্বাচন করুন"
            textSize = 16f
            setTextColor(darkText)
            setPadding(0, dp(8), 0, dp(5))
        }
        container.addView(dateLabel)
        var editedDate = document.getString("createdDate") ?: todayKey()
        val dateButton = Button(this).apply {
            text = formatDisplayDate(editedDate)
            setAllCaps(false)
            textSize = 17f
            setOnClickListener {
                val parts = editedDate.split("-")
                val y = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                val m = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
                DatePickerDialog(this@MainActivity, { _, year, month, day ->
                    editedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    text = formatDisplayDate(editedDate)
                }, y, m, d).show()
            }
        }
        container.addView(dateButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)))

        val descriptionInput = createFormInput("Description")
        descriptionInput.setSingleLine(false)
        descriptionInput.minLines = 4
        descriptionInput.gravity = Gravity.TOP
        descriptionInput.setText(document.getString("description") ?: "")
        container.addView(descriptionInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(110)))

        val vipCheck = CheckBox(this).apply {
            text = "⭐ VIP Patient"
            textSize = 17f
            isChecked = document.getBoolean("patientVip") ?: false
        }
        container.addView(vipCheck, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(55)))

        AlertDialog.Builder(this)
            .setTitle("সম্পূর্ণ Serial Edit")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val newPatient = patientInput.text.toString().trim()
                if (newPatient.isEmpty()) {
                    Toast.makeText(this, "Patient Name দিন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (editedDoctorId.isBlank() || editedDoctorName.isBlank()) {
                    Toast.makeText(this, "Doctor নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (editedCareId.isBlank() || editedCareName.isBlank()) {
                    Toast.makeText(this, "Care Of নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updates = mapOf<String, Any>(
                    "patient" to newPatient,
                    "doctorId" to editedDoctorId,
                    "doctorName" to editedDoctorName,
                    "careOfId" to editedCareId,
                    "careOfName" to editedCareName,
                    "careOfAddress" to editedCareAddress,
                    "createdDate" to editedDate,
                    "description" to descriptionInput.text.toString().trim(),
                    "patientVip" to vipCheck.isChecked,
                    "editedByUid" to (auth.currentUser?.uid ?: ""),
                    "editedByName" to currentUserDisplayName,
                    "editedAt" to FieldValue.serverTimestamp()
                )
                db.collection("serials").document(document.id).update(updates)
                    .addOnSuccessListener { Toast.makeText(this, "Serial-এর সম্পূর্ণ তথ্য আপডেট হয়েছে", Toast.LENGTH_LONG).show() }
                    .addOnFailureListener { error -> Toast.makeText(this, "Update ব্যর্থ: ${error.message}", Toast.LENGTH_LONG).show() }
            }
            .show()
    }

    // =========================================================
    // DELETE SERIAL
    // =========================================================

    private fun confirmDeleteSerial(
        documentId: String
    ) {

        val uid = auth.currentUser?.uid ?: ""
        db.collection("serials").document(documentId).get()
            .addOnSuccessListener { doc ->
                val owner = doc.getString("createdByUid") ?: ""
                if (currentRole != "admin" && uid != owner) {
                    Toast.makeText(this, "এই Serial শুধু যিনি দিয়েছেন তিনি Delete করতে পারবেন", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                showDeleteSerialConfirmation(documentId)
            }
            .addOnFailureListener { e -> Toast.makeText(this, "Serial যাচাই করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun showDeleteSerialConfirmation(
        documentId: String
    ) {

        AlertDialog.Builder(this)
            .setTitle(
                "Serial Delete"
            )
            .setMessage(
                "আপনি কি এই Serial টি Delete করতে চান?"
            )
            .setNegativeButton(
                "না",
                null
            )
            .setPositiveButton(
                "হ্যাঁ, Delete"
            ) { _, _ ->

                db.collection("serials")
                    .document(documentId)
                    .delete()
                    .addOnSuccessListener {

                        Toast.makeText(
                            this,
                            "Serial Delete হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            this,
                            "Delete ব্যর্থ: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    // =========================================================
    // STATUS
    // =========================================================

    private fun showStatusDialog(
        documentId: String,
        currentStatus: String
    ) {

        if (currentRole != "admin" && currentRole != "operator") {
            Toast.makeText(this, "শুধুমাত্র Operator/Admin Status পরিবর্তন করতে পারবেন", Toast.LENGTH_LONG).show()
            return
        }

        val statuses = if (currentRole == "admin") {
            arrayOf("Waiting", "Present", "Completed", "Cancelled")
        } else {
            arrayOf("Waiting", "Present", "Completed")
        }

        AlertDialog.Builder(this)
            .setTitle(
                "Serial Status"
            )
            .setSingleChoiceItems(
                statuses,
                statuses.indexOf(
                    currentStatus
                ).coerceAtLeast(0)
            ) { dialog, which ->

                val selected =
                    statuses[which]

                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(this, "Login প্রয়োজন", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }

                val updateData = mutableMapOf<String, Any>(
                    "status" to selected
                )

                // Whenever a serial is marked Completed, record the exact
                // Firebase user who performed the completion.
                if (selected.equals("Completed", true)) {
                    updateData["completedByUid"] = currentUser.uid
                    updateData["completedByName"] = currentUserDisplayName
                    updateData["completedByRole"] = currentRole
                    updateData["completedAt"] = FieldValue.serverTimestamp()
                }

                db.collection("serials")
                    .document(documentId)
                    .update(updateData)
                    .addOnSuccessListener {

                        Toast.makeText(
                            this,
                            "Status: $selected",
                            Toast.LENGTH_SHORT
                        ).show()

                        dialog.dismiss()
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            this,
                            "Status পরিবর্তন করা যায়নি: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    // =========================================================
    // TOTAL SERIAL MANAGEMENT
    // =========================================================

    private fun showSerialSearch() {

        selectedSerialDate = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Calendar.getInstance().time)

        currentScreen = SCREEN_TOTAL_SERIAL
        setupSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        root.addView(
            createInnerTopBar("Total Serial") {
                showDashboard(currentRole)
            }
        )

        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(30))
        }

        val heading = TextView(this).apply {
            text = "📋 Total Serial Management"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
        }
        content.addView(heading)

        val summary = TextView(this).apply {
            text = "Serial লোড হচ্ছে..."
            textSize = 16f
            setTextColor(primaryColor)
            setPadding(0, dp(6), 0, dp(10))
        }
        content.addView(summary)

        // ---------------------------------------------------------
        // SEARCH
        // ---------------------------------------------------------
        val searchInput = createFormInput("রোগী / Serial / Doctor / Care Of খুঁজুন")
        searchInput.inputType = InputType.TYPE_CLASS_TEXT
        content.addView(searchInput, formParams())

        // ---------------------------------------------------------
        // DATE FILTER
        // ---------------------------------------------------------
        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dateButton = createPrimaryButton("📅 তারিখ নির্বাচন")
        val allDateCheck = CheckBox(this).apply {
            text = "সব তারিখ"
            textSize = 16f
            setTextColor(darkText)
        }

        dateRow.addView(
            dateButton,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                setMargins(0, dp(4), dp(6), dp(4))
            }
        )
        dateRow.addView(
            allDateCheck,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(54)
            )
        )
        content.addView(dateRow)

        val selectedDateLabel = TextView(this).apply {
            text = "তারিখ: ${formatDisplayDate(selectedSerialDate)}"
            textSize = 15f
            setTextColor(darkText)
            setPadding(dp(4), 0, 0, dp(8))
        }
        content.addView(selectedDateLabel)

        // ---------------------------------------------------------
        // STATUS FILTER
        // ---------------------------------------------------------
        val statusSpinner = Spinner(this)
        val statusOptions = arrayOf(
            "সব Status",
            "Waiting",
            "Completed",
            "Cancelled"
        )
        statusSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            statusOptions
        )
        content.addView(
            statusSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                setMargins(0, 0, 0, dp(8))
            }
        )

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(listContainer)

        val progress = ProgressBar(this)
        content.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(50)
            ).apply { gravity = Gravity.CENTER }
        )

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)

        var allDocuments: List<DocumentSnapshot> = emptyList()

        fun render() {
            listContainer.removeAllViews()

            val query = searchInput.text.toString().trim().lowercase(Locale.getDefault())
            val statusFilter = statusOptions[statusSpinner.selectedItemPosition]
            val useAllDates = allDateCheck.isChecked

            val filtered = allDocuments.filter { doc ->
                val date = doc.getString("createdDate") ?: ""
                val patient = doc.getString("patient") ?: ""
                val number = (doc.getLong("number")?.toString()
                    ?: doc.getString("number") ?: "")
                val doctor = doc.getString("doctorName")
                    ?: doc.getString("doctor") ?: ""
                val care = doc.getString("careOfName")
                    ?: doc.getString("careOf") ?: ""
                val careAddress = doc.getString("careOfAddress") ?: ""
                val status = doc.getString("status") ?: "Waiting"

                val dateMatch = useAllDates || date == selectedSerialDate
                val statusMatch = statusFilter == "সব Status" || status == statusFilter
                val searchMatch = query.isEmpty() || listOf(
                    patient,
                    number,
                    doctor,
                    care,
                    careAddress,
                    status
                ).any { it.lowercase(Locale.getDefault()).contains(query) }

                dateMatch && statusMatch && searchMatch
            }.sortedWith(
                compareByDescending<DocumentSnapshot> {
                    it.getBoolean("patientVip") ?: false
                }.thenByDescending {
                    it.getString("createdDate") ?: ""
                }.thenBy {
                    it.getLong("number") ?: 0L
                }
            )

            val vipCount = filtered.count {
                it.getBoolean("patientVip") ?: false
            }
            val waitingCount = filtered.count {
                (it.getString("status") ?: "Waiting") == "Waiting"
            }
            val completedCount = filtered.count {
                (it.getString("status") ?: "Waiting") == "Completed"
            }
            val cancelledCount = filtered.count {
                (it.getString("status") ?: "Waiting") == "Cancelled"
            }

            summary.text =
                "মোট: ${filtered.size}   ⭐ VIP: $vipCount   ⏳ $waitingCount   ✅ $completedCount   ❌ $cancelledCount"

            if (filtered.isEmpty()) {
                listContainer.addView(
                    createEmptyText("এই Filter অনুযায়ী কোনো Serial পাওয়া যায়নি")
                )
                return
            }

            if (useAllDates) {
                filtered.forEach { document ->
                    listContainer.addView(createSerialCard(document))
                }
            } else {
                filtered.forEachIndexed { index, document ->
                    listContainer.addView(
                        createSerialCard(
                            document,
                            displayNumber = (index + 1).toLong()
                        )
                    )
                }
            }
        }

        fun loadSerials() {
            progress.visibility = View.VISIBLE
            listContainer.removeAllViews()
            summary.text = "Serial লোড হচ্ছে..."

            db.collection("serials")
                .get()
                .addOnSuccessListener { result ->
                    allDocuments = result.documents.filterNot {
                        (it.getString("status") ?: "Waiting") == "Cancelled"
                    }
                    progress.visibility = View.GONE
                    render()
                }
                .addOnFailureListener { error ->
                    progress.visibility = View.GONE
                    summary.text = "Serial লোড করা যায়নি"
                    Toast.makeText(
                        this,
                        "Total Serial পাওয়া যায়নি: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        dateButton.setOnClickListener {
            showAddSerialDatePicker {
                selectedDateLabel.text =
                    "তারিখ: ${formatDisplayDate(selectedSerialDate)}"
                if (!allDateCheck.isChecked) {
                    render()
                }
            }
        }

        allDateCheck.setOnCheckedChangeListener { _, checked ->
            selectedDateLabel.visibility = if (checked) View.GONE else View.VISIBLE
            render()
        }

        statusSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (allDocuments.isNotEmpty()) render()
                }
            }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                if (allDocuments.isNotEmpty()) render()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        loadSerials()
    }

    
    // =========================================================
    // REPORTS
    // =========================================================

    private fun showReports() {

        clearActiveListeners()
        currentScreen = SCREEN_REPORTS
        setupSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        root.addView(
            createInnerTopBar("Reports") {
                showDashboard(currentRole)
            }
        )

        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(30))
        }

        content.addView(TextView(this).apply {
            text = "📊 Reports"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
            setPadding(0, 0, 0, dp(10))
        })

        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val previousDate = createSmallButton("‹ পূর্ববর্তী")
        val dateButton = createPrimaryButton(
            "📅 ${formatDisplayDate(selectedSerialDate)}"
        )
        val nextDate = createSmallButton("পরবর্তী ›")

        dateRow.addView(previousDate, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            setMargins(0, dp(4), dp(4), dp(6))
        })
        dateRow.addView(dateButton, LinearLayout.LayoutParams(0, dp(54), 1.45f).apply {
            setMargins(dp(4), dp(4), dp(4), dp(6))
        })
        dateRow.addView(nextDate, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            setMargins(dp(4), dp(4), 0, dp(6))
        })
        content.addView(dateRow)

        val dateInfo = TextView(this).apply {
            text = "📅 তারিখ: ${formatDisplayDate(selectedSerialDate)}"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryColor)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        content.addView(dateInfo)

        val summary = TextView(this).apply {
            text = "রিপোর্ট লোড হচ্ছে..."
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        content.addView(summary)

        val reportContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(reportContent)

        val pdfButton = createPrimaryButton("📄 PDF Download / Save")
        pdfButton.isEnabled = false
        content.addView(pdfButton, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, dp(14), 0, dp(8)) })

        val monthlyPdfButton = createPrimaryButton("📚 পুরো মাসের PDF (১–৩১ দিন)")
        monthlyPdfButton.isEnabled = false
        content.addView(monthlyPdfButton, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, 0, 0, dp(8)) })

        scroll.addView(content)
        scroll.onPullToRefresh = { refreshCurrentScreen() }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(root)

        var reportDocuments: List<DocumentSnapshot> = emptyList()

        fun sortSerials(documents: List<DocumentSnapshot>): List<DocumentSnapshot> =
            documents.sortedWith(
                compareBy<DocumentSnapshot> { it.getLong("number") ?: 0L }
            )

        fun renderReport() {
            val selectedDateDocs = reportDocuments.filter {
                (it.getString("createdDate") ?: "") == selectedSerialDate
            }

            val completed = selectedDateDocs.filter {
                (it.getString("status") ?: "Waiting").equals("Completed", true)
            }
            val incomplete = selectedDateDocs.filter {
                !(it.getString("status") ?: "Waiting").equals("Completed", true)
            }

            val doctorGroups = completed
                .groupBy {
                    it.getString("doctorName")?.trim()
                        ?.takeIf { name -> name.isNotEmpty() }
                        ?: it.getString("doctor")?.trim()
                            ?.takeIf { name -> name.isNotEmpty() }
                        ?: "Doctor not specified"
                }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)

            summary.text = buildString {
                append("আজকের মোট সিরিয়াল = ${selectedDateDocs.size} টি\n")
                append("সম্পন্ন সিরিয়াল = ${completed.size} টি\n")
                append("অসম্পন্ন সিরিয়াল = ${incomplete.size} টি\n")
                append("ডাক্তারের রিপোর্ট = শুধু সম্পন্ন সিরিয়াল")
            }

            reportContent.removeAllViews()

            if (selectedDateDocs.isEmpty()) {
                reportContent.addView(createEmptyText("এই তারিখে কোনো Serial পাওয়া যায়নি"))
            } else if (completed.isEmpty()) {
                reportContent.addView(createEmptyText("এই তারিখে কোনো সম্পন্ন Serial নেই। Doctor-wise report দেখানোর মতো কোনো Completed Serial নেই।"))
            } else {
                reportContent.addView(TextView(this).apply {
                    text = "👨‍⚕️ Doctor-wise Completed Serial"
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(darkText)
                    setPadding(0, dp(8), 0, dp(8))
                })

                doctorGroups.forEach { (doctorName, documents) ->
                    val doctorTitle = TextView(this).apply {
                        text = "$doctorName = ${documents.size} টি"
                        textSize = 18f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(primaryColor)
                        setPadding(dp(6), dp(12), dp(6), dp(8))
                    }
                    reportContent.addView(doctorTitle)

                    val table = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = roundedCardDrawable(Color.WHITE, dp(10))
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                        elevation = dp(2).toFloat()
                    }

                    fun addRow(
                        serial: String,
                        patient: String,
                        careOf: String,
                        givenBy: String,
                        header: Boolean = false
                    ) {
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(dp(4), dp(7), dp(4), dp(7))
                            if (header) setBackgroundColor(Color.rgb(235, 242, 250))
                        }

                        fun cell(text: String, weight: Float): TextView = TextView(this).apply {
                            this.text = text
                            textSize = if (header) 13f else 14f
                            typeface = if (header) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                            setTextColor(darkText)
                            setPadding(dp(4), dp(2), dp(4), dp(2))
                        }

                        row.addView(cell(serial, 0.65f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f))
                        row.addView(cell(patient, 1.25f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.25f))
                        row.addView(cell(careOf, 1.15f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f))
                        row.addView(cell(givenBy, 1.15f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f))
                        table.addView(row)
                    }

                    addRow("Serial", "Patient", "Care Of", "Given By", true)

                    sortSerials(documents).forEach { document ->
                        val number = document.getLong("number")?.toString()
                            ?: document.getString("number") ?: "-"
                        val patient = document.getString("patient") ?: "-"
                        val careOf = document.getString("careOfName")
                            ?: document.getString("careOf") ?: "-"
                        val givenBy = document.getString("createdByName")?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: document.getString("createdByUid")
                            ?: "-"
                        val role = document.getString("createdByRole")?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        val givenByText = if (role != null) "$givenBy ($role)" else givenBy
                        addRow(number, patient, careOf, givenByText)
                    }

                    reportContent.addView(table, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, dp(12)) })
                }
            }

            pdfButton.isEnabled = selectedDateDocs.isNotEmpty()
            pdfButton.setOnClickListener { saveReportAsPdf(selectedSerialDate, selectedDateDocs) }

            val monthPrefix = selectedSerialDate.substring(0, 7)
            val monthDocuments = reportDocuments.filter {
                (it.getString("createdDate") ?: "").startsWith("$monthPrefix-")
            }
            monthlyPdfButton.isEnabled = monthDocuments.isNotEmpty()
            monthlyPdfButton.setOnClickListener { saveMonthlyReportAsPdf(monthPrefix, monthDocuments) }
        }

        fun moveReportDate(days: Int) {
            val calendar = Calendar.getInstance()
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                calendar.time = parser.parse(selectedSerialDate)
                    ?: Calendar.getInstance().time
            } catch (_: Exception) {
            }
            calendar.add(Calendar.DAY_OF_MONTH, days)
            selectedSerialDate = SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            ).format(calendar.time)
            dateButton.text = "📅 ${formatDisplayDate(selectedSerialDate)}"
            dateInfo.text = "📅 তারিখ: ${formatDisplayDate(selectedSerialDate)}"
            renderReport()
        }

        previousDate.setOnClickListener { moveReportDate(-1) }
        nextDate.setOnClickListener { moveReportDate(1) }
        dateButton.setOnClickListener {
            showAddSerialDatePicker {
                dateButton.text = "📅 ${formatDisplayDate(selectedSerialDate)}"
                dateInfo.text = "📅 তারিখ: ${formatDisplayDate(selectedSerialDate)}"
                renderReport()
            }
        }

        activeListeners.add(
            db.collection("serials").addSnapshotListener { result, error ->
                if (error != null || result == null) {
                    summary.text = "Report লোড করা যায়নি"
                    reportContent.removeAllViews()
                    reportContent.addView(createEmptyText("Report পাওয়া যায়নি: ${error?.message ?: "Unknown error"}"))
                    pdfButton.isEnabled = false
                    return@addSnapshotListener
                }
                reportDocuments = result.documents
                renderReport()
            }
        )
    }

    // =========================================================
    // SAVE WHOLE MONTH REPORT AS PDF
    // =========================================================

    private fun saveMonthlyReportAsPdf(
        monthPrefix: String,
        documents: List<DocumentSnapshot>
    ) {
        if (documents.isEmpty()) {
            Toast.makeText(this, "এই মাসে কোনো Serial নেই", Toast.LENGTH_LONG).show()
            return
        }

        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 30f
        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val headingPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = margin

        fun startPage() {
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page!!.canvas
            y = margin
            canvas!!.drawText("Moon Diagnostic Center", margin, y + 18, titlePaint)
            y += 38f
            canvas!!.drawText("Monthly Serial Report • $monthPrefix", margin, y, headingPaint)
            y += 22f
        }
        fun finishPage() {
            page?.let { pdf.finishPage(it) }
            page = null
            canvas = null
        }
        fun line(text: String, paint: android.graphics.Paint = textPaint, gap: Float = 15f) {
            if (y > pageHeight - 45f) { finishPage(); startPage() }
            canvas!!.drawText(text.take(105), margin, y, paint)
            y += gap
        }

        startPage()
        val sorted = documents.sortedWith(compareBy<DocumentSnapshot> { it.getString("createdDate") ?: "" }.thenBy { it.getLong("number") ?: 0L })
        var currentDate = ""
        for (doc in sorted) {
            val date = doc.getString("createdDate") ?: ""
            if (date != currentDate) {
                if (y > pageHeight - 80f) { finishPage(); startPage() }
                line("Date: ${formatDisplayDate(date)}", headingPaint, 19f)
                currentDate = date
            }
            val number = doc.getLong("number")?.toString() ?: doc.getString("number") ?: "-"
            val patient = doc.getString("patient") ?: "-"
            val doctor = doc.getString("doctorName") ?: doc.getString("doctor") ?: "-"
            val care = doc.getString("careOfName") ?: doc.getString("careOf") ?: "-"
            val status = doc.getString("status") ?: "Waiting"
            line("#$number  $patient  |  $doctor  |  Care Of: $care  |  $status", textPaint, 14f)
        }
        finishPage()

        try {
            val file = java.io.File(cacheDir, "MDC_Monthly_Report_${monthPrefix}.pdf")
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()
            pendingReportPdfFile = file
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, "MDC_Monthly_Report_${monthPrefix}.pdf")
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQUEST_REPORT_PDF)
        } catch (e: Exception) {
            pdf.close()
            Toast.makeText(this, "Monthly PDF তৈরি করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================
    // SAVE REPORT AS PDF
    // =========================================================

    private fun saveReportAsPdf(
        date: String,
        documents: List<DocumentSnapshot>
    ) {
        if (documents.isEmpty()) {
            Toast.makeText(
                this,
                "এই তারিখে PDF তৈরি করার মতো কোনো Serial নেই",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val completed = documents.filter {
            (it.getString("status") ?: "Waiting").equals("Completed", true)
        }

        val doctorGroups = completed
            .groupBy {
                it.getString("doctorName")?.trim()
                    ?.takeIf { name -> name.isNotEmpty() }
                    ?: it.getString("doctor")?.trim()
                        ?.takeIf { name -> name.isNotEmpty() }
                    ?: "Doctor not specified"
            }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 32f
        val usableWidth = pageWidth - (margin * 2)

        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headingPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
        }
        val smallPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
        }
        val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1f
        }

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = margin

        fun newPage() {
            page?.let { pdf.finishPage(it) }
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdf.startPage(info)
            canvas = page!!.canvas
            y = margin
        }

        fun ensureSpace(height: Float) {
            if (y + height > pageHeight - margin) newPage()
        }

        fun text(value: String, x: Float, sizePaint: android.graphics.Paint = textPaint) {
            canvas?.drawText(value, x, y, sizePaint)
        }

        fun wrapped(value: String, x: Float, maxWidth: Float, paint: android.graphics.Paint) {
            val words = value.split(" ")
            var line = ""
            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) > maxWidth && line.isNotEmpty()) {
                    ensureSpace(14f)
                    text(line, x, paint)
                    y += 13f
                    line = word
                } else {
                    line = test
                }
            }
            if (line.isNotEmpty()) {
                ensureSpace(14f)
                text(line, x, paint)
                y += 13f
            }
        }

        newPage()
        text("Moon Diagnostic Center - Reports", margin, titlePaint)
        y += 24f
        text("Date: ${formatDisplayDate(date)}", margin, headingPaint)
        y += 20f
        text("Total Serial: ${documents.size}", margin, textPaint)
        y += 15f
        text("Completed Serial: ${completed.size}", margin, textPaint)
        y += 15f
        text("Incomplete Serial: ${documents.size - completed.size}", margin, textPaint)
        y += 24f

        if (completed.isEmpty()) {
            text("No completed serials found for this date.", margin, headingPaint)
            y += 20f
        } else {
            doctorGroups.forEach { (doctorName, doctorDocs) ->
                ensureSpace(40f)
                text("Doctor: $doctorName (${doctorDocs.size} completed)", margin, headingPaint)
                y += 18f

                val colX = floatArrayOf(
                    margin,
                    margin + usableWidth * 0.12f,
                    margin + usableWidth * 0.38f,
                    margin + usableWidth * 0.70f
                )
                val headers = listOf("Serial", "Patient", "Care Of / Address", "Given By")
                ensureSpace(20f)
                headers.forEachIndexed { index, header ->
                    text(header, colX[index], smallPaint)
                }
                y += 11f
                canvas?.drawLine(margin, y, pageWidth - margin, y, linePaint)
                y += 12f

                doctorDocs.sortedBy { it.getLong("number") ?: 0L }.forEach { document ->
                    ensureSpace(34f)
                    val number = document.getLong("number")?.toString()
                        ?: document.getString("number") ?: "-"
                    val patient = document.getString("patient") ?: "-"
                    val careOfName = document.getString("careOfName")
                        ?: document.getString("careOf") ?: "-"
                    val careOfAddress = document.getString("careOfAddress") ?: ""
                    val careOf = if (careOfAddress.isBlank()) careOfName else "$careOfName / $careOfAddress"
                    val givenBy = document.getString("createdByName")?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: document.getString("createdByUid") ?: "-"
                    val role = document.getString("createdByRole")?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    val giver = if (role != null) "$givenBy ($role)" else givenBy

                    text(number, colX[0], smallPaint)
                    wrapped(patient, colX[1], usableWidth * 0.24f, smallPaint)
                    val patientLines = maxOf(1, (smallPaint.measureText(patient) / (usableWidth * 0.24f)).toInt() + 1)
                    val rowHeight = 13f * patientLines
                    text(careOf, colX[2], smallPaint)
                    wrapped(giver, colX[3], usableWidth * 0.30f, smallPaint)
                    y += maxOf(13f, rowHeight)
                    canvas?.drawLine(margin, y, pageWidth - margin, y, linePaint)
                    y += 8f
                }
                y += 8f
            }
        }

        page?.let { pdf.finishPage(it) }

        val fileName = "MDC_Report_${date}.pdf"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        val tempFile = java.io.File(cacheDir, fileName)
        try {
            FileOutputStream(tempFile).use { output ->
                pdf.writeTo(output)
            }
            pdf.close()
            pendingReportPdfFile = tempFile
            startActivityForResult(intent, REQUEST_REPORT_PDF)
        } catch (error: Exception) {
            pdf.close()
            Toast.makeText(
                this,
                "PDF তৈরি করা যায়নি: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showTotalSerial() {

        selectedSerialDate = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Calendar.getInstance().time)

        clearActiveListeners()
        currentScreen = SCREEN_TOTAL_SERIAL
        setupSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        root.addView(
            createInnerTopBar("Total Serial") {
                showDashboard(currentRole)
            }
        )

        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(30))
        }

        content.addView(TextView(this).apply {
            text = "📋 Total Serial"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
            setPadding(0, 0, 0, dp(12))
        })

        // =========================================================
        // THREE TOP TABS
        // =========================================================
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val allTab = createSmallButton("① সকল সিরিয়াল")
        val myTab = createSmallButton("② আমার সিরিয়াল")
        val adminTab = createSmallButton("③ User / Operator / Admin")

        fun tabParams(weight: Float): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(0, dp(52), weight).apply {
                setMargins(dp(3), 0, dp(3), dp(10))
            }

        tabRow.addView(allTab, tabParams(1f))
        tabRow.addView(myTab, tabParams(1f))
        if (currentRole == "admin") {
            tabRow.addView(adminTab, tabParams(1.35f))
        }
        content.addView(tabRow)

        // =========================================================
        // DATE SELECTOR
        // =========================================================
        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val previousDate = createSmallButton("‹ পূর্ববর্তী")
        val dateButton = createPrimaryButton(
            "📅 ${formatDisplayDate(selectedSerialDate)}"
        )
        val nextDate = createSmallButton("পরবর্তী ›")

        dateRow.addView(previousDate, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            setMargins(0, dp(4), dp(4), dp(6))
        })
        dateRow.addView(dateButton, LinearLayout.LayoutParams(0, dp(54), 1.45f).apply {
            setMargins(dp(4), dp(4), dp(4), dp(6))
        })
        dateRow.addView(nextDate, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            setMargins(dp(4), dp(4), 0, dp(6))
        })
        content.addView(dateRow)

        val dateInfo = TextView(this).apply {
            text = "📅 তারিখ: ${formatDisplayDate(selectedSerialDate)}"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryColor)
            setPadding(dp(4), dp(2), dp(4), dp(10))
        }
        content.addView(dateInfo)

        val progress = ProgressBar(this).apply {
            visibility = View.GONE
        }
        content.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(50)
        ).apply { gravity = Gravity.CENTER })

        // =========================================================
        // THREE TAB CONTENT CONTAINERS
        // =========================================================
        val layer1Container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val layer2Container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val adminContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        content.addView(layer1Container)
        content.addView(layer2Container)
        if (currentRole == "admin") content.addView(adminContainer)

        // ------------------------- Layer 1 -------------------------
        val layer1Summary = TextView(this).apply {
            text = "Serial লোড হচ্ছে..."
            textSize = 16f
            setTextColor(primaryColor)
            setPadding(0, 0, 0, dp(8))
        }
        val layer1List = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        layer1Container.addView(TextView(this).apply {
            text = "১ম — সকল সিরিয়াল"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
            setPadding(0, dp(6), 0, dp(6))
        })
        layer1Container.addView(layer1Summary)
        layer1Container.addView(layer1List)

        // ------------------------- Layer 2 -------------------------
        val layer2Summary = TextView(this).apply {
            text = "আমার Serial লোড হচ্ছে..."
            textSize = 16f
            setTextColor(primaryColor)
            setPadding(0, 0, 0, dp(8))
        }
        val layer2List = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        layer2Container.addView(TextView(this).apply {
            text = "২য় — আমার সিরিয়াল"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
            setPadding(0, dp(6), 0, dp(6))
        })
        layer2Container.addView(layer2Summary)
        layer2Container.addView(layer2List)

        // ------------------------- Layer 3 -------------------------
        var adminUserSpinner: Spinner? = null
        var adminUserOptions: List<Pair<String, String>> = emptyList()
        var adminLayerSummary: TextView? = null
        var adminLayerList: LinearLayout? = null

        if (currentRole == "admin") {
            adminContainer.addView(TextView(this).apply {
                text = "৩য় — User / Operator / Admin"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(darkText)
                setPadding(0, dp(6), 0, dp(6))
            })
            adminContainer.addView(TextView(this).apply {
                text = "ব্যক্তি নির্বাচন করুন"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(darkText)
                setPadding(0, dp(4), 0, dp(6))
            })

            adminUserSpinner = Spinner(this)
            adminContainer.addView(adminUserSpinner, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(55)
            ).apply { setMargins(0, 0, 0, dp(10)) })

            adminLayerSummary = TextView(this).apply {
                text = "User List লোড হচ্ছে..."
                textSize = 16f
                setTextColor(primaryColor)
                setPadding(0, 0, 0, dp(8))
            }
            adminContainer.addView(adminLayerSummary)

            adminLayerList = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            adminContainer.addView(adminLayerList)
        }

        scroll.addView(content)
        scroll.onPullToRefresh = { refreshCurrentScreen() }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        var activeTab = 1
        var allDocuments: List<DocumentSnapshot> = emptyList()

        fun updateTabStyle() {
            val selectedBg = roundedCardDrawable(topBarColor, dp(12))
            val normalBg = roundedCardDrawable(Color.WHITE, dp(12))
            val tabs = mutableListOf(allTab to 1, myTab to 2)
            if (currentRole == "admin") tabs.add(adminTab to 3)
            tabs.forEach { (button, index) ->
                button.background = if (activeTab == index) selectedBg else normalBg
                button.setTextColor(if (activeTab == index) Color.WHITE else primaryColor)
                button.typeface = Typeface.DEFAULT_BOLD
            }
        }

        fun renderAdminLayer(dateDocuments: List<DocumentSnapshot>) {
            if (currentRole != "admin" || adminUserSpinner == null ||
                adminLayerSummary == null || adminLayerList == null) return

            if (adminUserOptions.isEmpty()) {
                adminLayerSummary!!.text = "কোনো User / Operator / Admin পাওয়া যায়নি"
                adminLayerList!!.removeAllViews()
                return
            }

            val position = adminUserSpinner!!.selectedItemPosition.coerceIn(
                0, adminUserOptions.size - 1
            )
            val selected = adminUserOptions[position]
            val selectedUid = selected.first
            val selectedNameRole = selected.second

            val selectedSerials = dateDocuments.filter {
                (it.getString("status") ?: "Waiting") != "Cancelled" &&
                (it.getString("createdByUid") ?: "") == selectedUid
            }.sortedWith(
                compareByDescending<DocumentSnapshot> {
                    it.getBoolean("patientVip") ?: false
                }.thenBy { it.getLong("number") ?: 0L }
            )

            adminLayerSummary!!.text =
                "$selectedNameRole | মোট Serial: ${selectedSerials.size}"
            adminLayerList!!.removeAllViews()

            if (selectedSerials.isEmpty()) {
                adminLayerList!!.addView(createEmptyText(
                    "এই তারিখে নির্বাচিত ব্যক্তির কোনো Serial নেই"
                ))
            } else {
                selectedSerials.forEach { document ->
                    adminLayerList!!.addView(createSerialCard(document))
                }
            }
        }

        fun renderAllLayers() {
            val dateDocuments = allDocuments.filter {
                (it.getString("status") ?: "Waiting") != "Cancelled" &&
                (it.getString("createdDate") ?: "") == selectedSerialDate
            }.sortedWith(
                compareByDescending<DocumentSnapshot> {
                    it.getBoolean("patientVip") ?: false
                }.thenBy { document ->
                    document.getTimestamp("createdAt")?.seconds ?: Long.MAX_VALUE
                }.thenBy { document ->
                    document.getTimestamp("createdAt")?.nanoseconds ?: Int.MAX_VALUE
                }.thenBy {
                    it.getLong("number") ?: 0L
                }
            )

            layer1List.removeAllViews()
            layer1Summary.text =
                "📅 ${formatDisplayDate(selectedSerialDate)} | সকল ডাক্তার মিলিয়ে মোট Serial: ${dateDocuments.size}"
            if (dateDocuments.isEmpty()) {
                layer1List.addView(createEmptyText("এই তারিখে কোনো Serial পাওয়া যায়নি"))
            } else {
                dateDocuments.forEachIndexed { index, document ->
                    layer1List.addView(
                        createSerialCard(
                            document,
                            displayNumber = (index + 1).toLong()
                        )
                    )
                }
            }

            layer2List.removeAllViews()
            val myUid = auth.currentUser?.uid ?: ""
            val myDocuments = dateDocuments.filter {
                (it.getString("createdByUid") ?: "") == myUid
            }
            layer2Summary.text =
                "👤 ${currentUserDisplayName.ifBlank { "বর্তমান Account" }} | মোট Serial: ${myDocuments.size}"
            if (myDocuments.isEmpty()) {
                layer2List.addView(createEmptyText("এই তারিখে আপনার কোনো Serial নেই"))
            } else {
                myDocuments.forEach { document ->
                    layer2List.addView(createSerialCard(document))
                }
            }

            renderAdminLayer(dateDocuments)
        }

        fun showOnlyTab(tab: Int) {
            if (tab == 3 && currentRole != "admin") return
            activeTab = tab
            layer1Container.visibility = if (tab == 1) View.VISIBLE else View.GONE
            layer2Container.visibility = if (tab == 2) View.VISIBLE else View.GONE
            adminContainer.visibility = if (tab == 3 && currentRole == "admin") {
                View.VISIBLE
            } else {
                View.GONE
            }
            updateTabStyle()
        }

        fun loadSerials() {
            progress.visibility = View.VISIBLE
            layer1Summary.text = "Serial লোড হচ্ছে..."
            layer2Summary.text = "আমার Serial লোড হচ্ছে..."

            activeListeners.add(
                db.collection("serials")
                    .addSnapshotListener { result, error ->
                        if (error != null || result == null) {
                            progress.visibility = View.GONE
                            layer1Summary.text = "Serial লোড করা যায়নি"
                            layer2Summary.text = "Serial লোড করা যায়নি"
                            if (error != null) Toast.makeText(this, "Total Serial পাওয়া যায়নি: ${error.message}", Toast.LENGTH_LONG).show()
                            return@addSnapshotListener
                        }
                        allDocuments = result.documents.filterNot {
                            (it.getString("status") ?: "Waiting") == "Cancelled"
                        }
                        progress.visibility = View.GONE
                        renderAllLayers()
                    }
            )
        }

        fun moveDate(days: Int) {
            val calendar = Calendar.getInstance()
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                calendar.time = parser.parse(selectedSerialDate)
                    ?: Calendar.getInstance().time
            } catch (ignored: Exception) {
            }
            calendar.add(Calendar.DAY_OF_MONTH, days)
            selectedSerialDate = SimpleDateFormat(
                "yyyy-MM-dd", Locale.getDefault()
            ).format(calendar.time)
            dateButton.text = "📅 ${formatDisplayDate(selectedSerialDate)}"
            dateInfo.text = "📅 তারিখ: ${formatDisplayDate(selectedSerialDate)}"
            renderAllLayers()
        }

        previousDate.setOnClickListener { moveDate(-1) }
        nextDate.setOnClickListener { moveDate(1) }

        dateButton.setOnClickListener {
            showAddSerialDatePicker {
                dateButton.text = "📅 ${formatDisplayDate(selectedSerialDate)}"
                dateInfo.text = "📅 তারিখ: ${formatDisplayDate(selectedSerialDate)}"
                renderAllLayers()
            }
        }

        allTab.setOnClickListener { showOnlyTab(1) }
        myTab.setOnClickListener { showOnlyTab(2) }

        if (currentRole == "admin") {
            adminTab.setOnClickListener { showOnlyTab(3) }

            adminUserSpinner!!.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (allDocuments.isNotEmpty()) renderAllLayers()
                    }
                }

            db.collection("users")
                .get()
                .addOnSuccessListener { result ->
                    adminUserOptions = result.documents.mapNotNull { document ->
                        val role = document.getString("role") ?: ""
                        val normalizedRole = role.lowercase(Locale.getDefault())
                        if (normalizedRole !in listOf("user", "operator", "admin")) {
                            return@mapNotNull null
                        }

                        val uid = document.id
                        val name = document.getString("name")?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: document.getString("displayName")?.trim()
                                ?.takeIf { it.isNotEmpty() }
                            ?: document.getString("username")?.trim()
                                ?.takeIf { it.isNotEmpty() }
                            ?: document.getString("email")?.trim()
                                ?.takeIf { it.isNotEmpty() }
                            ?: uid

                        val roleText = when (normalizedRole) {
                            "admin" -> "Admin"
                            "operator" -> "Operator"
                            else -> "User"
                        }
                        uid to "$name ($roleText)"
                    }.sortedBy {
                        it.second.lowercase(Locale.getDefault())
                    }

                    adminUserSpinner!!.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        adminUserOptions.map { it.second }
                    )

                    if (allDocuments.isNotEmpty()) renderAllLayers()
                }
                .addOnFailureListener { error ->
                    adminUserOptions = emptyList()
                    adminLayerSummary?.text = "User List লোড করা যায়নি"
                    Toast.makeText(
                        this,
                        "User List পাওয়া যায়নি: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        showOnlyTab(1)
        loadSerials()
    }

    private fun createInnerTopBar(
        titleText: String,
        backAction: () -> Unit
    ): LinearLayout {

        val bar =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(8),
                    getStatusBarHeight() + dp(8),
                    dp(15),
                    dp(12)
                )

                setBackgroundColor(
                    topBarColor
                )

                elevation =
                    dp(8).toFloat()
            }

        val back =
            ImageButton(this).apply {

                setImageResource(
                    android.R.drawable.ic_media_previous
                )

                setBackgroundColor(
                    Color.TRANSPARENT
                )

                setColorFilter(
                    Color.WHITE
                )

                setOnClickListener {
                    backAction()
                }
            }

        bar.addView(
            back,
            LinearLayout.LayoutParams(
                dp(60),
                dp(60)
            )
        )

        val title =
            TextView(this).apply {

                text =
                    titleText

                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(10),
                    0,
                    0,
                    0
                )
            }

        bar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        )


        return bar.apply {

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(88)
                )
        }
    }

    // =========================================================
    // INFO TEXT
    // =========================================================

    private fun createInfoText(
        label: String,
        value: String
    ): TextView {

        return TextView(this).apply {

            text =
                if (label.isEmpty()) {
                    value
                } else {
                    "$label: $value"
                }

            textSize = 15f

            setTextColor(
                lightText
            )

            setPadding(
                0,
                dp(5),
                0,
                dp(2)
            )
        }
    }

    // =========================================================
    // EMPTY
    // =========================================================

    private fun createEmptyText(
        message: String
    ): TextView {

        return TextView(this).apply {

            text = message

            textSize = 18f

            gravity =
                Gravity.CENTER

            setTextColor(
                lightText
            )

            setPadding(
                dp(10),
                dp(40),
                dp(10),
                dp(40)
            )
        }
    }

    // =========================================================
    // FORM LABEL
    // =========================================================

    private fun addFormLabel(
        parent: LinearLayout,
        text: String
    ) {

        val label =
            TextView(this).apply {

                this.text = text

                textSize = 16f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(4),
                    dp(4),
                    dp(4),
                    dp(6)
                )
            }

        parent.addView(label)
    }

    // =========================================================
    // FORM INPUT
    // =========================================================

    private fun createFormInput(
        hintText: String
    ): EditText {

        return EditText(this).apply {

            hint =
                hintText

            textSize = 17f

            setTextColor(
                darkText
            )

            setHintTextColor(
                Color.rgb(
                    140,
                    140,
                    140
                )
            )

            setSingleLine(true)

            gravity =
                Gravity.CENTER_VERTICAL

            setPadding(
                dp(16),
                dp(10),
                dp(16),
                dp(10)
            )

            background =
                roundedCardDrawable(
                    Color.WHITE,
                    dp(12)
                )
        }
    }

    // =========================================================
    // FORM PARAMS
    // =========================================================

    private fun formParams():
            LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply {

            setMargins(
                0,
                0,
                0,
                dp(14)
            )
        }
    }

    // =========================================================
    // STATUS TEXT
    // =========================================================

    private fun createStatusText():
            TextView {

        return TextView(this).apply {

            textSize = 15f

            gravity =
                Gravity.CENTER

            setTextColor(
                primaryColor
            )

            setPadding(
                0,
                dp(10),
                0,
                dp(10)
            )
        }
    }

    // =========================================================
    // PRIMARY BUTTON
    // =========================================================

    private fun createPrimaryButton(
        text: String
    ): Button {

        return Button(this).apply {

            this.text = text

            textSize = 18f

            typeface =
                Typeface.DEFAULT_BOLD

            setTextColor(
                Color.WHITE
            )

            setAllCaps(false)

            background =
                roundedCardDrawable(
                    topBarColor,
                    dp(15)
                )

            elevation =
                dp(4).toFloat()
        }
    }

    // =========================================================
    // SMALL BUTTON
    // =========================================================

    private fun createSmallButton(
        text: String
    ): Button {

        return Button(this).apply {

            this.text = text

            textSize = 13f

            setAllCaps(false)

            setTextColor(
                primaryColor
            )

            setPadding(
                dp(5),
                0,
                dp(5),
                0
            )

            background =
                roundedCardDrawable(
                    Color.rgb(
                        235,
                        242,
                        250
                    ),
                    dp(10)
                )

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(45)
                ).apply {

                    setMargins(
                        dp(4),
                        0,
                        dp(4),
                        0
                    )
                }
        }
    }

    // =========================================================
    // DASHBOARD CARD
    // =========================================================

    private fun createDashboardCard(
        icon: String,
        label: String,
        cardColor: Int
    ): LinearLayout {

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(8),
                    dp(10),
                    dp(8),
                    dp(10)
                )

                background =
                    roundedCardDrawable(
                        cardColor,
                        dp(18)
                    )

                elevation =
                    dp(5).toFloat()

                isClickable = true
                isFocusable = true
            }

        val iconView =
            TextView(this).apply {

                text = icon

                textSize = 42f

                gravity =
                    Gravity.CENTER
            }

        val labelView =
            TextView(this).apply {

                text = label

                textSize = 17f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setTextColor(
                    darkText
                )

                setPadding(
                    dp(4),
                    dp(5),
                    dp(4),
                    0
                )
            }

        card.addView(
            iconView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        )

        card.addView(labelView)

        return card
    }

    // =========================================================
    // GRID PARAMS
    // =========================================================

    private fun gridParams():
            LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            dp(155),
            1f
        ).apply {

            setMargins(
                dp(6),
                dp(6),
                dp(6),
                dp(6)
            )
        }
    }

    // =========================================================
    // SUMMARY PARAMS
    // =========================================================

    private fun summaryParams():
            LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            dp(115),
            1f
        ).apply {

            setMargins(
                dp(4),
                dp(4),
                dp(4),
                dp(4)
            )
        }
    }

    // =========================================================
    // SUMMARY CARD
    // =========================================================

    private fun createSummaryCard(
        title: String,
        value: String
    ): LinearLayout {

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                background =
                    roundedCardDrawable(
                        Color.WHITE,
                        dp(14)
                    )

                elevation =
                    dp(2).toFloat()
            }

        val titleText =
            TextView(this).apply {

                text = title

                textSize = 13f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    lightText
                )

                typeface =
                    Typeface.DEFAULT_BOLD
            }

        val valueText =
            TextView(this).apply {

                text = value

                textSize = 25f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setTextColor(
                    primaryColor
                )
            }

        card.addView(titleText)
        card.addView(valueText)

        return card
    }

    // =========================================================
    // ROUNDED BACKGROUND
    // =========================================================

    private fun roundedCardDrawable(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                radius.toFloat()

            setStroke(
                dp(1),
                Color.argb(
                    35,
                    0,
                    0,
                    0
                )
            )
        }
    }

    // =========================================================
    // NAVIGATION DRAWER
    // =========================================================

    // =========================================================
    // ADMIN CONTROL PANEL
    // =========================================================

    private fun showAdminControlPanel() {
        if (currentRole != "admin") {
            Toast.makeText(this, "শুধু Admin Control Panel ব্যবহার করতে পারবেন", Toast.LENGTH_LONG).show()
            return
        }
        if (currentRole != "admin") {
            Toast.makeText(this, "শুধুমাত্র Admin এই পেজ ব্যবহার করতে পারবেন", Toast.LENGTH_LONG).show()
            return
        }

        currentScreen = SCREEN_DASHBOARD
        setupSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        root.addView(createInnerTopBar("Admin Control Panel") { showDashboard(currentRole) })

        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(30))
        }

        content.addView(TextView(this).apply {
            text = "👑 Admin Control Panel"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkText)
            setPadding(0, 0, 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "অ্যাপের প্রধান প্রশাসনিক কার্যক্রম এখান থেকে পরিচালনা করুন।"
            textSize = 15f
            setTextColor(lightText)
            setPadding(0, 0, 0, dp(18))
        })

        val actions = listOf(
            "👥  User Management" to { showUserManagement() },
            "👨‍⚕️  Doctor Management" to { showDoctorList() },
            "👤  Care Of Management" to { showCareOfList() },
            "🔔  Notifications" to { showNotifications() },
            "⚙️  Settings" to { showSettings() },
            "📋  Total Serial" to { showTotalSerial() }
        )

        actions.forEach { (title, action) ->
            content.addView(createPrimaryButton(title).apply {
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)
            ).apply { setMargins(0, 0, 0, dp(10)) })
        }

        scroll.addView(content)
        scroll.onPullToRefresh = { refreshCurrentScreen() }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    // =========================================================
    // USER MANAGEMENT
    // =========================================================

    private fun showUserManagement() {
        if (currentRole != "admin") {
            Toast.makeText(this, "শুধু Admin User Management ব্যবহার করতে পারবেন", Toast.LENGTH_LONG).show()
            return
        }
        setupSystemBars()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(backgroundColor) }
        root.addView(createInnerTopBar("User Management") { showAdminControlPanel() })
        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(30)) }
        content.addView(TextView(this).apply { text = "👥 User / Operator Management"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(darkText) })
        content.addView(TextView(this).apply { text = "User/Operator এখান থেকেই Edit, Delete, Password Reset, Activate/Deactivate এবং Role Change করা যাবে।"; textSize = 14f; setTextColor(lightText); setPadding(0, dp(8), 0, dp(10)) })
        content.addView(createPrimaryButton("＋  নতুন User / Operator তৈরি করুন").apply { setOnClickListener { showCreateUserDialog() } }, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0,0,0,dp(14)) })

        db.collection("users").get().addOnSuccessListener { result ->
            if (result.isEmpty) content.addView(createEmptyText("কোনো User পাওয়া যায়নি"))
            result.documents.sortedBy { it.getString("name") ?: it.getString("username") ?: it.id }.forEach { doc ->
                val uid = doc.id
                val name = doc.getString("name") ?: doc.getString("username") ?: uid
                val email = doc.getString("email") ?: "-"
                val role = doc.getString("role") ?: "user"
                val active = doc.getBoolean("active") ?: false
                val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(12)); background=roundedCardDrawable(Color.WHITE,dp(14)); elevation=dp(2).toFloat() }
                card.addView(TextView(this).apply { text=name; textSize=18f; typeface=Typeface.DEFAULT_BOLD; setTextColor(darkText) })
                card.addView(TextView(this).apply { text="Email: $email\nRole: ${role.uppercase()}\nStatus: ${if(active) "Active" else "Inactive"}"; textSize=14f; setTextColor(lightText); setPadding(0,dp(5),0,dp(8)) })
                val row1=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                val toggle=createPrimaryButton(if(active) "Deactivate" else "Activate"); toggle.setOnClickListener{
                    if (uid == auth.currentUser?.uid && active) {
                        Toast.makeText(this, "বর্তমান Admin নিজেকে Deactivate করতে পারবেন না", Toast.LENGTH_LONG).show()
                    } else {
                        db.collection("users").document(uid).update("active",!active).addOnSuccessListener{showUserManagement()}.addOnFailureListener{e->Toast.makeText(this,"Update ব্যর্থ: ${e.message}",Toast.LENGTH_LONG).show()}
                    }
                }
                row1.addView(toggle,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(0,0,dp(6),0)})
                val edit=createPrimaryButton("Edit"); edit.setOnClickListener{showEditUserDialog(uid)}
                row1.addView(edit,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(dp(6),0,dp(6),0)})
                val roleBtn=createPrimaryButton("Role"); roleBtn.setOnClickListener{showRoleChangeDialog(uid,role)}
                row1.addView(roleBtn,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(dp(6),0,0,0)})
                card.addView(row1)
                val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                val reset=createPrimaryButton("Reset Password"); reset.setOnClickListener{sendPasswordReset(email)}
                row2.addView(reset,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(0,dp(6),dp(6),0)})
                val delete=createPrimaryButton("Delete"); delete.setOnClickListener{confirmDeleteUser(uid,name)}
                row2.addView(delete,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(dp(6),dp(6),0,0)})
                card.addView(row2)
                content.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(10))})
            }
        }.addOnFailureListener { e -> content.addView(createEmptyText("User List পাওয়া যায়নি: ${e.message}")) }
        scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }

    private fun sendPasswordReset(email: String) {
        if (email == "-") { Toast.makeText(this,"Email পাওয়া যায়নি",Toast.LENGTH_LONG).show(); return }
        auth.sendPasswordResetEmail(email).addOnSuccessListener { Toast.makeText(this,"$email-এ Password Reset link পাঠানো হয়েছে",Toast.LENGTH_LONG).show() }.addOnFailureListener { e->Toast.makeText(this,"Reset email পাঠানো যায়নি: ${e.message}",Toast.LENGTH_LONG).show()}
    }

    private fun showEditUserDialog(uid: String) {
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            if (!doc.exists()) { Toast.makeText(this,"User পাওয়া যায়নি",Toast.LENGTH_LONG).show(); return@addOnSuccessListener }
            val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),0)}
            val name=createFormInput("নাম"); name.setText(doc.getString("name") ?: "")
            val email=createFormInput("Email (profile)"); email.setText(doc.getString("email") ?: "")
            box.addView(name,formParams()); box.addView(email,formParams())
            AlertDialog.Builder(this).setTitle("Edit User").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->
                val n=name.text.toString().trim(); val e=email.text.toString().trim()
                if(n.isEmpty()||e.isEmpty()){Toast.makeText(this,"Name ও Email দিন",Toast.LENGTH_SHORT).show();return@setPositiveButton}
                db.collection("users").document(uid).update(mapOf("name" to n,"email" to e)).addOnSuccessListener{showUserManagement()}.addOnFailureListener{err->Toast.makeText(this,"Edit ব্যর্থ: ${err.message}",Toast.LENGTH_LONG).show()}
            }.show()
        }.addOnFailureListener{e->Toast.makeText(this,"User পড়া যায়নি: ${e.message}",Toast.LENGTH_LONG).show()}
    }

    private fun confirmDeleteUser(uid: String, name: String) {
        if (uid == auth.currentUser?.uid) { Toast.makeText(this,"বর্তমান Admin নিজেকে Delete করতে পারবেন না",Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this).setTitle("User Delete").setMessage("$name-এর App profile মুছে দেবেন? এতে ওই account অ্যাপে আর access পাবে না।").setNegativeButton("না",null).setPositiveButton("হ্যাঁ, Delete"){_,_->
            db.collection("users").document(uid).delete().addOnSuccessListener{Toast.makeText(this,"User App profile Delete হয়েছে",Toast.LENGTH_LONG).show();showUserManagement()}.addOnFailureListener{e->Toast.makeText(this,"Delete ব্যর্থ: ${e.message}",Toast.LENGTH_LONG).show()}
        }.show()
    }

    // =========================================================
    // CREATE USER / OPERATOR DIRECTLY FROM ADMIN APP
    // =========================================================

    private fun showCreateUserDialog() {
        if (currentRole != "admin") {
            Toast.makeText(this, "শুধু Admin নতুন User / Operator তৈরি করতে পারবেন", Toast.LENGTH_LONG).show()
            return
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }

        val nameInput = createFormInput("নাম")
        val emailInput = createFormInput("Login Email")
        emailInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val passwordInput = createFormInput("Temporary Password (কমপক্ষে 6 অক্ষর)")
        passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val roleSpinner = Spinner(this)
        val roles = arrayOf("user", "operator")
        roleSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roles.map { if (it == "operator") "Operator" else "User" }
        )

        box.addView(TextView(this).apply {
            text = "নাম"
            textSize = 14f
            setTextColor(lightText)
            setPadding(0, dp(8), 0, dp(4))
        })
        box.addView(nameInput, formParams())
        box.addView(TextView(this).apply {
            text = "Login Email"
            textSize = 14f
            setTextColor(lightText)
            setPadding(0, dp(8), 0, dp(4))
        })
        box.addView(emailInput, formParams())
        box.addView(TextView(this).apply {
            text = "Password"
            textSize = 14f
            setTextColor(lightText)
            setPadding(0, dp(8), 0, dp(4))
        })
        box.addView(passwordInput, formParams())
        box.addView(TextView(this).apply {
            text = "Role"
            textSize = 14f
            setTextColor(lightText)
            setPadding(0, dp(8), 0, dp(4))
        })
        box.addView(roleSpinner, LinearLayout.LayoutParams(-1, dp(55)))

        AlertDialog.Builder(this)
            .setTitle("নতুন User / Operator")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameInput.text.toString().trim()
                        val email = emailInput.text.toString().trim()
                        val password = passwordInput.text.toString()
                        val role = if (roleSpinner.selectedItemPosition == 1) "operator" else "user"

                        if (name.isEmpty()) {
                            nameInput.error = "নাম দিন"
                            return@setOnClickListener
                        }
                        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            emailInput.error = "সঠিক Email দিন"
                            return@setOnClickListener
                        }
                        if (password.length < 6) {
                            passwordInput.error = "Password কমপক্ষে 6 অক্ষরের হতে হবে"
                            return@setOnClickListener
                        }

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                        Toast.makeText(this, "Account তৈরি হচ্ছে...", Toast.LENGTH_SHORT).show()

                        createAuthUserFromAdmin(name, email, password, role, dialog)
                    }
                }
            }
            .show()
    }

    private fun createAuthUserFromAdmin(
        name: String,
        email: String,
        password: String,
        role: String,
        dialog: AlertDialog
    ) {
        val primaryAdminUid = auth.currentUser?.uid
        if (primaryAdminUid == null) {
            dialog.dismiss()
            Toast.makeText(this, "Admin session পাওয়া যায়নি", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val primaryApp = FirebaseApp.getInstance()
            val secondaryAppName = "MDC_USER_CREATOR"
            val secondaryApp = try {
                FirebaseApp.getInstance(secondaryAppName)
            } catch (_: Exception) {
                FirebaseApp.initializeApp(this, primaryApp.options, secondaryAppName)
                    ?: throw IllegalStateException("Secondary FirebaseApp initialize করা যায়নি")
            }

            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val newUser = authResult.user
                    if (newUser == null) {
                        dialog.dismiss()
                        Toast.makeText(this, "Firebase account তৈরি হয়নি", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val uid = newUser.uid
                    val profile = hashMapOf<String, Any>(
                        "name" to name,
                        "email" to email,
                        "role" to role,
                        "active" to true,
                        "createdByUid" to primaryAdminUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    // Primary FirebaseAuth session remains the Admin session;
                    // only the secondary FirebaseApp creates the new account.
                    db.collection("users").document(uid).set(profile)
                        .addOnSuccessListener {
                            secondaryAuth.signOut()
                            dialog.dismiss()
                            Toast.makeText(
                                this,
                                "$name ($role) সফলভাবে তৈরি হয়েছে। এখন ওই Email ও Password দিয়ে Login করা যাবে।",
                                Toast.LENGTH_LONG
                            ).show()
                            showUserManagement()
                        }
                        .addOnFailureListener { error ->
                            // Roll back the Auth account if the profile write fails.
                            newUser.delete()
                                .addOnCompleteListener {
                                    secondaryAuth.signOut()
                                    dialog.dismiss()
                                    Toast.makeText(
                                        this,
                                        "User profile তৈরি করা যায়নি: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                }
                .addOnFailureListener { error ->
                    dialog.dismiss()
                    Toast.makeText(
                        this,
                        "Firebase account তৈরি ব্যর্থ: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        } catch (error: Exception) {
            dialog.dismiss()
            Toast.makeText(
                this,
                "Account creation setup ব্যর্থ: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRoleChangeDialog(uid: String, current: String) {
        if (uid == auth.currentUser?.uid) {
            Toast.makeText(this, "বর্তমান Admin নিজের Role পরিবর্তন করতে পারবেন না", Toast.LENGTH_LONG).show()
            return
        }
        val roles = arrayOf("admin", "operator", "user")
        var selected = roles.indexOf(current.lowercase()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("User Role পরিবর্তন")
            .setSingleChoiceItems(roles, selected) { _, which -> selected = which }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                db.collection("users").document(uid).update("role", roles[selected])
                    .addOnSuccessListener { showUserManagement() }
                    .addOnFailureListener { e -> Toast.makeText(this, "Role update ব্যর্থ: ${e.message}", Toast.LENGTH_LONG).show() }
            }.show()
    }

    // =========================================================
    // NOTIFICATIONS
    // =========================================================

    private fun showNotifications() {
        setupSystemBars()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(backgroundColor) }
        root.addView(createInnerTopBar("Notifications") { showDashboard(currentRole) })
        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(30)) }
        content.addView(TextView(this).apply { text = "🔔 Notifications"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(darkText) })
        if (currentRole == "admin") {
            content.addView(createPrimaryButton("＋  নতুন Notification").apply { setOnClickListener { showCreateNotificationDialog() } }, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(12), 0, dp(12)) })
        }
        db.collection("notifications").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50).get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) content.addView(createEmptyText("কোনো Notification নেই"))
                result.documents.forEach { doc ->
                    val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = roundedCardDrawable(Color.WHITE, dp(14)); elevation = dp(2).toFloat() }
                    card.addView(TextView(this).apply { text = doc.getString("title") ?: "Notification"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(darkText) })
                    card.addView(TextView(this).apply { text = doc.getString("message") ?: ""; textSize = 15f; setTextColor(lightText); setPadding(0, dp(5), 0, 0) })
                    content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })
                }
            }.addOnFailureListener { e -> content.addView(createEmptyText("Notification পড়া যায়নি: ${e.message}")) }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
    }

    private fun showCreateNotificationDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val title = createFormInput("Notification Title")
        val message = createFormInput("Message")
        message.setSingleLine(false); message.minLines = 4; message.gravity = Gravity.TOP
        box.addView(title, formParams()); box.addView(message, LinearLayout.LayoutParams(-1, dp(110)).apply { setMargins(0, 0, 0, dp(8)) })
        AlertDialog.Builder(this).setTitle("নতুন Notification").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Send", null).create().also { dialog ->
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = title.text.toString().trim(); val m = message.text.toString().trim()
                if (t.isEmpty() || m.isEmpty()) { Toast.makeText(this, "Title ও Message দিন", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                db.collection("notifications").add(hashMapOf("title" to t, "message" to m, "createdByUid" to (auth.currentUser?.uid ?: ""), "createdAt" to FieldValue.serverTimestamp(), "active" to true))
                    .addOnSuccessListener { dialog.dismiss(); showNotifications() }
                    .addOnFailureListener { e -> Toast.makeText(this, "Notification তৈরি করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show() }
            } }
        }.show()
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    private fun showSettings() {
        if (currentRole != "admin") {
            Toast.makeText(this, "শুধু Admin Settings ব্যবহার করতে পারবেন", Toast.LENGTH_LONG).show()
            return
        }
        setupSystemBars()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(backgroundColor) }
        root.addView(createInnerTopBar("Settings") { showDashboard(currentRole) })
        val scroll = PullToRefreshScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(30)) }
        content.addView(TextView(this).apply { text = "⚙️ App Settings"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(darkText) })
        val centerName = createFormInput("Center Name")
        val notice = createFormInput("Default Notice")
        val autoRefresh = createFormInput("Auto Refresh Seconds")
        content.addView(TextView(this).apply { text = "Center Name"; textSize = 14f; setTextColor(lightText); setPadding(0, dp(12), 0, dp(4)) })
        content.addView(centerName, formParams())
        content.addView(TextView(this).apply { text = "Default Notice"; textSize = 14f; setTextColor(lightText); setPadding(0, dp(8), 0, dp(4)) })
        content.addView(notice, formParams())
        content.addView(TextView(this).apply { text = "Auto Refresh Seconds"; textSize = 14f; setTextColor(lightText); setPadding(0, dp(8), 0, dp(4)) })
        content.addView(autoRefresh, formParams())
        val save = createPrimaryButton("💾  Save Settings")
        content.addView(save, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(12), 0, 0) })
        db.collection("settings").document("app").get().addOnSuccessListener { doc ->
            centerName.setText(doc.getString("centerName") ?: "Moon Diagnostic Center")
            notice.setText(doc.getString("defaultNotice") ?: "")
            autoRefresh.setText((doc.getLong("autoRefreshSeconds") ?: 20L).toString())
        }
        save.setOnClickListener {
            val seconds = autoRefresh.text.toString().toLongOrNull()?.coerceAtLeast(5L) ?: 20L
            db.collection("settings").document("app").set(hashMapOf("centerName" to centerName.text.toString().trim(), "defaultNotice" to notice.text.toString().trim(), "autoRefreshSeconds" to seconds, "updatedByUid" to (auth.currentUser?.uid ?: ""), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .addOnSuccessListener { Toast.makeText(this, "Settings সংরক্ষণ হয়েছে", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { e -> Toast.makeText(this, "Settings সংরক্ষণ করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show() }
        }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
    }

    private fun createNavigationMenu(
        navigationView: NavigationView,
        role: String
    ) {

        navigationView.menu.clear()

        val header =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(30),
                    dp(40),
                    dp(20),
                    dp(30)
                )

                setBackgroundColor(
                    topBarColor
                )
            }

        val logo =
            TextView(this).apply {

                text = "MDC"

                textSize = 34f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.WHITE
                )
            }

        val centerName =
            TextView(this).apply {

                text =
                    "মুন ডায়াগনস্টিক সেন্টার"

                textSize = 20f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(8)
                )
            }

        val userRole =
            TextView(this).apply {

                text =
                    "Role: ${role.uppercase()}"

                textSize = 16f

                setTextColor(
                    Color.WHITE
                )
            }

        header.addView(logo)
        header.addView(centerName)
        header.addView(userRole)

        navigationView.addHeaderView(
            header
        )

        if (role == "admin") {

            navigationView.menu.add(
                "Admin Control Panel"
            ).apply {

                setIcon(
                    android.R.drawable.ic_menu_manage
                )

                setOnMenuItemClickListener {

                    drawerLayout.closeDrawer(
                        Gravity.START
                    )

                    showAdminControlPanel()
                    true
                }
            }
        }

        val messageItem = navigationView.menu.add(
            "💬  Message"
        ).apply {
            setIcon(android.R.drawable.ic_dialog_info)
            setOnMenuItemClickListener {
                drawerLayout.closeDrawer(Gravity.START)
                showMessages()
                true
            }
        }

        messageItem.actionView = TextView(this).apply {
            text = "MESSAGE"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedCardDrawable(Color.rgb(21, 101, 192), dp(18))
            setOnClickListener {
                drawerLayout.closeDrawer(Gravity.START)
                showMessages()
            }
        }

        // User Management is an Admin-only management area.
        if (role == "admin") {
            navigationView.menu.add(
                "User Management"
            ).apply {

                setIcon(
                    android.R.drawable.ic_menu_myplaces
                )

                setOnMenuItemClickListener {

                    drawerLayout.closeDrawer(
                        Gravity.START
                    )

                    showUserManagement()
                    true
                }
            }
        }

        // Everyone may open the Notification screen, but only Admin sees
        // the create button; Firestore rules enforce Admin-only writes.
        navigationView.menu.add(
            "Notifications"
        ).apply {

            setIcon(
                android.R.drawable.ic_dialog_info
            )

            setOnMenuItemClickListener {

                drawerLayout.closeDrawer(
                    Gravity.START
                )

                showNotifications()
                true
            }
        }

        // Settings is an Admin-only configuration area.
        if (role == "admin") {
            navigationView.menu.add(
                "Settings"
            ).apply {

                setIcon(
                    android.R.drawable.ic_menu_preferences
                )

                setOnMenuItemClickListener {

                    drawerLayout.closeDrawer(
                        Gravity.START
                    )

                    showSettings()
                    true
                }
            }
        }

        navigationView.menu.add(
            "Logout"
        ).apply {

            setIcon(
                android.R.drawable.ic_lock_power_off
            )

            setOnMenuItemClickListener {

                drawerLayout.closeDrawer(
                    Gravity.START
                )

                auth.signOut()

                showLogin()

                true
            }
        }
    }
    override fun onDestroy() {
        clearActiveListeners()
        super.onDestroy()
    }

}
