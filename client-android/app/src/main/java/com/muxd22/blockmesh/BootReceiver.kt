package com.muxd22.blockmesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d("BlockMesh", "BootReceiver invoked.")
            
            val prefs = context.getSharedPreferences("BlockMeshPrefs", Context.MODE_PRIVATE)
            val startOnBoot = prefs.getBoolean("start_on_boot", true)
            
            if (startOnBoot) {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent == null) {
                    // VPN is already authorized, start service
                    val startIntent = Intent(context, MeshVpnService::class.java)
                    startIntent.action = MeshVpnService.ACTION_START
                    context.startForegroundService(startIntent)
                } else {
                    // Cannot start automatically if Android revoked VPN permission
                    Log.w("BlockMesh", "Cannot auto-start VPN, permission required.")
                }
            }
        }
    }
}
