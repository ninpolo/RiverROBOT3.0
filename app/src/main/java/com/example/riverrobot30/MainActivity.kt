package com.example.riverrobot30

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val btUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var btAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    private var isConnected = false

    private lateinit var btnConnect: Button
    private lateinit var txtStatus: TextView
    private lateinit var btnForward: Button
    private lateinit var btnBack: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStop: Button
    private lateinit var btnBelt: ToggleButton
    private lateinit var btnShred: ToggleButton
    private lateinit var btnBucket: ToggleButton
    private lateinit var btnAnchorDn: Button
    private lateinit var btnAnchorUp: Button
    private lateinit var seekSpeed: SeekBar
    private lateinit var txtSpeed: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btAdapter = getSystemService(BluetoothManager::class.java)?.adapter

        btnConnect = findViewById(R.id.btnConnect)
        txtStatus = findViewById(R.id.txtStatus)
        btnForward = findViewById(R.id.btnForward)
        btnBack = findViewById(R.id.btnBack)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        btnStop = findViewById(R.id.btnStop)
        btnBelt = findViewById(R.id.btnBelt)
        btnShred = findViewById(R.id.btnShred)
        btnBucket = findViewById(R.id.btnBucket)
        btnAnchorDn = findViewById(R.id.btnAnchorDn)
        btnAnchorUp = findViewById(R.id.btnAnchorUp)
        seekSpeed = findViewById(R.id.seekSpeed)
        txtSpeed = findViewById(R.id.txtSpeed)
        txtSpeed.text = getString(R.string.speed_format, seekSpeed.progress)

        if (btAdapter == null) {
            txtStatus.text = getString(R.string.bluetooth_not_supported)
            btnConnect.isEnabled = false
        }

        btnConnect.setOnClickListener {
            if (isConnected) {
                disconnectBluetooth()
            } else {
                connectBluetooth()
            }
        }

        btnForward.setOnTouchListener(holdListener('F'))
        btnBack.setOnTouchListener(holdListener('B'))
        btnLeft.setOnTouchListener(holdListener('L'))
        btnRight.setOnTouchListener(holdListener('R'))
        btnStop.setOnClickListener { send('S') }

        btnBelt.setOnCheckedChangeListener { _, on -> send(if (on) 'C' else 'c') }
        btnShred.setOnCheckedChangeListener { _, on -> send(if (on) 'H' else 'h') }
        btnBucket.setOnCheckedChangeListener { _, on -> send(if (on) 'K' else 'k') }

        btnAnchorDn.setOnClickListener { send('A') }
        btnAnchorUp.setOnClickListener { send('U') }

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                txtSpeed.text = getString(R.string.speed_format, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sendSpeed(seekBar.progress)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun connectBluetooth() {
        val adapter = btAdapter ?: run {
            txtStatus.text = getString(R.string.bluetooth_not_supported)
            return
        }

        if (!hasBluetoothConnectPermission()) {
            requestBluetoothConnectPermission()
            return
        }

        if (!adapter.isEnabled) {
            txtStatus.text = getString(R.string.bluetooth_disabled)
            return
        }

        val pairedDevices = adapter.bondedDevices.toList()
        if (pairedDevices.isEmpty()) {
            txtStatus.text = getString(R.string.no_paired_devices)
            return
        }

        val preferredDevice = pairedDevices.firstOrNull { device ->
            val name = device.name.orEmpty()
            name.contains("HC-05", ignoreCase = true) || name.contains("HC05", ignoreCase = true)
        }

        if (preferredDevice != null) {
            connectToDevice(preferredDevice)
        } else {
            showDeviceChooser(pairedDevices)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDeviceChooser(devices: List<BluetoothDevice>) {
        val labels = devices.map { device ->
            val name = device.name?.takeIf { it.isNotBlank() } ?: "Unknown device"
            "$name\n${device.address}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_device)
            .setItems(labels) { _, which ->
                connectToDevice(devices[which])
            }
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        txtStatus.text = getString(R.string.connecting_to, device.name ?: device.address)
        btnConnect.isEnabled = false

        Thread {
            var socket: BluetoothSocket? = null
            try {
                socket = connectRfcomm(device)
                btSocket = socket
                outStream = socket.outputStream
                isConnected = true

                runOnUiThread {
                    txtStatus.text = getString(R.string.connected)
                    txtStatus.setTextColor(COLOR_OK)
                    btnConnect.text = getString(R.string.disconnect)
                    btnConnect.setBackgroundColor(COLOR_OK)
                    btnConnect.isEnabled = true
                }
            } catch (error: IOException) {
                isConnected = false
                closeSocket(socket)
                runOnUiThread {
                    txtStatus.text = getString(R.string.connection_error_with_message, error.message ?: "")
                    txtStatus.setTextColor(COLOR_ERROR)
                    btnConnect.isEnabled = true
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    private fun connectRfcomm(device: BluetoothDevice): BluetoothSocket {
        return try {
            device.createRfcommSocketToServiceRecord(btUuid).also { it.connect() }
        } catch (secureError: IOException) {
            device.createInsecureRfcommSocketToServiceRecord(btUuid).also {
                try {
                    it.connect()
                } catch (insecureError: IOException) {
                    secureError.addSuppressed(insecureError)
                    throw secureError
                }
            }
        }
    }

    private fun disconnectBluetooth() {
        closeSocket(btSocket)
        btSocket = null
        outStream = null
        isConnected = false

        runOnUiThread {
            btnConnect.text = getString(R.string.connect)
            btnConnect.setBackgroundColor(COLOR_CONNECT)
            btnConnect.isEnabled = true
            txtStatus.text = getString(R.string.disconnected)
            txtStatus.setTextColor(COLOR_ERROR)
        }
    }

    private fun send(cmd: Char) {
        if (!isConnected || outStream == null) {
            txtStatus.text = getString(R.string.not_connected)
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            outStream?.write(cmd.code)
            outStream?.flush()
            txtStatus.text = getString(R.string.sent_command, cmd)
        } catch (error: IOException) {
            txtStatus.text = getString(R.string.send_error_with_message, error.message ?: "")
            disconnectBluetooth()
        }
    }

    private fun sendSpeed(speed: Int) {
        if (!isConnected || outStream == null) {
            txtStatus.text = getString(R.string.not_connected)
            return
        }

        val safeSpeed = speed.coerceIn(0, 100)
        try {
            outStream?.write(byteArrayOf('V'.code.toByte(), safeSpeed.toByte()))
            outStream?.flush()
            txtStatus.text = getString(R.string.sent_speed, safeSpeed)
        } catch (error: IOException) {
            txtStatus.text = getString(R.string.send_error_with_message, error.message ?: "")
            disconnectBluetooth()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun holdListener(cmdDown: Char): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    send(cmdDown)
                    view.isPressed = true
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    send('S')
                    view.isPressed = false
                    view.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_CONNECT
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_BLUETOOTH_CONNECT) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            connectBluetooth()
        } else {
            txtStatus.text = getString(R.string.bluetooth_permission_denied)
        }
    }

    override fun onDestroy() {
        disconnectBluetooth()
        super.onDestroy()
    }

    private fun closeSocket(socket: BluetoothSocket?) {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 1001
        private const val COLOR_CONNECT = 0xFF2196F3.toInt()
        private const val COLOR_OK = 0xFF4CAF50.toInt()
        private const val COLOR_ERROR = 0xFFF87171.toInt()
    }
}
