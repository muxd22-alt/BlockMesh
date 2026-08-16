package com.muxd22.blockmesh

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import engine.Engine

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var sourcesContainer: LinearLayout
    private lateinit var modeText: TextView

    private val darkBg = Color.parseColor("#121212")
    private val cardBg = Color.parseColor("#1E1E1E")
    private val textColor = Color.parseColor("#FFFFFF")
    private val textMuted = Color.parseColor("#AAAAAA")

    data class HostSource(val name: String, val url: String, val count: String)
    private val defaultSources = listOf(
        HostSource("AdAway official hosts", "https://adaway.org/hosts.txt", "7k hosts"),
        HostSource("Pete Lowe blocklist", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext", "4k hosts"),
        HostSource("StevenBlack Unified", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "99k hosts"),
        HostSource("oisd big", "https://big.oisd.nl/", "206k hosts"),
        HostSource("hagezi pro", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt", "150k hosts"),
        HostSource("oisd lite", "https://lite.oisd.nl/", "50k hosts")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(darkBg)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 64)
        }

        // --- HEADER ---
        val header = TextView(this).apply {
            text = "BlockMesh"
            textSize = 28f
            setTextColor(textColor)
            setPadding(16, 0, 0, 16)
        }
        layout.addView(header)
        
        checkBatteryOptimizations()

        // --- STATUS & TOGGLE ---
        val topCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 48, 32, 48)
            gravity = Gravity.CENTER_VERTICAL
            background = getCardBg()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 16, 0, 32)
            }
        }
        
        statusText = TextView(this).apply {
            textSize = 18f
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topCard.addView(statusText)

        toggleButton = Button(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(32, 16, 32, 16)
        }
        toggleButton.setOnClickListener {
            if (Engine.isRunning()) {
                val intent = Intent(this, MeshVpnService::class.java)
                intent.action = MeshVpnService.ACTION_STOP
                startService(intent)
                toggleButton.postDelayed({ updateUI() }, 300)
            } else {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 0)
                } else {
                    onActivityResult(0, RESULT_OK, null)
                }
            }
        }
        applyTVFocus(toggleButton)
        topCard.addView(toggleButton)
        layout.addView(topCard)

        // --- SPLIT TUNNELING (APP TARGETING) ---
        val modeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            background = getCardBg()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 32)
            }
        }
        val modeTitle = TextView(this).apply {
            text = "App Routing Strategy"
            textSize = 18f
            setTextColor(textColor)
        }
        modeText = TextView(this).apply {
            textSize = 14f
            setTextColor(textMuted)
            setPadding(0, 8, 0, 16)
        }
        val modeBtn = Button(this).apply {
            text = "Configure App Blocking"
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(textColor)
        }
        modeBtn.setOnClickListener { showAppConfigDialog() }
        applyTVFocus(modeBtn)
        
        modeCard.addView(modeTitle)
        modeCard.addView(modeText)
        modeCard.addView(modeBtn)
        layout.addView(modeCard)

        // --- TIPS & TRICKS FOR BATTERY (PHONE/TV) ---
        val tipsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            background = getCardBg()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 32)
            }
        }
        val tipsTitle = TextView(this).apply {
            text = "Battery Optimization Tips & Tricks \uD83D\uDCA1"
            textSize = 18f
            setTextColor(textColor)
        }
        val tipsText = TextView(this).apply {
            text = "Keep BlockMesh running 24/7 on Phones & TVs without random disconnects."
            textSize = 14f
            setTextColor(textMuted)
            setPadding(0, 8, 0, 16)
        }
        val tipsBtn = Button(this).apply {
            text = "View Tips"
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(textColor)
        }
        tipsBtn.setOnClickListener { showTipsDialog() }
        applyTVFocus(tipsBtn)
        
        tipsCard.addView(tipsTitle)
        tipsCard.addView(tipsText)
        tipsCard.addView(tipsBtn)
        layout.addView(tipsCard)

        // --- HOSTS SOURCES (ADAWAY STYLE) ---
        val sourceTitle = TextView(this).apply {
            text = "Hosts sources"
            textSize = 20f
            setTextColor(textColor)
            setPadding(16, 16, 0, 16)
        }
        layout.addView(sourceTitle)

        sourcesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        layout.addView(sourcesContainer)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        renderSources()
    }

    private fun updateUI() {
        if (Engine.isRunning()) {
            statusText.text = "Active \u2705\n${Engine.getBlockedCount()} domains"
            toggleButton.text = "Stop"
            toggleButton.setBackgroundColor(Color.parseColor("#D32F2F"))
        } else {
            statusText.text = "Stopped \u274C"
            toggleButton.text = "Start"
            toggleButton.setBackgroundColor(Color.parseColor("#2E7D32"))
        }

        val prefs = getSharedPreferences("BlockMeshPrefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("vpn_mode", "system")
        val count = (prefs.getStringSet("target_apps", setOf()) ?: setOf()).size
        modeText.text = when(mode) {
            "include" -> "Filtering $count specific apps only (e.g. Jodel/Games)"
            "exclude" -> "System-wide blocking (Excluding $count banking apps)"
            else -> "System-wide blocking (All Apps)"
        }
    }

    private fun applyTVFocus(view: android.view.View) {
        view.isFocusable = true
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(150).start()
                v.setBackgroundColor(Color.parseColor("#444444")) // Highlight color for TV remote
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                if (v is Button) {
                    when (v.text.toString().take(4)) {
                        "Start" -> v.setBackgroundColor(Color.parseColor("#1976D2"))
                        "Stop" -> v.setBackgroundColor(Color.parseColor("#D32F2F"))
                        else -> v.setBackgroundColor(Color.parseColor("#333333")) // Default
                    }
                } else if (v is LinearLayout) {
                    v.background = getCardBg()
                }
            }
        }
    }

    private fun checkBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val prefs = getSharedPreferences("BlockMeshPrefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("prompted_battery", false)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("Android 'Doze' mode might kill the BlockMesh VPN when your screen turns off. To keep ad-blocking running seamlessly 24/7 without disconnects, please allow BlockMesh to run in the background.")
                    .setPositiveButton("Configure") { _, _ ->
                        prefs.edit().putBoolean("prompted_battery", true).apply()
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        try { startActivity(intent) } catch (e: Exception) {}
                    }
                    .setNegativeButton("Skip") { _, _ -> 
                        prefs.edit().putBoolean("prompted_battery", true).apply()
                    }
                    .show()
            }
        }
    }

    private fun renderSources() {
        sourcesContainer.removeAllViews()
        val activeUrls = Engine.getBlocklistURLs().split("\n").filter { it.isNotEmpty() }.toSet()

        defaultSources.forEach { source ->
            val isEnabled = activeUrls.contains(source.url)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = getCardBg()
                setPadding(32, 32, 32, 32)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 16)
                }
            }

            val checkbox = CheckBox(this).apply {
                isChecked = isEnabled
                setPadding(0, 0, 32, 0)
                isFocusable = false // Let the card handle focus on TV
                isClickable = false
            }
            
            // TV remote interaction
            card.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
                if (checkbox.isChecked) Engine.addBlocklistURL(source.url)
                else Engine.removeBlocklistURL(source.url)
                
                if (Engine.isRunning()) Engine.refreshBlocklists()
                Toast.makeText(this@MainActivity, "Refreshing blocklists...", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            applyTVFocus(card)
            
            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val title = TextView(this).apply {
                text = source.name
                textSize = 16f
                setTextColor(textColor)
            }
            val urlTxt = TextView(this).apply {
                text = source.url
                textSize = 12f
                setTextColor(textMuted)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val countTxt = TextView(this).apply {
                text = source.count
                textSize = 12f
                setTextColor(Color.parseColor("#4CAF50"))
                setPadding(0, 8, 0, 0)
            }
            
            textLayout.addView(title)
            textLayout.addView(urlTxt)
            textLayout.addView(countTxt)
            
            card.addView(checkbox)
            card.addView(textLayout)
            sourcesContainer.addView(card)
        }
    }

    private fun showAppConfigDialog() {
        val prefs = getSharedPreferences("BlockMeshPrefs", Context.MODE_PRIVATE)
        val currentMode = prefs.getString("vpn_mode", "system")
        val currentApps = prefs.getStringSet("target_apps", setOf()) ?: setOf()

        val layout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL; setPadding(48,48,48,48) 
        }
        
        val modeGroup = RadioGroup(this)
        val rbSystem = RadioButton(this).apply { text = "System Wide (All Apps)"; id = 1 }
        val rbExclude = RadioButton(this).apply { text = "Exclude Specific Apps (e.g. Banks)"; id = 2 }
        val rbInclude = RadioButton(this).apply { text = "Only Filter Specific Apps (e.g. Jodel/Games)"; id = 3 }
        
        modeGroup.addView(rbSystem)
        modeGroup.addView(rbExclude)
        modeGroup.addView(rbInclude)

        when(currentMode) {
            "exclude" -> modeGroup.check(2)
            "include" -> modeGroup.check(3)
            else -> modeGroup.check(1)
        }

        val input = EditText(this).apply {
            hint = "com.tellm.android.app, com.chase.sig.android"
            setText(currentApps.joinToString(", "))
            setPadding(0, 32, 0, 0)
        }

        layout.addView(modeGroup)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Split Tunneling Routing")
            .setView(layout)
            .setPositiveButton("Save & Apply") { _, _ ->
                val newMode = when(modeGroup.checkedRadioButtonId) {
                    2 -> "exclude"; 3 -> "include"; else -> "system"
                }
                val newApps = input.text.toString().split(",").map{ it.trim() }.filter{ it.isNotEmpty() }.toSet()
                
                prefs.edit().putString("vpn_mode", newMode).putStringSet("target_apps", newApps).apply()
                // Restart VPN to apply routes!
                if(Engine.isRunning()) {
                    startService(Intent(this, MeshVpnService::class.java).setAction(MeshVpnService.ACTION_STOP))
                    Toast.makeText(this, "Applying routes...", Toast.LENGTH_SHORT).show()
                    toggleButton.postDelayed({ 
                        startService(Intent(this, MeshVpnService::class.java).setAction(MeshVpnService.ACTION_START))
                        updateUI()
                    }, 500)
                }
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTipsDialog() {
        val tipsMsg = """
            |To prevent Android and TV operating systems from killing the BlockMesh background VPN process:
            |
            |📱 Xiaomi/POCO: 
            |- Go to Settings > Apps > Manage Apps > BlockMesh
            |- Turn ON 'Autostart'
            |- Set 'Battery saver' to 'No restrictions'
            |
            |📱 Samsung:
            |- Go to Settings > Apps > BlockMesh > Battery
            |- Select 'Unrestricted'
            |
            |📱 Huawei:
            |- Go to Settings > Battery > App Launch
            |- Manage BlockMesh manually (enable Auto-launch, Secondary launch, Run in background)
            |
            |📺 Android TV / Google TV:
            |- Go to Settings > Apps > BlockMesh
            |- Ensure it's not restricted
            |- Use the 'Boot Receiver' feature (already enabled!) so BlockMesh auto-starts when you turn the TV on.
            |
            |🔒 For all devices: Open your recents app menu, long press on BlockMesh (or tap the icon) and select 'Lock'.
        """.trimMargin()

        AlertDialog.Builder(this)
            .setTitle("Tips & Tricks \uD83D\uDD0B")
            .setMessage(tipsMsg)
            .setPositiveButton("Configure Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                try { startActivity(intent) } catch (e: Exception) {}
            }
            .setNegativeButton("Got it", null)
            .show()
    }

    private fun getCardBg(): GradientDrawable {
        val dg = GradientDrawable()
        dg.setColor(cardBg)
        dg.cornerRadius = 24f
        return dg
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            startService(Intent(this, MeshVpnService::class.java).setAction(MeshVpnService.ACTION_START))
            toggleButton.postDelayed({ updateUI() }, 500)
        }
    }
}
