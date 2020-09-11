package com.elstatgroup.elstat.sdklite.sample

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.elstatgroup.elstat.sdk.api.*
import com.elstatgroup.elstat.sdk.api.NexoVerificationResult.NexoVerificationStatus.*
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newFixedThreadPool(10)

    private val mBluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        authAsynchronously()
    }

    private fun authAsynchronously() {
        NexoSync.getInstance().authorize(applicationContext, object: NexoAuthorizationListener {
            override fun onAuthorizationSuccessful() {
                Log.v("NexoSDKLiteSample", "SDK authorized successfully")
                startScanning()
            }

            override fun onError(nexoId: String?, error: NexoError) {
                Log.v("NexoSDKLiteSample", "error: ${error.errorType.name}")
            }
        })
    }

    private fun authSynchronously() {
        executor.execute {
            val result = NexoSync.getInstance().authorize(applicationContext)
            if (result.isSuccess)
                Log.v("NexoSDKLiteSample", "SDK authorized successfully")
            else
                Log.v("NexoSDKLiteSample", "error: ${result.nexoError?.errorType?.name}")
        }
    }

    private fun startScanning() {
        mBluetoothAdapter?.bluetoothLeScanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                executor.execute {
                    result?.device?.let {
                        val verificationResult = NexoSync.getInstance().verifyBeacon(
                            applicationContext, it, result.rssi)
                        handleVerificationResult(it, verificationResult)
                    }
                }
            }

        })
    }

    private fun handleVerificationResult(device: BluetoothDevice, result: NexoVerificationResult) {
        when(result.status) {
            NOT_AUTHORIZED -> Log.v("NexoSDKLiteSample", "unauthorized: ${device.name ?: device.address}")
            AUTHORIZED -> result.nexoId?.let { nexoId ->
                Log.v("NexoSDKLiteSample", "authorized: $nexoId")
                NexoSync.getInstance().syncCooler(applicationContext, nexoId, 3, syncListener)
            }
            ERROR_DURING_VERIFICATION -> result.error?.let { error ->
                Log.v("NexoSDKLiteSample", "error: ${error.errorType.name}")
            }
        }
    }

    private val syncListener = object: NexoSyncListener {

        override fun onSuccess(nexoId: String?, result: String?) {
            Log.v("NexoSDKLiteSample", "$nexoId -> success: $result")
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val service = am.getRunningServices(Integer.MAX_VALUE)
                .firstOrNull { it.process.contains("elstatgroup", true) }
                ?.service?.className
            Log.v("NexoSDKLiteSample", "Running service: $service")
        }

        override fun onError(nexoId: String?, error: NexoError) {
            Log.v("NexoSDKLiteSample", "$nexoId -> error: ${error.errorType.name}")
        }

        override fun onCoolerProgress(nexoId: String?, progress: Float) {
            Log.v("NexoSDKLiteSample", "$nexoId -> progress: ${progress * 100}%")
        }
    }

}


