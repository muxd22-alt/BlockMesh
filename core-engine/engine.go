package engine

import (
	"fmt"
	"os"

	"github.com/libp2p/go-libp2p"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun"
)

// Global reference if we need to close the mesh
var wgDevice *device.Device
var p2pHost interface{} // Type erased for simplicity in gomobile binding

// StartMesh is called by Kotlin (Android) when the user taps "Connect".
// fd: Android's raw TUN file descriptor detached via VpnService
func StartMesh(fd int, nodePrivateKey string, peerConfig string) {
	fmt.Println("Starting BlockMesh go-engine (Android)...")

	// 1. Take control of the Android TUN file descriptor
	tunFile := os.NewFile(uintptr(fd), "/dev/tun")
	tunDevice, err := tun.CreateTUNFromFile(tunFile, 1500)
	if err != nil {
		fmt.Printf("Failed to create TUN device: %v\n", err)
		return
	}

	// 2. Initialize the Ad-blocking DNS Interceptor and launch Background Updater
	StartBlocklistUpdater()

	// 3. Start libp2p host to handle NAT traversal
	host, err := libp2p.New(
		libp2p.NATPortMap(),         // Automatically handle UPnP
		libp2p.EnableHolePunching(), // Automate UDP Hole Punching
	)
	if err != nil {
		fmt.Printf("Failed to start libp2p: %v\n", err)
		return
	}
	p2pHost = host

	// 4. Initialize WireGuard using the TUN device and libp2p UDP transport
	// (buildP2PBind would be a custom bind coupling Wireguard to libp2p's UDP conn)
	logger := device.NewLogger(device.LogLevelError, "")
	wgDevice = device.NewDevice(tunDevice, nil, logger) // passing nil for bind as placeholder

	// 5. Apply WireGuard Configuration
	wgDevice.IpcSet(peerConfig)
}

// ReceivePacketFromIOS is called by Swift via NEPacketTunnelFlow because 
// Apple forbids detachFd()
func ReceivePacketFromIOS(rawPacket []byte) {
	// Let the DNS sinkhole process the packet first
	ProcessPacket(rawPacket)
}

// StopMesh gracefully tears down the network
func StopMesh() {
	if wgDevice != nil {
		wgDevice.Close()
	}
}
