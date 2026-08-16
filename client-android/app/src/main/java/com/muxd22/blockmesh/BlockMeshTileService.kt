package com.muxd22.blockmesh

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import engine.Engine

class BlockMeshTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = Engine.isRunning()
        
        if (isRunning) {
            val stopIntent = Intent(this, MeshVpnService::class.java)
            stopIntent.action = MeshVpnService.ACTION_STOP
            startService(stopIntent)
        } else {
            // Cannot start VPN directly from quick tile if permission not granted
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent == null) {
                val startIntent = Intent(this, MeshVpnService::class.java)
                startIntent.action = MeshVpnService.ACTION_START
                startForegroundService(startIntent)
            } else {
                // Open app to request permission
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(intent)
                return
            }
        }

        // Fake small delay to allow service to start/stop before updating tile
        Thread.sleep(200)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        if (Engine.isRunning()) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "BlockMesh On"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "BlockMesh Off"
        }
        tile.updateTile()
    }
}
