package com.moondiagnosticcenter.app

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private var currentRole: String = ""

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
        private const val SCREEN_DOCTOR_SERIAL = "doctor_serial"
        private const val SCREEN_CAREOF_SERIAL = "careof_serial"
        private const val SCREEN_ADD_DOCTOR = "add_doctor"
        private const val SCREEN_ADD_CAREOF = "add_careof"
        private const val SCREEN_ADD_SERIAL = "add_serial"

        private const val REQUEST_GALLERY = 501
        private const val REQUEST_CAMERA = 502

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

        val password =
            EditText(this).apply {

                hint = "Password"

                textSize = 18f

                setSingleLine(true)

                inputType =
                    InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_VARIATION_PASSWORD

                setPadding(
                    dp(15),
                    dp(12),
                    dp(15),
                    dp(12)
                )
            }

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
            password,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(15)
                )
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

    private fun showDashboard(role: String) {

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
            ScrollView(this).apply {
                isFillViewport = true
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

                text = "Serial Summary"

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
                "বাতিল",
                "0"
            ),
            summaryParams()
        )

        content.addView(summaryRow)

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

            if (role.lowercase() == "admin") {
                showAddCareOf()
            } else {
                Toast.makeText(
                    this,
                    "শুধুমাত্র Admin Care Of যোগ করতে পারবেন",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        search.setOnClickListener {
            showTotalSerial()
        }

        doctors.setOnClickListener {
            showDoctorList()
        }

        careOf.setOnClickListener {
            showCareOfList()
        }

        reports.setOnClickListener {
            showTotalSerial()
        }
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
            ScrollView(this).apply {
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

                val data =
                    hashMapOf(
                        "name" to careName,
                        "address" to careAddress,
                        "active" to true,
                        "createdByUid" to
                                (
                                    auth.currentUser?.uid
                                        ?: ""
                                ),
                        "createdAt" to
                                FieldValue.serverTimestamp()
                    )

                db.collection("careOf")
                    .add(data)
                    .addOnSuccessListener {

                        Toast.makeText(
                            this,
                            "Care Of সফলভাবে যোগ হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()

                        dialog.dismiss()

                        onSaved()
                    }
                    .addOnFailureListener { error ->

                        saveButton.isEnabled = true

                        Toast.makeText(
                            this,
                            "Care Of যোগ করা যায়নি: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }

        dialog.show()
    }

    // =========================================================
    // DATE PICKER FOR ADD SERIAL
    // =========================================================

    private fun showAddSerialDatePicker() {

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
                            (
                                auth.currentUser?.uid
                                    ?: ""
                            ),

                    "createdByName" to
                            (
                                auth.currentUser?.email
                                    ?: ""
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
            ScrollView(this)

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
            ScrollView(this)

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
            ScrollView(this)

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
                            address
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
        address: String
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

        val editButton =
            createSmallButton(
                "✏ Edit"
            )

        editButton.setOnClickListener {

            showEditCareOfDialog(
                careOfId
            )
        }

        buttonRow.addView(
            editButton
        )

        if (currentRole == "admin") {

            val deleteButton =
                createSmallButton(
                    "🗑 Delete"
                )

            deleteButton.setOnClickListener {

                confirmDeleteCareOf(
                    careOfId,
                    name
                )
            }

            buttonRow.addView(
                deleteButton
            )
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

        if (currentRole != "admin") {

            Toast.makeText(
                this,
                "শুধুমাত্র Admin Care Of Delete করতে পারবেন",
                Toast.LENGTH_LONG
            ).show()

            return
        }

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

        if (currentRole != "admin") {

            Toast.makeText(
                this,
                "শুধুমাত্র Admin Care Of যোগ করতে পারবেন",
                Toast.LENGTH_LONG
            ).show()

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
            ScrollView(this)

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

            val data =
                hashMapOf(
                    "name" to careName,
                    "address" to careAddress,
                    "active" to true,
                    "createdByUid" to
                            (
                                auth.currentUser?.uid
                                    ?: ""
                            ),
                    "createdAt" to
                            FieldValue.serverTimestamp()
                )

            db.collection("careOf")
                .add(data)
                .addOnSuccessListener {

                    save.isEnabled = true

                    status.text =
                        "Care Of সফলভাবে যোগ হয়েছে ✓"

                    Toast.makeText(
                        this,
                        "Care Of সফলভাবে যোগ হয়েছে",
                        Toast.LENGTH_SHORT
                    ).show()

                    name.text.clear()
                    address.text.clear()
                }
                .addOnFailureListener { error ->

                    save.isEnabled = true

                    status.text = ""

                    Toast.makeText(
                        this,
                        "Care Of যোগ করা যায়নি: ${error.message}",
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

        if (filterField == "doctorId") {

            currentScreen =
                SCREEN_DOCTOR_SERIAL

        } else {

            currentScreen =
                SCREEN_CAREOF_SERIAL
        }

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
                "Serial List"
            ) {

                if (
                    filterField == "doctorId"
                ) {
                    showDoctorList()
                } else {
                    showCareOfList()
                }
            }
        )

        val scroll =
            ScrollView(this)

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(30)
                )
            }

        val titleText =
            TextView(this).apply {

                text = title

                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )
            }

        content.addView(titleText)

        val dateText =
            TextView(this).apply {

                text =
                    "📅 তারিখ: ${formatDisplayDate(date)}"

                textSize = 17f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    primaryColor
                )

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(15)
                )
            }

        content.addView(dateText)

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

        db.collection("serials")
            .whereEqualTo(
                filterField,
                filterValue
            )
            .whereEqualTo(
                "createdDate",
                date
            )
            .get()
            .addOnSuccessListener { result ->

                progress.visibility =
                    View.GONE

                if (result.isEmpty) {

                    content.addView(
                        createEmptyText(
                            "এই তারিখে কোনো Serial পাওয়া যায়নি"
                        )
                    )

                    return@addOnSuccessListener
                }

                val serials =
                    result.documents.sortedWith(
                        compareByDescending<DocumentSnapshot> {
                            it.getBoolean(
                                "patientVip"
                            ) ?: false
                        }.thenBy {
                            it.getLong(
                                "number"
                            ) ?: 0L
                        }
                    )

                for (document in serials) {

                    content.addView(
                        createSerialCard(
                            document
                        )
                    )
                }
            }
            .addOnFailureListener { error ->

                progress.visibility =
                    View.GONE

                Toast.makeText(
                    this,
                    "Serial List পাওয়া যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // SERIAL CARD
    // =========================================================

    private fun createSerialCard(
        document: DocumentSnapshot
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
            document.getLong("number")
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

        if (
            document.getBoolean(
                "hasAttachment"
            ) == true
        ) {

            card.addView(
                createInfoText(
                    "📎 Document",
                    document.getString(
                        "attachmentName"
                    ) ?: "সংযুক্ত"
                )
            )
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
    // EDIT SERIAL
    // =========================================================

    private fun showEditSerialDialog(
        document: DocumentSnapshot
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

        val patientInput =
            createFormInput(
                "Patient Name"
            )

        patientInput.setText(
            document.getString(
                "patient"
            ) ?: ""
        )

        val descriptionInput =
            createFormInput(
                "Description"
            )

        descriptionInput.setSingleLine(false)
        descriptionInput.minLines = 4
        descriptionInput.gravity = Gravity.TOP

        descriptionInput.setText(
            document.getString(
                "description"
            ) ?: ""
        )

        val vipCheck =
            CheckBox(this).apply {

                text =
                    "⭐ VIP Patient"

                textSize = 17f

                isChecked =
                    document.getBoolean(
                        "patientVip"
                    ) ?: false
            }

        container.addView(
            patientInput,
            formParams()
        )

        container.addView(
            vipCheck,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        container.addView(
            descriptionInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(110)
            )
        )

        AlertDialog.Builder(this)
            .setTitle(
                "Edit Serial"
            )
            .setView(container)
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Save"
            ) { _: DialogInterface, _: Int ->

                val newPatient =
                    patientInput.text.toString().trim()

                val newDescription =
                    descriptionInput.text.toString().trim()

                if (
                    newPatient.isEmpty()
                ) {

                    Toast.makeText(
                        this,
                        "Patient Name দিন",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                db.collection("serials")
                    .document(document.id)
                    .update(
                        mapOf(
                            "patient" to newPatient,
                            "description" to newDescription,
                            "patientVip" to
                                    vipCheck.isChecked
                        )
                    )
                    .addOnSuccessListener {

                        Toast.makeText(
                            this,
                            "Serial আপডেট হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()

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

    // =========================================================
    // DELETE SERIAL
    // =========================================================

    private fun confirmDeleteSerial(
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

        val statuses =
            arrayOf(
                "Waiting",
                "Completed",
                "Cancelled"
            )

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

                db.collection("serials")
                    .document(documentId)
                    .update(
                        "status",
                        selected
                    )
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
    // TOTAL SERIAL
    // =========================================================

    private fun showTotalSerial() {

        currentScreen =
            SCREEN_TOTAL_SERIAL

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
                "Total Serial"
            ) {
                showDashboard(currentRole)
            }
        )

        val scroll =
            ScrollView(this)

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
                    "📋 Total Serial"

                textSize = 25f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(10)
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

        db.collection("serials")
            .get()
            .addOnSuccessListener { result ->

                progress.visibility =
                    View.GONE

                if (result.isEmpty) {

                    content.addView(
                        createEmptyText(
                            "কোনো Serial পাওয়া যায়নি"
                        )
                    )

                    return@addOnSuccessListener
                }

                // =================================================
                // VIP প্রথমে
                // তারপর Date
                // তারপর Serial Number
                // =================================================

                val serials =
                    result.documents.sortedWith(
                        compareByDescending<DocumentSnapshot> {
                            it.getBoolean(
                                "patientVip"
                            ) ?: false
                        }.thenByDescending {
                            it.getString(
                                "createdDate"
                            ) ?: ""
                        }.thenBy {
                            it.getLong(
                                "number"
                            ) ?: 0L
                        }
                    )

                for (document in serials) {

                    content.addView(
                        createSerialCard(
                            document
                        )
                    )
                }
            }
            .addOnFailureListener { error ->

                progress.visibility =
                    View.GONE

                Toast.makeText(
                    this,
                    "Total Serial পাওয়া যায়নি: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // INNER TOP BAR
    //
// =========================================================

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

                    Toast.makeText(
                        this@MainActivity,
                        "Admin Control Panel",
                        Toast.LENGTH_SHORT
                    ).show()

                    true
                }
            }
        }

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

                Toast.makeText(
                    this@MainActivity,
                    "User Management",
                    Toast.LENGTH_SHORT
                ).show()

                true
            }
        }

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

                Toast.makeText(
                    this@MainActivity,
                    "Notifications",
                    Toast.LENGTH_SHORT
                ).show()

                true
            }
        }

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

                Toast.makeText(
                    this@MainActivity,
                    "Settings",
                    Toast.LENGTH_SHORT
                ).show()

                true
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
}
