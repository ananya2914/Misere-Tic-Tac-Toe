package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ListView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import android.widget.ArrayAdapter
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.bluetooth.AppDatabase
import com.example.bluetooth.PastGame
import kotlinx.coroutines.launch
import java.io.IOException


private const val TAG = "[Project 2: Networking]"
const val MESSAGE_READ  = 0
const val MESSAGE_WRITE = 1
const val MESSAGE_TOAST = 2
const val MESSAGE_STATE = 3
private const val REQUEST_BLUETOOTH_PERMISSIONS = 100

private val MY_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
private const val SDP_NAME = "TicTacToe"

// Object to hold the connected socket and connection info
object BluetoothConnection {
    var connectedThread: ConnectedThread? = null
    var isServer: Boolean = false
}

class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null

    // True while a client connection attempt is in flight, so repeated taps
    // (easy on a slow device) don't cancel an attempt that is about to succeed
    @Volatile private var connecting = false

    // Device we asked to pair with; only that bond completing should trigger a connect
    private var pendingBondAddress: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.game_selector)
        val offlineGameButton = findViewById<Button>(R.id.offline_game)
        val onlineGameButton = findViewById<Button>(R.id.online_game)
        val historyButton = findViewById<Button>(R.id.history)
        historyButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
        offlineGameButton.setOnClickListener {
                val intent = Intent(this, OfflineGameActivity::class.java)
                startActivity(intent)
        }


        onlineGameButton.setOnClickListener {
            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions()
                return@setOnClickListener  // <- IMPORTANT: stop here, wait for onRequestPermissionsResult
            }

            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setContentView(R.layout.activity_1)
            val scanButton: Button = findViewById(R.id.scan)
            val startServerButton: Button = findViewById(R.id.startServer)
            val deviceListView: ListView = findViewById(R.id.deviceList)
            val statusText: TextView = findViewById(R.id.statusText)

            deviceListAdapter = ArrayAdapter(
                this,
                R.layout.item_device,   // custom row layout (you'll add it below)
                R.id.deviceName,        // TextView inside that layout
                mutableListOf<String>() // your device names
            )
            deviceListView.adapter = deviceListAdapter

            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Request permissions
                    requestBluetoothPermissions()
                }
                startActivityForResult(enableBtIntent, 1)
            }

            val requestCode = 1
            val discoverableIntent: Intent =
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
            startActivityForResult(discoverableIntent, requestCode)

            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            pairedDevices?.forEach { device ->
                val deviceName = device.name
                val deviceHardwareAddress = device.address // MAC address
                Log.d(TAG, "Paired: $deviceName")
                Log.d(TAG, "MAC: $deviceHardwareAddress")
            }

            // Start Server
            startServerButton.setOnClickListener {
                if (hasBluetoothPermissions()) {
                    BluetoothConnection.isServer = true
                    startServer()
                    statusText.text = "Status: Listening for connections..."
                } else {
                    Toast.makeText(this, "Please grant Bluetooth permissions", Toast.LENGTH_SHORT)
                        .show()
                    requestBluetoothPermissions()
                }
            }

            // Discover other devices
            scanButton.setOnClickListener {
                if (hasBluetoothPermissions()) {
                    if (isLocationEnabled()) {
                        BluetoothConnection.isServer = false
                        startBluetoothDiscovery()
                    } else {
                        Toast.makeText(this, "Please enable Location services", Toast.LENGTH_LONG)
                            .show()
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        startActivity(intent)
                    }
                } else {
                    Toast.makeText(this, "Please grant Bluetooth permissions", Toast.LENGTH_SHORT)
                        .show()
                    requestBluetoothPermissions()
                }
            }

            // Connect to Devices
            deviceListView.setOnItemClickListener { _, _, position, _ ->
                if (hasBluetoothPermissions()) {
                    val device = discoveredDevices[position]
                    statusText.text = "Status: Connecting to ${device.name}..."
                    pairAndConnect(device)
                }
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            registerReceiver(receiver, filter)
        }


    }


    private fun startServer() {
        acceptThread?.cancel()
        acceptThread = AcceptThread()
        acceptThread?.start()
        Toast.makeText(this, "Server started. Waiting for connections...", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Server socket listening...")
    }


    private fun pairAndConnect(device: BluetoothDevice) {
        if (connecting || BluetoothConnection.connectedThread != null) {
            Toast.makeText(this, "Already connecting...", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Attempting to pair with: ${device.name} - ${device.address}")

        // Joining another device means this one is no longer hosting
        acceptThread?.cancel()
        acceptThread = null

        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                Toast.makeText(this, "Already paired. Connecting...", Toast.LENGTH_SHORT).show()
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestBluetoothPermissions();
                }
                connectToDevice(device)
            }
            BluetoothDevice.BOND_BONDING -> {
                Toast.makeText(this, "Pairing in progress...", Toast.LENGTH_SHORT).show()
            }
            BluetoothDevice.BOND_NONE -> {
                Toast.makeText(this, "Initiating pairing...", Toast.LENGTH_SHORT).show()
                pendingBondAddress = device.address
                device.createBond()
            }
        }
    }


    private fun connectToDevice(device: BluetoothDevice) {
        // Cancel any ongoing Discovery and connect to Device
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permissions
            requestBluetoothPermissions()
        }
        if (connecting) return
        connecting = true
        bluetoothAdapter?.cancelDiscovery()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    private fun connected(socket: BluetoothSocket, device: BluetoothDevice, asServer: Boolean) {
        // Start the connected thread, disable any more connections and start the game.
        // The side that accepted the connection is the server (X, moves first).
        BluetoothConnection.isServer = asServer
        BluetoothConnection.connectedThread = ConnectedThread(socket)
        BluetoothConnection.connectedThread?.start()
        connectThread = null
        acceptThread = null
        connecting = false

        runOnUiThread {
            findViewById<TextView>(R.id.statusText).text = "Status: Connected to ${device.name}"
            Toast.makeText(this, "Connected successfully! Starting game...", Toast.LENGTH_LONG).show()

            // Start the game activity
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }
    }

    private fun connectionFailed() {
        connectThread = null
        connecting = false
        runOnUiThread {
            findViewById<TextView>(R.id.statusText).text = "Status: Connection failed"
            Toast.makeText(this, "Connection failed. Make sure the other device is running as server.", Toast.LENGTH_LONG).show()
        }
    }


    private inner class AcceptThread : Thread() {
        // Thread to listen for incoming connections (Server)
        private val serverSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) @androidx.annotation.RequiresPermission(
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) {
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SDP_NAME, MY_UUID)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    Log.d(TAG, "Server: Waiting for connection...")
                    serverSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Server socket accept() failed", e)
                    shouldLoop = false
                    null
                }

                socket?.also {
                    Log.d(TAG, "Server: Connection accepted from ${it.remoteDevice.name}")
                    // Only one opponent at a time, so stop listening and free the channel
                    cancel()
                    connected(it, it.remoteDevice, asServer = true)
                    shouldLoop = false
                }
            }
        }

        fun cancel() {
            try {
                // Only close the SERVER socket, not the accepted socket
                serverSocket?.close()
                Log.d(TAG, "Server socket closed")
            } catch (e: IOException) {
                Log.e(TAG, "Could not close server socket", e)
            }
        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        // Thread to connect to a device (Client)
        @Volatile
        private var socket: BluetoothSocket? = null

        @Volatile
        private var cancelled = false

        @RequiresPermission(allOf= [Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT])
        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            // The first RFCOMM connect after discovery sometimes times out, so try twice
            for (attempt in 1..2) {
                if (cancelled) return
                val socket = try {
                    device.createRfcommSocketToServiceRecord(MY_UUID).also { this.socket = it }
                } catch (e: IOException) {
                    Log.e(TAG, "Client: Could not create socket", e)
                    break
                }
                try {
                    Log.d(TAG, "Client: Connecting to ${device.name} (attempt $attempt)...")
                    socket.connect()
                    Log.d(TAG, "Client: Connected successfully")
                    connected(socket, device, asServer = false)
                    return
                } catch (e: IOException) {
                    Log.e(TAG, "Client: Connection failed", e)
                    try {
                        socket.close()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Could not close socket", closeException)
                    }
                }
                if (attempt == 1) sleep(1000)
            }
            if (!cancelled) connectionFailed()
        }

        fun cancel() {
            cancelled = true
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close client socket", e)
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "All Bluetooth permissions granted")
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show()
                Log.d(TAG, "Bluetooth permissions denied")
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        val deviceName = device.name ?: "Unknown Device"
                        val deviceHardwareAddress = device.address
                        Log.d(TAG, "Device found: $deviceName - $deviceHardwareAddress")

                        if (!discoveredDevices.contains(device)) {
                            discoveredDevices.add(device)
                            val bondStatus = when(device.bondState) {
                                BluetoothDevice.BOND_BONDED -> " (Paired)"
                                BluetoothDevice.BOND_BONDING -> " (Pairing...)"
                                else -> ""
                            }
                            deviceListAdapter.add("$deviceName\n$deviceHardwareAddress$bondStatus")
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)

                    device?.let {
                        when (bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                Log.d(TAG, "Successfully paired with ${it.name}")
                                // Both devices get this broadcast; only the one that asked to pair connects
                                if (it.address == pendingBondAddress) {
                                    pendingBondAddress = null
                                    Toast.makeText(this@MainActivity, "Paired! Now connecting...", Toast.LENGTH_SHORT).show()
                                    connectToDevice(it)
                                }
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                Log.d(TAG, "Pairing with ${it.name}...")
                            }
                            BluetoothDevice.BOND_NONE -> {
                                if (it.address == pendingBondAddress) pendingBondAddress = null
                                Toast.makeText(this@MainActivity, "Pairing failed", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Pairing failed with ${it.name}")
                            }
                        }
                        updateDeviceList()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery finished. Found ${discoveredDevices.size} devices")
                    Toast.makeText(this@MainActivity, "Scan complete. Found ${discoveredDevices.size} devices", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun updateDeviceList() {
        deviceListAdapter.clear()
        discoveredDevices.forEach { device ->
            val deviceName = device.name ?: "Unknown Device"
            val bondStatus = when(device.bondState) {
                BluetoothDevice.BOND_BONDED -> " (Paired)"
                BluetoothDevice.BOND_BONDING -> " (Pairing...)"
                else -> ""
            }
            deviceListAdapter.add("$deviceName\n${device.address}$bondStatus")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBluetoothDiscovery() {
        discoveredDevices.clear()
        deviceListAdapter.clear()

        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }

        Log.d(TAG, "=== STARTING DEVICE DISCOVERY ===")
        val started = bluetoothAdapter?.startDiscovery()
        if (started == true) {
            Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Discovery started successfully")
        } else {
            Toast.makeText(this, "Failed to start discovery", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        acceptThread?.cancel()
        connectThread?.cancel()
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
        }
    }
}

class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
    private val inputStream: InputStream = socket.inputStream
    private val outputStream: OutputStream = socket.outputStream
    private val buffer: ByteArray = ByteArray(1024)

    // Messages that arrive before a listener is attached are queued, so the
    // opponent's first move isn't lost while GameActivity is still starting up
    private val pendingMessages = mutableListOf<String>()
    private var disconnected = false

    var messageListener: ((String) -> Unit)? = null
        set(value) {
            val queued = synchronized(this) {
                field = value
                if (value == null) emptyList() else pendingMessages.toList().also { pendingMessages.clear() }
            }
            queued.forEach { value?.invoke(it) }
        }

    var disconnectListener: (() -> Unit)? = null
        set(value) {
            val alreadyDisconnected = synchronized(this) {
                field = value
                disconnected
            }
            if (alreadyDisconnected) value?.invoke()
        }

    override fun run() {
        // Bluetooth is a byte stream, so messages are newline-delimited and
        // may arrive split or merged; accumulate until we have whole lines
        val partial = StringBuilder()

        // Keep listening to the InputStream
        while (true) {
            val numBytes = try {
                inputStream.read(buffer)
            } catch (e: IOException) {
                Log.e(TAG, "Input stream disconnected", e)
                break
            }
            if (numBytes < 0) {
                Log.d(TAG, "Input stream reached end of stream")
                break
            }

            partial.append(String(buffer, 0, numBytes))
            var newline = partial.indexOf("\n")
            while (newline >= 0) {
                val readMsg = partial.substring(0, newline)
                partial.delete(0, newline + 1)
                Log.d(TAG, "Received: $readMsg")
                deliver(readMsg)
                newline = partial.indexOf("\n")
            }
        }

        val listener = synchronized(this) {
            disconnected = true
            disconnectListener
        }
        listener?.invoke()
    }

    private fun deliver(message: String) {
        val listener = synchronized(this) {
            messageListener ?: run { pendingMessages.add(message); null }
        }
        listener?.invoke(message)
    }

    fun write(message: String) {
        try {
            outputStream.write("$message\n".toByteArray())
            Log.d(TAG, "Sent: $message")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to output stream", e)
        }
    }

    fun cancel() {
        try {
            socket.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close connected socket", e)
        }
    }
}