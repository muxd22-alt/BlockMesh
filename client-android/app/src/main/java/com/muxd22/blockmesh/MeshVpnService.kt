package com.muxd22.blockmesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import engine.Engine

class MeshVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_START = "com.muxd22.blockmesh.START"
        const val ACTION_STOP = "com.muxd22.blockmesh.STOP"
        private const val CHANNEL_ID = "blockmesh_vpn_channel"
        private const val NOTIFICATION_ID = 101
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (vpnInterface == null) {
            val builder = Builder()
            builder.setSession("BlockMesh DNS")
            builder.addAddress("10.100.0.2", 32)
            // REMOVED route to 0.0.0.0/0 to keep normal internet working

            // Split Tunneling Logic
            val prefs = getSharedPreferences("BlockMeshPrefs", Context.MODE_PRIVATE)
            val mode = prefs.getString("vpn_mode", "system")
            val apps = prefs.getStringSet("target_apps", setOf()) ?: setOf()

            if (mode == "exclude") {
                for (pkg in apps) {
                    try { builder.addDisallowedApplication(pkg) } catch (e: Exception) {}
                }
            } else if (mode == "include") {
                for (pkg in apps) {
                    try { builder.addAllowedApplication(pkg) } catch (e: Exception) {}
                }
            }

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                showNotification()
                Thread {
                    Engine.startEngine()
                }.start()
            } else {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun showNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BlockMesh VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MeshVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlockMesh Active")
            .setContentText("DNS Sinkhole is running")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop AdBlock", stopPendingIntent)
            .setOngoing(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        
        Engine.stopEngine()
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
