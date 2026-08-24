package com.moondiagnostic.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    // =========================================================
    // UI / COLORS
    // =========================================================
    private val BG = Color.rgb(242, 248, 253)
    private val BLUE = Color.rgb(28, 91, 145)
    private val DARK_BLUE = Color.rgb(20, 67, 110)
    private val TEAL = Color.rgb(18, 137, 128)
    private val RED = Color.rgb(198, 58, 58)
    private val GREEN = Color.rgb(39, 135, 91)
    private val ORANGE = Color.rgb(224, 143, 39)
    private val PURPLE = Color.rgb(103, 78, 161)
    private val WHITE = Color.WHITE
    private val DARK = Color.rgb(45, 50, 55)
    private val GRAY = Color.rgb(105, 110, 115)
    private val LIGHT_BORDER = Color.rgb(205, 220, 232)

    // =========================================================
    // FIREBASE REALTIME DATABASE
    // =========================================================
    private lateinit var db: FirebaseDatabase
    private lateinit var rootRef: DatabaseReference
    private var firebaseAvailable = false

    private val LOCAL_SERIAL_PREFIX = "serial_"

    private var serialListener: ValueEventListener? = null
    private var doctorListener: ValueEventListener? = null
    private var careListener: ValueEventListener? = null

    private val serials = mutableListOf<SerialRecord>()
    private val doctors = mutableListOf<String>()
    private val careOfs = mutableListOf<String>()

    // =========================================================
    // SESSION
    // =========================================================
    private val PREF_NAME = "MDC_APP_SESSION"
    private lateinit var pref: android.content.SharedPreferences
    private var currentUsername = ""
    private var currentRole = ""

    private enum class Screen {
        LOGIN, DASHBOARD, TOTAL, DOCTOR_SERIALS, CARE_SERIALS,
        ADD_SERIAL, ADD_DOCTOR, ADD_CARE, REPORT, ADMIN
    }

    private var currentScreen = Screen.LOGIN
    private var selectedDoctor = ""
    private var selectedCareOf = ""

    private data class SerialRecord(
        val id: String,
        val number: Int,
        val dateKey: String,
        val patient: String,
        val careOf: String,
        val doctor: String,
        val status: String,
        val createdBy: String,
        val createdRole: String,
        val createdAt: String,
        val completedBy: String = "",
        val completedAt: String = ""
    )

    // =========================================================
    // ACTIVITY
    // =========================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        initializeFirebaseIfAvailable()
        createDefaultAdmin()
        seedFirebaseAdminIfNeeded()

        if (pref.getBoolean("logged_in", false)) {
            currentUsername = pref.getString("current_user", "") ?: ""
            currentRole = pref.getString("current_role", "") ?: ""
            if (currentUsername.isNotEmpty() && currentRole.isNotEmpty()) {
                showDashboard()
                startRealtimeListeners()
            } else {
                showLogin()
            }
        } else {
            showLogin()
        }
    }

    // =========================================================
    // LOGIN
    // =========================================================
    private fun showLogin() {
        stopRealtimeListeners()
        currentUsername = ""
        currentRole = ""
        currentScreen = Screen.LOGIN

        val content = verticalContainer()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(18, 26, 18, 22)

        content.addView(space(22))
        content.addView(label("MDC", 50f, BLUE, true))
        content.addView(space(4))
        content.addView(label("মুন ডায়াগনস্টিক সেন্টার", 24f, DARK_BLUE, true))
        content.addView(space(3))
        content.addView(label("সঠিক নির্ণয়, সুস্থ জীবনের প্রত্যয়", 13f, GRAY))
        content.addView(space(16))

        val card = cardLayout()
        card.addView(label("লগইন করুন", 26f, DARK_BLUE, true))
        card.addView(space(10))

        val username = input("ইউজারনেম")
        val password = input("পাসওয়ার্ড", true)
        card.addView(username)

        val passwordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        password.layoutParams = LinearLayout.LayoutParams(0, 56, 1f).apply { setMargins(6, 5, 2, 5) }
        passwordRow.addView(password)
        passwordRow.addView(smallButton("👁", TEAL) {
            val hidden = password.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            val pos = password.selectionStart.coerceAtLeast(0)
            password.inputType = if (hidden) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            password.setSelection(pos.coerceAtMost(password.text.length))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(54, 46).apply { setMargins(2, 5, 6, 5) }
        })
        card.addView(passwordRow)
        card.addView(space(6))
        card.addView(actionButton("🔐   লগইন", BLUE, 58) {
            loginUser(username.text.toString().trim(), password.text.toString())
        })

        content.addView(card, matchWrap())
        content.addView(space(14))
        content.addView(label("অ্যাক্সেস শুধুমাত্র অনুমোদিত User / Operator / Admin-এর জন্য", 12.5f, GRAY))
        content.addView(space(10))
        content.addView(label("Moon Diagnostic Center", 14f, GRAY, true))
        content.addView(label("আপনার বিশ্বস্ত স্বাস্থ্যসেবা কেন্দ্র", 12f, GRAY))

        setContentView(pullToRefresh(content) { })
    }

    private fun loginUser(username: String, password: String) {
        if (username.isEmpty()) { toast("Username লিখুন"); return }
        if (password.isEmpty()) { toast("Password লিখুন"); return }

        if (firebaseAvailable) {
            rootRef.child("users").child(username).get().addOnSuccessListener { snap ->
                val savedHash = snap.child("passwordHash").getValue(String::class.java) ?: ""
                val role = snap.child("role").getValue(String::class.java) ?: ""
                val active = snap.child("active").getValue(Boolean::class.java) ?: false
                if (snap.exists() && active && savedHash == hashPassword(password) && role.isNotEmpty()) {
                    completeLogin(username, role)
                } else {
                    toast("Username অথবা Password ভুল / অ্যাকাউন্ট নিষ্ক্রিয়")
                }
            }.addOnFailureListener { toast("Firebase Database সংযোগ করা যাচ্ছে না") }
        } else {
            val savedUsername = pref.getString("user_$username", null)
            val savedPassword = pref.getString("pass_$username", null)
            val savedRole = pref.getString("role_$username", null)
            val active = pref.getBoolean("active_$username", true)
            if (savedUsername != null && active && savedPassword == hashPassword(password) && !savedRole.isNullOrEmpty()) {
                completeLogin(username, savedRole)
            } else {
                toast("Username অথবা Password ভুল")
            }
        }
    }

    private fun completeLogin(username: String, role: String) {
        currentUsername = username
        currentRole = role
        pref.edit()
            .putBoolean("logged_in", true)
            .putString("current_user", username)
            .putString("current_role", role)
            .apply()
        toast("সফলভাবে লগইন হয়েছে")
        showDashboard()
        startRealtimeListeners()
    }

    // =========================================================
    // DASHBOARD
    // =========================================================
    private fun showDashboard() {
        currentScreen = Screen.DASHBOARD
        val root = verticalContainer()
        root.setPadding(12, 16, 12, 18)

        root.addView(label("MDC", 46f, BLUE, true))
        root.addView(label("স্বাগতম, $currentUsername", 21f, DARK, true))
        root.addView(label("Role: $currentRole", 14f, TEAL, true))
        root.addView(space(6))
        root.addView(actionButton("🚪   Logout", RED, 54) { logout() })
        root.addView(space(10))

        val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        root.addView(label("আজকের তারিখ", 19f, DARK_BLUE, true))
        root.addView(label(date, 17f, DARK))
        root.addView(space(10))

        // serials is loaded only for today's date, so these are today's counts.
        val waiting = serials.count { it.status.equals("Waiting", true) }
        val present = serials.count { it.status.equals("Present", true) }
        val completed = serials.count { it.status.equals("Completed", true) }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(statCard("👥", "মোট সিরিয়াল", serials.size.toString(), BLUE))
        row1.addView(statCard("⏳", "অপেক্ষমাণ", waiting.toString(), ORANGE))
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(statCard("●", "উপস্থিত", present.toString(), Color.rgb(218, 170, 20)))
        row2.addView(statCard("✓", "সম্পন্ন", completed.toString(), GREEN))
        root.addView(row2)
        root.addView(space(12))

        root.addView(label("দ্রুত অ্যাকশন", 21f, DARK_BLUE, true))
        root.addView(space(3))
        root.addView(actionButton("📋   টোটাল সিরিয়াল", BLUE) { showTotalSerial() })
        root.addView(actionButton("＋   অ্যাড সিরিয়াল", BLUE) { showAddSerial() })
        root.addView(actionButton("👨‍⚕️   অ্যাড ডাক্তার", TEAL) { showAddDoctor() })
        root.addView(actionButton("👤   অ্যাড কেয়ার অফ", TEAL) { showAddCare() })

        root.addView(actionButton("📄   রিপোর্ট / PDF", PURPLE) { showReport() })

        if (currentRole.equals("Admin", true)) {
            root.addView(space(6))
            root.addView(actionButton("⚙   Admin Control Panel", PURPLE) { showAdminPanel() })
        }

        root.addView(space(12))
        root.addView(label(if (firebaseAvailable) "রিয়েল-টাইম ডাটা চালু আছে" else "লোকাল ডাটা মোড — Firebase যুক্ত করলে রিয়েল-টাইম হবে", 13f, if (firebaseAvailable) TEAL else ORANGE, true))
        root.addView(label("নতুন ডাটা এলে নিজে থেকেই আপডেট হবে। চাইলে উপর থেকে টেনে Refresh করতে পারবেন।", 12f, GRAY))
        root.addView(space(10))
        root.addView(label("মুন ডায়াগনস্টিক সেন্টার", 14f, GRAY, true))
        root.addView(label("আপনার বিশ্বস্ত স্বাস্থ্যসেবা কেন্দ্র", 12f, GRAY))

        setContentView(pullToRefresh(root) {
            refreshCurrentScreen()
        })
    }

    // =========================================================
    // TOTAL SERIAL: TWO INNER TABS
    // =========================================================
    private fun showTotalSerial() {
        currentScreen = Screen.TOTAL
        val root = verticalContainer()
        root.setPadding(12, 16, 12, 18)

        root.addView(label("📋 টোটাল সিরিয়াল", 25f, DARK_BLUE, true))
        root.addView(label("ডাক্তার ও কেয়ার অফ অনুযায়ী সিরিয়াল দেখতে নিচের ট্যাব নির্বাচন করুন", 12.5f, GRAY))
        root.addView(space(10))

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val doctorTab = tabButton("👨‍⚕️\nডাক্তার ওয়াইজ", BLUE) { showDoctorWise() }
        val careTab = tabButton("👤\nকেয়ার অফ ওয়াইজ", TEAL) { showCareWise() }
        tabs.addView(doctorTab)
        tabs.addView(careTab)
        root.addView(tabs)
        root.addView(space(12))

        root.addView(label("আজকের মোট সিরিয়াল: ${serials.size} জন", 18f, TEAL, true))
        root.addView(space(6))
        root.addView(label("সব সিরিয়াল ১ নম্বর থেকে ধারাবাহিকভাবে দেখা যাবে", 12.5f, GRAY))
        root.addView(space(8))

        serials.sortedBy { it.number }.forEach { root.addView(serialCard(it, allowActions = true)) }
        if (serials.isEmpty()) root.addView(label("আজ এখনো কোনো সিরিয়াল তৈরি হয়নি", 15f, GRAY))

        setContentView(pullToRefresh(root) { refreshCurrentScreen() })
    }

    private fun showDoctorWise() {
        currentScreen = Screen.DOCTOR_SERIALS
        selectedDoctor = ""
        val root = verticalContainer()
        root.addView(label("👨‍⚕️ ডাক্তার ওয়াইজ সিরিয়াল", 24f, DARK_BLUE, true))
        root.addView(label("এড করা ডাক্তার নির্বাচন করুন", 13f, GRAY))
        root.addView(space(8))

        if (doctors.isEmpty()) {
            root.addView(label("কোনো ডাক্তার এখনো যোগ করা হয়নি", 15f, GRAY))
        } else {
            doctors.sorted().forEach { doctor ->
                root.addView(actionButton("👨‍⚕️   $doctor", BLUE, 54) {
                    selectedDoctor = doctor
                    showFilteredSerials("ডাক্তার: $doctor", serials.filter { it.doctor == doctor })
                })
            }
        }
        setContentView(pullToRefresh(root) { refreshCurrentScreen() })
    }

    private fun showCareWise() {
        currentScreen = Screen.CARE_SERIALS
        selectedCareOf = ""
        val root = verticalContainer()
        root.addView(label("👤 কেয়ার অফ ওয়াইজ সিরিয়াল", 24f, DARK_BLUE, true))
        root.addView(label("এড করা কেয়ার অফ নির্বাচন করুন", 13f, GRAY))
        root.addView(space(8))

        if (careOfs.isEmpty()) {
            root.addView(label("কোনো কেয়ার অফ এখনো যোগ করা হয়নি", 15f, GRAY))
        } else {
            careOfs.sorted().forEach { care ->
                root.addView(actionButton("👤   $care", TEAL, 54) {
                    selectedCareOf = care
                    showFilteredSerials("কেয়ার অফ: $care", serials.filter { it.careOf == care })
                })
            }
        }
        setContentView(pullToRefresh(root) { refreshCurrentScreen() })
    }

    private fun showFilteredSerials(title: String, records: List<SerialRecord>) {
        val root = verticalContainer()
        root.addView(label(title, 23f, DARK_BLUE, true))
        root.addView(label("আজকের ${records.size}টি সিরিয়াল", 15f, TEAL, true))
        root.addView(space(8))
        records.sortedBy { it.number }.forEachIndexed { index, record -> root.addView(serialCard(record, true, index + 1)) }
        if (records.isEmpty()) root.addView(label("আজ এই নির্বাচন অনুযায়ী কোনো সিরিয়াল নেই", 15f, GRAY))
        setContentView(pullToRefresh(root) { refreshCurrentScreen() })
    }

    // =========================================================
    // ADD SERIAL
    // =========================================================
    private fun showAddSerial() {
        currentScreen = Screen.ADD_SERIAL
        val root = verticalContainer()
        root.addView(label("➕ নতুন সিরিয়াল", 25f, DARK_BLUE, true))
        root.addView(label("শুধু নতুন সিরিয়াল যোগ করুন", 13f, GRAY))
        root.addView(space(10))

        val card = cardLayout()
        val patient = input("রোগীর নাম")
        val careField = careOfField()
        val care = careField.first
        val doctor = selectionInput("ডাক্তার নির্বাচন করুন")

        card.addView(label("রোগীর নাম", 14f, DARK_BLUE, true))
        card.addView(patient)
        card.addView(space(4))
        card.addView(label("Care Of", 14f, DARK_BLUE, true))
        card.addView(careField.second)
        card.addView(space(4))
        card.addView(label("ডাক্তার", 14f, DARK_BLUE, true))
        card.addView(doctor)
        card.addView(space(8))
        card.addView(label("সিরিয়াল: $currentUsername • $currentRole", 13f, TEAL, true))
        card.addView(space(5))
        card.addView(actionButton("✅   সিরিয়াল তৈরি করুন", GREEN, 58) {
            val careName = care.text.toString().trim()
            val doctorName = doctor.tag?.toString()?.trim() ?: doctor.text.toString().trim()
            saveSerial(patient.text.toString().trim(), careName, doctorName)
        })
        root.addView(card)

        // Intentionally no serial list is shown on this page.
        setContentView(pullToRefresh(root) { refreshCurrentScreen() })
    }

    private fun saveSerial(patient: String, careOf: String, doctor: String) {
        if (patient.isEmpty()) {
            toast("রোগীর নাম লিখুন")
            return
        }
        if (doctor.isEmpty()) {
            toast("ডাক্তার নির্বাচন করুন")
            return
        }

        if (!firebaseAvailable) {
            saveSerialLocal(patient, careOf, doctor)
            return
        }

        val dateKey = todayKey()
        val counterRef = rootRef.child("counters").child(dateKey)
        counterRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(Int::class.java) ?: 0
                currentData.value = current + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null || !committed) {
                    toast("সিরিয়াল তৈরি করা যায়নি")
                    return
                }
                val number = currentData?.getValue(Int::class.java) ?: return
                val id = number.toString()
                val record = mapOf(
                    "number" to number,
                    "dateKey" to dateKey,
                    "patient" to patient,
                    "careOf" to careOf,
                    "doctor" to doctor,
                    "status" to "Waiting",
                    "createdBy" to currentUsername,
                    "createdRole" to currentRole,
                    "createdAt" to currentTime24()
                )
                rootRef.child("serials").child(dateKey).child(id).setValue(record)
                    .addOnSuccessListener {
                        toast("সিরিয়াল #$number তৈরি হয়েছে")
                        showDashboard()
                    }
                    .addOnFailureListener { toast("ডাটা সংরক্ষণ করা যায়নি") }
            }
        })
    }

    private fun saveSerialLocal(patient: String, careOf: String, doctor: String) {
        val dateKey = todayKey()
        var next = 1
        for (key in pref.all.keys) {
            if (key.startsWith(LOCAL_SERIAL_PREFIX + dateKey + "_")) {
                next = maxOf(next, (key.substringAfterLast("_").toIntOrNull() ?: 0) + 1)
            }
        }
        val key = LOCAL_SERIAL_PREFIX + dateKey + "_" + next
        val value = listOf(patient, careOf, doctor, "Waiting", currentUsername, currentRole, currentTime24()).joinToString("||")
        pref.edit().putString(key, value).apply()
        loadLocalData()
        toast("সিরিয়াল #$next তৈরি হয়েছে")
        showDashboard()
    }

    // =========================================================
    // DOCTOR / CARE OF MANAGEMENT
    // =========================================================
    private fun showAddDoctor() {
        currentScreen = Screen.ADD_DOCTOR
        val root = verticalContainer()
        root.addView(label("👨‍⚕️ অ্যাড ডাক্তার", 25f, DARK_BLUE, true))
        root.addView(label("ডাক্তারের নাম যোগ করুন", 13f, GRAY))
        root.addView(space(10))
        val name = input("ডাক্তারের নাম")
        root.addView(name)
        root.addView(actionButton("＋   ডাক্তার যোগ করুন", TEAL, 56) { addNamedItem("doctors", name.text.toString().trim()) })
        root.addView(space(12))
        doctors.sorted().forEach { root.addView(label("• $it", 15f, DARK)) }
        setContentView(pullToRefresh(root) { refreshCurrentScreen() })
    }

    private fun showAddCare() {
        currentScreen = Screen.ADD_CARE
        val root = verticalContainer()
        root.addView(label("👤 অ্যাড কেয়ার অফ", 25f, DARK_BLUE, true))
        root.addView(label("কেয়ার অফ / অভিভাবকের নাম যোগ করুন", 13f, GRAY))
        root.addView(space(10))
        val name = input("Care Of নাম")
        root.addView(name)
        root.addView(actionButton("＋   কেয়ার অফ যোগ করুন", TEAL, 56) { addNamedItem("careOfs", name.text.toString().trim()) })
        root.addView(space(12))
        careOfs.sorted().forEach { root.addView(label("• $it", 15f, DARK)) }
        setContentView(pullToRefresh(root) { refreshCurrentScreen() })
    }

    private fun addNamedItem(collection: String, value: String, onSuccess: (() -> Unit)? = null) {
        if (!currentRole.equals("Admin", true)) {
            toast("শুধুমাত্র Admin যোগ করতে পারবেন")
            return
        }
        if (value.isEmpty()) {
            toast("নাম লিখুন")
            return
        }

        if (!firebaseAvailable) {
            val exists = if (collection == "doctors") {
                doctors.any { it.equals(value, true) }
            } else {
                careOfs.any { it.equals(value, true) }
            }
            if (exists) {
                toast("এই নামটি আগে থেকেই আছে")
                return
            }
            val key = if (collection == "doctors") "doctor_$value" else "care_$value"
            pref.edit().putString(key, value).apply()
            loadLocalData()
            toast("সফলভাবে যোগ হয়েছে")
            onSuccess?.invoke()
            return
        }

        rootRef.child(collection).get().addOnSuccessListener { snap ->
            val duplicate = snap.children.any {
                (it.getValue(String::class.java) ?: "").equals(value, true)
            }
            if (duplicate) {
                toast("এই নামটি আগে থেকেই আছে")
                return@addOnSuccessListener
            }

            val safeKey = value.replace(".", "_").replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_").replace("/", "_")
            rootRef.child(collection).child(safeKey).setValue(value)
                .addOnSuccessListener {
                    toast("সফলভাবে যোগ হয়েছে")
                    onSuccess?.invoke()
                }
                .addOnFailureListener { toast("যোগ করা যায়নি") }
        }.addOnFailureListener { toast("নাম যাচাই করা যায়নি") }
    }

    // =========================================================
    // SERIAL CARD / STATUS / EDIT / DELETE
    // =========================================================
    private fun serialCard(r: SerialRecord, allowActions: Boolean, displayNumber: Int? = null): View {
        val card = cardLayout()
        card.setPadding(14, 12, 14, 12)
        val shownNumber = displayNumber ?: r.number
        card.addView(label(
            "সিরিয়াল #$shownNumber${if (displayNumber != null) "  (মূল #${r.number})" else ""}   •   ${statusBangla(r.status)}",
            18f, statusColor(r.status), true
        ))
        card.addView(label("👤 ${r.patient}", 16f, DARK, true))
        card.addView(label("Care Of: ${if (r.careOf.isEmpty()) "—" else r.careOf}", 13.5f, GRAY))
        card.addView(label("👨‍⚕️ ডাক্তার: ${r.doctor}", 14f, DARK))
        card.addView(label("✍ দিয়েছেন: ${r.createdBy} (${r.createdRole})", 13f, TEAL, true))
        card.addView(label("সময়: ${r.createdAt}", 12f, GRAY))

        if (r.status.equals("Completed", true) && r.completedBy.isNotEmpty()) {
            card.addView(label("সম্পন্ন করেছেন: ${r.completedBy} • ${r.completedAt}", 12.5f, GREEN, true))
        }
        if (r.status.equals("Present", true)) {
            card.addView(label("উপস্থিত করেছেন: ${r.completedBy.ifEmpty { "Operator" }}", 12.5f,
                Color.rgb(180, 135, 10), true))
        }

        if (allowActions) {
            val isOperator = currentRole.equals("Operator", true) || currentRole.equals("Admin", true)
            val isOwner = currentRole.equals("User", true) && r.createdBy.equals(currentUsername, true)

            if (isOperator || isOwner) {
                card.addView(space(5))
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

                if (isOperator) {
                    row.addView(smallButton("● ${statusBangla(r.status)}", statusColor(r.status)) {
                        showStatusChooser(r)
                    })
                    row.addView(smallButton("✎ এডিট", BLUE) { showEditSerial(r) })
                    row.addView(smallButton("🗑 ডিলিট", RED) { confirmDelete(r) })
                } else if (isOwner) {
                    // User can edit/delete only their own serial. They cannot change status.
                    row.addView(smallButton("✎ এডিট", BLUE) { showEditSerialAsOwner(r) })
                    row.addView(smallButton("🗑 ডিলিট", RED) { confirmDeleteAsOwner(r) })
                }
                card.addView(row)
            }
        }
        return card
    }

    private fun showStatusChooser(r: SerialRecord) {
        if (!canModifySerials()) {
            toast("শুধুমাত্র Operator / Admin status পরিবর্তন করতে পারবেন")
            return
        }
        val statuses = arrayOf("Waiting", "Present", "Completed")
        val labels = arrayOf("অপেক্ষমাণ", "উপস্থিত", "সম্পন্ন")
        AlertDialog.Builder(this)
            .setTitle("সিরিয়াল #${r.number} — Status")
            .setItems(labels) { _, which -> updateStatus(r, statuses[which]) }
            .show()
    }

    private fun showEditSerialAsOwner(r: SerialRecord) {
        if (!currentRole.equals("User", true) || !r.createdBy.equals(currentUsername, true)) {
            toast("শুধু নিজের সিরিয়াল এডিট করতে পারবেন")
            return
        }
        showEditSerial(r, ownerOnly = true)
    }

    private fun confirmDeleteAsOwner(r: SerialRecord) {
        if (!currentRole.equals("User", true) || !r.createdBy.equals(currentUsername, true)) {
            toast("শুধু নিজের সিরিয়াল ডিলিট করতে পারবেন")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("সিরিয়াল Delete")
            .setMessage("সিরিয়াল #${r.number} মুছে ফেলবেন?")
            .setNegativeButton("না", null)
            .setPositiveButton("হ্যাঁ") { _, _ -> deleteSerial(r, ownerOnly = true) }
            .show()
    }

    private fun updateStatus(r: SerialRecord, newStatus: String) {
        if (!canModifySerials()) {
            toast("শুধুমাত্র Operator / Admin পরিবর্তন করতে পারবেন")
            return
        }
        if (!firebaseAvailable) {
            updateLocalStatus(r, newStatus)
            return
        }
        val updates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "completedBy" to currentUsername,
            "completedAt" to currentTime24()
        )
        if (newStatus == "Waiting") {
            updates["completedBy"] = ""
            updates["completedAt"] = ""
        }
        rootRef.child("serials").child(r.dateKey).child(r.id).updateChildren(updates)
            .addOnSuccessListener { toast("সিরিয়াল আপডেট হয়েছে") }
            .addOnFailureListener { toast("আপডেট করা যায়নি") }
    }

    private fun showEditSerial(r: SerialRecord, ownerOnly: Boolean = false) {
        if (ownerOnly) {
            if (!currentRole.equals("User", true) || !r.createdBy.equals(currentUsername, true)) {
                toast("শুধু নিজের সিরিয়াল এডিট করতে পারবেন")
                return
            }
        } else if (!canModifySerials()) {
            toast("শুধুমাত্র Operator / Admin এডিট করতে পারবেন")
            return
        }
        val root = verticalContainer()
        root.addView(label("✎ সিরিয়াল এডিট", 24f, DARK_BLUE, true))
        root.addView(label("সিরিয়াল #${r.number}", 16f, TEAL, true))
        val patient = input("রোগীর নাম")
        patient.setText(r.patient)
        root.addView(patient)
        val care = input("Care Of")
        care.setText(r.careOf)
        root.addView(care)
        val doctor = input("ডাক্তার")
        doctor.setText(r.doctor)
        root.addView(doctor)
        root.addView(actionButton("💾   পরিবর্তন সংরক্ষণ", GREEN, 56) {
            if (patient.text.toString().trim().isEmpty() || doctor.text.toString().trim().isEmpty()) {
                toast("রোগীর নাম ও ডাক্তার আবশ্যক")
                return@actionButton
            }
            val newPatient = patient.text.toString().trim()
            val newCare = care.text.toString().trim()
            val newDoctor = doctor.text.toString().trim()
            if (!firebaseAvailable) {
                val raw = listOf(newPatient, newCare, newDoctor, r.status, r.createdBy, r.createdRole, r.createdAt, r.completedBy, r.completedAt).joinToString("||")
                pref.edit().putString(r.id, raw).apply()
                loadLocalData()
                toast("সিরিয়াল এডিট হয়েছে")
                showTotalSerial()
                return@actionButton
            }
            val updates = mapOf("patient" to newPatient, "careOf" to newCare, "doctor" to newDoctor)
            rootRef.child("serials").child(r.dateKey).child(r.id).updateChildren(updates)
                .addOnSuccessListener { toast("সিরিয়াল এডিট হয়েছে"); showTotalSerial() }
                .addOnFailureListener { toast("এডিট করা যায়নি") }
        })
        setContentView(pullToRefresh(root) { })
    }

    private fun updateLocalStatus(r: SerialRecord, newStatus: String) {
        val parts = listOf(r.patient, r.careOf, r.doctor, newStatus, r.createdBy, r.createdRole, r.createdAt, if (newStatus == "Completed") currentUsername else r.completedBy, if (newStatus == "Completed") currentTime24() else r.completedAt)
        pref.edit().putString(r.id, parts.joinToString("||")).apply()
        loadLocalData()
        toast("সিরিয়াল আপডেট হয়েছে")
    }

    private fun confirmDelete(r: SerialRecord) {
        if (!canModifySerials()) {
            toast("শুধুমাত্র Operator / Admin ডিলিট করতে পারবেন")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("সিরিয়াল মুছে ফেলবেন?")
            .setMessage("সিরিয়াল #${r.number} স্থায়ীভাবে মুছে যাবে।")
            .setNegativeButton("না", null)
            .setPositiveButton("হ্যাঁ, মুছুন") { _, _ -> deleteSerial(r, ownerOnly = false) }
            .show()
    }

    private fun deleteSerial(r: SerialRecord, ownerOnly: Boolean) {
        if (ownerOnly) {
            if (!currentRole.equals("User", true) || !r.createdBy.equals(currentUsername, true)) {
                toast("শুধু নিজের সিরিয়াল ডিলিট করতে পারবেন")
                return
            }
        } else if (!canModifySerials()) {
            toast("শুধুমাত্র Operator / Admin ডিলিট করতে পারবেন")
            return
        }

        if (!firebaseAvailable) {
            pref.edit().remove(r.id).apply()
            loadLocalData()
            toast("সিরিয়াল মুছে ফেলা হয়েছে")
            return
        }
        rootRef.child("serials").child(r.dateKey).child(r.id).removeValue()
            .addOnSuccessListener { toast("সিরিয়াল মুছে ফেলা হয়েছে") }
            .addOnFailureListener { toast("ডিলিট করা যায়নি") }
    }

    // =========================================================
    // REPORTS / PDF
    // =========================================================
    private fun showReport() {
        currentScreen = Screen.REPORT
        val root = verticalContainer()
        root.setPadding(12, 16, 12, 20)

        root.addView(label("📄 রিপোর্ট / PDF", 25f, DARK_BLUE, true))
        root.addView(label("একদিনের রিপোর্ট অথবা নির্বাচিত মাসের ১–৩১ দিনের পূর্ণ রিপোর্ট তৈরি করুন", 13f, GRAY))
        root.addView(space(10))

        val dateCal = Calendar.getInstance()
        var selectedDateKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(dateCal.time)
        var selectedDateText = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(dateCal.time)

        val dateCard = cardLayout()
        dateCard.addView(label("একদিনের রিপোর্ট", 19f, DARK_BLUE, true))
        dateCard.addView(space(5))
        val dateButton = actionButton("📅   তারিখ: $selectedDateText", BLUE, 54) {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    dateCal.set(y, m, d)
                    selectedDateKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(dateCal.time)
                    selectedDateText = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(dateCal.time)
                    dateButton.text = "📅   তারিখ: $selectedDateText"
                },
                dateCal.get(Calendar.YEAR),
                dateCal.get(Calendar.MONTH),
                dateCal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        dateCard.addView(dateButton)
        dateCard.addView(actionButton("⬇   একদিনের PDF ডাউনলোড", GREEN, 56) {
            fetchReportDays(listOf(selectedDateKey)) { days ->
                createAndDownloadPdf("MDC_Report_$selectedDateKey.pdf", "মুন ডায়াগনস্টিক সেন্টার — $selectedDateText", days)
            }
        })
        root.addView(dateCard)
        root.addView(space(10))

        val monthCal = Calendar.getInstance()
        var monthYear = monthCal.get(Calendar.YEAR)
        var monthIndex = monthCal.get(Calendar.MONTH)
        var monthText = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(monthCal.time)

        val monthCard = cardLayout()
        monthCard.addView(label("পুরো মাসের রিপোর্ট", 19f, DARK_BLUE, true))
        monthCard.addView(label("নির্বাচিত মাসের ১ তারিখ থেকে মাসের শেষ দিন পর্যন্ত সব দিনের রিপোর্ট এক PDF-এ থাকবে", 12.5f, GRAY))
        monthCard.addView(space(5))
        val monthButton = actionButton("🗓   মাস: $monthText", TEAL, 54) {
            DatePickerDialog(
                this,
                { y, m, d ->
                    monthYear = y
                    monthIndex = m
                    monthCal.set(y, m, 1)
                    monthText = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(monthCal.time)
                    monthButton.text = "🗓   মাস: $monthText"
                },
                monthYear, monthIndex, 1
            ).show()
        }
        monthCard.addView(monthButton)
        monthCard.addView(actionButton("⬇   পুরো মাসের PDF ডাউনলোড", PURPLE, 58) {
            val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val keys = (1..daysInMonth).map { day ->
                String.format(Locale.US, "%04d%02d%02d", monthYear, monthIndex + 1, day)
            }
            fetchReportDays(keys) { days ->
                val fileName = "MDC_Report_${String.format(Locale.US, "%04d-%02d", monthYear, monthIndex + 1)}.pdf"
                createAndDownloadPdf("$fileName", "মুন ডায়াগনস্টিক সেন্টার — $monthText", days)
            }
        })
        root.addView(monthCard)
        root.addView(space(10))
        root.addView(label("নোট: PDF ফোনের Downloads ফোল্ডারে সংরক্ষণ হবে।", 12.5f, GRAY))

        setContentView(pullToRefresh(root) { refreshCurrentScreen() })
    }

    private data class ReportDay(val dateKey: String, val records: List<SerialRecord>)

    private fun fetchReportDays(dateKeys: List<String>, onDone: (List<ReportDay>) -> Unit) {
        if (dateKeys.isEmpty()) {
            onDone(emptyList())
            return
        }
        if (!firebaseAvailable) {
            val result = dateKeys.map { key -> ReportDay(key, readLocalReportRecords(key)) }
            onDone(result)
            return
        }

        val result = arrayOfNulls<ReportDay>(dateKeys.size)
        var remaining = dateKeys.size
        dateKeys.forEachIndexed { index, key ->
            rootRef.child("serials").child(key).get()
                .addOnSuccessListener { snapshot ->
                    val records = snapshot.children.mapNotNull { snapshotToSerialRecord(it, key) }
                        .sortedBy { it.number }
                    result[index] = ReportDay(key, records)
                    remaining--
                    if (remaining == 0) onDone(result.filterNotNull())
                }
                .addOnFailureListener {
                    toast("$key এর রিপোর্ট পড়া যায়নি")
                    result[index] = ReportDay(key, emptyList())
                    remaining--
                    if (remaining == 0) onDone(result.filterNotNull())
                }
        }
    }

    private fun readLocalReportRecords(dateKey: String): List<SerialRecord> {
        val result = mutableListOf<SerialRecord>()
        for (key in pref.all.keys) {
            if (!key.startsWith(LOCAL_SERIAL_PREFIX + dateKey + "_")) continue
            val number = key.substringAfterLast("_").toIntOrNull() ?: continue
            val parts = (pref.getString(key, "") ?: "").split("||")
            if (parts.size >= 7) {
                result.add(
                    SerialRecord(
                        key, number, dateKey, parts[0], parts[1], parts[2], parts[3],
                        parts[4], parts[5], parts[6],
                        parts.getOrElse(7) { "" }, parts.getOrElse(8) { "" }
                    )
                )
            }
        }
        return result.sortedBy { it.number }
    }

    private fun createAndDownloadPdf(fileName: String, title: String, days: List<ReportDay>) {
        try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val left = 32f
            val right = 563f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
            val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            var pageNumber = 0
            var page: PdfDocument.Page? = null
            var canvas: android.graphics.Canvas? = null
            var y = 0f

            fun startPage() {
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page!!.canvas
                canvas!!.drawColor(Color.WHITE)
                y = 42f
            }
            fun finishPage() {
                page?.let { document.finishPage(it) }
                page = null
                canvas = null
            }
            fun ensureSpace(height: Float) {
                if (page == null) startPage()
                if (y + height > 790f) {
                    finishPage()
                    startPage()
                }
            }
            fun drawText(text: String, size: Float, isBold: Boolean = false, color: Int = Color.BLACK, gap: Float = 5f) {
                ensureSpace(size + gap + 2f)
                val p = if (isBold) bold else paint
                p.textSize = size
                p.color = color
                canvas!!.drawText(text, left, y, p)
                y += size + gap
            }

            startPage()
            drawText(title, 18f, true, DARK_BLUE, 8f)
            drawText("প্রস্তুতকারক: $currentUsername • $currentRole", 10f, false, GRAY, 12f)

            if (days.isEmpty()) {
                drawText("কোনো রিপোর্ট ডাটা পাওয়া যায়নি।", 13f, false, GRAY)
            }

            days.forEach { day ->
                val displayDate = formatReportDate(day.dateKey)
                ensureSpace(45f)
                drawText("তারিখ: $displayDate", 14f, true, TEAL, 5f)
                val total = day.records.size
                val waiting = day.records.count { it.status.equals("Waiting", true) }
                val present = day.records.count { it.status.equals("Present", true) }
                val completed = day.records.count { it.status.equals("Completed", true) }
                drawText("মোট: $total • অপেক্ষমাণ: $waiting • উপস্থিত: $present • সম্পন্ন: $completed", 9.5f, false, DARK, 7f)

                if (day.records.isEmpty()) {
                    drawText("কোনো সিরিয়াল নেই", 10f, false, GRAY, 7f)
                } else {
                    day.records.sortedBy { it.number }.forEach { r ->
                        ensureSpace(48f)
                        val line = "#${r.number}  ${r.patient}  |  ${r.doctor}"
                        drawText(line, 9.5f, true, DARK, 3f)
                        drawText("Care Of: ${if (r.careOf.isEmpty()) "—" else r.careOf}  |  ${statusBangla(r.status)}  |  ${r.createdBy}", 8.5f, false, GRAY, 3f)
                    }
                }
                y += 6f
            }

            if (page != null) finishPage()
            val bytesFile = File(cacheDir, fileName)
            FileOutputStream(bytesFile).use { document.writeTo(it) }
            document.close()
            savePdfToDownloads(bytesFile, fileName)
        } catch (e: Exception) {
            toast("PDF তৈরি করা যায়নি: ${e.message ?: "অজানা সমস্যা"}")
        }
    }

    private fun formatReportDate(dateKey: String): String = try {
        val d = SimpleDateFormat("yyyyMMdd", Locale.US).parse(dateKey)
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(d ?: Date())
    } catch (_: Exception) { dateKey }

    private fun savePdfToDownloads(file: java.io.File, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Moon Diagnostic Center")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    toast("PDF Downloads-এ সংরক্ষণ করা যায়নি")
                    return
                }
                contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                toast("PDF Downloads/Moon Diagnostic Center-এ সংরক্ষণ হয়েছে")
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
                val target = File(dir, fileName)
                file.copyTo(target, overwrite = true)
                toast("PDF Downloads ফোল্ডারে সংরক্ষণ হয়েছে")
            }
        } catch (e: Exception) {
            toast("PDF সংরক্ষণ করা যায়নি: ${e.message ?: "অজানা সমস্যা"}")
        }
    }

    // =========================================================
    // ADMIN PANEL / USERS
    // =========================================================
    private fun showAdminPanel() {
        currentScreen = Screen.ADMIN
        if (!currentRole.equals("Admin", true)) {
            toast("শুধুমাত্র Admin এই পেজ ব্যবহার করতে পারবেন")
            return
        }

        val root = verticalContainer()
        root.addView(label("👑 Admin Control Panel", 24f, DARK_BLUE, true))
        root.addView(label("User এবং Operator পরিচালনা করুন", 13f, GRAY))
        root.addView(space(10))

        val username = input("নতুন Username")
        val password = input("নতুন Password", true)
        val roleSpinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Operator", "User"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = adapter

        root.addView(username)
        root.addView(password)
        root.addView(roleSpinner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 54
        ).apply { setMargins(8, 5, 8, 5) })

        root.addView(actionButton("＋   নতুন User / Operator তৈরি করুন", TEAL, 56) {
            createUser(
                username.text.toString().trim(),
                password.text.toString(),
                roleSpinner.selectedItem.toString()
            )
        })

        root.addView(space(14))
        root.addView(label("বর্তমান User / Operator", 20f, DARK_BLUE, true))
        root.addView(label("Inactive করলেও User এখানেই থাকবে; পরে Active করা যাবে।", 12.5f, GRAY))
        loadUsers(root)

        setContentView(pullToRefresh(root) { showAdminPanel() })
    }

    private fun createUser(username: String, password: String, role: String) {
        if (username.isEmpty() || password.length < 4) {
            toast("Username দিন এবং Password কমপক্ষে ৪ অক্ষরের দিন")
            return
        }
        if (username.equals("admin", true)) {
            toast("admin নামটি সংরক্ষিত")
            return
        }

        if (firebaseAvailable) {
            rootRef.child("users").child(username).get().addOnSuccessListener { snap ->
                if (snap.exists()) {
                    toast("এই Username আগে থেকেই আছে")
                    return@addOnSuccessListener
                }
                val data = mapOf(
                    "username" to username,
                    "passwordHash" to hashPassword(password),
                    "role" to role,
                    "active" to true
                )
                rootRef.child("users").child(username).setValue(data)
                    .addOnSuccessListener {
                        toast("$role সফলভাবে তৈরি হয়েছে")
                        showAdminPanel()
                    }
                    .addOnFailureListener { toast("User তৈরি করা যায়নি") }
            }
        } else {
            if (pref.contains("user_$username")) {
                toast("এই Username আগে থেকেই আছে")
                return
            }
            pref.edit()
                .putString("user_$username", username)
                .putString("pass_$username", hashPassword(password))
                .putString("role_$username", role)
                .putBoolean("active_$username", true)
                .apply()
            toast("$role সফলভাবে তৈরি হয়েছে")
            showAdminPanel()
        }
    }

    private fun loadUsers(root: LinearLayout) {
        if (firebaseAvailable) {
            rootRef.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val username = child.key ?: continue
                        if (username.equals("admin", true)) continue

                        val role = child.child("role").getValue(String::class.java) ?: "User"
                        val active = child.child("active").getValue(Boolean::class.java) ?: false

                        val card = cardLayout()
                        card.addView(label(
                            "$username\nRole: $role\n${if (active) "Active" else "Inactive"}",
                            15f, DARK, true
                        ))

                        val row1 = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }
                        row1.addView(smallButton("✎ Edit", BLUE) {
                            showEditUserDialog(username, role, active)
                        })
                        row1.addView(smallButton("🔑 Password", PURPLE) {
                            showResetPasswordDialog(username)
                        })
                        card.addView(row1)

                        val row2 = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }
                        row2.addView(smallButton(
                            if (active) "⛔ Deactivate" else "✅ Activate",
                            if (active) RED else GREEN
                        ) {
                            setUserActive(username, !active)
                        })
                        row2.addView(smallButton("🗑 Delete", RED) {
                            confirmDeleteUser(username)
                        })
                        card.addView(row2)
                        root.addView(card)
                    }

                    if (!snapshot.children.any { !it.key.equals("admin", true) }) {
                        root.addView(label("এখনও কোনো User / Operator তৈরি করা হয়নি", 14f, GRAY))
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    toast("User list পড়া যায়নি")
                }
            })
            return
        }

        var count = 0
        for (key in pref.all.keys.sorted()) {
            if (!key.startsWith("user_")) continue
            val username = pref.getString(key, "") ?: ""
            if (username.isEmpty() || username.equals("admin", true)) continue

            val role = pref.getString("role_$username", "User") ?: "User"
            val active = pref.getBoolean("active_$username", true)

            val card = cardLayout()
            card.addView(label(
                "$username\nRole: $role\n${if (active) "Active" else "Inactive"}",
                15f, DARK, true
            ))
            val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(smallButton("✎ Edit", BLUE) {
                showEditUserDialog(username, role, active)
            })
            row1.addView(smallButton("🔑 Password", PURPLE) {
                showResetPasswordDialog(username)
            })
            card.addView(row1)

            val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row2.addView(smallButton(
                if (active) "⛔ Deactivate" else "✅ Activate",
                if (active) RED else GREEN
            ) { setUserActive(username, !active) })
            row2.addView(smallButton("🗑 Delete", RED) { confirmDeleteUser(username) })
            card.addView(row2)
            root.addView(card)
            count++
        }
        if (count == 0) root.addView(label("এখনও কোনো User / Operator তৈরি করা হয়নি", 14f, GRAY))
    }

    private fun showEditUserDialog(username: String, currentRole: String, active: Boolean) {
        val box = verticalContainer()
        val newUsername = input("Username")
        newUsername.setText(username)
        val roleSpinner = Spinner(this)
        val roles = arrayOf("Operator", "User")
        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        roleSpinner.setSelection(roles.indexOfFirst { it.equals(currentRole, true) }.coerceAtLeast(0))
        box.addView(newUsername)
        box.addView(roleSpinner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 54
        ).apply { setMargins(6, 5, 6, 5) })

        AlertDialog.Builder(this)
            .setTitle("User Edit")
            .setView(box)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("সংরক্ষণ") { _, _ ->
                val target = newUsername.text.toString().trim()
                if (target.isEmpty()) {
                    toast("Username খালি রাখা যাবে না")
                    return@setPositiveButton
                }
                if (target.equals("admin", true)) {
                    toast("admin নামটি ব্যবহার করা যাবে না")
                    return@setPositiveButton
                }
                updateUserProfile(username, target, roleSpinner.selectedItem.toString(), active)
            }
            .show()
    }

    private fun updateUserProfile(oldUsername: String, newUsername: String, newRole: String, active: Boolean) {
        if (!firebaseAvailable) {
            if (!oldUsername.equals(newUsername, true) && pref.contains("user_$newUsername")) {
                toast("নতুন Username আগে থেকেই আছে")
                return
            }
            val oldPass = pref.getString("pass_$oldUsername", "") ?: ""
            pref.edit()
                .remove("user_$oldUsername").remove("pass_$oldUsername")
                .remove("role_$oldUsername").remove("active_$oldUsername")
                .putString("user_$newUsername", newUsername)
                .putString("pass_$newUsername", oldPass)
                .putString("role_$newUsername", newRole)
                .putBoolean("active_$newUsername", active)
                .apply()
            toast("User তথ্য আপডেট হয়েছে")
            showAdminPanel()
            return
        }

        if (oldUsername.equals(newUsername, true)) {
            rootRef.child("users").child(oldUsername).updateChildren(
                mapOf("username" to newUsername, "role" to newRole, "active" to active)
            ).addOnSuccessListener {
                toast("User তথ্য আপডেট হয়েছে")
                showAdminPanel()
            }.addOnFailureListener { toast("User আপডেট করা যায়নি") }
            return
        }

        rootRef.child("users").child(newUsername).get().addOnSuccessListener { existing ->
            if (existing.exists()) {
                toast("নতুন Username আগে থেকেই আছে")
                return@addOnSuccessListener
            }
            rootRef.child("users").child(oldUsername).get().addOnSuccessListener { oldSnap ->
                val data = mapOf(
                    "username" to newUsername,
                    "passwordHash" to (oldSnap.child("passwordHash").getValue(String::class.java) ?: ""),
                    "role" to newRole,
                    "active" to active
                )
                rootRef.child("users").child(newUsername).setValue(data)
                    .addOnSuccessListener {
                        rootRef.child("users").child(oldUsername).removeValue()
                            .addOnSuccessListener {
                                toast("User তথ্য আপডেট হয়েছে")
                                showAdminPanel()
                            }
                            .addOnFailureListener { toast("পুরনো User record সরানো যায়নি") }
                    }
                    .addOnFailureListener { toast("নতুন User record তৈরি করা যায়নি") }
            }
        }
    }

    private fun showResetPasswordDialog(username: String) {
        val field = input("নতুন Password", true)
        AlertDialog.Builder(this)
            .setTitle("$username-এর Password Reset")
            .setView(field)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("Reset") { _, _ ->
                val password = field.text.toString()
                if (password.length < 4) {
                    toast("Password কমপক্ষে ৪ অক্ষরের হতে হবে")
                    return@setPositiveButton
                }
                resetUserPassword(username, password)
            }.show()
    }

    private fun resetUserPassword(username: String, password: String) {
        val hash = hashPassword(password)
        if (firebaseAvailable) {
            rootRef.child("users").child(username).child("passwordHash").setValue(hash)
                .addOnSuccessListener { toast("$username-এর Password reset হয়েছে") }
                .addOnFailureListener { toast("Password reset করা যায়নি") }
        } else {
            pref.edit().putString("pass_$username", hash).apply()
            toast("$username-এর Password reset হয়েছে")
        }
    }

    private fun setUserActive(username: String, active: Boolean) {
        if (username.equals("admin", true)) {
            toast("Admin account-এর status এখান থেকে পরিবর্তন করা যাবে না")
            return
        }
        if (firebaseAvailable) {
            rootRef.child("users").child(username).child("active").setValue(active)
                .addOnSuccessListener {
                    toast(if (active) "$username Active হয়েছে" else "$username Deactivate হয়েছে")
                    showAdminPanel()
                }
                .addOnFailureListener { toast("Status পরিবর্তন করা যায়নি") }
        } else {
            pref.edit().putBoolean("active_$username", active).apply()
            toast(if (active) "$username Active হয়েছে" else "$username Deactivate হয়েছে")
            showAdminPanel()
        }
    }

    private fun confirmDeleteUser(username: String) {
        AlertDialog.Builder(this)
            .setTitle("User Delete")
            .setMessage("$username-কে স্থায়ীভাবে Delete করবেন?")
            .setNegativeButton("না", null)
            .setPositiveButton("হ্যাঁ, Delete") { _, _ -> deleteUser(username) }
            .show()
    }

    private fun deleteUser(username: String) {
        if (username.equals("admin", true)) {
            toast("Admin account মুছা যাবে না")
            return
        }
        if (firebaseAvailable) {
            rootRef.child("users").child(username).removeValue()
                .addOnSuccessListener { toast("$username মুছে ফেলা হয়েছে"); showAdminPanel() }
                .addOnFailureListener { toast("User মুছা যায়নি") }
        } else {
            pref.edit()
                .remove("user_$username")
                .remove("pass_$username")
                .remove("role_$username")
                .remove("active_$username")
                .apply()
            toast("$username মুছে ফেলা হয়েছে")
            showAdminPanel()
        }
    }

    // =========================================================
    // REAL-TIME LISTENERS
    // =========================================================
    private fun seedFirebaseAdminIfNeeded() {
        if (!firebaseAvailable) return
        rootRef.child("users").child("admin").get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                val admin = mapOf("username" to "admin", "passwordHash" to hashPassword("admin123"), "role" to "Admin", "active" to true)
                rootRef.child("users").child("admin").setValue(admin)
            }
        }
    }

    private fun initializeFirebaseIfAvailable() {
        try {
            db = FirebaseDatabase.getInstance()
            rootRef = db.reference
            firebaseAvailable = true
        } catch (_: Exception) {
            firebaseAvailable = false
            loadLocalData()
        }
    }

    private fun startRealtimeListeners() {
        if (currentUsername.isEmpty()) return
        stopRealtimeListeners()

        if (!firebaseAvailable) {
            loadLocalData()
            return
        }

        val dateKey = todayKey()
        serialListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serials.clear()
                for (child in snapshot.children) {
                    val r = snapshotToSerialRecord(child, dateKey)
                    if (r != null && !r.status.equals("Cancelled", true)) serials.add(r)
                }
                serials.sortBy { it.number }
                when (currentScreen) {
                    Screen.DASHBOARD, Screen.TOTAL, Screen.DOCTOR_SERIALS, Screen.CARE_SERIALS -> refreshCurrentScreen()
                    else -> Unit
                }
            }
            override fun onCancelled(error: DatabaseError) { toast("Serial real-time data পড়া যায়নি") }
        }
        rootRef.child("serials").child(dateKey).addValueEventListener(serialListener!!)

        doctorListener = namedListListener(doctors)
        careListener = namedListListener(careOfs)
        rootRef.child("doctors").addValueEventListener(doctorListener!!)
        rootRef.child("careOfs").addValueEventListener(careListener!!)
    }

    private fun namedListListener(target: MutableList<String>): ValueEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            target.clear()
            for (child in snapshot.children) {
                val value = child.getValue(String::class.java) ?: child.key ?: continue
                if (value.isNotBlank()) target.add(value)
            }
            if (currentScreen == Screen.DOCTOR_SERIALS || currentScreen == Screen.CARE_SERIALS) refreshCurrentScreen()
        }
        override fun onCancelled(error: DatabaseError) { }
    }

    private fun stopRealtimeListeners() {
        if (!firebaseAvailable) return
        serialListener?.let { rootRef.child("serials").child(todayKey()).removeEventListener(it) }
        doctorListener?.let { rootRef.child("doctors").removeEventListener(it) }
        careListener?.let { rootRef.child("careOfs").removeEventListener(it) }
        serialListener = null
        doctorListener = null
        careListener = null
    }

    private fun loadLocalData() {
        serials.clear()
        val dateKey = todayKey()
        for (key in pref.all.keys) {
            if (!key.startsWith(LOCAL_SERIAL_PREFIX + dateKey + "_")) continue
            val number = key.substringAfterLast("_").toIntOrNull() ?: continue
            val parts = (pref.getString(key, "") ?: "").split("||")
            if (parts.size >= 7) {
                if (!parts[3].equals("Cancelled", true)) {
                    serials.add(SerialRecord(key, number, dateKey, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6]))
                }
            }
        }
        serials.sortBy { it.number }
        doctors.clear()
        careOfs.clear()
        for (key in pref.all.keys) {
            if (key.startsWith("doctor_")) pref.getString(key, null)?.let { doctors.add(it) }
            if (key.startsWith("care_")) pref.getString(key, null)?.let { careOfs.add(it) }
        }
    }

    // =========================================================
    // PULL TO REFRESH
    // =========================================================
    private fun pullToRefresh(content: View, onRefresh: () -> Unit): SwipeRefreshLayout {
        val swipe = SwipeRefreshLayout(this)
        swipe.setColorSchemeColors(BLUE, TEAL, GREEN)
        swipe.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        swipe.setOnRefreshListener {
            onRefresh()
            swipe.postDelayed({ swipe.isRefreshing = false }, 450)
        }
        return swipe
    }

    private fun refreshCurrentScreen() {
        if (currentUsername.isEmpty()) return
        if (serialListener == null) startRealtimeListeners()
        when (currentScreen) {
            Screen.DASHBOARD -> showDashboard()
            Screen.TOTAL -> showTotalSerial()
            Screen.DOCTOR_SERIALS -> {
                if (selectedDoctor.isNotEmpty()) {
                    showFilteredSerials("ডাক্তার: $selectedDoctor", serials.filter { it.doctor == selectedDoctor })
                } else {
                    showDoctorWise()
                }
            }
            Screen.CARE_SERIALS -> {
                if (selectedCareOf.isNotEmpty()) {
                    showFilteredSerials("কেয়ার অফ: $selectedCareOf", serials.filter { it.careOf == selectedCareOf })
                } else {
                    showCareWise()
                }
            }
            Screen.ADD_SERIAL -> showAddSerial()
            Screen.ADD_DOCTOR -> showAddDoctor()
            Screen.ADD_CARE -> showAddCare()
            Screen.REPORT -> showReport()
            Screen.ADMIN -> showAdminPanel()
            Screen.LOGIN -> showLogin()
        }
    }

    // =========================================================
    // UI HELPERS
    // =========================================================
    private fun label(text: String, size: Float, color: Int = DARK, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            includeFontPadding = true
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(4, 3, 4, 3)
        }
    }

    private fun verticalContainer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(14, 14, 14, 18)
        setBackgroundColor(BG)
    }

    private fun cardLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(12, 14, 12, 14)
        background = background(WHITE, 17f, LIGHT_BORDER)
        elevation = 3f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(4, 4, 4, 4)
        }
    }

    private fun background(color: Int, radius: Float = 17f, strokeColor: Int? = null) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeColor != null) setStroke(2, strokeColor)
    }

    private fun input(hint: String, password: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 17f
        setTextColor(DARK)
        setHintTextColor(Color.rgb(125, 130, 135))
        setPadding(14, 0, 14, 0)
        background = this@MainActivity.background(WHITE, 13f, TEAL)
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56).apply { setMargins(6, 5, 6, 5) }
    }

    private fun careOfField(): Pair<EditText, LinearLayout> {
        val edit = input("Care Of / অভিভাবকের নাম")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        edit.layoutParams = LinearLayout.LayoutParams(0, 56, 1f).apply { setMargins(6, 5, 2, 5) }
        row.addView(edit)

        row.addView(smallButton("▼", TEAL) {
            if (careOfs.isEmpty()) {
                toast("আগে Care Of যোগ করুন")
            } else {
                val items = careOfs.distinct().sorted()
                AlertDialog.Builder(this)
                    .setTitle("Care Of নির্বাচন করুন")
                    .setItems(items.toTypedArray()) { _, which ->
                        edit.setText(items[which])
                        edit.setSelection(edit.text.length)
                    }.show()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(52, 46).apply { setMargins(2, 5, 2, 5) }
        })

        row.addView(smallButton("＋", TEAL) {
            showAddCareDialog(edit)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(52, 46).apply { setMargins(2, 5, 6, 5) }
        })
        return Pair(edit, row)
    }

    private fun showAddCareDialog(target: EditText? = null) {
        val field = input("নতুন Care Of নাম")
        AlertDialog.Builder(this)
            .setTitle("Care Of যোগ করুন")
            .setView(field)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("যোগ করুন") { _, _ ->
                val value = field.text.toString().trim()
                addNamedItem("careOfs", value) {
                    target?.setText(value)
                    target?.setSelection(target.text.length)
                }
            }.show()
    }

    private fun selectionInput(hint: String): TextView {
        val t = label(hint, 16f, GRAY)
        t.background = background(WHITE, 13f, TEAL)
        t.setPadding(12, 0, 12, 0)
        t.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56).apply { setMargins(6, 5, 6, 5) }
        t.setOnClickListener {
            val source = if (hint.startsWith("Care")) careOfs else doctors
            if (source.isEmpty()) {
                toast(if (hint.startsWith("Care")) "আগে Care Of যোগ করুন" else "আগে ডাক্তার যোগ করুন")
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle(if (hint.startsWith("Care")) "Care Of নির্বাচন করুন" else "ডাক্তার নির্বাচন করুন")
                .setItems(source.sorted().toTypedArray()) { _, which ->
                    t.text = source.sorted()[which]
                    t.setTextColor(DARK)
                    t.tag = source.sorted()[which]
                }.show()
        }
        return t
    }

    private fun actionButton(text: String, color: Int = BLUE, height: Int = 56, onClick: () -> Unit): TextView {
        return label(text, 15.5f, WHITE, true).apply {
            background = this@MainActivity.background(color, 13f)
            setPadding(10, 0, 10, 0)
            elevation = 2f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply { setMargins(6, 4, 6, 4) }
            setOnClickListener { onClick() }
        }
    }

    private fun smallButton(text: String, color: Int, onClick: () -> Unit): TextView {
        return label(text, 12.5f, WHITE, true).apply {
            background = this@MainActivity.background(color, 10f)
            layoutParams = LinearLayout.LayoutParams(0, 42, 1f).apply { setMargins(3, 2, 3, 2) }
            setOnClickListener { onClick() }
        }
    }

    private fun tabButton(text: String, color: Int, onClick: () -> Unit): TextView {
        return label(text, 14f, WHITE, true).apply {
            background = this@MainActivity.background(color, 13f)
            layoutParams = LinearLayout.LayoutParams(0, 68, 1f).apply { setMargins(4, 3, 4, 3) }
            setOnClickListener { onClick() }
        }
    }

    private fun statCard(icon: String, title: String, value: String, color: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6, 9, 6, 9)
            background = this@MainActivity.background(WHITE, 15f, LIGHT_BORDER)
            elevation = 2f
            layoutParams = LinearLayout.LayoutParams(0, 108, 1f).apply { setMargins(3, 3, 3, 3) }
            addView(label(icon, 27f, color, true))
            addView(label(title, 14.5f, DARK, true))
            addView(label(value, 16f, color, true))
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, height) }

    // =========================================================
    // UTILITY
    // =========================================================
    private fun canModifySerials(): Boolean = currentRole.equals("Operator", true) || currentRole.equals("Admin", true)

    private fun statusBangla(status: String): String = when (status) {
        "Waiting" -> "অপেক্ষমাণ"
        "Present" -> "উপস্থিত"
        "Completed" -> "সম্পন্ন"
        "Cancelled" -> "বাতিল"
        else -> status
    }

    private fun statusColor(status: String): Int = when (status) {
        "Waiting" -> RED
        "Present" -> Color.rgb(218, 170, 20)
        "Completed" -> GREEN
        "Cancelled" -> RED
        else -> BLUE
    }

    private fun todayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun currentTime24(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun hashPassword(password: String): String = try {
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { password }

    private fun snapshotToSerialRecord(snapshot: DataSnapshot, dateKey: String): SerialRecord? {
        val number = snapshot.child("number").getValue(Int::class.java) ?: return null
        return SerialRecord(
            id = snapshot.key ?: number.toString(),
            number = number,
            dateKey = dateKey,
            patient = snapshot.child("patient").getValue(String::class.java) ?: "",
            careOf = snapshot.child("careOf").getValue(String::class.java) ?: "",
            doctor = snapshot.child("doctor").getValue(String::class.java) ?: "",
            status = snapshot.child("status").getValue(String::class.java) ?: "Waiting",
            createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: "",
            createdRole = snapshot.child("createdRole").getValue(String::class.java) ?: "",
            createdAt = snapshot.child("createdAt").getValue(String::class.java) ?: "",
            completedBy = snapshot.child("completedBy").getValue(String::class.java) ?: "",
            completedAt = snapshot.child("completedAt").getValue(String::class.java) ?: ""
        )
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun logout() {
        stopRealtimeListeners()
        pref.edit().clear().apply()
        currentUsername = ""
        currentRole = ""
        selectedDoctor = ""
        selectedCareOf = ""
        toast("Logout সফল হয়েছে")
        showLogin()
    }

    // =========================================================
    // ANDROID BACK BUTTON
    // No dashboard "ফিরে যান" buttons are needed.
    // =========================================================
    override fun onBackPressed() {
        when (currentScreen) {
            Screen.LOGIN -> super.onBackPressed()
            Screen.DASHBOARD -> super.onBackPressed()
            Screen.TOTAL -> showDashboard()
            Screen.DOCTOR_SERIALS, Screen.CARE_SERIALS -> showTotalSerial()
            Screen.ADD_SERIAL, Screen.ADD_DOCTOR, Screen.ADD_CARE, Screen.REPORT, Screen.ADMIN -> showDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentUsername.isNotEmpty()) startRealtimeListeners()
    }

    override fun onPause() {
        stopRealtimeListeners()
        super.onPause()
    }

    override fun onDestroy() {
        stopRealtimeListeners()
        super.onDestroy()
    }
}
