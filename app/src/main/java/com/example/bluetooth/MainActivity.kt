package com.example.bluetooth

import android.Manifest
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.game_selector)

        val offlineGameButton = findViewById<Button>(R.id.offline_game)
        val onlineGameButton = findViewById<Button>(R.id.online_game)

        offlineGameButton.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }


        onlineGameButton.setOnClickListener {
            setContentView(R.layout.activity_1)
            val scanButton: Button = findViewById(R.id.scan)
            val startServerButton: Button = findViewById(R.id.startServer)
            val deviceListView: ListView = findViewById(R.id.deviceList)
            val statusText: TextView = findViewById(R.id.statusText)

            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()
                setContentView(R.layout.game_selector)
            }

            deviceListAdapter =
                ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
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
        Log.d(TAG, "Attempting to pair with: ${device.name} - ${device.address}")

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
        bluetoothAdapter?.cancelDiscovery()
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    private fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        // Start the connected thread, disable any more connections and start the game
        BluetoothConnection.connectedThread = ConnectedThread(socket)
        BluetoothConnection.connectedThread?.start()
        connectThread = null
        acceptThread = null

        runOnUiThread {
            findViewById<TextView>(R.id.statusText).text = "Status: Connected to ${device.name}"
            Toast.makeText(this, "Connected successfully! Starting game...", Toast.LENGTH_LONG).show()

            // Start the game activity
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }
    }

    private fun connectionFailed() {
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
                    connected(it, it.remoteDevice)
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
        private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        @Volatile
        private var shouldClose = true  // Add this flag

        @RequiresPermission(allOf= [Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT])
        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            socket?.let { socket ->
                try {
                    Log.d(TAG, "Client: Connecting to ${device.name}...")
                    socket.connect()
                    Log.d(TAG, "Client: Connected successfully")
                    shouldClose = false  // Don't close socket on successful connection
                    connected(socket, device)
                } catch (e: IOException) {
                    Log.e(TAG, "Client: Connection failed", e)
                    try {
                        socket.close()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Could not close socket", closeException)
                    }
                    connectionFailed()
                }
            }
        }

        fun cancel() {
            if (shouldClose) {
                try {
                    socket?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Could not close client socket", e)
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

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
                                Toast.makeText(this@MainActivity, "Paired! Now connecting...", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Successfully paired with ${it.name}")
                                connectToDevice(it)
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                Log.d(TAG, "Pairing with ${it.name}...")
                            }
                            BluetoothDevice.BOND_NONE -> {
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

    var messageListener: ((String) -> Unit)? = null

    override fun run() {
        var numBytes: Int

        // Keep listening to the InputStream
        while (true) {
            numBytes = try {
                inputStream.read(buffer)
            } catch (e: IOException) {
                Log.e(TAG, "Input stream disconnected", e)
                break
            }

            // Send received data to listener
            val readMsg = String(buffer, 0, numBytes)
            messageListener?.invoke(readMsg)
            Log.d(TAG, "Received: $readMsg")
        }
    }

    fun write(message: String) {
        try {
            outputStream.write(message.toByteArray())
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