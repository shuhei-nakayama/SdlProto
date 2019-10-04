package com.example.sdlproto

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

/**
 * 接続したことのある車両情報を表示するためのクラス
 */
class InformationActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_information_wrapper)

        findViewById<Button>(R.id.btn_show_licnse).setOnClickListener(this)
        _setDisplay()
    }

    /**
     * ※※※※※
     * 接続した車両情報を表示する
     */
    private fun _setDisplay() {
        val prefManager = PrefManager.getInstance(this)
        val json = prefManager.read(resources.getString(R.string.pref_key_vin), EMPTY_STRING)
        val layout = findViewById<LinearLayout>(R.id.info_wrapper)

        if (json?.isEmpty() == true) {
            // 車両との接続データが無ければ、未接続用のメッセージを表示する
            val tv = TextView(this)
            tv.setText(getResources().getString(R.string.never_connected_to_vehicle))
            tv.setPadding(PADDING_OTHER, PADDING_TOP, PADDING_OTHER, PADDING_OTHER)
            tv.gravity = Gravity.CENTER
            layout.addView(tv)
        } else {
            val gson = Gson()
            val arrayList =
                gson.fromJson<ArrayList<String>>(json, object : TypeToken<ArrayList<String>>() { }.type)
            for (s in arrayList) {
                // Vehicle形式で保存したjsonデータを復元する
                val vehicle = gson.fromJson(prefManager.read(s, EMPTY_STRING), Vehicle::class.java)
                // 表示中のviewに表示用のテンプレートを追加
                val view = layoutInflater.inflate(R.layout.activity_information_body, null)
                layout.addView(view)
                // テンプレートに値を反映
                (view.findViewById(R.id.txt_label_vin) as TextView).text = vehicle.vin
                (view.findViewById(R.id.txt_label_maker) as TextView).text = (vehicle.maker)
                (view.findViewById(R.id.txt_label_model) as TextView).text = (vehicle.model)
                (view.findViewById(R.id.txt_label_year) as TextView).text = (vehicle.modelYear)
                (view.findViewById(R.id.txt_label_create_at) as TextView).text = (vehicle.createAt)
                (view.findViewById(R.id.txt_label_update_at) as TextView).text = (vehicle.updateAt)
                (view.findViewById(R.id.txt_label_fuel) as TextView).text = vehicle.fuelLevel
                (view.findViewById(R.id.txt_label_tire_front_left) as TextView).text = (
                    vehicle.tireMap[resources.getString(R.string.tire_front_left)]
                )
                (view.findViewById(R.id.txt_label_tire_front_right) as TextView).text = (
                    vehicle.tireMap[resources.getString(R.string.tire_front_right)]
                )
                (view.findViewById(R.id.txt_label_tire_rear_left) as TextView).text = (
                    vehicle.tireMap[resources.getString(R.string.tire_rear_left)]
                )
                (view.findViewById(R.id.txt_label_tire_rear_right) as TextView).text = (
                    vehicle.tireMap[resources.getString(R.string.tire_rear_right)]
                )
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_show_licnse -> {
                val i = Intent(this, OssLicensesMenuActivity::class.java)
                startActivity(i)
            }
            else -> {
            }
        }
    }

    companion object {

        private val PADDING_TOP = 100
        private val PADDING_OTHER = 0
        private val EMPTY_STRING = ""
    }
}
