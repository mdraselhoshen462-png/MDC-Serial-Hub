package com.moondiagnosticcenter.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
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
    // SYSTEM BAR SETUP
    // =========================================================

    private fun setupSystemBars() {

        // Edge-to-edge বন্ধ রাখা
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
                45,
                40,
                45,
                40
            )

            setBackgroundColor(whiteColor)
        }

        val logo = TextView(this).apply {

            text = "MDC"

            textSize = 44f

            typeface = Typeface.DEFAULT_BOLD

            gravity = Gravity.CENTER

            setTextColor(primaryColor)
        }

        val title = TextView(this).apply {

            text = "মুন ডায়াগনস্টিক সেন্টার"

            textSize = 24f

            typeface = Typeface.DEFAULT_BOLD

            gravity = Gravity.CENTER

            setPadding(
                0,
                10,
                0,
                35
            )

            setTextColor(darkText)
        }

        val loginTitle = TextView(this).apply {

            text = "Login"

            textSize = 25f

            typeface = Typeface.DEFAULT_BOLD

            gravity = Gravity.CENTER

            setPadding(
                0,
                10,
                0,
                25
            )
        }

        val email = EditText(this).apply {

            hint = "Email"

            textSize = 18f

            setSingleLine(true)
        }

        val password = EditText(this).apply {

            hint = "Password"

            textSize = 18f

            setSingleLine(true)

            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val loginButton = Button(this).apply {

            text = "LOGIN"

            textSize = 18f

            setPadding(
                0,
                15,
                0,
                15
            )
        }

        val status = TextView(this).apply {

            textSize = 15f

            gravity = Gravity.CENTER

            setPadding(
                0,
                15,
                0,
                0
            )
        }

        // -----------------------------------------------------
        // LOGIN BUTTON
        // -----------------------------------------------------

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

        root.addView(email)

        root.addView(password)

        root.addView(loginButton)

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

        setupSystemBars()

        drawerLayout =
            DrawerLayout(this).apply {

                setBackgroundColor(
                    backgroundColor
                )
            }

        // =====================================================
        // MAIN CONTENT
        // =====================================================

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

                // Status bar-এর জায়গা + নিচের spacing
                setPadding(
                    8,
                    getStatusBarHeight() + 8,
                    15,
                    12
                )

                setBackgroundColor(
                    topBarColor
                )

                elevation = 8f
            }

        // -----------------------------------------------------
        // MENU BUTTON
        // -----------------------------------------------------

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

                isClickable = true

                isFocusable = true
            }

        topBar.addView(
            menuButton,
            LinearLayout.LayoutParams(
                65,
                65
            )
        )

        // -----------------------------------------------------
        // TITLE
        // -----------------------------------------------------

        val title =
            TextView(this).apply {

                text =
                    "Moon Diagnostic Center"

                textSize = 20f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    12,
                    0,
                    0,
                    0
                )

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        topBar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        // -----------------------------------------------------
        // ROLE
        // -----------------------------------------------------

        val roleText =
            TextView(this).apply {

                text =
                    role.uppercase()

                textSize = 13f

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
            topBar
        )

        // =====================================================
        // SCROLL CONTENT
        // =====================================================

        val scrollView =
            ScrollView(this)

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    18,
                    18,
                    18,
                    30
                )
            }

        // =====================================================
        // WELCOME
        // =====================================================

        val welcome =
            TextView(this).apply {

                text =
                    "স্বাগতম"

                textSize = 23f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    darkText
                )

                setPadding(
                    5,
                    5,
                    5,
                    20
                )
            }

        content.addView(
            welcome
        )

        // =====================================================
        // TOP FOUR OPTIONS
        // =====================================================

        val topRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        val totalSerial =
            createDashboardButton(
                "📋\nTotal Serial"
            )

        val addSerial =
            createDashboardButton(
                "➕\nAdd Serial"
            )

        topRow.addView(
            totalSerial,
            gridParams()
        )

        topRow.addView(
            addSerial,
            gridParams()
        )

        content.addView(
            topRow
        )

        // -----------------------------------------------------
        // SECOND ROW
        // -----------------------------------------------------

        val secondRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        val addDoctor =
            createDashboardButton(
                "👨‍⚕️\nAdd Doctor"
            )

        val addCareOf =
            createDashboardButton(
                "👤\nAdd Care Of"
            )

        secondRow.addView(
            addDoctor,
            gridParams()
        )

        secondRow.addView(
            addCareOf,
            gridParams()
        )

        content.addView(
            secondRow
        )

        // =====================================================
        // SERIAL SUMMARY
        // =====================================================

        val summaryTitle =
            TextView(this).apply {

                text =
                    "Serial Summary"

                textSize = 21f

                typeface =
                    Typeface.DEFAULT_BOLD

                setPadding(
                    5,
                    30,
                    5,
                    12
                )
            }

        content.addView(
            summaryTitle
        )

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

        content.addView(
            summaryRow
        )

        // =====================================================
        // BOTTOM FOUR OPTIONS
        // =====================================================

        val quickTitle =
            TextView(this).apply {

                text =
                    "Quick Access"

                textSize = 21f

                typeface =
                    Typeface.DEFAULT_BOLD

                setPadding(
                    5,
                    30,
                    5,
                    12
                )
            }

        content.addView(
            quickTitle
        )

        // -----------------------------------------------------
        // BOTTOM ROW 1
        // -----------------------------------------------------

        val bottomRow1 =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        val search =
            createDashboardButton(
                "🔎\nSearch"
            )

        val doctors =
            createDashboardButton(
                "👨‍⚕️\nDoctors"
            )

        bottomRow1.addView(
            search,
            gridParams()
        )

        bottomRow1.addView(
            doctors,
            gridParams()
        )

        content.addView(
            bottomRow1
        )

        // -----------------------------------------------------
        // BOTTOM ROW 2
        // -----------------------------------------------------

        val bottomRow2 =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        val careOf =
            createDashboardButton(
                "👤\nCare Of"
            )

        val reports =
            createDashboardButton(
                "📊\nReports"
            )

        bottomRow2.addView(
            careOf,
            gridParams()
        )

        bottomRow2.addView(
            reports,
            gridParams()
        )

        content.addView(
            bottomRow2
        )

        scrollView.addView(
            content
        )

        mainLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // =====================================================
        // ADD MAIN CONTENT TO DRAWER
        // =====================================================

        drawerLayout.addView(
            mainLayout,
            DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
            )
        )

        // =====================================================
        // NAVIGATION DRAWER
        // =====================================================

        navigationView =
            NavigationView(this).apply {

                setBackgroundColor(
                    Color.WHITE
                )
            }

        val drawerWidth =
            (
                resources.displayMetrics.widthPixels
                    * 0.82
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

        setContentView(
            drawerLayout
        )

        // =====================================================
        // OPEN DRAWER
        // =====================================================

        menuButton.setOnClickListener {

            drawerLayout.openDrawer(
                Gravity.START
            )
        }

        // =====================================================
        // DASHBOARD BUTTON ACTIONS
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

        addDoctor.setOnClickListener {

            Toast.makeText(
                this,
                "Add Doctor",
                Toast.LENGTH_SHORT
            ).show()
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
    // NAVIGATION DRAWER MENU
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
                    30,
                    40,
                    20,
                    30
                )

                setBackgroundColor(
                    topBarColor
                )
            }

        val logo =
            TextView(this).apply {

                text =
                    "MDC"

                textSize = 32f

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

                textSize = 19f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    8,
                    0,
                    8
                )
            }

        val userRole =
            TextView(this).apply {

                text =
                    "Role: ${role.uppercase()}"

                textSize = 15f

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

        // =====================================================
        // ADMIN CONTROL PANEL
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

    // =========================================================
    // DASHBOARD BUTTON
    // =========================================================

    private fun createDashboardButton(
        text: String
    ): Button {

        return Button(this).apply {

            this.text = text

            textSize = 17f

            gravity = Gravity.CENTER

            setAllCaps(false)

            setTextColor(
                primaryColor
            )

            setBackgroundColor(
                Color.WHITE
            )

            isAllCaps = false
        }
    }

    // =========================================================
    // GRID PARAMS
    // =========================================================

    private fun gridParams():
            LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            145,
            1f
        ).apply {

            setMargins(
                6,
                6,
                6,
                6
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
            105,
            1f
        ).apply {

            setMargins(
                4,
                4,
                4,
                4
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

                setBackgroundColor(
                    Color.WHITE
                )
            }

        val titleText =
            TextView(this).apply {

                text = title

                textSize = 13f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.DKGRAY
                )
            }

        val valueText =
            TextView(this).apply {

                text = value

                textSize = 24f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setTextColor(
                    primaryColor
                )
            }

        card.addView(
            titleText
        )

        card.addView(
            valueText
        )

        return card
    }
}
