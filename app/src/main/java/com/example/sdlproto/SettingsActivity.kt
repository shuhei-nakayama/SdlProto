package com.example.sdlproto

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * SDL Coreで利用する通知項目の設定を行うためのクラス
 */
class SettingsActivity : AppCompatActivity(), View.OnClickListener {

    private var et1: EditText? = null
    private var et2: EditText? = null
    private var et3: EditText? = null
    private var et4: EditText? = null
    private var et5: EditText? = null
    private var seekBar1: SeekBar? = null
    private var seekBar2: SeekBar? = null
    private var seekBar3: SeekBar? = null
    private var seekBar4: SeekBar? = null
    private var seekBar5: SeekBar? = null
    private var aSwitch1: Switch? = null
    private var aSwitch2: Switch? = null
    private var aSwitch3: Switch? = null
    private var aSwitch4: Switch? = null
    private var aSwitch5: Switch? = null
    private var tSwitch: Switch? = null
    private var hSwitchOn: Switch? = null
    private var hSwitchOff: Switch? = null

    private var prefManager: PrefManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefManager = PrefManager.getInstance(this.applicationContext)

        // binding
        aSwitch1 = findViewById(R.id.seekSwitch1)
        aSwitch2 = findViewById(R.id.seekSwitch2)
        aSwitch3 = findViewById(R.id.seekSwitch3)
        aSwitch4 = findViewById(R.id.seekSwitch4)
        aSwitch5 = findViewById(R.id.seekSwitch5)
        aSwitch1!!.setOnClickListener(this)
        aSwitch2!!.setOnClickListener(this)
        aSwitch3!!.setOnClickListener(this)
        aSwitch4!!.setOnClickListener(this)
        aSwitch5!!.setOnClickListener(this)

        seekBar1 = findViewById(R.id.seekBar1)
        seekBar2 = findViewById(R.id.seekBar2)
        seekBar3 = findViewById(R.id.seekBar3)
        seekBar4 = findViewById(R.id.seekBar4)
        seekBar5 = findViewById(R.id.seekBar5)
        seekBar1!!.setOnSeekBarChangeListener(_changeSeekBar())
        seekBar2!!.setOnSeekBarChangeListener(_changeSeekBar())
        seekBar3!!.setOnSeekBarChangeListener(_changeSeekBar())
        seekBar4!!.setOnSeekBarChangeListener(_changeSeekBar())
        seekBar5!!.setOnSeekBarChangeListener(_changeSeekBar())

        et1 = findViewById(R.id.seekText1)
        et2 = findViewById(R.id.seekText2)
        et3 = findViewById(R.id.seekText3)
        et4 = findViewById(R.id.seekText4)
        et5 = findViewById(R.id.seekText5)
        et1!!.addTextChangedListener(ChangeEditText(et1!!))
        et2!!.addTextChangedListener(ChangeEditText(et2!!))
        et3!!.addTextChangedListener(ChangeEditText(et3!!))
        et4!!.addTextChangedListener(ChangeEditText(et4!!))
        et5!!.addTextChangedListener(ChangeEditText(et5!!))

        tSwitch = findViewById(R.id.tireSwitch)
        hSwitchOn = findViewById(R.id.lightOnSwitch)
        hSwitchOff = findViewById(R.id.lightOffSwitch)
        tSwitch!!.setOnClickListener(this)
        hSwitchOn!!.setOnClickListener(this)
        hSwitchOff!!.setOnClickListener(this)

        // 初期設定
        et1!!.setText(prefManager!!.getPrefByStr(R.id.seekText1, SEEK_1_DEF))
        et2!!.setText(prefManager!!.getPrefByStr(R.id.seekText2, SEEK_2_DEF))
        et3!!.setText(prefManager!!.getPrefByStr(R.id.seekText3, SEEK_3_DEF))
        et4!!.setText(prefManager!!.getPrefByStr(R.id.seekText4, SEEK_4_DEF))
        et5!!.setText(prefManager!!.getPrefByStr(R.id.seekText5, SEEK_5_DEF))

        aSwitch1!!.setChecked(prefManager!!.getPrefByBool(R.id.seekSwitch1, true)!!)
        aSwitch2!!.setChecked(prefManager!!.getPrefByBool(R.id.seekSwitch2, true)!!)
        aSwitch3!!.setChecked(prefManager!!.getPrefByBool(R.id.seekSwitch3, true)!!)
        aSwitch4!!.setChecked(prefManager!!.getPrefByBool(R.id.seekSwitch4, true)!!)
        aSwitch5!!.setChecked(prefManager!!.getPrefByBool(R.id.seekSwitch5, true)!!)
        tSwitch!!.setChecked(prefManager!!.getPrefByBool(R.id.tireSwitch, true)!!)
        hSwitchOn!!.setChecked(prefManager!!.getPrefByBool(R.id.lightOnSwitch, true)!!)
        hSwitchOff!!.setChecked(prefManager!!.getPrefByBool(R.id.lightOffSwitch, true)!!)

        _changeEnable(aSwitch1!!.isChecked, R.id.seekSwitch1, seekBar1, et1)
        _changeEnable(aSwitch2!!.isChecked, R.id.seekSwitch2, seekBar2, et2)
        _changeEnable(aSwitch3!!.isChecked, R.id.seekSwitch3, seekBar3, et3)
        _changeEnable(aSwitch4!!.isChecked, R.id.seekSwitch4, seekBar4, et4)
        _changeEnable(aSwitch5!!.isChecked, R.id.seekSwitch5, seekBar5, et5)
    }

    /**
     * ※※※※※
     * EditTextの値の変更をWatchし、紐付くseekbarを更新する
     */
    inner class ChangeEditText(private val view: EditText) : TextWatcher {

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable) {
            try {
                val inputVal = Integer.valueOf(s.toString())
                if (inputVal < SEEK_MIN) {
                    Toast.makeText(
                        getApplicationContext(),
                        getResources().getString(R.string.setting_error),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    prefManager!!.setPrefByStr(view.id, s.toString())
                    _updateSeekBar(view.id, inputVal)
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(
                    getApplicationContext(),
                    getResources().getString(R.string.setting_error),
                    Toast.LENGTH_SHORT
                ).show()
                e.printStackTrace()
            }

        }
    }

    /**
     * ※※※※※
     * EditTextで入力のあった値で、紐付くSeekBarを更新する
     * @param id EditTextの一意のID(R.id.xxx)値
     * @param progress SeekBarに設定する、入力値
     */
    private fun _updateSeekBar(id: Int, progress: Int) {
        var progress = progress
        progress = _validateSeekBar(progress)
        when (id) {
            R.id.seekText1 -> {
                seekBar1!!.refreshDrawableState()
                seekBar1!!.progress = progress
            }
            R.id.seekText2 -> {
                seekBar2!!.refreshDrawableState()
                seekBar2!!.progress = progress
            }
            R.id.seekText3 -> {
                seekBar3!!.refreshDrawableState()
                seekBar3!!.progress = progress
            }
            R.id.seekText4 -> {
                seekBar4!!.refreshDrawableState()
                seekBar4!!.progress = progress
            }
            R.id.seekText5 -> {
                seekBar5!!.refreshDrawableState()
                seekBar5!!.progress = progress
            }
        }
    }

    /**
     * ※※※※※
     * SeelBarに設定するための許容範囲値内の値を返却する
     * @param progress 入力のあった値
     * @return 有効な値
     */
    private fun _validateSeekBar(progress: Int): Int {
        var progress = progress
        if (progress > SEEK_MAX) {
            progress = SEEK_MAX
        } else if (progress < SEEK_MIN) {
            progress = SEEK_MIN
        }
        return progress
    }

    /**
     * ※※※※※
     * SeekBarの変更を検知する
     */
    private inner class _changeSeekBar : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                // ユーザ自身による変更時のみ許容する
                // (EditText変更時にSeekbarをプログラム上から変更するので、そこからの変更は無視する)
                _updateTextValue(seekBar.id, progress)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }

    /**
     * ※※※※※
     * SeekBarの変更から、紐付くEditTextを更新する
     * @param id SeekBarの一意のID(R.id.xxx)値
     * @param progress EditTextに設定する、入力値
     */
    private fun _updateTextValue(id: Int, progress: Int) {
        var progress = progress
        progress = _validateSeekBar(progress)
        when (id) {
            R.id.seekBar1 -> et1!!.setText(progress.toString())
            R.id.seekBar2 -> et2!!.setText(progress.toString())
            R.id.seekBar3 -> et3!!.setText(progress.toString())
            R.id.seekBar4 -> et4!!.setText(progress.toString())
            R.id.seekBar5 -> et5!!.setText(progress.toString())
        }
    }

    /**
     * switchのOn/Offを切り替えた際の処理
     * @param v switchのview
     */
    override fun onClick(v: View) {
        var sb: SeekBar? = null
        var et: EditText? = null
        val isChecked = (v as Switch).isChecked

        when (v.getId()) {
            R.id.seekSwitch1, R.id.seekSwitch2, R.id.seekSwitch3, R.id.seekSwitch4, R.id.seekSwitch5 -> {
                val row = v.getParent() as ViewGroup
                for (itemPos in 0 until row.childCount) {
                    val view = row.getChildAt(itemPos)
                    if (view is SeekBar) {
                        sb = view
                    } else if (view is EditText) {
                        et = view
                    }
                }
            }
            R.id.lightOffSwitch, R.id.lightOnSwitch, R.id.tireSwitch -> {
            }
            else -> {
            }
        }
        _changeEnable(isChecked, v.getId(), sb, et)
    }

    /**
     * ※※※※※
     * resourceIdで指定されたswitchのチェック状態(isChecked)に応じて、seekbarとedittextの活性化/非活性化を切り替える
     * @param isChecked チェックのOn/Off状態
     * @param resourceId switchのリソースID
     * @param sb resourceIdに紐づくseekbar
     * @param et resourceIdに紐づくedittext
     */
    private fun _changeEnable(
        isChecked: Boolean,
        resourceId: Int, sb: SeekBar?, et: EditText?
    ) {
        prefManager!!.setPrefByBool(resourceId, isChecked)
        if (sb != null) {
            sb.isEnabled = isChecked
        }
        if (et != null) {
            et.isEnabled = isChecked
        }
    }

    companion object {

        private val LOG_TAG = "LOG:[SettingsActivity]"
        // seekbarの最大/小値
        private val SEEK_MAX = 99
        private val SEEK_MIN = 1
        // seekbarの初期値
        val SEEK_1_DEF = "50"
        val SEEK_2_DEF = "40"
        val SEEK_3_DEF = "30"
        val SEEK_4_DEF = "20"
        val SEEK_5_DEF = "10"
    }
}
