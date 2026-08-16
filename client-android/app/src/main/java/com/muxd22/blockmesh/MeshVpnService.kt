package com.muxd22.blockmesh

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import engine.Engine // This is generated dynamically from your Go gomobile build

class MeshVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("BlockMesh Decentralized Pi-Hole")
        builder.addAddress("10.100.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("10.100.0.1")

        vpnInterface = builder.establish()
        
        val fd = vpnInterface?.fd ?: return START_NOT_STICKY
        val rawFd = vpnInterface?.detachFd()!!

        // Pass the raw FD directly to the Go Engine
        Thread {
            Engine.startMesh(rawFd.toLong(), "dummy_private_key", "dummy_peer_config")
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        Engine.stopMesh()
        super.onDestroy()
    }
}
