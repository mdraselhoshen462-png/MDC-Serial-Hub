package com.moondiagnosticcenter.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            checkUserAccess()
        } else {
            showLogin()
        }
    }

    // =========================================================
    // LOGIN
    // =========================================================

    private fun showLogin() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(45, 40, 45, 40)
            setBackgroundColor(Color.WHITE)
        }

        val logo = TextView(this).apply {
            text = "MDC"
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(21, 101, 192))
        }

        val title = TextView(this).apply {
            text = "মুন ডায়াগনস্টিক সেন্টার"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 35)
        }

        val loginTitle = TextView(this).apply {
            text = "Login"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 25)
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
        }

        val status = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 0)
        }

        loginButton.setOnClickListener {

            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString()

            if (emailText.isEmpty() || passwordText.isEmpty()) {
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
    // USER ACCESS CHECK
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
                    document.getBoolean("active") ?: false

                val role =
                    document.getString("role") ?: ""

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
    // MAIN DASHBOARD
    // =========================================================

    private fun showDashboard(role: String) {

        drawerLayout = DrawerLayout(this)

        drawerLayout.setBackgroundColor(
            Color.rgb(245, 247, 250)
        )

        // =====================================================
        // MAIN CONTENT
        // =====================================================

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                Color.rgb(245, 247, 250)
            )
        }

        // =====================================================
        // TOP BAR
        // =====================================================

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 10, 15, 10)
            setBackgroundColor(Color.WHITE)
        }

        val menuButton = ImageButton(this).apply {

            setImageResource(
                android.R.drawable.ic_menu_sort_by_size
            )

            setBackgroundColor(Color.TRANSPARENT)

            contentDescription = "Menu"

            setColorFilter(
                Color.rgb(21, 101, 192)
            )
        }

        topBar.addView(
            menuButton,
            LinearLayout.LayoutParams(
                60,
                60
            )
        )

        val title = TextView(this).apply {
            text = "Moon Diagnostic Center"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                Color.rgb(21, 101, 192)
            )
            setPadding(12, 0, 0, 0)
        }

        topBar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val roleText = TextView(this).apply {
            text = role.uppercase()
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.DKGRAY)
        }

        topBar.addView(roleText)

        mainLayout.addView(topBar)

        // =====================================================
        // SCROLL CONTENT
        // =====================================================

        val scrollView = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 30)
        }

        val welcome = TextView(this).apply {
            text = "স্বাগতম"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.DKGRAY)
            setPadding(5, 5, 5, 20)
        }

        content.addView(welcome)

        // =====================================================
        // TOP FOUR OPTIONS
        // =====================================================

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val totalSerial =
            createDashboardButton("📋\nTotal Serial")

        val addSerial =
            createDashboardButton("➕\nAdd Serial")

        topRow.addView(
            totalSerial,
            gridParams()
        )

        topRow.addView(
            addSerial,
            gridParams()
        )

        content.addView(topRow)

        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val addDoctor =
            createDashboardButton("👨‍⚕️\nAdd Doctor")

        val addCareOf =
            createDashboardButton("👤\nAdd Care Of")

        secondRow.addView(
            addDoctor,
            gridParams()
        )

        secondRow.addView(
            addCareOf,
            gridParams()
        )

        content.addView(secondRow)

        // =====================================================
        // SERIAL SUMMARY
        // =====================================================

        val summaryTitle = TextView(this).apply {
            text = "Serial Summary"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(5, 30, 5, 12)
        }

        content.addView(summaryTitle)

        val summaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        summaryRow.addView(
            createSummaryCard("মোট", "0"),
            LinearLayout.LayoutParams(
                0,
                105,
                1f
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        )

        summaryRow.addView(
            createSummaryCard("অপেক্ষমাণ", "0"),
            LinearLayout.LayoutParams(
                0,
                105,
                1f
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        )

        summaryRow.addView(
            createSummaryCard("সম্পন্ন", "0"),
            LinearLayout.LayoutParams(
                0,
                105,
                1f
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        )

        summaryRow.addView(
            createSummaryCard("বাতিল", "0"),
            LinearLayout.LayoutParams(
                0,
                105,
                1f
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        )

        content.addView(summaryRow)

        // =====================================================
        // BOTTOM FOUR OPTIONS
        // =====================================================

        val quickTitle = TextView(this).apply {
            text = "Quick Access"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(5, 30, 5, 12)
        }

        content.addView(quickTitle)

        val bottomRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val search =
            createDashboardButton("🔎\nSearch")

        val doctors =
            createDashboardButton("👨‍⚕️\nDoctors")

        bottomRow1.addView(
            search,
            gridParams()
        )

        bottomRow1.addView(
            doctors,
            gridParams()
        )

        content.addView(bottomRow1)

        val bottomRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val careOf =
            createDashboardButton("👤\nCare Of")

        val reports =
            createDashboardButton("📊\nReports")

        bottomRow2.addView(
            careOf,
            gridParams()
        )

        bottomRow2.addView(
            reports,
            gridParams()
        )

        content.addView(bottomRow2)

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

        // =====================================================
        // NAVIGATION DRAWER
        // =====================================================

        navigationView = NavigationView(this)

        val drawerWidth =
            (resources.displayMetrics.widthPixels * 0.82).toInt()

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

        // =====================================================
        // MENU BUTTON
        // =====================================================

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(
                Gravity.START
            )
        }

        // =====================================================
        // DASHBOARD ACTIONS
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
    // NAVIGATION MENU
    // =========================================================

    private fun createNavigationMenu(
        navigationView: NavigationView,
        role: String
    ) {

        val menu = navigationView.menu

        menu.clear()

        // -----------------------------------------------------
        // HEADER
        // -----------------------------------------------------

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 45, 20, 30)
            setBackgroundColor(
                Color.rgb(21, 101, 192)
            )
        }

        val logo = TextView(this).apply {
            text = "MDC"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val centerName = TextView(this).apply {
            text = "মুন ডায়াগনস্টিক সেন্টার"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 8)
        }

        val userRole = TextView(this).apply {
            text = "Role: ${role.uppercase()}"
            textSize = 15f
            setTextColor(Color.WHITE)
        }

        header.addView(logo)
        header.addView(centerName)
        header.addView(userRole)

        navigationView.addHeaderView(header)

        // -----------------------------------------------------
        // ADMIN CONTROL PANEL
        // -----------------------------------------------------

        if (role.lowercase() == "admin") {

            menu.add(
                "Admin Control Panel"
            ).apply {
                setIcon(
                    android.R.drawable.ic_menu_manage
                )
                setOnMenuItemClickListener {
                    navigationView.menu
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

        // -----------------------------------------------------
        // USER MANAGEMENT
        // -----------------------------------------------------

        menu.add(
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

        // -----------------------------------------------------
        // NOTIFICATIONS
        // -----------------------------------------------------

        menu.add(
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

        // -----------------------------------------------------
        // SETTINGS
        // -----------------------------------------------------

        menu.add(
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

        // -----------------------------------------------------
        // LOGOUT
        // -----------------------------------------------------

        menu.add(
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
                Color.rgb(21, 101, 192)
            )

            setBackgroundColor(Color.WHITE)
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
    // SUMMARY CARD
    // =========================================================

    private fun createSummaryCard(
        title: String,
        value: String
    ): LinearLayout {

        val card = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER

            setBackgroundColor(Color.WHITE)
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 14f
            gravity = Gravity.CENTER
        }

        val valueText = TextView(this).apply {
            text = value
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                Color.rgb(21, 101, 192)
            )
        }

        card.addView(titleText)
        card.addView(valueText)

        return card
    }
}
