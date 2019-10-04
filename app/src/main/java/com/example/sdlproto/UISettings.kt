package com.example.sdlproto

/**
 * HU(HMI)に対して画面描画を行う際の設定クラス
 */
class UISettings(
    var eventType: EventType?, var image1: String?, var image2: String?,
    textField1: String?, textField2: String?,
    textField3: String?, textField4: String?
) {
    var text1: String? = null
    var text2: String? = null
    var text3: String? = null
    var text4: String? = null
    var id = 0

    enum class EventType {
        Default,
        Greeting,
        Fuel,
        Tire,
        Headlight,
        Other,
        NotSupport,
    }

    init {
        text1 = textField1 ?: ""
        text2 = textField2 ?: ""
        text3 = textField3 ?: ""
        text4 = textField4 ?: ""
    }
}
