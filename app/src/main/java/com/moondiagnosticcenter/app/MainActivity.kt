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

    private var addSerialPatientInput: EditText? = null
    private var addSerialDoctorSpinner: Spinner? = null
    private var addSerialCareOfSpinner: Spinner? = null
    private var addSerialDateText: TextView? = null
    private var addSerialNumberText: TextView? = null
    private var addSerialStarCheck: CheckBox? = null
    private var addSerialDescriptionInput: EditText? = null
    private var addSerialAttachmentText: TextView? = null

    private val doctorIds = mutableListOf<String>()
    private val doctorNames = mutableListOf<String>()

    private val careOfIds = mutableListOf<String>()
    private val careOfNames = mutableListOf<String>()

    private var selectedDoctorId: String = ""
    private var selectedDoctorName: String = ""

    private var selectedCareOfId: String = ""
    private var selectedCareOfName: String = ""

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

        doctorIds.clear()
        doctorNames.clear()

        careOfIds.clear()
        careOfNames.clear()

        selectedDoctorId = ""
        selectedDoctorName = ""

        selectedCareOfId = ""
        selectedCareOfName = ""

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

                text = "⭐"

                textSize = 28f

                gravity =
                    Gravity.CENTER

                buttonDrawable =
                    null

                setPadding(
                    dp(8),
                    0,
                    dp(8),
                    0
                )

                contentDescription =
                    "VIP Patient"
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
            Spinner(this)

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
        // CARE OF CHANGE
        // =====================================================

        careSpinner.onItemSelectedListener =
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
                        position < careOfIds.size
                    ) {

                        selectedCareOfId =
                            careOfIds[position]

                        selectedCareOfName =
                            careOfNames[position]
                    }
                }
            }

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

        val spinner =
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

                // প্রথম option optional
                careOfIds.add("")
                careOfNames.add(
                    "কেয়ার অফ নির্বাচন করুন"
                )

                val sorted =
                    result.documents.sortedBy {

                        (
                            it.getString("name")
                                ?: ""
                        ).lowercase()
                    }

                for (doc in sorted) {

                    careOfIds.add(
                        doc.id
                    )

                    careOfNames.add(
                        doc.getString("name")
                            ?: "Unknown"
                    )
                }

                selectedCareOfId = ""
                selectedCareOfName = ""

                val adapter =
                    ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        careOfNames
                    )

                spinner.adapter =
                    adapter
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

                serialData["hasAttachment"] =
                    true

            } else {

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
                Intent.ACTION_GET_CONTENT
            ).apply {

                type = "image/*"

                addCategory(
                    Intent.CATEGORY_OPENABLE
                )
            }

        startActivityForResult(
            Intent.createChooser(
                intent,
                "ছবি নির্বাচন করুন"
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

                    processSelectedImage(
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
    // PROCESS GALLERY IMAGE
    // =========================================================

    private fun processSelectedImage(
        uri: Uri
    ) {

        try {

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

            val base64 =
                bitmapToBase64(
                    bitmap
                )

            selectedAttachmentBase64 =
                base64

            selectedAttachmentName =
                "gallery_${System.currentTimeMillis()}.jpg"

            addSerialAttachmentText?.text =
                "📎 ছবি সংযুক্ত হয়েছে"

        } catch (error: Exception) {

            Toast.makeText(
                this,
                "ছবি নির্বাচন করা যায়নি: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // PROCESS
