package com.moondiagnosticcenter.app

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
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
    // BACK NAVIGATION
    // =========================================================

    private var currentPage: String = "login"

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
    // BACK BUTTON / GESTURE
    // =========================================================

    private fun setupBackButton() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    // Drawer খোলা থাকলে আগে Drawer বন্ধ হবে
                    if (
                        ::drawerLayout.isInitialized &&
                        drawerLayout.isDrawerOpen(Gravity.START)
                    ) {
                        drawerLayout.closeDrawer(Gravity.START)
                        return
                    }

                    when (currentPage) {

                        "doctor_serials" -> {
                            showDoctorList()
                        }

                        "doctor_list" -> {
                            showDashboard(currentRole)
                        }

                        "add_doctor" -> {
                            showDashboard(currentRole)
                        }

                        "careof_serials" -> {
                            showCareOfList()
                        }

                        "careof_list" -> {
                            showDashboard(currentRole)
                        }

                        "add_careof" -> {
                            showDashboard(currentRole)
                        }

                        "total_serial" -> {
                            showDashboard(currentRole)
                        }

                        "dashboard" -> {
                            // Dashboard থেকে Back দিলে অ্যাপ বন্ধ হবে
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }

                        "login" -> {
                            // Login page থেকে Back দিলে অ্যাপ বন্ধ হবে
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
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

        val resourceId = resources.getIdentifier(
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

        currentPage = "login"

        setupSystemBars()

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER

            setPadding(
                dp(30),
                dp(30),
                dp(30),
                dp(30)
            )

            setBackgroundColor(whiteColor)
        }

        val logo = TextView(this).apply {

            text = "MDC"

            textSize = 46f

            typeface = Typeface.DEFAULT_BOLD

            gravity = Gravity.CENTER

            setTextColor(primaryColor)
        }

        val title = TextView(this).apply {

            text = "মুন ডায়াগনস্টিক সেন্টার"

            textSize = 25f

            typeface = Typeface.DEFAULT_BOLD

            gravity = Gravity.CENTER

            setPadding(
                0,
                dp(10),
                0,
                dp(30)
            )

            setTextColor(darkText)
        }

        val loginTitle = TextView(this).apply {

            text = "Login"

            textSize = 26f

            typeface = Typeface.DEFAULT_BOLD

            gravity = Gravity.CENTER

            setPadding(
                0,
                dp(10),
                0,
                dp(22)
            )
        }

        val email = EditText(this).apply {

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

        val password = EditText(this).apply {

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

        val loginButton = Button(this).apply {

            text = "LOGIN"

            textSize = 18f

            setPadding(
                0,
                dp(12),
                0,
                dp(12)
            )
        }

        val status = TextView(this).apply {

            textSize = 15f

            gravity = Gravity.CENTER

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

            status.text = "Login হচ্ছে..."

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

                currentRole = role.lowercase()

                showDashboard(currentRole)
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

        currentPage = "dashboard"

        setupSystemBars()

        drawerLayout =
            DrawerLayout(this).apply {
                setBackgroundColor(backgroundColor)
            }

        val mainLayout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    backgroundColor
                )
            }

        // =====================================================
        // TOP BAR
        // =====================================================

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

                setColorFilter(Color.WHITE)

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

                setTextColor(Color.WHITE)

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

                setTextColor(Color.WHITE)

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

        // =====================================================
        // SCROLL
        // =====================================================

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

                setTextColor(darkText)

                setPadding(
                    dp(5),
                    dp(4),
                    dp(5),
                    dp(14)
                )
            }

        content.addView(welcome)

        // =====================================================
        // ROW 1
        // =====================================================

        val row1 =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
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

        // =====================================================
        // ROW 2
        // =====================================================

        val row2 =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
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

        // =====================================================
        // SUMMARY
        // =====================================================

        val summaryTitle =
            TextView(this).apply {

                text = "Serial Summary"

                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(darkText)

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
                orientation = LinearLayout.HORIZONTAL
            }

        summaryRow.addView(
            createSummaryCard("মোট", "0"),
            summaryParams()
        )

        summaryRow.addView(
            createSummaryCard("অপেক্ষমাণ", "0"),
            summaryParams()
        )

        summaryRow.addView(
            createSummaryCard("সম্পন্ন", "0"),
            summaryParams()
        )

        summaryRow.addView(
            createSummaryCard("বাতিল", "0"),
            summaryParams()
        )

        content.addView(summaryRow)

        // =====================================================
        // QUICK ACCESS
        // =====================================================

        val quickTitle =
            TextView(this).apply {

                text = "Quick Access"

                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(darkText)

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
                orientation = LinearLayout.HORIZONTAL
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
                orientation = LinearLayout.HORIZONTAL
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

        // =====================================================
        // DRAWER
        // =====================================================

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

        drawerParams.gravity = Gravity.START

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

        // =====================================================
        // BUTTON ACTIONS
        // =====================================================

        totalSerial.setOnClickListener {
            showTotalSerial()
        }

        addSerial.setOnClickListener {

            Toast.makeText(
                this,
                "Add Serial",
                Toast.LENGTH_SHORT
            ).show()
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
    // ADD DOCTOR
    // =========================================================

    private fun showAddDoctor() {

        currentPage = "add_doctor"

        if (currentRole != "admin") {

            Toast.makeText(
                this,
                "শুধুমাত্র Admin এই কাজ করতে পারবেন",
                Toast.LENGTH_LONG
            ).show()

            return
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

                setTextColor(darkText)

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
                            (auth.currentUser?.uid ?: ""),
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

        currentPage = "doctor_list"

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

                setTextColor(darkText)

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
                gravity = Gravity.CENTER
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

        // =====================================================
        // IMPORTANT FIX:
        // No whereEqualTo + orderBy combination.
        // This avoids Firestore composite-index problem.
        // =====================================================

        db.collection("doctors")
            .get()
            .addOnSuccessListener { result ->

                progress.visibility =
                    View.GONE

                val doctors =
                    result.documents
                        .filter { document ->

                            val active =
                                document.getBoolean("active")

                            active == null || active
                        }
                        .sortedBy { document ->

                            document.getString("name")
                                ?.lowercase(Locale.getDefault())
                                ?: ""
                        }

                if (doctors.isEmpty()) {

                    content.addView(
                        createEmptyText(
                            "কোনো Doctor পাওয়া যায়নি"
                        )
                    )

                    return@addOnSuccessListener
                }

                for (document in doctors) {

                    val doctorName =
                        document.getString("name")
                            ?: "Unknown Doctor"

                    val specialty =
                        document.getString("specialty")
                            ?: ""

                    val qualification =
                        document.getString("qualification")
                            ?: ""

                    val card =
                        createDoctorListCard(
                            doctorName,
                            specialty,
                            qualification
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
    // DOCTOR DATE SELECTION
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
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {

            setTitle(
                "তারিখ নির্বাচন করুন"
            )

            show()
        }
    }

    // =========================================================
    // DOCTOR SERIALS
    // =========================================================

    private fun showDoctorSerials(
        doctorId: String,
        doctorName: String,
        date: String
    ) {

        showSerialListPage(
            title = "Doctor: $doctorName",
            filterField = "doctorId",
            filterValue = doctorId,
            date = date,
            fromDoctor = true
        )
    }

    // =========================================================
    // CARE OF LIST
    // =========================================================

    private fun showCareOfList() {

        currentPage = "careof_list"

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

                setTextColor(darkText)

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
                gravity = Gravity.CENTER
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

        // =====================================================
        // IMPORTANT FIX:
        // No whereEqualTo + orderBy combination.
        // =====================================================

        db.collection("careOf")
            .get()
            .addOnSuccessListener { result ->

                progress.visibility =
                    View.GONE

                val careOfList =
                    result.documents
                        .filter { document ->

                            val active =
                                document.getBoolean("active")

                            active == null || active
                        }
                        .sortedBy { document ->

                            document.getString("name")
                                ?.lowercase(Locale.getDefault())
                                ?: ""
                        }

                if (careOfList.isEmpty()) {

                    content.addView(
                        createEmptyText(
                            "কোনো Care Of পাওয়া যায়নি"
                        )
                    )

                    return@addOnSuccessListener
                }

                for (document in careOfList) {

                    val name =
                        document.getString("name")
                            ?: "Unknown"

                    val phone =
                        document.getString("mobile")
                            ?: ""

                    val card =
                        createCareOfListCard(
                            name,
                            phone
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
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {

            setTitle(
                "তারিখ নির্বাচন করুন"
            )

            show()
        }
    }

    // =========================================================
    // CARE OF SERIALS
    // =========================================================

    private fun showCareOfSerials(
        careOfId: String,
        careOfName: String,
        date: String
    ) {

        showSerialListPage(
            title = "Care Of: $careOfName",
            filterField = "careOfId",
            filterValue = careOfId,
            date = date,
            fromDoctor = false
        )
    }

    // =========================================================
    // SERIAL LIST PAGE
    // =========================================================

    private fun showSerialListPage(
        title: String,
        filterField: String,
        filterValue: String,
        date: String,
        fromDoctor: Boolean
    ) {

        if (fromDoctor) {
            currentPage = "doctor_serials"
        } else {
            currentPage = "careof_serials"
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

                if (fromDoctor) {
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

                setTextColor(darkText)

                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(5)
                )
            }

        content.addView(titleText)

        val dateText =
            TextView(this).apply {

                text =
                    "📅 তারিখ: $date"

                textSize = 17f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(primaryColor)

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
                gravity = Gravity.CENTER
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

        // =====================================================
        // IMPORTANT FIX:
        // No orderBy here.
        // Sort locally after Firestore returns documents.
        // =====================================================

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

                val serials =
                    result.documents.sortedBy { document ->

                        document.getLong("number")
                            ?: (
                                document.getString("number")
                                    ?.toLongOrNull()
                                    ?: Long.MAX_VALUE
                                )
                    }

                if (serials.isEmpty()) {

                    content.addView(
                        createEmptyText(
                            "এই তারিখে কোনো Serial পাওয়া যায়নি"
                        )
                    )

                    return@addOnSuccessListener
                }

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
                        Color.WHITE,
                        dp(15)
                    )

                elevation =
                    dp(3).toFloat()
            }

        val number =
            document.getLong("number")
                ?.toString()
                ?: document.getString("number")
                ?: "-"

        val patient =
            document.getString("patient")
                ?: "-"

        val careOf =
            document.getString("careOfName")
                ?: document.getString("careOf")
                ?: "-"

        val doctor =
            document.getString("doctorName")
                ?: document.getString("doctor")
                ?: "-"

        val status =
            document.getString("status")
                ?: "Waiting"

        val createdByRole =
            document.getString("createdByRole")
                ?: "-"

        val createdByName =
            document.getString("createdByName")
                ?: document.getString("createdBy")
                ?: "-"

        val title =
            TextView(this).apply {

                text =
                    "Serial #$number"

                textSize = 21f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(primaryColor)
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

        card.addView(
            createInfoText(
                "👤 Care Of",
                careOf
            )
        )

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

        // =====================================================
        // CREATOR EDIT / DELETE
        // =====================================================

        val currentUid =
            auth.currentUser?.uid ?: ""

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

        // =====================================================
        // OPERATOR / ADMIN STATUS
        // =====================================================

        if (
            currentRole == "operator" ||
            currentRole == "admin"
        ) {

            val statusButton =
                createSmallButton(
                    "📌 Status"
                )

            statusButton.setOnClickListener {

                showStatusDialog(
                    document.id,
                    status
                )
            }

            buttonRow.addView(
                statusButton
            )
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

        val patientInput =
            EditText(this).apply {

                hint = "Patient Name"

                setText(
                    document.getString(
                        "patient"
                    ) ?: ""
                )

                textSize = 17f
            }

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(10),
                    dp(20),
                    0
                )

                addView(
                    patientInput,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(60)
                    )
                )
            }

        AlertDialog.Builder(this)
            .setTitle("Edit Serial")
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

                if (newPatient.isEmpty()) {

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
                        "patient",
                        newPatient
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
            .setTitle("Serial Delete")
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
    // STATUS DIALOG
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
                statuses.indexOf(currentStatus)
                    .coerceAtLeast(0)
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

        currentPage = "total_serial"

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

                setTextColor(darkText)
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
                gravity = Gravity.CENTER
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

        // Local sorting instead of Firestore orderBy
        db.collection("serials")
            .get()
            .addOnSuccessListener { result ->

                progress.visibility =
                    View.GONE

                val serials =
                    result.documents.sortedBy { document ->

                        document.getLong("number")
                            ?: (
                                document.getString("number")
                                    ?.toLongOrNull()
                                    ?: Long.MAX_VALUE
                                )
                    }

                if (serials.isEmpty()) {

                    content.addView(
                        createEmptyText(
                            "কোনো Serial পাওয়া যায়নি"
                        )
                    )

                    return@addOnSuccessListener
                }

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
    // ADD CARE OF
    // =========================================================

    private fun showAddCareOf() {

        currentPage = "add_careof"

        if (currentRole != "admin") {

            Toast.makeText(
                this,
                "শুধুমাত্র Admin Care Of যোগ করতে পারবেন",
                Toast.LENGTH_LONG
            ).show()

            return
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

                setTextColor(darkText)
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

            if (careName.isEmpty()) {

                name.error =
                    "Care Of নাম দিন"

                return@setOnClickListener
            }

            save.isEnabled = false

            status.text =
                "Care Of সংরক্ষণ হচ্ছে..."

            val data =
                hashMapOf(
                    "name" to careName,
                    "mobile" to
                            mobile.text.toString().trim(),
                    "active" to true,
                    "createdByUid" to
                            (auth.currentUser?.uid ?: ""),
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
                    mobile.text.clear()
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
    // INNER TOP BAR
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

                contentDescription =
                    "Back"

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

                text = titleText

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
    // DOCTOR CARD
    // =========================================================

    private fun createDoctorListCard(
        name: String,
        specialty: String,
        qualification: String
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

                isClickable = true
                isFocusable = true
            }

        val title =
            TextView(this).apply {

                text =
                    "👨‍⚕️ $name"

                textSize = 19f

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
    // CARE OF CARD
    // =========================================================

    private fun createCareOfListCard(
        name: String,
        phone: String
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

                isClickable = true
                isFocusable = true
            }

        val title =
            TextView(this).apply {

                text =
                    "👤 $name"

                textSize = 19f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    primaryColor
                )
            }

        card.addView(title)

        if (phone.isNotEmpty()) {

            card.addView(
                createInfoText(
                    "📱 Mobile",
                    phone
                )
            )
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

            setTextColor(lightText)

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

            gravity = Gravity.CENTER

            setTextColor(lightText)

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

                setTextColor(darkText)

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

            hint = hintText

            textSize = 17f

            setTextColor(darkText)

            setHintTextColor(
                Color.rgb(140, 140, 140)
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

            setTextColor(Color.WHITE)

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
                    Color.rgb(235, 242, 250),
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

                setTextColor(darkText)

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

                setTextColor(lightText)

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

                setTextColor(primaryColor)
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

        navigationView.addHeaderView(header)

        // =====================================================
        // ADMIN CONTROL
        // =====================================================

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

        // =====================================================
        // USER MANAGEMENT
        // =====================================================

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

        // =====================================================
        // NOTIFICATIONS
        // =====================================================

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

        // =====================================================
        // SETTINGS
        // =====================================================

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

        // =====================================================
        // LOGOUT
        // =====================================================

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

                currentRole = ""

                showLogin()

                true
            }
        }
    }
}
