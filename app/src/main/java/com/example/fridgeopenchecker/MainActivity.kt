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
    private lateinit var mButtonReadChara1 : Button // キャラクタリスティック１の読み込みボタン
    private lateinit var mButtonReadChara2 : Button // キャラクタリスティック２の読み込みボタン
    private lateinit var mCheckBoxNotifyChara1: CheckBox // キャラクタリスティック１の変更通知ON/OFFチェックボックス
    private lateinit var mButtonWrite100ms: Button
    private lateinit var mButtonWrite1000ms: Button

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
                    mButtonReadChara1.isEnabled = false
                    mButtonReadChara2.isEnabled = false
                    mCheckBoxNotifyChara1.isEnabled = false
                    mButtonWrite100ms.isEnabled = false
                    mButtonWrite1000ms.isEnabled = false
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
            super.onCharacteristicRead(gatt, characteristic, status)
            if (BluetoothGatt.GATT_SUCCESS != status) {
                return
            }

            if (characteristic.uuid == UUID_CHARACTERISTIC_PRIVATE1) {
                val intChara = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0)
                val strChara = "${intChara}℃"
                runOnUiThread {
                    (findViewById<View>(R.id.textview_readchara1) as TextView).text = strChara
                }
            }

            if (characteristic.uuid == UUID_CHARACTERISTIC_PRIVATE2) {
                val intChara = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                val strChara = "${intChara}ms"
                runOnUiThread {
                    (findViewById<View>(R.id.textview_readchara2) as TextView).text = strChara
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
                if (UUID_SERVICE_PRIVATE == service.uuid) {
                    runOnUiThread {
                        mButtonReadChara1.isEnabled = true
                        mButtonReadChara2.isEnabled = true
                        mCheckBoxNotifyChara1.isEnabled = true
                        mButtonWrite100ms.isEnabled = true
                        mButtonWrite1000ms.isEnabled = true
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic.uuid == UUID_CHARACTERISTIC_PRIVATE1) {
                val intChara = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0)
                val strChara = "${intChara}℃"
                runOnUiThread {
                    (findViewById<View>(R.id.textview_notifychara1) as TextView).text = strChara
                }
                return
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (BluetoothGatt.GATT_SUCCESS !=  status) { return }
            if (characteristic.uuid == UUID_CHARACTERISTIC_PRIVATE2) {
                runOnUiThread {
                    mButtonWrite100ms.isEnabled = true
                    mButtonWrite1000ms.isEnabled = true
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
        mButtonReadChara1 = findViewById( R.id.button_readchara1 )
        mButtonReadChara1.setOnClickListener( this )
        mButtonReadChara2 = findViewById( R.id.button_readchara2 )
        mButtonReadChara2.setOnClickListener( this )
        mCheckBoxNotifyChara1 = findViewById(R.id.checkbox_notifychara1)
        mCheckBoxNotifyChara1.setOnClickListener(this)
        mButtonWrite100ms = findViewById(R.id.button_write_100ms)
        mButtonWrite100ms.setOnClickListener(this)
        mButtonWrite1000ms = findViewById(R.id.button_write_1000ms)
        mButtonWrite1000ms.setOnClickListener(this)

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
        mButtonReadChara1.isEnabled = false
        mButtonReadChara2.isEnabled = false
        mCheckBoxNotifyChara1.isEnabled = false
        mCheckBoxNotifyChara1.isChecked = false
        mButtonWrite100ms.isEnabled = false
        mButtonWrite1000ms.isEnabled = false

        // デバイスアドレスが空でなければ、接続ボタンを有効にする。
        if (mDeviceAddress != "") {
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
        if (mButtonReadChara1.id == v.id) {
            readCharacteristic(UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE1)
            return
        }
        if (mButtonReadChara2.id == v.id) {
            readCharacteristic(UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE2)
            return
        }
        if (mCheckBoxNotifyChara1.id == v.id) {
            setCharacteristicNotification(UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE1, mCheckBoxNotifyChara1.isChecked)
            return
        }
        if (mButtonWrite100ms.id == v.id) {
            mButtonWrite100ms.isEnabled = false
            mButtonWrite1000ms.isEnabled = false
            writeCharacteristic(UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE2, "100")
            return
        }
        if (mButtonWrite1000ms.id == v.id) {
            mButtonWrite100ms.isEnabled = false
            mButtonWrite1000ms.isEnabled = false
            writeCharacteristic(UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE2, "1000")
            return
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
        (findViewById<View>(R.id.textview_readchara1) as TextView).text = ""
        (findViewById<View>(R.id.textview_readchara2) as TextView).text = ""
        (findViewById<View>(R.id.textview_notifychara1) as TextView).text = ""
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
        var device = mBluetoothAdapter!!.getRemoteDevice(mDeviceAddress)
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
        mButtonReadChara1.isEnabled = false
        mButtonReadChara2.isEnabled = false
        mCheckBoxNotifyChara1.isEnabled = false
        mCheckBoxNotifyChara1.isChecked = false
        mButtonWrite100ms.isEnabled = false
        mButtonWrite1000ms.isEnabled = false
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
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        mBluetoothGatt!!.writeDescriptor(descriptor)
    }

    private fun writeCharacteristic(uuidService: UUID, uuidCharacteristic: UUID, string: String) {
        mBluetoothGatt ?: return
        val bleChar = mBluetoothGatt!!.getService(uuidService).getCharacteristic(uuidCharacteristic)
        bleChar.setValue(string.toInt(), BluetoothGattCharacteristic.FORMAT_SINT16, 0)
        mBluetoothGatt!!.writeCharacteristic(bleChar)
    }

    // 定数（Bluetooth LE Gatt UUID）
    companion object {
        // Private Service
        private val UUID_SERVICE_PRIVATE: UUID = UUID.fromString("E95D6100-251D-470A-A062-FA1922DFA9A8")
        private val UUID_CHARACTERISTIC_PRIVATE1: UUID = UUID.fromString("E95D9250-251D-470A-A062-FA1922DFA9A8")
        private val UUID_CHARACTERISTIC_PRIVATE2: UUID = UUID.fromString("E95D1B25-251D-470A-A062-FA1922DFA9A8")
        // for Notification
        private val UUID_NOTIFY: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")   // 固定
    }
}