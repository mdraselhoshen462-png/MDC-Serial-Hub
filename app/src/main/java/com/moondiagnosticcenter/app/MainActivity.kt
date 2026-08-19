package com.moondiagnosticcenter.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            checkUserAccess()
        } else {
            showLogin()
        }
    }

    // =========================
    // LOGIN
    // =========================

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
            setPadding(0, 10, 0, 40)
            setTextColor(Color.DKGRAY)
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
            setPadding(0, 15, 0, 15)
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

            auth.signInWithEmailAndPassword(emailText, passwordText)
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

    // =========================
    // CHECK USER
    // =========================

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

                val active = document.getBoolean("active") ?: false
                val role = document.getString("role") ?: ""

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

                showDashboard(role)
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

    // =========================
    // DASHBOARD
    // =========================

    private fun showDashboard(role: String) {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        // =========================
        // TOP BAR
        // =========================

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 15, 15, 15)
            setBackgroundColor(Color.WHITE)
        }

        val menuButton = Button(this).apply {
            text = "☰"
            textSize = 27f
            setTextColor(Color.rgb(21, 101, 192))
            setBackgroundColor(Color.TRANSPARENT)
        }

        val title = TextView(this).apply {
            text = "Moon Diagnostic Center"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(21, 101, 192))
            setPadding(15, 0, 0, 0)
        }

        topBar.addView(
            menuButton,
            LinearLayout.LayoutParams(
                65,
                65
            )
        )

        topBar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(topBar)

        // =========================
        // SCROLL CONTENT
        // =========================

        val scrollView = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Welcome

        val welcome = TextView(this).apply {
            text = "স্বাগতম\nRole: ${role.uppercase()}"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.DKGRAY)
            setPadding(5, 5, 5, 20)
        }

        content.addView(welcome)

        // =========================
        // TOP FOUR OPTIONS
        // =========================

        val topGrid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val totalSerial = createDashboardButton(
            "📋\nTotal Serial"
        )

        val addSerial = createDashboardButton(
            "➕\nAdd Serial"
        )

        topGrid.addView(
            totalSerial,
            LinearLayout.LayoutParams(
                0,
                150,
                1f
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        )

        topGrid.addView(
            addSerial,
            LinearLayout.LayoutParams(
                0,
                150,
                1f
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        )

        content.addView(topGrid)

        val secondGrid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val addDoctor = createDashboardButton(
            "👨‍⚕️\nAdd Doctor"
        )

        val addCareOf = createDashboardButton(
            "👤\nAdd Care Of"
        )

        secondGrid.addView(
            addDoctor,
            LinearLayout.LayoutParams(
                0,
                150,
                1f
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        )

        secondGrid.addView(
            addCareOf,
            LinearLayout.LayoutParams(
                0,
                150,
                1f
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        )

        content.addView(secondGrid)

        // =========================
        // SERIAL SUMMARY
        // =========================

        val summaryTitle = TextView(this).apply {
            text = "Serial Summary"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(5, 35, 5, 15)
        }

        content.addView(summaryTitle)

        val summaryGrid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val waiting = createSummaryCard(
            "অপেক্ষমাণ",
            "0"
        )

        val completed = createSummaryCard(
            "সম্পন্ন",
            "0"
        )

        summaryGrid.addView(
            waiting,
            LinearLayout.LayoutParams(
                0,
                110,
                1f
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        )

        summaryGrid.addView(
            completed,
            LinearLayout.LayoutParams(
                0,
                110,
                1f
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        )

        content.addView(summaryGrid)

        // =========================
        // BOTTOM FOUR OPTIONS
        // =========================

        val bottomTitle = TextView(this).apply {
            text = "Quick Access"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(5, 30, 5, 10)
        }

        content.addView(bottomTitle)

        val bottomGrid1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val search = createDashboardButton(
            "🔎\nSearch"
        )

        val doctors = createDashboardButton(
            "👨‍⚕️\nDoctors"
        )

        bottomGrid1.addView(
            search,
            LinearLayout.LayoutParams(
                0,
                135,
                1f
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        )

        bottomGrid1.addView(
            doctors,
            LinearLayout.LayoutParams(
                0,
                135,
                1f
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        )

        content.addView(bottomGrid1)

        val bottomGrid2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val careOf = createDashboardButton(
            "👤\nCare Of"
        )

        val reports = createDashboardButton(
            "📊\nReports"
        )

        bottomGrid2.addView(
            careOf,
            LinearLayout.LayoutParams(
                0,
                135,
                1f
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        )

        bottomGrid2.addView(
            reports,
            LinearLayout.LayoutParams(
                0,
                135,
                1f
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        )

        content.addView(bottomGrid2)

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

        // =========================
        // HAMBURGER MENU
        // =========================

        menuButton.setOnClickListener {
            showMenu(role)
        }

        // =========================
        // TEMPORARY ACTIONS
        // =========================

        totalSerial.setOnClickListener {
            Toast.makeText(
                this,
                "Total Serial শীঘ্রই চালু হবে",
                Toast.LENGTH_SHORT
            ).show()
        }

        addSerial.setOnClickListener {
            Toast.makeText(
                this,
                "Add Serial শীঘ্রই চালু হবে",
                Toast.LENGTH_SHORT
            ).show()
        }

        addDoctor.setOnClickListener {
            Toast.makeText(
                this,
                "Add Doctor শীঘ্রই চালু হবে",
                Toast.LENGTH_SHORT
            ).show()
        }

        addCareOf.setOnClickListener {
            Toast.makeText(
                this,
                "Add Care Of শীঘ্রই চালু হবে",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // =========================
    // DASHBOARD BUTTON
    // =========================

    private fun createDashboardButton(text: String): Button {

        return Button(this).apply {
            this.text = text
            textSize = 17f
            gravity = Gravity.CENTER
            setAllCaps(false)
            setTextColor(Color.rgb(21, 101, 192))
        }
    }

    // =========================
    // SUMMARY CARD
    // =========================

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
            textSize = 15f
            gravity = Gravity.CENTER
        }

        val valueText = TextView(this).apply {
            text = value
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(21, 101, 192))
        }

        card.addView(titleText)
        card.addView(valueText)

        return card
    }

    // =========================
    // HAMBURGER MENU
    // =========================

    private fun showMenu(role: String) {

        val popup = PopupWindow(
            this
        )

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 10, 10, 10)
            setBackgroundColor(Color.WHITE)
        }

        if (role.lowercase() == "admin") {

            val admin = createMenuButton(
                "⚙️  Admin Control Panel"
            )

            admin.setOnClickListener {
                popup.dismiss()

                Toast.makeText(
                    this,
                    "Admin Control Panel শীঘ্রই চালু হবে",
                    Toast.LENGTH_SHORT
                ).show()
            }

            menu.addView(admin)
        }

        val users = createMenuButton(
            "👥  User Management"
        )

        users.setOnClickListener {
            popup.dismiss()

            Toast.makeText(
                this,
                "User Management শীঘ্রই চালু হবে",
                Toast.LENGTH_SHORT
            ).show()
        }

        menu.addView(users)

        val settings = createMenuButton(
            "⚙️  Settings"
        )

        settings.setOnClickListener {
            popup.dismiss()

            Toast.makeText(
                this,
                "Settings শীঘ্রই চালু হবে",
                Toast.LENGTH_SHORT
            ).show()
        }

        menu.addView(settings)

        val notifications = createMenuButton(
            "🔔  Notifications"
        )

        notifications.setOnClickListener {
            popup.dismiss()

            Toast.makeText(
                this,
                "Notifications শীঘ্রই চালু হবে",
                Toast.LENGTH_SHORT
            ).show()
        }

        menu.addView(notifications)

        val logout = createMenuButton(
            "🚪  Logout"
        )

        logout.setOnClickListener {
            popup.dismiss()

            auth.signOut()

            showLogin()
        }

        menu.addView(logout)

        popup.contentView = menu
        popup.width = 310
        popup.height = LinearLayout.LayoutParams.WRAP_CONTENT
        popup.isFocusable = true
        popup.isOutsideTouchable = true

        popup.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.WHITE)
        )

        popup.elevation = 12f

        popup.showAtLocation(
            window.decorView,
            Gravity.TOP or Gravity.START,
            10,
            80
        )
    }

    private fun createMenuButton(text: String): Button {

        return Button(this).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setAllCaps(false)
            setPadding(20, 5, 20, 5)
            setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
