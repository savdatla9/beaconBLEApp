package com.example.beaconbleapp

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.Manifest
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.le.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Color.*
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.ranging.RangingSession
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beaconbleapp.components.BlDevice
import com.example.beaconbleapp.components.ViewSeen
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executor
//import androidx.core.uwb.UwbManagerCompat
//import androidx.core.uwb.RangingParameters
//import androidx.core.uwb.UwbAddress

class MainActivity : ComponentActivity() {
    private var isScanning = false
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var adapter: ViewSeen
    private val devices = mutableListOf<BlDevice>()
    private lateinit var closePreviewButton: Button
    private var cameraImageUri: Uri? = null
    private lateinit var imagePreview: ImageView
    private var bluetoothGatt: BluetoothGatt? = null
    private var activeConnectionName: String? = null
    private val seenDevices = HashSet<String>()
//    private var rangingSession: androidx.core.uwb.R? = null
//
//    private val uwbManager by lazy {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            UwbManagerCompat.create(this)
//        } else {
//            null
//        }
//    }
//
//    private fun isUwbSupported(): Boolean {
//        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
//                uwbManager?.isAvailable == true
//    }

//    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
//    private fun startUwbRanging(peerAddress: ByteArray) {
//
//        if (!isUwbSupported()) {
//            Toast.makeText(this, "UWB not supported", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val params = RangingParameters(
//            RangingParameters.CONFIG_UNICAST_DS_TWR,
//            1001,
//            listOf(
//                UwbAddress(peerAddress)
//            ),
//            null,
//            25,
//        )
//
//        uwbManager!!.openRangingSession(
//            params,
//            ContextCompat.getMainExecutor(this),
//            object : RangingSession.Callback {
//
//                override fun onRangingResult(result: RangingSession.RangingResult) {
//                    val distance = result.distance?.value
//                    Toast.makeText(
//                        this@MainActivity,
//                        "UWB distance: ${"%.2f".format(distance)} m",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//
//                override fun onRangingFailure(reason: Int) {
//                    Toast.makeText(
//                        this@MainActivity,
//                        "UWB error: $reason",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//        )
//    }

//    private var uwbSession: RangingSession? = null
//
//    private fun stopUwbRanging() {
//        uwbSession?.close()
//        uwbSession = null
//    }

    private val appImageDir: File by lazy {
        File(filesDir, "images").apply { if (!exists()) mkdirs() }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted ->
            if (granted) {
                openSystemCameraInternal()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) {
            success ->
            if (success && cameraImageUri != null) {
                imagePreview.setImageURI(cameraImageUri)
                imagePreview.visibility = View.VISIBLE
                closePreviewButton.visibility = View.VISIBLE
                Toast.makeText(
                    this,
                    "Image captured",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Capture cancelled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                saveImageToAppFolder(it)
            }
        }

    private val blePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.values.all { it }
            if (granted) {
                startScan()
            } else {
                Toast.makeText(
                    this,
                    "BLE permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val address = gatt.device.address

            runOnUiThread {
                devices.find { it.address == address }?.let { device ->
                    device.isConnecting = false
                    device.isConnected = (newState == BluetoothProfile.STATE_CONNECTED)
                    adapter.notifyDataSetChanged()
                }
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close()
                bluetoothGatt = null
            }
        }
    }

    private fun connectToDevice(device: BlDevice) {
        val adapter = getDefaultAdapter()

        device.isConnecting = true
//        adapter.notifyDataSetChanged()

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.bluetoothDevice.connectGatt(
                this,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.bluetoothDevice.connectGatt(
                this,
                false,
                gattCallback
            )
        }

    }

    private fun disconnectDevice() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        activeConnectionName = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val statusBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                80
            )
            setBackgroundColor(BLACK)
        }

        val cameraButton = Button(this).apply {
            text = "Open Camera"
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            layoutParams = centeredParams(24)
            setOnClickListener { openSystemCamera() }
        }

        val uploadButton = Button(this).apply {
            text = "Upload Image"
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            layoutParams = centeredParams(16)
            setOnClickListener {
                imagePickerLauncher.launch(arrayOf("image/*"))
            }
        }

        val scanButton = Button(this).apply {
            text = "B-LE Scan"
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            layoutParams = centeredParams(16)
            setOnClickListener {
                if (!isScanning) {
                    if (hasBlePermissions()) {
                        startScan()
                        text = "Stop B-LE"
                    } else {
                        requestBlePermissions()
                    }
                } else {
                    stopScan()
                    text = "B-LE Scan"
                }
            }
        }

        imagePreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                450
            ).apply {
                topMargin = 16
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        closePreviewButton = Button(this).apply {
            text = "Close Preview"
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            layoutParams = centeredParams(8)
            visibility = View.GONE

            setOnClickListener {
                hideImagePreview()
            }
        }

        adapter = ViewSeen(devices) { device ->
            if (isScanning) stopScan()

            // Prevent double taps
            if (device.isConnecting) return@ViewSeen

            // DISCONNECT
            if (device.isConnected) {
                Toast.makeText(
                    this,
                    "Disconnecting ${device.name}",
                    Toast.LENGTH_SHORT
                ).show()
                bluetoothGatt?.disconnect()
                return@ViewSeen
            }

            // CONNECT
            Toast.makeText(
                this,
                "Connecting ${device.name}",
                Toast.LENGTH_SHORT
            ).show()
            connectToDevice(device)
        }

        recyclerView.adapter = adapter

        root.addView(statusBar)
        root.addView(cameraButton)
        root.addView(uploadButton)
        root.addView(imagePreview)
        root.addView(closePreviewButton)
        root.addView(scanButton)
        root.addView(recyclerView)

        setContentView(root)

        val manager =
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        val btAdapter = manager.adapter

        if (!btAdapter.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        bluetoothLeScanner = btAdapter.bluetoothLeScanner

        enableEdgeToEdge()
    }

    private fun openSystemCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openSystemCameraInternal()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openSystemCameraInternal() {
        cameraImageUri = createCameraImageUri()
        cameraLauncher.launch(cameraImageUri)
    }

    private fun createCameraImageUri(): Uri {
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "IMG_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/BeaconBLE"
            )
        }

        return contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )!!
    }

    private fun saveImageToAppFolder(uri: Uri) {
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        val destFile = File(appImageDir, fileName)

        val input: InputStream? = contentResolver.openInputStream(uri)
        val output = FileOutputStream(destFile)

        input?.copyTo(output)

        input?.close()
        output.close()

        imagePreview.setImageURI(Uri.fromFile(destFile))
        imagePreview.visibility = View.VISIBLE
        closePreviewButton.visibility = View.VISIBLE

        Toast.makeText(
            this,
            "Image saved in app folder",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startScan() {
        if (isScanning) return

        devices.clear()
        seenDevices.clear()
        adapter.notifyDataSetChanged()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner.startScan(null, settings, scanCallback)
        isScanning = true
    }

    private fun stopScan() {
        bluetoothLeScanner.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: return
            val address = device.address

            synchronized(seenDevices) {
                if (seenDevices.contains(name)) return
                seenDevices.add(name)
            }

            runOnUiThread {
                adapter.notifyDataSetChanged()

                devices.add(
                    BlDevice(
                        name = name,
                        address = address,
                        bluetoothDevice = device,
                        rssi = result.rssi
                    )
                )

                adapter.notifyItemInserted(devices.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "BLE Scan failed: $errorCode",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==
                    PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==
                    PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            blePermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    private fun centeredParams(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        topMargin = top
    }

    private fun hideImagePreview() {
        imagePreview.setImageDrawable(null)
        imagePreview.visibility = View.GONE
        closePreviewButton.visibility = View.GONE
    }
}