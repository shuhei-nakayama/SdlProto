package com.example.sdlproto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartdevicelink.transport.TransportConstants

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var vehicleLight: ImageView? = null
    private var receiver: UpdateReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Constants.LOG_TAG, "MainActivity.onCreate:in")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        vehicleLight = findViewById(R.id.vehicle_light)
        vehicleLight!!.imageAlpha = 0

        _initPreference()

        findViewById<View>(R.id.btn_connect).setOnClickListener(this)
        findViewById<View>(R.id.btn_settings).setOnClickListener(this)
        findViewById<View>(R.id.btn_info).setOnClickListener(this)

        // SdlServiceからのレスポンスを取得
        receiver = UpdateReceiver()
        val filter = IntentFilter()
        filter.addAction(resources.getString(R.string.action_service_close))
        registerReceiver(receiver, filter)

        Log.d(Constants.LOG_TAG, "MainActivity.onCreate:out")
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    /**
     * SdlServiceからのレスポンスを取得するためのレシーバー
     */
    private inner class UpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vehicleLight!!.imageAlpha = 0
            val extras = intent.extras
            isFirstConnect = extras!!.getBoolean(resources.getString(R.string.is_first_connect))
        }
    }

    /**
     * ※※※※※
     * SharedPreferencesに初期値を格納する
     */
    private fun _initPreference() {
        val prefManager = PrefManager.getInstance(this)
        val res = resources
        if (!prefManager.read(res.getString(R.string.pref_key_init_flg), false)!!) {
            prefManager.write(res.getString(R.string.pref_key_init_flg), true)
            prefManager.write(res.getResourceEntryName(R.id.lightOffSwitch), true)
            prefManager.write(res.getResourceEntryName(R.id.lightOnSwitch), true)
            prefManager.write(res.getResourceEntryName(R.id.tireSwitch), true)
            prefManager.write(res.getResourceEntryName(R.id.seekSwitch1), true)
            prefManager.write(res.getResourceEntryName(R.id.seekSwitch2), true)
            prefManager.write(res.getResourceEntryName(R.id.seekSwitch3), true)
            prefManager.write(res.getResourceEntryName(R.id.seekSwitch4), true)
            prefManager.write(res.getResourceEntryName(R.id.seekSwitch5), true)
            prefManager.write(res.getResourceEntryName(R.id.seekText1), SettingsActivity.SEEK_1_DEF)
            prefManager.write(res.getResourceEntryName(R.id.seekText2), SettingsActivity.SEEK_2_DEF)
            prefManager.write(res.getResourceEntryName(R.id.seekText3), SettingsActivity.SEEK_3_DEF)
            prefManager.write(res.getResourceEntryName(R.id.seekText4), SettingsActivity.SEEK_4_DEF)
            prefManager.write(res.getResourceEntryName(R.id.seekText5), SettingsActivity.SEEK_5_DEF)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_connect -> _connectToSdl()
            R.id.btn_settings -> {
                val i = Intent(this, SettingsActivity::class.java)
                startActivity(i)
            }
            else -> {
            }
        }
    }

    /**
     * ※※※※※
     * SDL Coreに対して接続を試みる
     */
    private fun _connectToSdl() {
        // *** about BuildCounfig ***
        // MBT - Multiplexing Bluetooth
        // LBT - Legacy Bluetooth
        //   ->https://www.bluetooth.com/ja-jp/specifications/bluetooth-core-specification/legacy-specifications
        // TCP - Transmission Control Protocol
        // USB - Universal Serial Bus
        // BuildConfigについてはbuild.gradleを参照してください。
        if (BuildConfig.TRANSPORT == "MBT") {
            // 接続確認を行い(問題なければ)SdlReceiver.onSdlEnabled()が呼ばれます。
            // queryForConnectedServiceは内部処理でBTを利用するので、
            // Android Studioのエミュレータでは基本的にテストができません。
            //SdlReceiver.queryForConnectedService(this);
        } else if (BuildConfig.TRANSPORT == "TCP" || BuildConfig.TRANSPORT == "LBT") {
            Log.d("[Log:[MainActivity]]", "onCreate")
            val proxyIntent = Intent(this, SdlService::class.java)
            proxyIntent.putExtra("isFirstConnect", isFirstConnect)
            proxyIntent.putExtra(TransportConstants.FORCE_TRANSPORT_CONNECTED, false)

            // Android Oreo(API 26)からはサービスの挙動に変更・制限が発生しているため、
            // OSバージョンに合わせてサービスの起動方法を変更します。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // minSdkVersion >= 26
                // startForegroundService()を呼び出し、起動されたサービスは、
                // 5秒以内にService.startForeground()を呼び出さないとRemoteServiceExceptionが発生します。
                startForegroundService(proxyIntent)
                vehicleLight!!.imageAlpha = 150
            } else {
                // minSdkVersion < 26
                Toast.makeText(this, "ご利用中の端末は対象外となっております。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {

        private var isFirstConnect = true
    }
}
