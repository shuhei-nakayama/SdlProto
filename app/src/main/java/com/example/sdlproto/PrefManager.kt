package com.example.sdlproto

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

/**
 * SharedPreferencesのマネージャクラスです
 * インスタンスの生成はgetInstance(Context)を使用してください
 */
class PrefManager private constructor(private val context: Context) {
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val editor: SharedPreferences.Editor?

    init {
        editor = preferences.edit()
    }

    /**
     * ※※※※※
     * Setterです
     * @param key 保存キー
     * @param val キーに紐づけて保存したい値
     */
    fun write(key: String, `val`: String) {
        editor!!.putString(key, `val`)
        editor.commit()
    }

    fun write(key: String, `val`: Int) {
        editor!!.putInt(key, `val`)
        editor.commit()
    }

    fun write(key: String, `val`: Boolean?) {
        editor!!.putBoolean(key, `val`!!)
        editor.commit()
    }

    /**
     * ※※※※※
     * Getterです
     * @param key 取得したいキー
     * @param defaultValue キーに紐づく値がない場合の初期値
     * @return defaultValueと同じデータ型の値
     */
    fun read(key: String, defaultValue: String): String? {
        return preferences.getString(key, defaultValue)
    }

    fun read(key: String, defaultValue: Int): Int {
        return preferences.getInt(key, defaultValue)
    }

    fun read(key: String, defaultValue: Boolean?): Boolean? {
        return preferences.getBoolean(key, defaultValue!!)
    }

    /**
     * ※※※※※
     * キーに紐づく値を削除します
     * @param key 削除したいキー
     */
    fun remove(key: String) {
        editor?.remove(key)?.commit()
    }

    /**
     * SharedPreferenceに保存されている全てのkey/valueを削除します
     */
    fun removeAll() {
        editor!!.clear().commit()
    }


    /**
     * ※※※※※
     * リソースIDに設定されているnameをキーとして文字列を保存する
     * @param resourceId 保存したいリソースID
     * @param val 保存したい文字列情報
     */
    fun setPrefByStr(resourceId: Int, `val`: String) {
        write(context.resources.getResourceEntryName(resourceId), `val`)
    }

    fun setPrefByBool(resourceId: Int, `val`: Boolean?) {
        write(context.resources.getResourceEntryName(resourceId), `val`)
    }

    /**
     * ※※※※※
     * リソースIDに設定されているnameをキーとして文字列を取得するする
     * @param resourceId 取得したいリソースID
     * @param val リソースIDに紐づくデータが保持されていなかった場合の、デフォルト返却値
     * @return 文字列情報
     */
    fun getPrefByStr(resourceId: Int, `val`: String): String? {
        return read(context.resources.getResourceEntryName(resourceId), `val`)
    }

    fun getPrefByBool(resourceId: Int, `val`: Boolean?): Boolean? {
        return read(context.resources.getResourceEntryName(resourceId), `val`)
    }

    companion object {
        private var prefManager: PrefManager? = null

        /**
         * ※※※※※
         * インスタンスを生成します
         * @param context コンテキスト
         * @return マネージャクラス
         */
        @Synchronized
        fun getInstance(context: Context): PrefManager {
            if (prefManager == null) {
                prefManager = PrefManager(context)
            }
            return prefManager!!
        }
    }
}
