package com.example.sdlproto

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_extra.*
import java.util.*
import kotlin.collections.ArrayList


class ExtraActivity : AppCompatActivity() {
    private var mTextToSpeech : TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Constants.LOG_TAG, "ExtraActivity.onCreate:in")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extra)

        val onInitListener = TextToSpeech.OnInitListener {
            if (it != TextToSpeech.SUCCESS) return@OnInitListener

            mTextToSpeech?.let { tts ->

                val voiceNames = ArrayList<String>()
                tts.voices?.forEach { voice ->
                    if (voice.name.toUpperCase().indexOf("JA-JP-") > -1) voiceNames.add(voice.name)
//                    ja-jp-x-htm#female_1-local
//                    ja-jp-x-htm#female_2-local
//                    ja-jp-x-htm#female_3-local
//                    ja-jp-x-htm#male_1-local
//                    ja-jp-x-htm#male_2-local
//                    ja-jp-x-htm#male_3-local
//                    ja-jp-x-htm-local
//                    ja-jp-x-htm-network
//                    ja-JP-language
                }

                val adapter = ArrayAdapter(
                    applicationContext,
                    android.R.layout.simple_spinner_dropdown_item,
                    voiceNames.toArray()
                )
                spinner_voice.setAdapter(adapter)

                btn_speech.setOnClickListener(object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        if (tts.isSpeaking) {
                            return
                        }
                        txt_input_tts.text?.let { text ->
                            tts.setSpeechRate(1.0f)
                            tts.setPitch(1.0f)
                            tts.voice = Voice(voiceNames[spinner_voice.selectedIndex], Locale("ja","JP"), Voice.QUALITY_VERY_HIGH, Voice.LATENCY_VERY_HIGH, true, HashSet<String>())
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "messageID")
                        }
                    }
                })
            }
        }
        mTextToSpeech = TextToSpeech(applicationContext, onInitListener)
    }

    override fun onDestroy() {
        mTextToSpeech?.shutdown()

        super.onDestroy()
    }

}