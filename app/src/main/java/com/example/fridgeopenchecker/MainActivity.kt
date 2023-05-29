package com.example.fridgeopenchecker

import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.*


class MainActivity : AppCompatActivity(), View.OnClickListener {

    // BLE adapter
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mDeviceAddress = ""
    private var mBluetoothGatt: BluetoothGatt? = null // Gattサービスの検索、キャラスタリスティックの読み書き
    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

    // GUIアイテム
    private lateinit var mButtonConnect: Button     // 接続ボタン
    private lateinit var mButtonDisconnect: Button  // 切断ボタン
    private lateinit var mButtonBuzzerStart: Button
    private lateinit var mButtonBuzzerStop: Button

    // BluetoothGattコールバック
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        // 接続状態変更（connectGatt()の結果として呼ばれる。）
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (BluetoothGatt.GATT_SUCCESS != status) {
                return
            }
            if (BluetoothProfile.STATE_CONNECTED == newState) {    // 接続完了
                mBluetoothGatt!!.discoverServices()
                runOnUiThread { // GUIアイテムの有効無効の設定
                    // 切断ボタンを有効にする
                    mButtonDisconnect.isEnabled = true
                }
                return
            }
            if (BluetoothProfile.STATE_DISCONNECTED == newState) {    // 切断完了（接続可能範囲から外れて切断された）
                // 接続可能範囲に入ったら自動接続するために、mBluetoothGatt.connect()を呼び出す。
                mBluetoothGatt!!.connect()
                runOnUiThread {
                    mButtonBuzzerStart.isEnabled = false
                    mButtonBuzzerStop.isEnabled = false
                }
                return
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            println("onCharacteristicRead")
            super.onCharacteristicRead(gatt, characteristic, status)
            if (BluetoothGatt.GATT_SUCCESS != status) {
                return
            }

            if (characteristic.uuid == UUID_UART_TX) {
                val strChara = String(characteristic.getValue())
                println("Read from TX line: $strChara")
                runOnUiThread {
                    (findViewById<View>(R.id.textview_uart_read_result) as TextView).text = strChara
                }
                if (strChara == BUZZER_START) {
                    runOnUiThread {
                        (findViewById<View>(R.id.button_buzzer_start) as Button).isEnabled = false
                        (findViewById<View>(R.id.button_buzzer_stop) as Button).isEnabled = true
                    }
                } else if (strChara == BUZZER_STOP) {
                    runOnUiThread {
                        (findViewById<View>(R.id.button_buzzer_start) as Button).isEnabled = true
                        (findViewById<View>(R.id.button_buzzer_stop) as Button).isEnabled = false
                    }
                }

            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if( BluetoothGatt.GATT_SUCCESS != status ) { return }
            for(service in gatt.services)
            {
                if( ( null == service ) || ( null == service.uuid) )
                {
                    continue
                }
                if (UUID_UART_SERVICE == service.uuid) {
                    //uart service found
                    println("uart service found >> ${service.uuid}")

                    // set notification of UART
                    setCharacteristicNotification(UUID_UART_SERVICE, UUID_UART_TX, true)
                    runOnUiThread {
                        mButtonBuzzerStart.isEnabled = true
                        mButtonBuzzerStop.isEnabled = true
                    }
                }
            }
        }

        /** call when getting notified data */
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            println("onCharacteristicChanged")
            if (characteristic.uuid == UUID_UART_TX) {
                val strChara = String(characteristic.getValue())
                println("Notify from TX line: $strChara")
                runOnUiThread {
                    (findViewById<View>(R.id.textview_uart_read_result) as TextView).text = strChara
                }
                if (strChara == BUZZER_START) {
                    runOnUiThread {
                        (findViewById<View>(R.id.imageview_lock_state) as ImageView).setImageResource(R.drawable.icons_unlock)
                        (findViewById<View>(R.id.button_buzzer_start) as Button).isEnabled = false
                        (findViewById<View>(R.id.button_buzzer_stop) as Button).isEnabled = true
                    }
                } else if (strChara == BUZZER_STOP) {
                    runOnUiThread {
                        (findViewById<View>(R.id.imageview_lock_state) as ImageView).setImageResource(R.drawable.icons_lock)
                        (findViewById<View>(R.id.button_buzzer_start) as Button).isEnabled = true
                        (findViewById<View>(R.id.button_buzzer_stop) as Button).isEnabled = false
                    }
                }
                return
            }
        }

        /** call when writeCharacteristic() success */
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            println("onCharacteristicWrite")
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (BluetoothGatt.GATT_SUCCESS !=  status) { return }
            if (characteristic.uuid == UUID_UART_RX) {
                runOnUiThread {
                    mButtonBuzzerStart.isEnabled = true
                    mButtonBuzzerStop.isEnabled = false
                }
                return
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mButtonConnect = findViewById(R.id.button_connect)
        mButtonDisconnect = findViewById(R.id.button_disconnect)
        mButtonConnect.setOnClickListener(this)
        mButtonDisconnect.setOnClickListener(this)
        mButtonBuzzerStart = findViewById(R.id.button_buzzer_start)
        mButtonBuzzerStart.setOnClickListener(this)
        mButtonBuzzerStop = findViewById(R.id.button_buzzer_stop)
        mButtonBuzzerStop.setOnClickListener(this)


        // check if ble is supported
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.let {
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // get ble adapter
        var bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        if (null == mBluetoothAdapter) {
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()

        // Android端末のBLE有効化機能
        requestBluetoothFeature()

        // GUIアイテムの有効無効の設定
        mButtonConnect.isEnabled = false
        mButtonDisconnect.isEnabled = false
        mButtonBuzzerStart.isEnabled = false
        mButtonBuzzerStop.isEnabled = false

        // デバイスアドレスが空でなければ、接続ボタンを有効にする。
        if (mDeviceAddress.isNotEmpty()) {
            mButtonConnect.isEnabled = true
        }

        // 接続ボタンを押す
        mButtonConnect.callOnClick()
    }

    override fun onPause() {
        super.onPause()
        disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBluetoothGatt != null) {
            mBluetoothGatt?.close()
            mBluetoothGatt = null
        }
    }

    /** check BLE on/off */
    private fun requestBluetoothFeature() {
        if (mBluetoothAdapter!!.isEnabled) {
            return
        }
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)
    }

    override fun onClick(v: View) {
        // 接続切断ボタンの押下時の挙動
        if (mButtonConnect.id == v.id) {
            mButtonConnect.isEnabled = false // 接続ボタンの無効化（連打対策）
            connect() // 接続
            return
        }
        if (mButtonDisconnect.id == v.id) {
            mButtonDisconnect.isEnabled = false // 切断ボタンの無効化（連打対策）
            disconnect() // 切断
            return
        }

        // Buzzer setting by UART
        if (mButtonBuzzerStart.id == v.id && v.isEnabled) {
            mButtonBuzzerStart.isEnabled = false
            mButtonBuzzerStop.isEnabled = false
            writeUartCharacteristic(UUID_UART_SERVICE, UUID_UART_RX, BUZZER_START)
            return
        }
        if (mButtonBuzzerStop.id == v.id && v.isEnabled) {
            mButtonBuzzerStart.isEnabled = false
            mButtonBuzzerStop.isEnabled = false
            writeUartCharacteristic(UUID_UART_SERVICE, UUID_UART_RX, BUZZER_STOP)
        }
    }

    private var enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Callback function when checking BLE is enabled or not
        if (result.resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private var connectDeviceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Callback function when connecting a devise
        val strDeviceName: String?
        val data: Intent? = result.data
        if (result.resultCode == RESULT_OK) {
            strDeviceName = data?.getStringExtra(DeviceListActivity.EXTRAS_DEVICE_NAME)!!
            mDeviceAddress = data.getStringExtra(DeviceListActivity.EXTRAS_DEVICE_ADDRESS)!!
        } else {
            strDeviceName = ""
            mDeviceAddress = ""
        }
        (findViewById<View>(R.id.textview_devicename) as TextView).text = strDeviceName
        (findViewById<View>(R.id.textview_deviceaddress) as TextView).text = mDeviceAddress
        (findViewById<View>(R.id.textview_uart_read_result) as TextView).text = ""
    }

    // オプションメニュー作成時の処理
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return true
    }

    // オプションメニューのアイテム選択時の処理
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuitem_search -> {
                val deviceListActivityIntent = Intent(this, DeviceListActivity::class.java)
                connectDeviceLauncher.launch(deviceListActivityIntent)
                return true
            }
        }
        return false
    }

    // 接続
    private fun connect() {
        if (mDeviceAddress == "") { return }
        if (mBluetoothGatt != null) { return }
        val device = mBluetoothAdapter!!.getRemoteDevice(mDeviceAddress)
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback)
    }

    // 切断
    private fun disconnect() {
        if (null == mBluetoothGatt) { return }
        // 切断
        //   mBluetoothGatt.disconnect()ではなく、mBluetoothGatt.close()しオブジェクトを解放する。
        //   理由：「ユーザーの意思による切断」と「接続範囲から外れた切断」を区別するため。
        //   ①「ユーザーの意思による切断」は、mBluetoothGattオブジェクトを解放する。再接続は、オブジェクト構築から。
        //   ②「接続可能範囲から外れた切断」は、内部処理でmBluetoothGatt.disconnect()処理が実施される。
        //     切断時のコールバックでmBluetoothGatt.connect()を呼んでおくと、接続可能範囲に入ったら自動接続する。
        mBluetoothGatt!!.close()
        mBluetoothGatt = null
        // GUIアイテムの有効無効の設定
        // 接続ボタンのみ有効にする
        mButtonConnect.isEnabled = true
        mButtonDisconnect.isEnabled = false
        mButtonBuzzerStart.isEnabled = false
        mButtonBuzzerStop.isEnabled = false
    }

    // キャラクタリスティックの読み込み
    private fun readCharacteristic(uuidService: UUID, uuidCharacteristic: UUID) {
        mBluetoothGatt ?: return
        val bleChar = mBluetoothGatt!!.getService(uuidService).getCharacteristic(uuidCharacteristic)
        mBluetoothGatt!!.readCharacteristic(bleChar)
    }

    // キャラクタリスティックの通知設定
    private fun setCharacteristicNotification(uuiService: UUID, uuidCharacteristic: UUID, isEnabled: Boolean) {
        mBluetoothGatt ?: return
        val bleChar = mBluetoothGatt!!.getService(uuiService).getCharacteristic(uuidCharacteristic)
        mBluetoothGatt!!.setCharacteristicNotification(bleChar, isEnabled)
        val descriptor = bleChar.getDescriptor(UUID_NOTIFY)
        descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        mBluetoothGatt!!.writeDescriptor(descriptor)
    }

    private fun writeCharacteristic(uuidService: UUID, uuidCharacteristic: UUID, string: String) {
        mBluetoothGatt ?: return
        val bleChar = mBluetoothGatt!!.getService(uuidService).getCharacteristic(uuidCharacteristic)
        bleChar.setValue(string.toInt(), BluetoothGattCharacteristic.FORMAT_SINT16, 0)
        mBluetoothGatt!!.writeCharacteristic(bleChar)
    }

    private fun writeUartCharacteristic(uuidService: UUID, uuidCharacteristic: UUID, string: String) {
        mBluetoothGatt ?: return
        val bleChar = mBluetoothGatt!!.getService(uuidService).getCharacteristic(uuidCharacteristic)
        bleChar.setValue(string + "\n")
        mBluetoothGatt!!.writeCharacteristic(bleChar)
    }

    // 定数（Bluetooth LE Gatt UUID）
    companion object {
        // uart service
        private val UUID_UART_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val UUID_UART_TX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val UUID_UART_RX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        // for Notification
        private val UUID_NOTIFY: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")   // 固定

        private const val BUZZER_START = "BUZZER START"
        private const val BUZZER_STOP = "BUZZER STOP"
    }
}