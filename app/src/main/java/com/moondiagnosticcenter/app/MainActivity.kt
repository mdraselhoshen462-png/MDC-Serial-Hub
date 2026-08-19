package com.moondiagnosticcenter.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

    private fun showLogin() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 40, 50, 40)
        }

        val title = TextView(this).apply {
            text = "MDC"
            textSize = 42f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "মুন ডায়াগনস্টিক সেন্টার"
            textSize = 22f
            gravity = Gravity.CENTER
        }

        val loginTitle = TextView(this).apply {
            text = "Admin / User Login"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 30)
        }

        val email = EditText(this).apply {
            hint = "Email"
            textSize = 18f
            setSingleLine(true)
        }

        val password = EditText(this).apply {
            hint = "Password"
            textSize = 18f
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        val loginButton = Button(this).apply {
            text = "LOGIN"
            textSize = 18f
        }

        val status = TextView(this).apply {
            text = ""
            textSize = 16f
            gravity = Gravity.CENTER
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

        root.addView(title)
        root.addView(subtitle)
        root.addView(loginTitle)
        root.addView(email)
        root.addView(password)
        root.addView(loginButton)
        root.addView(status)

        setContentView(root)
    }

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

    private fun showDashboard(role: String) {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Moon Diagnostic Center"
            textSize = 28f
        }

        val welcome = TextView(this).apply {
            text = "স্বাগতম\nRole: ${role.uppercase()}"
            textSize = 22f
            setPadding(0, 30, 0, 40)
        }

        val totalSerial = Button(this).apply {
            text = "📋 Total Serial"
            textSize = 18f
        }

        val addSerial = Button(this).apply {
            text = "➕ Add Serial"
            textSize = 18f
        }

        val addDoctor = Button(this).apply {
            text = "👨‍⚕️ Add Doctor"
            textSize = 18f
        }

        val addCareOf = Button(this).apply {
            text = "👤 Add Care Of"
            textSize = 18f
        }

        val logout = Button(this).apply {
            text = "Logout"
            textSize = 18f
        }

        logout.setOnClickListener {
            auth.signOut()
            showLogin()
        }

        root.addView(title)
        root.addView(welcome)
        root.addView(totalSerial)
        root.addView(addSerial)
        root.addView(addDoctor)
        root.addView(addCareOf)

        if (role.lowercase() == "admin") {
            val admin = Button(this).apply {
                text = "⚙️ Admin Control Panel"
                textSize = 18f
            }

            root.addView(admin)
        }

        root.addView(logout)

        setContentView(root)
    }
}
