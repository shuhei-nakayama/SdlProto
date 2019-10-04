package com.example.sdlproto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.Toast

/**
 * ロックスクリーンを表示するためのクラス
 */
class LockScreenActivity : AppCompatActivity() {

    private val closeLockScreenBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(LOG_TAG, "onCreate")
        super.onCreate(savedInstanceState)

        registerReceiver(closeLockScreenBroadcastReceiver, IntentFilter(CLOSE_LOCK_SCREEN_ACTION))
        setContentView(R.layout.activity_lock_screen)

        val intent = intent
        val imageView = findViewById<ImageView>(R.id.lockscreen)

        if (intent.hasExtra(LOCKSCREEN_BITMAP_EXTRA)) {
            val lockscreen =
                intent.getParcelableExtra<Parcelable>(LOCKSCREEN_BITMAP_EXTRA) as Bitmap
            if (lockscreen != null) {
                imageView.setImageBitmap(lockscreen)
            }
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) {
            Toast.makeText(
                this,
                resources.getString(R.string.lockscreen_press_back_btn),
                Toast.LENGTH_SHORT
            ).show()
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onDestroy() {
        Log.i(LOG_TAG, "onDestroy")
        unregisterReceiver(closeLockScreenBroadcastReceiver)
        super.onDestroy()
    }

    companion object {
        private val LOG_TAG = "[Log:[LockScreenActivity]]"
        val LOCKSCREEN_BITMAP_EXTRA = "LOCKSCREEN_BITMAP_EXTRA"
        val CLOSE_LOCK_SCREEN_ACTION = "CLOSE_LOCK_SCREEN"
    }
}
