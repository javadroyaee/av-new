package com.bookwallet.antivirus

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.File

data class ThreatItem(
    val title: String,
    val description: String,
    val category: String,
    val actionLabel: String,
    val targetData: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var ivStatusShield: ImageView
    private lateinit var tvSubStatusBadge: TextView
    private lateinit var tvMainStatusText: TextView
    private lateinit var tvScanDetailedProgress: TextView
    private lateinit var wideProgressBar: ProgressBar
    private lateinit var btnQuickScanMain: Button
    private lateinit var btnFullScanMain: Button
    private lateinit var tvPrivacyLockLabel: TextView
    private lateinit var tvAccountTabLabel: TextView
    private lateinit var recyclerThreatsList: RecyclerView

    private val threatList = mutableListOf<ThreatItem>()
    private lateinit var threatsAdapter: ThreatsAdapter

    private var isScanRunning = false
    private val PAY_URL = "https://jrweb.ir/wp-json/bookwallet/v1/pay"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initUIComponents()
        requestAppPermissions()
        refreshAppAccountStatus()
        handleIncomingIntents(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntents(it) }
    }

    private fun initUIComponents() {
        ivStatusShield = findViewById(R.id.ivStatusShield)
        tvSubStatusBadge = findViewById(R.id.tvSubStatusBadge)
        tvMainStatusText = findViewById(R.id.tvMainStatusText)
        tvScanDetailedProgress = findViewById(R.id.tvScanDetailedProgress)
        wideProgressBar = findViewById(R.id.wideProgressBar)
        btnQuickScanMain = findViewById(R.id.btnQuickScanMain)
        btnFullScanMain = findViewById(R.id.btnFullScanMain)
        tvPrivacyLockLabel = findViewById(R.id.tvPrivacyLockLabel)
        tvAccountTabLabel = findViewById(R.id.tvAccountTabLabel)
        recyclerThreatsList = findViewById(R.id.recyclerThreatsList)

        threatsAdapter = ThreatsAdapter(threatList) { item -> executeThreatAction(item) }
        recyclerThreatsList.layoutManager = LinearLayoutManager(this)
        recyclerThreatsList.adapter = threatsAdapter

        btnQuickScanMain.setOnClickListener { runEngineScan(false) }
        btnFullScanMain.setOnClickListener { handleProFeature { runEngineScan(true) } }

        findViewById<LinearLayout>(R.id.gridQuickScan).setOnClickListener { runEngineScan(false) }
        findViewById<LinearLayout>(R.id.gridFullScan).setOnClickListener { handleProFeature { runEngineScan(true) } }
        findViewById<LinearLayout>(R.id.gridVpnDetector).setOnClickListener { handleProFeature { runVpnAndHiddenAppAudit() } }
        findViewById<LinearLayout>(R.id.gridPrivacyLock).setOnClickListener { handleProFeature { runPrivacyPermissionAudit() } }

        findViewById<LinearLayout>(R.id.tabDashboard).setOnClickListener {
            findViewById<NestedScrollView>(R.id.mainScrollView).smoothScrollTo(0, 0)
        }
        findViewById<LinearLayout>(R.id.tabScanHub).setOnClickListener { runEngineScan(false) }
        findViewById<LinearLayout>(R.id.tabSecurityStatus).setOnClickListener { handleProFeature { runVpnAndHiddenAppAudit() } }
        findViewById<LinearLayout>(R.id.tabAccount).setOnClickListener { initiatePurchaseFlow() }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        }
    }

    private fun handleProFeature(action: () -> Unit) {
        val prefs = getSharedPreferences("bw_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("activated", false)) {
            AlertDialog.Builder(this)
                .setTitle("قابلیت قفل شده (نسخه Pro)")
                .setMessage("اسکن عمیق، شناسایی برنامه‌های مخفی جاسوسی، تحلیل ریسک فیلترشکن‌های رایگان و ممیزی حریم خصوصی مخصوص نسخه کامل می‌باشد.")
                .setPositiveButton("ارتقا و فعال‌سازی") { _, _ -> initiatePurchaseFlow() }
                .setNegativeButton("بستن", null)
                .show()
        } else {
            action()
        }
    }

    private fun initiatePurchaseFlow() {
        val prefs = getSharedPreferences("bw_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("activated", false)) {
            val name = prefs.getString("user_name", "کاربر گرامی") ?: "کاربر گرامی"
            val phone = prefs.getString("user_phone", "ثبت شده") ?: "ثبت شده"
            AlertDialog.Builder(this)
                .setTitle("اشتراک فعال نسخه کامل")
                .setMessage("این اپلیکیشن برای «$name» به شماره «$phone» فعال می‌باشد.\nتمامی ماژول‌های امنیتی بدون محدودیت در دسترس هستند.")
                .setPositiveButton("تایید", null)
                .show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val etName = EditText(this).apply { hint = "نام و نام خانوادگی" }
        val etPhone = EditText(this).apply {
            hint = "شماره موبایل (مثلاً 09120000000)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        layout.addView(etName)
        layout.addView(etPhone)

        AlertDialog.Builder(this)
            .setTitle("فعال‌سازی نسخه کامل NOD5 Pro")
            .setMessage("جهت بازگشایی تمام قابلیت‌های حرفه‌ای، مشخصات خود را وارد کنید:")
            .setView(layout)
            .setPositiveButton("پرداخت و فعال‌سازی") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                if (name.length >= 2 && phone.length >= 10) {
                    prefs.edit().putString("user_name", name).putString("user_phone", phone).apply()
                    val payUri = Uri.parse(PAY_URL).buildUpon()
                        .appendQueryParameter("name", name)
                        .appendQueryParameter("phone", phone)
                        .build()
                    startActivity(Intent(Intent.ACTION_VIEW, payUri))
                } else {
                    Toast.makeText(this, "لطفاً نام و شماره موبایل معتبر وارد کنید.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun handleIncomingIntents(intent: Intent) {
        if (intent.getBooleanExtra("TRIGGER_ALERT", false)) {
            AlertDialog.Builder(this)
                .setTitle(intent.getStringExtra("ALERT_TITLE") ?: "هشدار امنیتی")
                .setMessage(intent.getStringExtra("ALERT_DESC") ?: "")
                .setPositiveButton("متوجه شدم", null)
                .show()
        }

        val data: Uri? = intent.data
        if (data != null && data.scheme == "bookwallet" && data.host == "activate") {
            val phone = data.getQueryParameter("phone") ?: ""
            val status = data.getQueryParameter("status") ?: ""
            val prefs = getSharedPreferences("bw_prefs", Context.MODE_PRIVATE)
            if (phone.isNotEmpty()) prefs.edit().putString("user_phone", phone).apply()
            if (status == "success") {
                prefs.edit().putBoolean("activated", true).apply()
                refreshAppAccountStatus()
                AlertDialog.Builder(this)
                    .setTitle("پرداخت با موفقیت انجام شد")
                    .setMessage("نسخه کامل NOD5 Security Pro برای شما فعال گردید.")
                    .setPositiveButton("شروع", null)
                    .show()
            }
        }
    }

    private fun refreshAppAccountStatus() {
        val isActivated = getSharedPreferences("bw_prefs", Context.MODE_PRIVATE).getBoolean("activated", false)
        if (isActivated) {
            tvSubStatusBadge.text = "نسخه پرو فعال"
            tvSubStatusBadge.setBackgroundColor(0xFF16A34A.toInt())
            tvPrivacyLockLabel.text = "حریم خصوصی"
            tvAccountTabLabel.text = "اشتراک من"
        } else {
            tvSubStatusBadge.text = "قفل شده"
            tvSubStatusBadge.setBackgroundColor(0xFFF97316.toInt())
            tvPrivacyLockLabel.text = "حریم خصوصی (قفل)"
            tvAccountTabLabel.text = "ارتقا پرو"
        }
    }

    private fun runEngineScan(isFullScan: Boolean) {
        if (isScanRunning) return
        isScanRunning = true
        threatList.clear()
        threatsAdapter.notifyDataSetChanged()

        wideProgressBar.visibility = View.VISIBLE
        wideProgressBar.progress = 0
        btnQuickScanMain.isEnabled = false
        btnFullScanMain.isEnabled = false
        tvMainStatusText.text = if (isFullScan) "در حال اسکن عمیق کامل..." else "در حال اسکن سریع..."

        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName }

            val rootDir = Environment.getExternalStorageDirectory()
            val targetDirs = if (isFullScan) listOf(rootDir) else listOf(File(rootDir, "Download"), File(rootDir, "DCIM"))

            val multimediaExts = setOf("mp4", "mkv", "mp3", "jpg", "png", "wav", "avi", "mov")
            val executableExts = setOf("apk", "exe", "jar", "dex", "so")

            var scannedExecutables = 0
            var scannedMultimedia = 0
            var junkCount = 0

            var appIdx = 0
            for (app in installedApps) {
                appIdx++
                scannedExecutables++
                val appName = pm.getApplicationLabel(app).toString()
                val hasIcon = pm.getLaunchIntentForPackage(app.packageName) != null
                val pct = ((appIdx * 100) / (installedApps.size.coerceAtLeast(1) * 2)).coerceIn(1, 48)

                withContext(Dispatchers.Main) {
                    wideProgressBar.progress = pct
                    tvScanDetailedProgress.text = "بررسی برنامه: $appName"
                }
                delay(25)

                if (SecurityAnalyzer.isSpywareOrHidden(app.packageName, appName, hasIcon)) {
                    threatList.add(ThreatItem("برنامه مخفی / مشکوک: $appName", "این برنامه فاقد آیکون عادی است یا الگوی نظارتی/جاسوسی دارد.", "MALWARE", "حذف برنامه", app.packageName))
                } else if (SecurityAnalyzer.isFreeVpnRisk(app.packageName, appName)) {
                    threatList.add(ThreatItem("فیلترشکن رایگان پرریسک: $appName", "ابزارهای VPN رایگان ممکن است باعث نشت اطلاعات یا نصب برنامه‌های جانبی شوند.", "FREE_VPN", "حذف VPN", app.packageName))
                }
            }

            val filesToScan = mutableListOf<File>()
            for (dir in targetDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().maxDepth(if (isFullScan) 5 else 2).filter { it.isFile }.take(if (isFullScan) 350 else 120).forEach {
                        filesToScan.add(it)
                    }
                }
            }

            var fileIdx = 0
            for (file in filesToScan) {
                fileIdx++
                val ext = file.extension.lowercase()
                if (multimediaExts.contains(ext)) {
                    scannedMultimedia++
                } else if (executableExts.contains(ext)) {
                    scannedExecutables++
                    if (ext == "apk") {
                        junkCount++
                        threatList.add(ThreatItem("فایل نصبی اضافی (APK): ${file.name}", "پوشه: ${file.parentFile?.name} | حجم: ${file.length() / 1024} KB (فایل رهاشده)", "JUNK_APK", "حذف فایل", file.absolutePath))
                    }
                } else if (ext == "tmp" || ext == "log" || file.name.startsWith(".")) {
                    junkCount++
                }

                if (fileIdx % 8 == 0) {
                    val pct = 50 + ((fileIdx * 50) / filesToScan.size.coerceAtLeast(1)).coerceIn(1, 49)
                    withContext(Dispatchers.Main) {
                        wideProgressBar.progress = pct
                        tvScanDetailedProgress.text = "اسکن فایل: ${file.name}"
                    }
                    delay(15)
                }
            }

            withContext(Dispatchers.Main) {
                wideProgressBar.progress = 100
                wideProgressBar.visibility = View.GONE
                btnQuickScanMain.isEnabled = true
                btnFullScanMain.isEnabled = true
                isScanRunning = false
                threatsAdapter.notifyDataSetChanged()

                val summary = "فایل اجرایی: $scannedExecutables | مولتی‌مدیا: $scannedMultimedia | فایل اضافه: $junkCount"
                if (threatList.isEmpty()) {
                    ivStatusShield.setImageResource(R.drawable.ic_shield_good)
                    tvMainStatusText.text = "همه چیز ایمن است و مشکلی یافت نشد"
                    tvScanDetailedProgress.text = summary
                } else {
                    ivStatusShield.setImageResource(R.drawable.ic_shield_warning)
                    tvMainStatusText.text = "${threatList.size} مورد مشکوک و فایل اضافه شناسایی شد"
                    tvScanDetailedProgress.text = summary
                }
            }
        }
    }

    private fun runVpnAndHiddenAppAudit() {
        threatList.clear()
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName }

        for (app in apps) {
            val appName = pm.getApplicationLabel(app).toString()
            val hasIcon = pm.getLaunchIntentForPackage(app.packageName) != null
            if (!hasIcon) {
                threatList.add(ThreatItem("برنامه مخفی بدون آیکون: $appName", "بسته ${app.packageName} در منوی گوشی مخفی شده است.", "MALWARE", "حذف برنامه", app.packageName))
            } else if (SecurityAnalyzer.isFreeVpnRisk(app.packageName, appName)) {
                threatList.add(ThreatItem("فیلترشکن رایگان: $appName", "این VPN رایگان امنیت پایینی دارد و توصیه نمی‌شود.", "FREE_VPN", "حذف برنامه", app.packageName))
            }
        }
        threatsAdapter.notifyDataSetChanged()
        tvMainStatusText.text = if (threatList.isEmpty()) "برنامه مخفی یا VPN پرخطری یافت نشد" else "${threatList.size} برنامه مخفی/VPN شناسایی شد"
    }

    private fun runPrivacyPermissionAudit() {
        threatList.clear()
        val pm = packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || pkg.packageName == packageName) continue
            val perms = pkg.requestedPermissions ?: continue
            val risky = mutableListOf<String>()
            if (perms.any { it.contains("CAMERA") }) risky.add("دوربین")
            if (perms.any { it.contains("RECORD_AUDIO") }) risky.add("میکروفون")
            if (perms.any { it.contains("READ_SMS") }) risky.add("پیامک")
            if (perms.any { it.contains("READ_CONTACTS") }) risky.add("مخاطبین")
            if (risky.isNotEmpty()) {
                threatList.add(ThreatItem("حریم خصوصی: ${pm.getApplicationLabel(appInfo)}", "دسترسی به: ${risky.joinToString("، ")}", "PRIVACY", "تنظیمات", pkg.packageName))
            }
        }
        threatsAdapter.notifyDataSetChanged()
        tvMainStatusText.text = "ممیزی مجوزهای حریم خصوصی (${threatList.size} برنامه)"
    }

    private fun executeThreatAction(item: ThreatItem) {
        when (item.category) {
            "MALWARE", "FREE_VPN" -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${item.targetData}")))
            "JUNK_APK" -> {
                val f = File(item.targetData)
                if (f.exists() && f.delete()) {
                    threatList.remove(item)
                    threatsAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "فایل با موفقیت حذف شد.", Toast.LENGTH_SHORT).show()
                }
            }
            "PRIVACY" -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${item.targetData}")))
        }
    }
}

class ThreatsAdapter(
    private val list: List<ThreatItem>,
    private val onActionClick: (ThreatItem) -> Unit
) : RecyclerView.Adapter<ThreatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvThreatTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvThreatDesc)
        val btnAction: Button = view.findViewById(R.id.btnThreatAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_threat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvTitle.text = item.title
        holder.tvDesc.text = item.description
        holder.btnAction.text = item.actionLabel
        holder.btnAction.setOnClickListener { onActionClick(item) }
    }

    override fun getItemCount(): Int = list.size
}