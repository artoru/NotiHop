package com.artor.notihop

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.artor.notihop.model.SharedNotification
import com.artor.notihop.receiver.ReceiverService
import com.artor.notihop.sender.SenderService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Int.dp get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

class MainActivity : AppCompatActivity() {

    // Main page
    private lateinit var pageMain: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var btnTest: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button

    // Settings page
    private lateinit var pageSettings: ScrollView
    private lateinit var radioMode: RadioGroup
    private lateinit var radioReceiver: RadioButton
    private lateinit var radioSender: RadioButton
    private lateinit var layoutDeviceSelect: LinearLayout
    private lateinit var txtSelectedDevice: TextView
    private lateinit var btnSelectDevice: Button
    private lateinit var switchForwardCalls: SwitchMaterial
    private lateinit var switchAutostart: SwitchMaterial
    private lateinit var switchPersistentNotifications: SwitchMaterial
    private lateinit var btnBatteryOpt: Button

    // Log page
    private lateinit var pageLog: LinearLayout
    private lateinit var debugText: TextView
    private lateinit var debugScroll: ScrollView

    // Filter page
    private lateinit var pageFilter: LinearLayout
    private lateinit var switchAutoEnableNew: SwitchMaterial
    private lateinit var filterAppList: LinearLayout

    private lateinit var bottomNav: BottomNavigationView

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var awaitingNotificationAccess = false

    private val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            @Suppress("DEPRECATION") add(Manifest.permission.BLUETOOTH)
            @Suppress("DEPRECATION") add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val callPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )

    private var pendingAction: (() -> Unit)? = null

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            pendingAction?.invoke()
        } else {
            statusText.text = "Permission denied."
            appendLog("PERMISSION DENIED: ${results.filter { !it.value }.keys}")
        }
        pendingAction = null
    }

    private val requestCallPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            switchForwardCalls.isChecked = true
        } else {
            appendLog("call permissions denied — incoming call notifications disabled")
            Toast.makeText(this, "Permissions required for incoming call notifications", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pageMain         = findViewById(R.id.pageMain)
        statusText       = findViewById(R.id.statusText)
        btnTest          = findViewById(R.id.btnTest)
        btnStartService  = findViewById(R.id.btnStartService)
        btnStopService   = findViewById(R.id.btnStopService)

        pageSettings      = findViewById(R.id.pageSettings)
        radioMode         = findViewById(R.id.radioMode)
        radioReceiver     = findViewById(R.id.radioReceiver)
        radioSender       = findViewById(R.id.radioSender)
        layoutDeviceSelect = findViewById(R.id.layoutDeviceSelect)
        txtSelectedDevice = findViewById(R.id.txtSelectedDevice)
        btnSelectDevice   = findViewById(R.id.btnSelectDevice)
        switchForwardCalls = findViewById(R.id.switchForwardCalls)
        switchAutostart   = findViewById(R.id.switchAutostart)
        switchPersistentNotifications = findViewById(R.id.switchPersistentNotifications)
        btnBatteryOpt     = findViewById(R.id.btnBatteryOpt)

        pageLog     = findViewById(R.id.pageLog)
        debugText   = findViewById(R.id.debugText)
        debugScroll = findViewById(R.id.debugScroll)

        pageFilter        = findViewById(R.id.pageFilter)
        switchAutoEnableNew = findViewById(R.id.switchAutoEnableNew)
        filterAppList     = findViewById(R.id.filterAppList)

        bottomNav = findViewById(R.id.bottomNav)

        setupListeners()
        loadSettings()

        val root = findViewById<LinearLayout>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, 0)
            insets
        }

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        appendLog("SDK=${Build.VERSION.SDK_INT} v$versionName")

        if (Prefs.isAutostartEnabled(this)
            && SenderService.instance == null
            && ReceiverService.instance == null
        ) {
            withPermissions { BootReceiver.autoStart(this) }
        }
    }

    private fun setupListeners() {
        btnStartService.setOnClickListener { withPermissions { startServiceForCurrentRole() } }
        btnTest.setOnClickListener { sendTestNotification() }
        btnStopService.setOnClickListener { stopActiveService() }

        radioMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.radioSender) Prefs.MODE_SENDER else Prefs.MODE_RECEIVER
            Prefs.setMode(this, mode)
            layoutDeviceSelect.visibility = if (mode == Prefs.MODE_SENDER) View.VISIBLE else View.GONE
            if (SenderService.instance != null || ReceiverService.instance != null) {
                stopActiveService()
                withPermissions { startServiceForCurrentRole() }
            }
        }

        switchForwardCalls.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val missing = callPermissions.filter {
                    ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    Prefs.setPhoneCallForwardEnabled(this, true)
                } else {
                    switchForwardCalls.isChecked = false
                    requestCallPermissions.launch(missing.toTypedArray())
                }
            } else {
                Prefs.setPhoneCallForwardEnabled(this, false)
            }
        }

        switchAutostart.setOnCheckedChangeListener { _, checked ->
            Prefs.setAutostartEnabled(this, checked)
        }

        switchPersistentNotifications.setOnCheckedChangeListener { _, checked ->
            Prefs.setPersistentNotifications(this, checked)
            if (checked) {
                SenderService.instance?.showStatusNotification()
                ReceiverService.instance?.showStatusNotification()
            } else {
                SenderService.instance?.hideStatusNotification()
                ReceiverService.instance?.hideStatusNotification()
            }
        }

        switchAutoEnableNew.setOnCheckedChangeListener { _, checked ->
            Prefs.setAutoEnableNewApps(this, checked)
        }

        btnSelectDevice.setOnClickListener { withPermissions { pickDevice() } }
        btnBatteryOpt.setOnClickListener { requestBatteryOptimizationExemption() }

        bottomNav.setOnItemSelectedListener { item ->
            showPage(item.itemId)
            true
        }
    }

    private fun loadSettings() {
        val mode = Prefs.getMode(this)
        if (mode == Prefs.MODE_SENDER) radioSender.isChecked = true else radioReceiver.isChecked = true
        layoutDeviceSelect.visibility = if (mode == Prefs.MODE_SENDER) View.VISIBLE else View.GONE

        val deviceName = Prefs.getDeviceName(this)
        txtSelectedDevice.text = deviceName ?: "None selected"
        txtSelectedDevice.setTextColor(if (deviceName != null) getColor(android.R.color.black) else 0xFF888888.toInt())

        switchForwardCalls.isChecked = Prefs.isPhoneCallForwardEnabled(this)
        switchAutostart.isChecked = Prefs.isAutostartEnabled(this)
        switchPersistentNotifications.isChecked = Prefs.isPersistentNotifications(this)
        switchAutoEnableNew.isChecked = Prefs.isAutoEnableNewApps(this)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        showPage(bottomNav.selectedItemId)
    }

    override fun onResume() {
        super.onResume()
        LogBuffer.onNewEntry = { entry -> appendLog(entry) }
        LogBuffer.onStatusUpdate = { msg ->
            statusText.text = msg
            updateMainButtons()
        }
        debugText.text = ""
        LogBuffer.getAll().forEach { appendLog(it) }

        updateMainButtons()
        updateBatteryOptButton()

        if (awaitingNotificationAccess && isNotificationAccessGranted()) {
            awaitingNotificationAccess = false
            withPermissions { startServiceForCurrentRole() }
        }
    }

    override fun onPause() {
        super.onPause()
        LogBuffer.onNewEntry = null
        LogBuffer.onStatusUpdate = null
    }

    private fun updateMainButtons() {
        val running = SenderService.instance != null || ReceiverService.instance != null
        btnStartService.visibility = if (running) View.GONE else View.VISIBLE
        btnStopService.visibility  = if (running) View.VISIBLE else View.GONE
        btnTest.visibility = if (SenderService.instance != null) View.VISIBLE else View.GONE
    }

    private fun showPage(itemId: Int) {
        pageMain.visibility     = if (itemId == R.id.nav_main)     View.VISIBLE else View.GONE
        pageSettings.visibility = if (itemId == R.id.nav_settings) View.VISIBLE else View.GONE
        pageLog.visibility      = if (itemId == R.id.nav_log)      View.VISIBLE else View.GONE
        pageFilter.visibility   = if (itemId == R.id.nav_filter)   View.VISIBLE else View.GONE
        if (itemId == R.id.nav_log)    debugScroll.post { debugScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        if (itemId == R.id.nav_filter) refreshFilterList()
    }

    // The filter list only stores a package name, not which profile it lives in, so a
    // work-profile app (relayed into this personal-profile process) isn't found by the plain
    // packageManager lookup. Fall back to LauncherApps across every profile associated with this
    // user (personal + work) to resolve the icon there. Same rationale as NotificationListener.
    private fun findAppIcon(pkg: String): android.graphics.drawable.Drawable? {
        try {
            return packageManager.getApplicationIcon(pkg)
        } catch (_: PackageManager.NameNotFoundException) {}
        return try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            val userManager = getSystemService(UserManager::class.java)
            userManager?.userProfiles?.firstNotNullOfOrNull { user ->
                launcherApps?.getActivityList(pkg, user)?.firstOrNull()
                    ?.getBadgedIcon(resources.configuration.densityDpi)
            }
        } catch (_: Exception) { null }
    }

    private fun refreshFilterList() {
        filterAppList.removeAllViews()
        val apps = Prefs.getFilterApps(this)
        if (apps.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No apps seen yet.\nNotifications from apps will appear here once received."
            empty.setPadding(16.dp, 32.dp, 16.dp, 16.dp)
            empty.textAlignment = View.TEXT_ALIGNMENT_CENTER
            empty.setTextColor(0xFF888888.toInt())
            filterAppList.addView(empty)
            return
        }
        for (app in apps) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(16.dp, 12.dp, 16.dp, 12.dp)

            val iconView = android.widget.ImageView(this)
            val iconSize = 40.dp
            iconView.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).also {
                it.marginEnd = 12.dp
            }
            val icon = findAppIcon(app.pkg)
            if (icon != null) iconView.setImageDrawable(icon)
            else iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            row.addView(iconView)

            val textLayout = LinearLayout(this)
            textLayout.orientation = LinearLayout.VERTICAL
            textLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val labelView = TextView(this)
            labelView.text = app.label
            labelView.textSize = 16f
            textLayout.addView(labelView)

            val pkgView = TextView(this)
            pkgView.text = app.pkg
            pkgView.textSize = 11f
            pkgView.setTextColor(0xFF888888.toInt())
            textLayout.addView(pkgView)

            row.addView(textLayout)

            val sw = SwitchMaterial(this)
            sw.isChecked = app.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                Prefs.setFilterAppEnabled(this, app.pkg, checked)
            }
            row.addView(sw)

            filterAppList.addView(row)

            val divider = View(this)
            divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            divider.setBackgroundColor(0x1A000000)
            filterAppList.addView(divider)
        }
    }

    private fun withPermissions(action: () -> Unit) {
        val missing = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            appendLog("requesting: $missing")
            pendingAction = action
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    // ── Start / Stop ──────────────────────────────────────────────────────────

    private fun startServiceForCurrentRole() {
        when (Prefs.getMode(this)) {
            Prefs.MODE_RECEIVER -> {
                appendLog("starting ReceiverService")
                try {
                    startForegroundService(Intent(this, ReceiverService::class.java))
                } catch (e: Exception) {
                    appendLog("FAILED: ${e.javaClass.simpleName}: ${e.message}")
                    return
                }
                updateMainButtons()
                statusText.text = "Receiver active — waiting for sender…"
            }
            Prefs.MODE_SENDER -> {
                if (!isNotificationAccessGranted()) {
                    awaitingNotificationAccess = true
                    AlertDialog.Builder(this)
                        .setTitle("Notification Access Required")
                        .setMessage("NotiHop needs permission to read your notifications.\n\nTap OK, then enable NotiHop in the list.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                        .setNegativeButton("Cancel") { _, _ -> awaitingNotificationAccess = false }
                        .show()
                    return
                }
                val address = Prefs.getDeviceAddress(this)
                if (address == null) {
                    Toast.makeText(this, "Select a receiver device in Settings first", Toast.LENGTH_SHORT).show()
                    return
                }
                appendLog("starting SenderService → $address")
                val intent = Intent(this, SenderService::class.java)
                    .putExtra(SenderService.EXTRA_DEVICE_ADDRESS, address)
                try {
                    startForegroundService(intent)
                } catch (e: Exception) {
                    appendLog("FAILED: ${e.javaClass.simpleName}: ${e.message}")
                    return
                }
                updateMainButtons()
                statusText.text = "Sender active — connecting to ${Prefs.getDeviceName(this) ?: address}…"
            }
        }
    }

    private fun stopActiveService() {
        if (SenderService.instance != null) {
            stopService(Intent(this, SenderService::class.java))
            appendLog("SenderService stopped")
        }
        if (ReceiverService.instance != null) {
            stopService(Intent(this, ReceiverService::class.java))
            appendLog("ReceiverService stopped")
        }
        btnTest.visibility = View.GONE
        btnStartService.visibility = View.VISIBLE
        btnStopService.visibility = View.GONE
        statusText.text = "Ready"
    }

    private fun sendTestNotification() {
        val svc = SenderService.instance
        if (svc == null) { appendLog("sendTest: SenderService not running"); return }
        svc.sendNotification(SharedNotification(
            appPackage = "com.artor.notihop.test",
            appLabel   = "NotiHop Test",
            title      = "Test Notification",
            text       = "Hello from sender at ${timeFormat.format(Date())}",
            timestamp  = System.currentTimeMillis()
        ))
        appendLog("test notification queued")
    }

    // ── Settings: device picker ───────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun pickDevice() {
        val bonded = bondedDevices()
        if (bonded.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found", Toast.LENGTH_SHORT).show()
            return
        }
        val names = bonded.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select receiver device")
            .setItems(names) { _, idx ->
                val device = bonded[idx]
                Prefs.setDevice(this, device.address, device.name ?: device.address)
                txtSelectedDevice.text = device.name ?: device.address
                txtSelectedDevice.setTextColor(getColor(android.R.color.black))
                appendLog("device set: ${device.name} ${device.address}")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Battery optimization ──────────────────────────────────────────────────

    private fun updateBatteryOptButton() {
        val pm = getSystemService(PowerManager::class.java)
        val exempt = pm.isIgnoringBatteryOptimizations(packageName)
        btnBatteryOpt.text = if (exempt) "Battery Optimization Disabled ✓" else "Disable Battery Optimization"
        btnBatteryOpt.isEnabled = !exempt
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already exempt from battery optimization", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun bondedDevices(): List<BluetoothDevice> =
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.bondedDevices.toList()

    private fun appendLog(msg: String) {
        val ts = timeFormat.format(Date())
        debugText.append("$ts $msg\n")
        if (pageLog.visibility == View.VISIBLE)
            debugScroll.post { debugScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }
}
