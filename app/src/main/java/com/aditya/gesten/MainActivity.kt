package com.aditya.gesten

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.aditya.gesten.ui.theme.GestenTheme

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID


val PERMISSIONS_STORAGE: Array<String?> = arrayOf<String?>(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_PRIVILEGED
)
val PERMISSIONS_LOCATION: Array<String?> = arrayOf<String?>(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_PRIVILEGED
)





@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() , TextToSpeech.OnInitListener{
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val sppUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        var slf = this
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
        setContent {
            GestenTheme {
//                LaunchedEffect(Unit) {
//                    val file = File(ctx.filesDir, modelFileLocation)
//                    modelFileLocation = file.absolutePath
//                    gc.load()
//                    parseFile(ctx)
//                }


                Thread(Runnable {
                    while (true) {
                        var newDisplayText = predict()
                        if(newDisplayText != displayText){
                            displayText = newDisplayText
                            speak(displayText)
                        }
                        Thread.sleep(2000)
                    }
                }
                ).start()


                Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                    TopAppBar(
                        title = {
                            Column {
                            Spacer(modifier = Modifier.height(8.dp)) // like <br> but proper Compose way
                            Text("Gesten")
                                }
                                },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        modifier = Modifier.height(60.dp)
                    )
                }) { innerPadding ->
                    var selectedTabIndex by remember { mutableIntStateOf(0) }
                    val tabs = listOf("Home", "Settings", "Debug")

                    Column {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier
                                .fillMaxWidth()

                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { Text(title) },
                                    modifier = Modifier
                                        .padding(innerPadding)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                )
                            }
                        }

                        when (selectedTabIndex) {
                            0 -> HomeScreen()
                            1 -> DataScreen(slf)
                            2 -> DebugScreen()
                        }
                    }
                }
            }
        }

        // Initialize TTS
        tts = TextToSpeech(this, this)
    }
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
        try {
            socket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        var connected = false

        try {
            while (!connected) {
                socket = device.createRfcommSocketToServiceRecord(sppUUID)
                bluetoothAdapter?.cancelDiscovery() // important
                socket?.connect()
                connected = true
            }
        } catch (e: IOException) {
            e.printStackTrace()
            socket?.close()
            return
        }

    }
    @SuppressLint("MissingPermission")
    fun connectAndRead(name:String,n:Int = 0) {
        try {
            val device: BluetoothDevice? =
                bluetoothAdapter?.bondedDevices?.firstOrNull { it.name == name }
            if (device == null) {
                bluetoothStatus[n] = 9
                return
            }

            connect(device)

            val inputStream = socket?.inputStream
            while (true) {
                val byteCount = 7 * 4 // 7 floats × 4 bytes each
                val buffer = ByteArray(byteCount)

                val bytes = inputStream?.read(buffer) ?: 0
                if (bytes == byteCount) {
                    val byteBuffer = ByteBuffer.wrap(buffer)
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

                    val floats = FloatArray(7)
                    for (i in 0 until 7) {
                        floats[i] = byteBuffer.float
                    }

                    if (floats.size == 7) {
                        for (i in n * 7 until (n + 1) * 7) {
                            hand_data = hand_data.copyOf().also { it[i][index_read[n]] = floats[i % 7] }

                        }
                        nameyesy(n)
                        bluetoothStatus = bluetoothStatus.copyOf().also { it[n] = 1 }
                        index_read[n]++
                        index_read[n] %= n_read

                    } else {
                        bluetoothStatus = bluetoothStatus.copyOf().also { it[n] = 99 }
                    }
                } else {
                    bluetoothStatus = bluetoothStatus.copyOf().also { it[n] = -1 }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (_: NullPointerException) {
            return
        }
    }
    private fun checkPermissions() {
        val permission1 =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val permission2 =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
        if (permission1 != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                this,
                PERMISSIONS_STORAGE,
                1
            )
        } else if (permission2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                PERMISSIONS_LOCATION,
                1
            )
        }
    }
    private var tts: TextToSpeech? = null
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
//            val result =
            tts?.language = Locale.US  // Change to Locale("hi", "IN") for Hindi, etc.
//            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) { } else { }
        }
    }
    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }
}

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Main content
        Text(text = displayText)

        // Footer
        Row (
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .background(Color(0xFFF7F7F7))
                .fillMaxWidth()
        ){
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "logo",
                Modifier.width(80.dp)
            )
            Text(
                color = Color.Black,
                text = "by Team BIS",
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}
@Composable
fun DebugScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        if (range.isEmpty())
            return Text("Calibrate")


        Text(manualPredict())

        var i = 0
        for (el in hand_data) {
            Text("${el.average().toInt()}   ${min[i].toInt()} ${max[i].toInt()} ${range[i].toInt()}")
            Text(((el.average() - min[i]) / range[i]).toString())
            Text("  ")
            i++
        }
    }
}

@Composable
fun DataScreen(ctx:MainActivity) {
    Box(
        modifier = Modifier
            .padding(10.dp)               // ~20px margin
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.TopStart
    ) {
        Column (modifier = Modifier.padding(10.dp)){
            Button(onClick = {
                calibrate(getGestureFromData())
            }) {
                Text("Calibrate")
            }
            Row {
                Button(onClick = {
                    if (bluetoothStatus[0] != 1)
                        Thread(Runnable {
                            ctx.connectAndRead("Gesten Left Glove V2", 0)
                        }).start()
                }) { Text("Left: ${bluetoothStatus[0]}") }


                Button(onClick = {
                    if (bluetoothStatus[1] != 1)
                        Thread(Runnable {
                            ctx.connectAndRead("Gesten Right Glove V2", 1)
                        }).start()
                }) { Text("Right: ${bluetoothStatus[1]}") }

            }
            Text(hand_data.map { it.average().toInt() }.joinToString(", "))
            Text(displayText)
        }
    }
}