package com.muxd22.blockmesh

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import engine.Engine

class MeshVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("BlockMesh Decentralized Pi-Hole")
        builder.addAddress("10.100.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("10.100.0.1")

        vpnInterface = builder.establish() ?: return START_NOT_STICKY

        // Start the Go DNS sinkhole engine (loads blocklists into radix trie)
        Thread {
            Engine.startEngine()
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        Engine.stopEngine()
        vpnInterface?.close()
        super.onDestroy()
    }
}
