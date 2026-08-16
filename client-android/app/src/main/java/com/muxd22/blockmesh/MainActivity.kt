package com.muxd22.blockmesh

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import engine.Engine

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var urlListText: TextView
    private lateinit var blockedCountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Header
        val header = TextView(this).apply {
            text = "BlockMesh"
            textSize = 32f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 16)
        }
        layout.addView(header)

        // Status
        statusText = TextView(this).apply {
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(statusText)

        // Count
        blockedCountText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 48)
        }
        layout.addView(blockedCountText)

        // Toggle Button
        toggleButton = Button(this).apply {
            textSize = 18f
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        toggleButton.setOnClickListener {
            if (Engine.isRunning()) {
                val intent = Intent(this, MeshVpnService::class.java)
                intent.action = MeshVpnService.ACTION_STOP
                startService(intent)
                
                // Slight delay to let service stop
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
        layout.addView(toggleButton)

        // Divider
        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 64, 0, 64)
            }
        }
        layout.addView(divider)

        // Blocklist Manager Section
        val blHeader = TextView(this).apply {
            text = "Custom Blocklists"
            textSize = 22f
            setTextColor(Color.DKGRAY)
        }
        layout.addView(blHeader)

        val urlInput = EditText(this).apply {
            hint = "https://example.com/hosts.txt"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        layout.addView(urlInput)

        val addUrlBtn = Button(this).apply {
            text = "Add URL & Refresh"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        addUrlBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                Engine.addBlocklistURL(url)
                if (Engine.isRunning()) {
                    Engine.refreshBlocklists()
                    Toast.makeText(this, "Refreshing blocklists...", Toast.LENGTH_SHORT).show()
                }
                urlInput.text.clear()
                updateUI()
            }
        }
        layout.addView(addUrlBtn)

        val resetBtn = Button(this).apply {
            text = "Reset to Default"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        resetBtn.setOnClickListener {
            Engine.setBlocklistURLs("https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext")
            if (Engine.isRunning()) {
                Engine.refreshBlocklists()
            }
            updateUI()
            Toast.makeText(this, "Reset to default blocklist", Toast.LENGTH_SHORT).show()
        }
        layout.addView(resetBtn)

        urlListText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 32, 0, 32)
            setTextColor(Color.GRAY)
        }
        layout.addView(urlListText)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val running = Engine.isRunning()
        if (running) {
            statusText.text = "Status: Active \u2705"
            statusText.setTextColor(Color.parseColor("#388E3C"))
            toggleButton.text = "Stop BlockMesh"
            toggleButton.setBackgroundColor(Color.parseColor("#D32F2F")) // Red
            toggleButton.setTextColor(Color.WHITE)
            blockedCountText.text = "Domains loaded: ${Engine.getBlockedCount()}"
        } else {
            statusText.text = "Status: Disconnected \u274C"
            statusText.setTextColor(Color.parseColor("#D32F2F"))
            toggleButton.text = "Start BlockMesh"
            toggleButton.setBackgroundColor(Color.parseColor("#1976D2")) // Blue
            toggleButton.setTextColor(Color.WHITE)
            blockedCountText.text = "Domains loaded: 0"
        }
        
        val urls = Engine.getBlocklistURLs()
        urlListText.text = "Current Sources:\n$urls"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val intent = Intent(this, MeshVpnService::class.java)
            intent.action = MeshVpnService.ACTION_START
            startService(intent)
            
            // Poll for update while Go engine spins up
            toggleButton.postDelayed({ updateUI() }, 500)
        }
    }
}
