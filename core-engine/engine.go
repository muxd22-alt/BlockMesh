package engine

import (
	"fmt"
	"os"

	"github.com/libp2p/go-libp2p"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun"
)

var wgDevice *device.Device
var p2pHost interface{} 

// StartMesh is called by Kotlin / Swift
func StartMesh(fd int, nodePrivateKey string, peerConfig string) {
	fmt.Println("Starting BlockMesh go-engine...")

	tunFile := os.NewFile(uintptr(fd), "/dev/tun")
	tunDevice, err := tun.CreateTUNFromFile(tunFile, 1500)
	if err != nil {
		fmt.Printf("Failed to create TUN device: %v\n", err)
		return
	}

	startBlocklistUpdater()

	host, err := libp2p.New(
		libp2p.NATPortMap(),         
		libp2p.EnableHolePunching(), 
	)
	if err != nil {
		fmt.Printf("Failed to start libp2p: %v\n", err)
		return
	}
	p2pHost = host

	logger := device.NewLogger(device.LogLevelError, "")
	wgDevice = device.NewDevice(tunDevice, nil, logger) 

	wgDevice.IpcSet(peerConfig)
}

// ReceivePacketFromIOS is a hook for iOS Packet flow
func ReceivePacketFromIOS(rawPacket []byte) {
	processPacket(rawPacket)
}

// StopMesh cleanly shuts down operations
func StopMesh() {
	if wgDevice != nil {
		wgDevice.Close()
	}
}
