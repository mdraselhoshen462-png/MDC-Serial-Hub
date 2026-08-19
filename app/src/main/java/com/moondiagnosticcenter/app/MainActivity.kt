package com.moondiagnosticcenter.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private var currentRole: String = ""

    // =========================================================
    // COLORS
    // =========================================================

    private val topBarColor = Color.rgb(13, 110, 110)
    private val primaryColor = Color.rgb(21, 101, 192)
    private val backgroundColor = Color.rgb(245, 247, 250)
    private val whiteColor = Color.WHITE
    private val darkText = Color.rgb(45, 45, 45)
    private val lightText = Color.rgb(90, 90, 90)

    // Dashboard colors
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

        if (auth.currentUser != null) {
            checkUserAccess()
        } else {
            showLogin()
        }
    }

    // =========================================================
    // DP
    // =========================================================

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // =========================================================
    // SYSTEM BARS
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

                currentRole =
                    role.lowercase()

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

                gravity = Gravity.CENTER
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

        // =====================================================
        // WELCOME
        // =====================================================

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

        // =====================================================
        // ROW 2
        // =====================================================

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

                orientation =
                    LinearLayout.HORIZONTAL
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

        // =====================================================
        // MENU
        // =====================================================

        menuButton.setOnClickListener {

            drawerLayout.openDrawer(
                Gravity.START
            )
        }

        // =====================================================
        // ACTIONS
        // =====================================================

        totalSerial.setOnClickListener {

            Toast.makeText(
                this,
                "Total Serial",
                Toast.LENGTH_SHORT
            ).show()
        }

        addSerial.setOnClickListener {

            Toast.makeText(
                this,
                "Add Serial",
                Toast.LENGTH_SHORT
            ).show()
        }

        // =====================================================
        // ADD DOCTOR
        // =====================================================

        addDoctor.setOnClickListener {

            showAddDoctor()
        }

        addCareOf.setOnClickListener {

            Toast.makeText(
                this,
                "Add Care Of",
                Toast.LENGTH_SHORT
            ).show()
        }

        search.setOnClickListener {

            Toast.makeText(
                this,
                "Search",
                Toast.LENGTH_SHORT
            ).show()
        }

        doctors.setOnClickListener {

            Toast.makeText(
                this,
                "Doctors",
                Toast.LENGTH_SHORT
            ).show()
        }

        careOf.setOnClickListener {

            Toast.makeText(
                this,
                "Care Of",
                Toast.LENGTH_SHORT
            ).show()
        }

        reports.setOnClickListener {

            Toast.makeText(
                this,
                "Reports",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // =========================================================
    // ADD DOCTOR PAGE
    // =========================================================

    private fun showAddDoctor() {

        setupSystemBars()

        val root =
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

        val backButton =
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
            }

        topBar.addView(
            backButton,
            LinearLayout.LayoutParams(
                dp(60),
                dp(60)
            )
        )

        val title =
            TextView(this).apply {

                text =
                    "Add Doctor"

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

        root.addView(
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
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(30)
                )
            }

        // =====================================================
        // PAGE TITLE
        // =====================================================

        val pageTitle =
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

        content.addView(pageTitle)

        // =====================================================
        // DOCTOR NAME
        // =====================================================

        addFormLabel(
            content,
            "ডাক্তারের নাম *"
        )

        val doctorName =
            createFormInput(
                "যেমন: ডাঃ আব্দুল্লাহ আল মুজাহিদ"
            )

        content.addView(
            doctorName,
            formParams()
        )

        // =====================================================
        // QUALIFICATION
        // =====================================================

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

        // =====================================================
        // SPECIALTY
        // =====================================================

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

        // =====================================================
        // VISITING TIME
        // =====================================================

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

        // =====================================================
        // MOBILE
        // =====================================================

        addFormLabel(
            content,
            "মোবাইল নম্বর"
        )

        val mobile =
            createFormInput(
                "যেমন: 01XXXXXXXXX"
            )

        mobile.inputType =
            InputType.TYPE_CLASS_PHONE

        content.addView(
            mobile,
            formParams()
        )

        // =====================================================
        // STATUS
        // =====================================================

        val status =
            TextView(this).apply {

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

        content.addView(status)

        // =====================================================
        // SAVE BUTTON
        // =====================================================

        val saveButton =
            Button(this).apply {

                text =
                    "💾  SAVE DOCTOR"

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

        content.addView(
            saveButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            ).apply {

                setMargins(
                    0,
                    dp(15),
                    0,
                    dp(10)
                )
            }
        )

        // =====================================================
        // SAVE
        // =====================================================

        saveButton.setOnClickListener {

            val name =
                doctorName.text.toString().trim()

            val degree =
                qualification.text.toString().trim()

            val specialist =
                specialty.text.toString().trim()

            val time =
                visitingTime.text.toString().trim()

            val phone =
                mobile.text.toString().trim()

            // -----------------------------------------------
            // VALIDATION
            // -----------------------------------------------

            if (name.isEmpty()) {

                doctorName.error =
                    "ডাক্তারের নাম দিন"

                doctorName.requestFocus()

                return@setOnClickListener
            }

            saveButton.isEnabled = false

            status.text =
                "Doctor তথ্য সংরক্ষণ হচ্ছে..."

            // -----------------------------------------------
            // FIRESTORE DATA
            // -----------------------------------------------

            val doctorData =
                hashMapOf(
                    "name" to name,
                    "qualification" to degree,
                    "specialty" to specialist,
                    "visitingTime" to time,
                    "mobile" to phone,
                    "active" to true,
                    "createdBy" to (
                        auth.currentUser?.uid ?: ""
                    ),
                    "createdAt" to
                            FieldValue.serverTimestamp()
                )

            // -----------------------------------------------
            // SAVE
            // -----------------------------------------------

            db.collection("doctors")
                .add(doctorData)
                .addOnSuccessListener {

                    saveButton.isEnabled = true

                    status.text =
                        "Doctor সফলভাবে যোগ হয়েছে ✓"

                    Toast.makeText(
                        this,
                        "Doctor সফলভাবে যোগ হয়েছে",
                        Toast.LENGTH_LONG
                    ).show()

                    doctorName.text.clear()
                    qualification.text.clear()
                    specialty.text.clear()
                    visitingTime.text.clear()
                    mobile.text.clear()
                }
                .addOnFailureListener { error ->

                    saveButton.isEnabled = true

                    status.text = ""

                    Toast.makeText(
                        this,
                        "Doctor যোগ করা যায়নি: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        scrollView.addView(content)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        // =====================================================
        // BACK
        // =====================================================

        backButton.setOnClickListener {

            showDashboard(currentRole)
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

        parent.addView(
            label,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
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

            setTextColor(
                darkText
            )

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

                includeFontPadding = true
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

        card.addView(
            labelView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

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

        // =====================================================
        // HEADER
        // =====================================================

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
        // ADMIN
        // =====================================================

        if (role.lowercase() == "admin") {

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

                showLogin()

                true
            }
        }
    }
}
