package com.example.sdlproto

import java.util.HashMap

/**
 * 車両情報クラス
 */
class Vehicle {
    var vin: String? = null
    var maker: String? = null
    var model: String? = null
    var modelYear: String? = null
    val tireMap = HashMap<String, String>()
    var fuelLevel: String? = null
    var speed: String? = null
    var breake: String? = null
    var createAt: String? = null
    var updateAt: String? = null

    fun setTireMap(key: String, `val`: String) {
        this.tireMap[key] = `val`
    }
}
