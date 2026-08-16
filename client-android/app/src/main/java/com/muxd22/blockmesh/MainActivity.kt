package com.muxd22.blockmesh

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        
        val button = Button(this)
        button.text = "Connect BlockMesh"
        button.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                onActivityResult(0, RESULT_OK, null)
            }
        }
        
        layout.addView(button)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val intent = Intent(this, MeshVpnService::class.java)
            startService(intent)
            Toast.makeText(this, "Mesh VPN Started", Toast.LENGTH_SHORT).show()
        }
    }
}
