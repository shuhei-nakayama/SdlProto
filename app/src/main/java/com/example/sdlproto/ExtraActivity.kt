package com.example.sdlproto

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_extra.*
import kotlinx.android.synthetic.main.activity_settings.view.*
import java.util.*
import kotlin.collections.ArrayList
import android.Manifest.permission.RECORD_AUDIO
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.widget.TextView
import androidx.core.content.ContextCompat


class ExtraActivity : AppCompatActivity() {
    private var mTextToSpeech: TextToSpeech? = null
    private var sr: SpeechRecognizer? = null
    private var isListening = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Constants.LOG_TAG, "ExtraActivity.onCreate:in")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extra)

        // permission チェック
        if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(this, arrayOf(RECORD_AUDIO), 0)
            }
        }

        // Google様のテキスト読み上げ
        val onInitListener = TextToSpeech.OnInitListener {
            if (it != TextToSpeech.SUCCESS) return@OnInitListener

            mTextToSpeech?.let { tts ->
                val voiceNames = ArrayList<String>()
                tts.voices?.forEach { voice ->
                    if (voice.name.toUpperCase().indexOf("JA-JP-") > -1) voiceNames.add(voice.name)
                }

                val adapter = ArrayAdapter(
                    applicationContext,
                    android.R.layout.simple_spinner_dropdown_item,
                    voiceNames.toArray()
                )
                spinner_voice.setAdapter(adapter)

                btn_speech.setOnClickListener{
                    if (tts.isSpeaking) {
                        return@setOnClickListener
                    }
                    txt_input_tts.text?.let { text ->
                        tts.setSpeechRate(1.0f)
                        tts.setPitch(1.0f)
                        tts.voice = Voice(voiceNames[spinner_voice.selectedIndex], Locale("ja","JP"), Voice.QUALITY_VERY_HIGH, Voice.LATENCY_VERY_HIGH, true, HashSet<String>())
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "messageID")
                    }
                }
            }
        }
        mTextToSpeech = TextToSpeech(applicationContext, onInitListener)

        // Google様の音声認識
        btn_listen.setOnClickListener {
            isListening = !isListening

            if (isListening) {
                txt_recognized.text = ""
                (it as TextView).text = "Now Listening...Tap to Stop SpeechRecognizer."
                startListening()
            } else {
                (it as TextView).text = "Tap to Start SpeechRecognizer..."
                stopListening()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isListening) startListening()
    }

    override fun onPause() {
        if (isListening) stopListening()
        super.onPause()
    }

    override fun onDestroy() {
        mTextToSpeech?.shutdown()

        super.onDestroy()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Toast.makeText(applicationContext, "Time to rant!!", Toast.LENGTH_SHORT).show()
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            var reason = ""
            when (error) {
                SpeechRecognizer.ERROR_AUDIO -> reason = "ERROR_AUDIO"
                SpeechRecognizer.ERROR_CLIENT -> reason = "ERROR_CLIENT"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> reason = "ERROR_INSUFFICIENT_PERMISSIONS"
                SpeechRecognizer.ERROR_NETWORK -> reason = "ERROR_NETWORK"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> reason = "ERROR_NETWORK_TIMEOUT"
                SpeechRecognizer.ERROR_NO_MATCH -> reason = "ERROR_NO_MATCH"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> reason = "ERROR_RECOGNIZER_BUSY"
                SpeechRecognizer.ERROR_SERVER -> reason = "ERROR_SERVER"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> reason = "ERROR_SPEECH_TIMEOUT"
            }
            Toast.makeText(applicationContext, reason, Toast.LENGTH_SHORT).show()
            restartListeningService()
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let {
                var resultsString = txt_recognized.text.toString()
                for (i in 0 until it.size) {
                    resultsString += "${it[i]},"
                }
                txt_recognized.text = "${resultsString}。\n"
            }
            restartListeningService()
        }
    }

    private fun startListening() {
        try {
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this)
                if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
                    Toast.makeText(
                        applicationContext, "音声認識が使えません",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                sr!!.setRecognitionListener(listener)
            }
            // インテントの作成
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            // 言語モデル指定
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
            )
            sr!!.startListening(intent)
        } catch (ex: Exception) {
            Toast.makeText(
                applicationContext, "startListening()でエラーが起こりました",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

    }

    private fun stopListening() {
        if (sr != null) sr!!.destroy()
        sr = null
    }

    fun restartListeningService() {
        if (!isListening) return

        stopListening()
        startListening()
    }

}