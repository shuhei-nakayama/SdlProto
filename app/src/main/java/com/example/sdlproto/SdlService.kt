package com.example.sdlproto


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartdevicelink.managers.SdlManager
import com.smartdevicelink.managers.SdlManagerListener
import com.smartdevicelink.managers.file.filetypes.SdlArtwork
import com.smartdevicelink.managers.lockscreen.LockScreenConfig
import com.smartdevicelink.protocol.enums.FunctionID
import com.smartdevicelink.proxy.LockScreenManager
import com.smartdevicelink.proxy.RPCNotification
import com.smartdevicelink.proxy.RPCResponse
import com.smartdevicelink.proxy.interfaces.OnSystemCapabilityListener
import com.smartdevicelink.proxy.rpc.*
import com.smartdevicelink.proxy.rpc.enums.*
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener
import com.smartdevicelink.transport.BaseTransportConfig
import com.smartdevicelink.transport.MultiplexTransportConfig
import com.smartdevicelink.transport.TCPTransportConfig
import com.smartdevicelink.util.CorrelationIdGenerator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class SdlService : Service(), TextToSpeech.OnInitListener {

    private var sdlManager: SdlManager? = null
    private val lockScreenManager = LockScreenManager()
    private var prefManager: PrefManager? = null

    // Settings(DisplayCapabilities)
    private var mDisplayCapabilities: DisplayCapabilities? = null
    private var mAvailableTemplates: ArrayList<String>? = null

    // SubscribeVehicleData時の登録成否確認用の一時変数
    private val subscribeVehicleRequest = HashMap<Int, String>()

    // 取得可能な車両情報のMapデータ
    private val usableVehicleData = HashMap<String, Boolean>()

    // image file name
    private val artWorks = HashMap<String, SdlArtwork>()
    private var isHeadlightTurnOn = false
    private var isHeadlightTurnOff = false

    private var isTimerWorked = false      // タイマーの動作状況(何らかの画面変更を行った後、TIMER_DELAY_MS時間経過するまではtrue)
    private var isChangeUIWorked = false

    // TTS用変数
    private var tts: TextToSpeech? = null
    private var isTtsEnabled = false
    private val ttsStandby = HashMap<Int, String>()

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(Constants.LOG_TAG, "SdlService.onCreate:in")

        super.onCreate()
        connectForeground()

        Log.d(Constants.LOG_TAG, "SdlService.onCreate:out")
    }

    private fun connectForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            APP_ID = resources.getString(R.string.app_id)
            APP_NAME = resources.getString(R.string.app_name)

            // TODO とりあえず、manticore必須
            val manticorePort = BuildConfig.MANTICORE_PORT
            MANTICORE_IP_ADDRESS = BuildConfig.MANTICORE_IP_ADDR
            if (manticorePort == null || MANTICORE_IP_ADDRESS == null) {
                USE_MANTICORE = false
            } else {
                MANTICORE_TCP_PORT = Integer.parseInt(manticorePort!!)
                USE_MANTICORE = true
            }

            // 車両情報HashMap
            usableVehicleData[VD_FUEL_LEVEL] = false
            usableVehicleData[VD_HEAD_LAMP_STATUS] = false
            usableVehicleData[VD_TIRE_PRESSURE] = false
            usableVehicleData[VD_SPEED] = false
            usableVehicleData[VD_BREAKING] = false
            prevFuelLevel = 0

            NOTIFICATION_CHANNEL_ID = resources.getString(R.string.notif_channel_id)
            startForeground(1, createNotification())

            tts = TextToSpeech(this, this)
        }
    }

    /**
     * Android Oreo(v26)以降の端末向け対応
     * 通知チャネル(NotificationChannel)を登録し、通知用のインスタンスを返却する
     * @return Notification 作成した通知情報
     */
    private fun createNotification(): Notification {
        val name = resources.getString(R.string.notif_channel_name)
        val description = resources.getString(R.string.notif_channel_desctiption)
        val importance = NotificationManager.IMPORTANCE_HIGH // デフォルトの重要度

        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager != null && notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
            channel.description = description
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            channel.enableVibration(true)
            channel.enableLights(true)
            channel.lightColor = Color.RED
            channel.setSound(null, null)
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(resources.getString(R.string.notif_content_title))
            .setContentText(resources.getString(R.string.notif_content_text))
            .setSmallIcon(R.drawable.ic_sdl)
            .build()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(Constants.LOG_TAG, "SdlService.onStartCommand:in")

        if (!intent.getBooleanExtra(resources.getString(R.string.is_first_connect), true)) {
            connectForeground()
        }
        prefManager = PrefManager.getInstance(applicationContext)

        if (sdlManager == null) {
            var transport: BaseTransportConfig? = null
            if (BuildConfig.TRANSPORT.equals("MULTI")) {
                val securityLevel: Int
                if (BuildConfig.SECURITY.equals("HIGH")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH
                } else if (BuildConfig.SECURITY.equals("MED")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED
                } else if (BuildConfig.SECURITY.equals("LOW")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW
                } else {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF
                }
                transport = MultiplexTransportConfig(this, APP_ID, securityLevel)
            } else if (BuildConfig.TRANSPORT.equals("TCP")) {
                transport = TCPTransportConfig(MANTICORE_TCP_PORT, MANTICORE_IP_ADDRESS, true)
            } else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
                val mtc = MultiplexTransportConfig(
                    this,
                    APP_ID,
                    MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF
                )
                mtc.setRequiresHighBandwidth(true)
                transport = mtc
            }

            // SdlManagerでのイベントリスナー
            val listener = object : SdlManagerListener {
                override fun onStart() {
                    // RPC listeners and other functionality can be called once this callback is triggered.
                    // HMI Status Listener
                    sdlManager?.addOnRPCNotificationListener(
                        FunctionID.ON_HMI_STATUS,
                        object : OnRPCNotificationListener() {
                            override fun onNotified(notification: RPCNotification) {
                                val status = notification as OnHMIStatus
                                if (status.firstRun == true && mDisplayCapabilities == null) {
                                    getDisplayCapabilities()
                                }
                                if (status.hmiLevel == HMILevel.HMI_FULL) {
                                    // Other HMI (Show, PerformInteraction, etc.) would go here
                                    if (status.firstRun!!) {
                                        registVehicleData()
                                        setCommand()
                                        showGreetingUI()
                                    }

                                    // @todo 仮対応
                                    // lockscreenが自動的に表示されないため、
                                    // FunctionID.ON_LOCK_SCREEN_STATUSも同様に発火しないため、
                                    // HMI_StatusがFullの時にロックスクリーンを表示する
                                    val showLockScreenIntent =
                                        Intent(applicationContext, LockScreenActivity::class.java)
                                    showLockScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    if (lockScreenManager.lockScreenIcon != null) {
                                        // HUからロックスクリーン用のアイコンが取得できた場合、デフォルトで設定していた画像は上書きする
                                        showLockScreenIntent.putExtra(
                                            LockScreenActivity.LOCKSCREEN_BITMAP_EXTRA,
                                            lockScreenManager.lockScreenIcon
                                        )
                                    }
                                    startActivity(showLockScreenIntent)
                                } else {
                                    sendBroadcast(Intent(LockScreenActivity.CLOSE_LOCK_SCREEN_ACTION))
                                }
                            }
                        })

                    // Menu Selected Listener
                    sdlManager?.addOnRPCNotificationListener(
                        FunctionID.ON_COMMAND,
                        object : OnRPCNotificationListener() {
                            override fun onNotified(notification: RPCNotification) {
                                val command = notification as OnCommand
                                val id = command.cmdID

                                if (id != null) {
                                    when (id) {
                                        COMMAND_ID_1 -> {
                                            // set data to MainActivity
                                            val broadcast = Intent()
                                            broadcast.putExtra(
                                                resources.getString(R.string.is_first_connect),
                                                false
                                            )
                                            broadcast.action =
                                                resources.getString(R.string.action_service_close)
                                            baseContext.sendBroadcast(broadcast)

                                            // @todo 仮対応
                                            // sdl_android 4.7.1ではonDestroy()をコールすると
                                            // スマホは接続解除されるが、
                                            // Manticore側は待機中のまま解除されなため
                                            // これが正しいやり方かどうか不明
                                            stopSelf()
                                        }
                                        else -> {
                                        }
                                    }//SdlService.this.onDestroy();
                                    //SdlService.this.stopSelf();
                                }
                            }
                        })

                    // onOnVehicleData
                    // 車両データに変更があった場合、このメソッドに通知されます。
                    // 複数登録していても、変更のあった項目1つのみが飛んできます。
                    sdlManager?.addOnRPCNotificationListener(
                        FunctionID.ON_VEHICLE_DATA,
                        object : OnRPCNotificationListener() {
                            override fun onNotified(notif: RPCNotification) {
                                val notification = notif as OnVehicleData
                                if (usableVehicleData[VD_HEAD_LAMP_STATUS] == true && notification.headLampStatus != null) {
                                    changeDisplayByHeadLampStatus(notification.headLampStatus)
                                }
                                if (usableVehicleData[VD_FUEL_LEVEL] == true && notification.fuelLevel != null) {
                                    changeDisplayByFuelLevel(notification.fuelLevel!!)
                                }
                                if (usableVehicleData[VD_TIRE_PRESSURE] == true && notification.tirePressure != null) {
                                    changeDisplayByTirePressure(notification.tirePressure)
                                }
                                if (usableVehicleData[VD_SPEED] == true && notification.speed != null) {
                                    latestSpeed = notification.speed!!.toInt()
                                    if (latestSpeed == 0) {
                                        // TODO 車両停止中
                                    }
                                }
                                if (usableVehicleData[VD_BREAKING] == true && notification.driverBraking != null) {
                                    latestBreakState = notification.driverBraking
                                    if (latestBreakState == VehicleDataEventStatus.YES) {
                                        // TODO 車両停止中
                                    }
                                }
                            }
                        })

                    // onOnSystemRequest
                    sdlManager?.addOnRPCNotificationListener(
                        FunctionID.ON_LOCK_SCREEN_STATUS,
                        object : OnRPCNotificationListener() {
                            // [原因調査中]実際にはLockSceenが自動的に起動しないため、この処理には入らない。
                            override fun onNotified(notif: RPCNotification) {
                                val notification = notif as OnLockScreenStatus
                                if (notification.hmiLevel == HMILevel.HMI_FULL && notification.showLockScreen == LockScreenStatus.REQUIRED) {
                                    val showLockScreenIntent =
                                        Intent(applicationContext, LockScreenActivity::class.java)
                                    showLockScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    if (lockScreenManager.lockScreenIcon != null) {
                                        // HUからロックスクリーン用のアイコンが取得できた場合、デフォルトで設定していた画像は上書きする
                                        showLockScreenIntent.putExtra(
                                            LockScreenActivity.LOCKSCREEN_BITMAP_EXTRA,
                                            lockScreenManager.lockScreenIcon
                                        )
                                    }
                                    startActivity(showLockScreenIntent)
                                }
                            }
                        })

                    sdlManager?.addOnRPCNotificationListener(
                        FunctionID.ON_SYSTEM_REQUEST,
                        object : OnRPCNotificationListener() {

                            override fun onNotified(notif: RPCNotification) {
                                val notification = notif as OnSystemRequest
                                if (notification.requestType == RequestType.LOCK_SCREEN_ICON_URL) {
                                    Log.d(Constants.LOG_TAG, "ON_SYSTEM_REQUEST")
                                    lockScreenManager.downloadLockScreenIcon(
                                        notification.url,
                                        LockScreenDownloadedListener()
                                    )

                                }
                            }
                        })
                }

                override fun onDestroy() {
                    Log.d(Constants.LOG_TAG, "onDestroy")
                    this@SdlService.stopSelf()
                }

                override fun onError(info: String, e: Exception) {
                    e.printStackTrace()
                }
            }

            val appIcon = SdlArtwork(
                ICON_FILENAME,
                FileType.GRAPHIC_PNG,
                R.drawable.ic_application_icon,
                true
            )

            // The app type to be used
            val appType = Vector<AppHMIType>()
            appType.add(AppHMIType.INFORMATION)
            appType.add(AppHMIType.MESSAGING)
            appType.add(AppHMIType.COMMUNICATION)
            appType.add(AppHMIType.TESTING)

            // The manager builder sets options for your session
            val builder = SdlManager.Builder(this, APP_ID, APP_NAME!!, listener)
            builder.setAppTypes(appType)
            if (USE_MANTICORE == true) {
                builder.setTransportType(
                    TCPTransportConfig(
                        MANTICORE_TCP_PORT,
                        MANTICORE_IP_ADDRESS,
                        true
                    )
                )
            } else {
                builder.setTransportType(transport!!)
            }
            builder.setAppIcon(appIcon)

            // set Lock Screen
            val lockScreenConfig = LockScreenConfig()
            lockScreenConfig.isEnabled = true
            lockScreenConfig.backgroundColor = R.color.colorPrimaryDark
            lockScreenConfig.appIcon = R.drawable.sdl_lockscreen_icon
            builder.setLockScreenConfig(lockScreenConfig)
            sdlManager = builder.build()
            sdlManager?.start()
        }

        Log.d(Constants.LOG_TAG, "SdlService.onStartCommand:out")
        return START_STICKY
    }

    private inner class LockScreenDownloadedListener :
        LockScreenManager.OnLockScreenIconDownloadedListener {
        override fun onLockScreenIconDownloaded(icon: Bitmap) {
            Log.d(Constants.LOG_TAG, "Lock screen icon downloaded successfully")
        }

        override fun onLockScreenIconDownloadError(e: Exception) {
            Log.d(Constants.LOG_TAG, "Couldn't download lock screen icon, resorting to default.")
        }
    }

    override fun onDestroy() {
        Log.d(Constants.LOG_TAG, "SdlService.onDestroy:in")

        if (tts != null) {
            tts!!.shutdown()
        }
        sdlManager?.dispose()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()

        Log.d(Constants.LOG_TAG, "SdlService.onDestroy:out")
    }

    private fun getDisplayCapabilities() {
        if (sdlManager == null) {
            return
        }

        detectDisplayCapabilities()
        mDisplayCapabilities?.let {
            val gSupport = it.graphicSupported
            if (gSupport != null && gSupport) {
                // Graphics Supported
                createArtWork()
            }

            mAvailableTemplates = ArrayList(it.templatesAvailable)
            if (mAvailableTemplates != null && mAvailableTemplates!!.contains(reqTemplateName)) {
                // mDisplayLayoutSupported = true;
                /*
                // show enable template list
                for (String str : mAvailableTemplates) {
                    Log.i(DEBUG_TAG, "dispCapabilities：" + str);
                }
                 */
                updateTemplate()
            }
        }
    }

    /**
     * アプリで使用する画像を準備する
     */
    private fun createArtWork() {
        artWorks[ICON_TIRE] = SdlArtwork(ICON_TIRE, FileType.GRAPHIC_PNG, R.drawable.tire, true)
        artWorks[ICON_FUEL] = SdlArtwork(ICON_FUEL, FileType.GRAPHIC_PNG, R.drawable.fuel, true)
        artWorks[ICON_HEADLIGHT] =
            SdlArtwork(ICON_HEADLIGHT, FileType.GRAPHIC_PNG, R.drawable.headlight, true)
        artWorks[PIC_SORRY] =
            SdlArtwork(PIC_SORRY, FileType.GRAPHIC_PNG, R.drawable.pic_sorry, true)
        artWorks[PIC_CHARACTER] =
            SdlArtwork(PIC_CHARACTER, FileType.GRAPHIC_PNG, R.drawable.pic_welcome, true)
    }

    private fun updateTemplate() {
        Log.d(Constants.LOG_TAG, "Called updateTemplate")
        val setDisplayLayoutRequest = SetDisplayLayout()
        setDisplayLayoutRequest.setDisplayLayout(reqTemplateName)
        sdlManager?.sendRPC(setDisplayLayoutRequest)
    }

    private fun detectDisplayCapabilities() {
        if (mDisplayCapabilities != null) {
            return
        }
        sdlManager?.systemCapabilityManager?.getCapability(
            SystemCapabilityType.DISPLAY,
            object : OnSystemCapabilityListener {

                override fun onCapabilityRetrieved(capability: Any) {
                    mDisplayCapabilities = capability as DisplayCapabilities
                }

                override fun onError(info: String) {
                    Log.d(Constants.LOG_TAG, "DisplayCapability could not be retrieved: $info")
                }
            })
    }

    /**
     * ※※※※※
     * GetVehicleDataで取得した車両情報を元に、車両がサポートしている情報をSharedPreferencesに保存する。
     * この際、サポートしていない項目と、SettingsActivityで設定した通知情報を加味して、
     * 必要のない車両情報はsubscribeしないようにする。
     */
    private fun registVehicleData() {
        val vdRequest = GetVehicleData()
        vdRequest.vin = true
        vdRequest.tirePressure = true
        vdRequest.fuelLevel = true

        vdRequest.onRPCResponseListener = object : OnRPCResponseListener() {

            var vehicle: Vehicle? = null
            var tire: TireStatus? = null

            override fun onResponse(correlationId: Int, response: RPCResponse) {
                if (response.success!!) {
                    val vin = (response as GetVehicleDataResponse).vin
                    val d = LocalDateTime.now()
                    val f = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

                    val vehicleType = VehicleType()
                    vehicle = Vehicle()
                    vehicle?.vin = vin
                    vehicle?.createAt = d.format(f)
                    vehicle?.updateAt = d.format(f)
                    vehicle?.maker = vehicleType.make
                    vehicle?.model = vehicleType.model
                    vehicle?.modelYear = vehicleType.modelYear

                    // タイヤ空気圧の判定
                    tire = response.tirePressure
                    if (tire == null) {
                        val tireNotSupport = ComponentVolumeStatus.NOT_SUPPORTED
                        checkTirePressureSupport(
                            tireNotSupport,
                            resources.getString(R.string.tire_front_left)
                        )
                        checkTirePressureSupport(
                            tireNotSupport,
                            resources.getString(R.string.tire_front_right)
                        )
                        checkTirePressureSupport(
                            tireNotSupport,
                            resources.getString(R.string.tire_rear_left)
                        )
                        checkTirePressureSupport(
                            tireNotSupport,
                            resources.getString(R.string.tire_rear_right)
                        )
                        checkTirePressureSupport(
                            tireNotSupport,
                            resources.getString(R.string.tire_inner_left)
                        )
                        checkTirePressureSupport(
                            tireNotSupport,
                            resources.getString(R.string.tire_inner_right)
                        )
                    } else {
                        checkTirePressureSupport(
                            tire!!.leftFront.status,
                            resources.getString(R.string.tire_front_left)
                        )
                        checkTirePressureSupport(
                            tire!!.rightFront.status,
                            resources.getString(R.string.tire_front_right)
                        )
                        checkTirePressureSupport(
                            tire!!.leftRear.status,
                            resources.getString(R.string.tire_rear_left)
                        )
                        checkTirePressureSupport(
                            tire!!.rightRear.status,
                            resources.getString(R.string.tire_rear_right)
                        )
                        checkTirePressureSupport(
                            tire!!.innerLeftRear.status,
                            resources.getString(R.string.tire_inner_left)
                        )
                        checkTirePressureSupport(
                            tire!!.innerRightRear.status,
                            resources.getString(R.string.tire_inner_right)
                        )
                    }

                    // 燃料残量の判定
                    val fuel = response.fuelLevel
                    if (fuel == null || fuel.toInt() < 0) {
                        vehicle?.fuelLevel = NONE_SUPPORTED
                    } else {
                        vehicle?.fuelLevel = SUPPORTED
                        usableVehicleData[VD_FUEL_LEVEL] = true
                    }

                    var arrayList = ArrayList<String>()
                    val gson = Gson()
                    val vinKey = resources.getString(R.string.pref_key_vin)
                    val json = prefManager!!.read(vinKey, "")
                    var isNweCar = false // 未登録の車に接続している場合True
                    if (json?.isEmpty() == true) {
                        isNweCar = true
                    } else {
                        arrayList = gson.fromJson(json, object : TypeToken<ArrayList<String>>() {

                        }.getType())
                        if (!arrayList.contains(vin)) {
                            isNweCar = true
                        }
                    }

                    // SharedPreferencesに車両情報が保持されているか確認し、
                    // 無ければ追加、あれば最終接続日時を更新する
                    if (isNweCar) {
                        // 車両識別番号のリストを保存する
                        arrayList.add(vin)
                        prefManager!!.write(vinKey, gson.toJson(arrayList))
                        prefManager!!.write(vin, gson.toJson(vehicle))
                    } else {
                        val existVehicle =
                            gson.fromJson(prefManager!!.read(vin, ""), Vehicle::class.java)
                        existVehicle.updateAt = d.format(f)
                        prefManager!!.write(vin, gson.toJson(existVehicle))
                    }

                    // ユーザの通知許可があるものに限り通知するようする
                    // TirePressure
                    if (prefManager?.read(
                            resources.getResourceEntryName(R.id.tireSwitch),
                            true
                        ) != true
                    ) {
                        usableVehicleData[VD_TIRE_PRESSURE] = false
                    } else {
                        changeDisplayByTirePressure(tire!!)
                    }
                    // FuelLevelをSubscribeするかどうか
                    if (usableVehicleData[VD_FUEL_LEVEL] == true) {
                        for (i in FUEL_SWITCH_LIST.indices) {
                            if (prefManager?.read(FUEL_SWITCH_LIST[i], true) == true) {
                                val fuelLv =
                                    Integer.parseInt(prefManager?.read(FUEL_LEVEL_LIST[i], "0") ?: "0")
                                if (!fuelLvThreshold.contains(fuelLv)) {
                                    fuelLvThreshold.add(fuelLv)
                                    fuelLvThreshold.reverse()
                                }
                            }
                        }
                        if (fuelLvThreshold.size == 0) {
                            usableVehicleData[VD_FUEL_LEVEL] = false
                        } else {
                            usableVehicleData[VD_FUEL_LEVEL] = true
                            assert(fuel != null)
                            changeDisplayByFuelLevel(fuel!!)
                        }
                    }
                    // HeadLightをSubscribeするかどうか
                    isHeadlightTurnOn =
                        prefManager?.read(
                            resources.getResourceEntryName(R.id.lightOnSwitch),
                            true
                        ) == true
                    isHeadlightTurnOff = prefManager?.read(
                        resources.getResourceEntryName(R.id.lightOffSwitch),
                        true
                    ) == true

                    if (isHeadlightTurnOn || isHeadlightTurnOff) {
                        usableVehicleData[VD_HEAD_LAMP_STATUS] = true
                    }
                    _subscribeVehicleData()
                }
            }

            private fun checkTirePressureSupport(status: ComponentVolumeStatus, str: String) {
                if (ComponentVolumeStatus.NOT_SUPPORTED == status) {
                    vehicle?.tireMap?.set(str, NONE_SUPPORTED)
                } else {
                    vehicle?.tireMap?.set(str, SUPPORTED)
                    usableVehicleData[VD_TIRE_PRESSURE] = true
                }
            }
        }
        sdlManager?.sendRPC(vdRequest)
    }

    /**
     * ※※※※※
     * コマンド(メニュー)をHUに表示する
     * どのコマンドが選択されたかは、onOnCommand()で判定を行う
     */
    private fun setCommand() {
        val params = MenuParams()
        params.parentID = 0
        params.position = 0
        params.menuName = resources.getString(R.string.cmd_exit)

        val command = AddCommand()
        command.cmdID = COMMAND_ID_1
        command.menuParams = params
        command.vrCommands = listOf(resources.getString(R.string.cmd_exit))
        sdlManager?.sendRPC(command)
    }

    private fun showGreetingUI() {
        val ui = UISettings(
            UISettings.EventType.Greeting, PIC_CHARACTER, null,
            "こんにちは！あなたの運転のサポートを担当いたします。",
            "パワーアップするために皆様のコメントお待ちしています！", null, null
        )
        addChangeUIQueue(ui)
    }


    /**
     * 車両データの登録結果から、利用可否を保持するクラス
     * ※sdl_android 4.6.1 で実装されていたonSubscribeVehicleDataResponseと同じ処理を行うため
     * 関数名は同一にしていますが、SDL非標準機能です。
     */
    private inner class OnSubscribeVehicleDataResponse : OnRPCResponseListener() {
        override fun onResponse(correlationId: Int, response: RPCResponse) {
            subscribeVehicleRequest[correlationId]?.let {
                usableVehicleData[it] = response.success
            }

            // 全ての機能に対してsubscribeが失敗した場合は、エラーを表示する。
            subscribeVehicleRequest.remove(correlationId)
            if (subscribeVehicleRequest.isEmpty()) {
                var supportFlg = false
                for (key in usableVehicleData.keys) {
                    if (usableVehicleData[key] == true) {
                        Log.d(
                            Constants.LOG_TAG,
                            "OnSubscribeVehicleDataResponse " + usableVehicleData[key] + "support"
                        )
                        supportFlg = true
                        break
                    } else {
                        Log.d(
                            Constants.LOG_TAG,
                            "OnSubscribeVehicleDataResponse " + usableVehicleData[key] + " is not supported"
                        )
                    }
                }
                if (!supportFlg) {
                    val ui = UISettings(
                        UISettings.EventType.NotSupport,
                        PIC_SORRY, null,
                        "ご乗車中のお車ではお手伝い出来ることがなさそうです。",
                        "別の車にてお試しください。", null, null
                    )
                    addChangeUIQueue(ui)
                }
            }
        }
    }

    /**
     * ※※※※※
     * 車両情報に変更があった際、通知するように要求する
     */
    private fun _subscribeVehicleData() {
        var subscribeRequest: SubscribeVehicleData// = new SubscribeVehicleData();
        if (usableVehicleData[VD_HEAD_LAMP_STATUS] == true) {
            subscribeRequest = SubscribeVehicleData()
            subscribeRequest.headLampStatus = true
            subscribeVehicleRequest[subscribeRequest.correlationID] = VD_HEAD_LAMP_STATUS
            subscribeRequest.onRPCResponseListener = OnSubscribeVehicleDataResponse()
            sdlManager?.sendRPC(subscribeRequest)
        }
        if (usableVehicleData[VD_FUEL_LEVEL] == true) {
            subscribeRequest = SubscribeVehicleData()
            subscribeRequest.fuelLevel = true
            subscribeVehicleRequest[subscribeRequest.correlationID] = VD_FUEL_LEVEL
            subscribeRequest.onRPCResponseListener = OnSubscribeVehicleDataResponse()
            sdlManager?.sendRPC(subscribeRequest)
        }
        if (usableVehicleData[VD_TIRE_PRESSURE] == true) {
            subscribeRequest = SubscribeVehicleData()
            subscribeRequest.tirePressure = true
            subscribeVehicleRequest[subscribeRequest.correlationID] = VD_TIRE_PRESSURE
            subscribeRequest.onRPCResponseListener = OnSubscribeVehicleDataResponse()
            sdlManager?.sendRPC(subscribeRequest)
        }
        if (usableVehicleData[VD_SPEED] == true) {
            subscribeRequest = SubscribeVehicleData()
            subscribeRequest.speed = true
            subscribeVehicleRequest[subscribeRequest.correlationID] = VD_SPEED
            subscribeRequest.onRPCResponseListener = OnSubscribeVehicleDataResponse()
            sdlManager?.sendRPC(subscribeRequest)
        }
        if (usableVehicleData[VD_BREAKING] == true) {
            subscribeRequest = SubscribeVehicleData()
            subscribeRequest.driverBraking = true
            subscribeVehicleRequest[subscribeRequest.correlationID] = VD_BREAKING
            subscribeRequest.onRPCResponseListener = OnSubscribeVehicleDataResponse()
            sdlManager?.sendRPC(subscribeRequest)
        }
    }

    /**
     * ※※※※※
     * タイヤ空気圧に変更があった場合の処理を定義しています
     * @param tire TireStatus
     */
    private fun changeDisplayByTirePressure(tire: TireStatus) {
        val inLeft = tire.innerLeftRear.status
        val inRight = tire.innerRightRear.status
        val frontLeft = tire.leftFront.status
        val frontRight = tire.rightFront.status
        val rearLeft = tire.leftRear.status
        val rearRight = tire.rightRear.status

        var textfield1 = checkTirePressure(
            ComponentVolumeStatus.LOW,
            frontLeft,
            frontRight,
            rearLeft,
            rearRight,
            inLeft,
            inRight
        )
        var textfield2 = checkTirePressure(
            ComponentVolumeStatus.ALERT,
            frontLeft,
            frontRight,
            rearLeft,
            rearRight,
            inLeft,
            inRight
        )
        val textfield3 = checkTirePressure(
            ComponentVolumeStatus.FAULT,
            frontLeft,
            frontRight,
            rearLeft,
            rearRight,
            inLeft,
            inRight
        )

        if (textfield1 != null) {
            textfield1 = "${textfield1}の空気圧が低くなっています。"
        }

        if (textfield2 != null) {
            if (textfield3 != null) {
                textfield2 = arrayOf(textfield2, textfield3).joinToString("、")
            }
            textfield2 = "${textfield2}に異常を検知しました。"
        } else if (textfield3 != null) {
            textfield2 = "${textfield3}に異常を検知しました。"
        }
        if (textfield1 == null && textfield2 != null) {
            textfield1 = textfield2
            textfield2 = null
        }

        if (textfield1 != null) {
            val ui = UISettings(
                UISettings.EventType.Tire,
                ICON_TIRE,
                null,
                textfield1,
                textfield2,
                null,
                null
            )
            addChangeUIQueue(ui)
        }
    }

    /**
     * ※※※※※
     * タイヤ空気圧のチェックを行い、指定したステータスと一致したものを文字列ベースで連結して返却する
     * @param checkStatus ComponentVolumeStatusのインスタンス
     * @param frontLeft 前輪(左)の状態
     * @param frontRight 前輪(右)の状態
     * @param rearLeft 後輪(左)の状態
     * @param rearRight 後輪(右)の状態
     * @param inLeft 中輪(左)の状態
     * @param inRight 中輪(右)の状態
     * @return checkStatusで指定された状態と一致したタイヤ情報
     */
    private fun checkTirePressure(
        checkStatus: ComponentVolumeStatus,
        frontLeft: ComponentVolumeStatus,
        frontRight: ComponentVolumeStatus,
        rearLeft: ComponentVolumeStatus,
        rearRight: ComponentVolumeStatus,
        inLeft: ComponentVolumeStatus,
        inRight: ComponentVolumeStatus
    ): String? {
        val list = ArrayList<String>()
        if (checkStatus == frontLeft) {
            list.add(resources.getString(R.string.tire_front_left))
        }
        if (checkStatus == frontRight) {
            list.add(resources.getString(R.string.tire_front_right))
        }
        if (checkStatus == rearLeft) {
            list.add(resources.getString(R.string.tire_rear_left))
        }
        if (checkStatus == rearRight) {
            list.add(resources.getString(R.string.tire_rear_right))
        }
        if (checkStatus == inLeft) {
            list.add(resources.getString(R.string.tire_inner_left))
        }
        if (checkStatus == inRight) {
            list.add(resources.getString(R.string.tire_inner_right))
        }
        return if (list.size != 0) {
            list.joinToString("、")
        } else null
    }

    /**
     * ※※※※※
     * 残燃料状態に応じてメッセージを表示する
     * @param fuelLevel 燃料残量
     */
    private fun changeDisplayByFuelLevel(fuelLevel: Double) {
        val fuel = fuelLevel.toInt()
        // doubleをintに変換したことで、同じ整数値が最大10回呼ばれるため、前回の値と比較をする
        // ex.30.9%～30.0%までのdouble値がすべて30%(int)となる
        if (fuel == prevFuelLevel) {
            return
        }
        prevFuelLevel = fuel
        if (fuelLvThreshold.contains(fuel)) {
            // 30%を切ったらGSを探すように通知する
            val str1 = "燃料の残量が$fuel%になりました。"
            val str2 = if (fuel <= 30) "そろそろガソリンスタンドを探しましょう。" else ""
            val ui = UISettings(UISettings.EventType.Fuel, ICON_FUEL, null, str1, str2, null, null)
            addChangeUIQueue(ui)
        }
    }

    /**
     * ※※※※※
     * ヘッドランプステータスの状態変更通知があった際の処理
     * @param lampStatus OnVehicleData()で取得したnotification.getHeadLampStatus()
     */
    private fun changeDisplayByHeadLampStatus(lampStatus: HeadLampStatus) {
        val lightStatus = lampStatus.ambientLightStatus
        if (checkAmbientStatusIsNight(lightStatus) && isHeadlightTurnOn) {
            if (!checkAnyHeadLightIsOn(lampStatus)) {
                val ui = UISettings(
                    UISettings.EventType.Headlight, ICON_HEADLIGHT, null,
                    "ヘッドライトが点灯していませんが大丈夫ですか？", "安全運転を心がけてください。", null, null
                )
                addChangeUIQueue(ui)
            }
        } else if (lightStatus == AmbientLightStatus.DAY && isHeadlightTurnOff) {
            if (checkAnyHeadLightIsOn(lampStatus)) {
                val ui = UISettings(
                    UISettings.EventType.Headlight, ICON_HEADLIGHT, null,
                    "ヘッドライトが点灯していませんか？", "まだ明るいようなので、消灯してはいかがでしょうか？", null, null
                )
                addChangeUIQueue(ui)
            }
        }
    }

    /**
     * ※※※※※
     * 周辺光センサーの値が夜(Twilight_1～4、Night)かどうか判定する
     * @param lightStatus AmbientLightStatus
     * @return 周辺光が夜に該当する場合Trueを返却する
     */
    private fun checkAmbientStatusIsNight(lightStatus: AmbientLightStatus): Boolean {
        return lightStatus == AmbientLightStatus.TWILIGHT_1 ||
                lightStatus == AmbientLightStatus.TWILIGHT_2 ||
                lightStatus == AmbientLightStatus.TWILIGHT_3 ||
                lightStatus == AmbientLightStatus.TWILIGHT_4 ||
                lightStatus == AmbientLightStatus.NIGHT
    }

    /**
     * ※※※※※
     * ハイビームかロービームのいずれかが点灯状態にあるか確認する
     * @param lampStatus HeadLampStatus
     * @return いずれかが点灯状態の場合Trueを返却する
     */
    private fun checkAnyHeadLightIsOn(lampStatus: HeadLampStatus): Boolean {
        return lampStatus.highBeamsOn == true || lampStatus.lowBeamsOn == true
    }

    /**
     * ※※※※※
     * HU上にデフォルト画面用のアイコン、文字を表示するように設定する
     */
    private fun showDefaultUI() {
        val ui = UISettings(
            UISettings.EventType.Default,
            PIC_CHARACTER, null,
            "デフォルト画面になります。", null, null, null
        )
        addChangeUIQueue(ui)
    }

    /**
     * ※※※※※
     * HUに表示したい画像、テキスト情報をキューに格納する
     * @param ui UISettings 表示したい情報が格納されたUISettings
     */
    private fun addChangeUIQueue(ui: UISettings) {
        uiQueue.offer(ui)
        checkNextQueue()
    }

    /**
     * ※※※※※
     * 画面の表示コントロールをするためのタイマー機能。
     * 何らかの画面を表示した後、一定時間経過すると、デフォルト画面になるようにする。
     */
    private fun waitTimer() {
        isChangeUIWorked = false
        if (isTimerWorked) {
            return
        } else if (uiQueue.isEmpty()) {
            return
        }
        isTimerWorked = true
        val task = object : TimerTask() {
            override fun run() {
                isTimerWorked = false
                val ui = uiQueue.poll()
                if (uiQueue.isEmpty()) {
                    if (ui.eventType?.equals(UISettings.EventType.NotSupport) == true) {
                        // サポートしている機能がないので、画面をロックする
                        Log.d(Constants.LOG_TAG, "利用中の車両で、本アプリケーションがサポート可能な機能がありません。")
                    } else if (ui.eventType?.equals(UISettings.EventType.Default) != true) {
                        showDefaultUI()
                    }
                } else {
                    checkNextQueue()
                }
            }
        }
        var delayTime = TIMER_DELAY_MS
        uiQueue.peek().run {
            if (this.eventType == UISettings.EventType.Default) {
                delayTime = 0
            }
        }
        val uiChangeTimer = Timer(false)
        uiChangeTimer.schedule(task, delayTime.toLong())
    }

    /**
     * ※※※※※
     * キューに格納されている画面情報を元に(表示可能なタイミングになったら)HUに表示リクエストを出す
     */
    @Synchronized
    private fun checkNextQueue() {
        // 次の画面変更があれば、一定時間経過後に表示するようにする
        if (!uiQueue.isEmpty() && !isTimerWorked && !isChangeUIWorked) {

            isChangeUIWorked = true
            val id = CorrelationIdGenerator.generateId()
            uiQueue.peek()?.let {
                it.id = id
            }
            uiQueue.peek()?.let { uisettings ->
                sdlManager?.screenManager?.let { it ->
                    it.beginTransaction()
                    uisettings.text1.run {
                        it.textField1 = this
                    }
                    uisettings.text2.run {
                        it.textField2 = this
                    }
                    uisettings.text3.run {
                        it.textField3 = this
                    }
                    uisettings.text4.run {
                        it.textField4 = this
                    }
                    uisettings.image1.run {
                        it.primaryGraphic = artWorks[this]
                    }
                    uisettings.image2.run {
                        it.primaryGraphic = artWorks[this]
                    }
                    // TTS
                    if (uisettings.eventType !== UISettings.EventType.Default && uisettings.eventType !== UISettings.EventType.Greeting) {
                        ttsStandby[uisettings.id] = uisettings.text1?: ""
                    }
                    it.commit { success ->
                        val reqId = uisettings.id
                        if (success) {
                            if (ttsStandby.containsKey(reqId)) {
                                ttsSpeech(ttsStandby[reqId]?: "", reqId.toString())
                            }
                        }
                        waitTimer()
                    }
                }
            }
        }
    }

    /**
     * TTS：initialize
     * @param status init status
     */
    override fun onInit(status: Int) {
        if (TextToSpeech.SUCCESS == status) {
            val locale = Locale.JAPAN
            if (tts!!.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                isTtsEnabled = true
                tts!!.language = locale
            } else {
                Log.d(Constants.LOG_TAG, "言語設定に日本語を選択できませんでした")
                isTtsEnabled = true
            }
        } else {
            isTtsEnabled = false
        }
    }

    /**
     * ※※※※※
     * TTS：指定した文字列を読み上げさせる
     * @param str TTSで読み上げさせたい文字列
     * @param utteranceId リクエスト用の一意のID値(null可)
     */
    private fun ttsSpeech(str: String, utteranceId: String?) {
        var utteranceId = utteranceId
        if (isTtsEnabled) {
            tts?.run {
                // SDLのTTSには読み上げキャンセル機能が見当たらないので、デモ用にはスマホ側のTTSを利用する
                // スマホ側のTTSを利用して読み上げさせる
                if (this.isSpeaking) {
                    this.stop()
                }
                this.setSpeechRate(1.2f)
                this.setPitch(1.0f)
                if (utteranceId == null) {
                    utteranceId = CorrelationIdGenerator.generateId().toString()
                }
                this.speak(str, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
        }
    }

    companion object {

        private var APP_ID = "0"  // set your own APP_ID
        private var USE_MANTICORE: Boolean = true
        private var APP_NAME: String? = null
        private var MANTICORE_TCP_PORT = 0
        private var MANTICORE_IP_ADDRESS: String? = null
        private lateinit var NOTIFICATION_CHANNEL_ID: String
        private val SUPPORTED = "supported"
        private val NONE_SUPPORTED = "not supported"
        private val VD_FUEL_LEVEL = "FUEL_LEVEL"
        private val VD_HEAD_LAMP_STATUS = "HEAD_LAMP_STATUS"
        private val VD_TIRE_PRESSURE = "TIRE_PRESSURE"
        private val VD_SPEED = "SPEED"
        private val VD_BREAKING = "DIVER_BREAKING"

        // 画面表示切替用のQueue
        private val uiQueue = LinkedList<UISettings>()
        // Templateの変更管理
        private val reqTemplateName =
            PredefinedLayout.GRAPHIC_WITH_TEXT.toString()  // 変更要求をかける際のテンプレート

        // Command
        private val COMMAND_ID_1 = 1

        // SoftButton
        private val ICON_TIRE = "sdl_tire.png"
        private val ICON_HEADLIGHT = "sdl_headlight.png"
        private val ICON_FUEL = "sdl_fuel.png"
        private val ICON_FILENAME = "sdl_hu_icon.png"
        private val PIC_CHARACTER = "sdl_chara.png"
        private val PIC_SORRY = "sdl_hu_sorry.png"

        // 主要機能のデータ
        private val FUEL_SWITCH_LIST = object : ArrayList<String>() {
            init {
                add("seekSwitch1")
                add("seekSwitch2")
                add("seekSwitch3")
                add("seekSwitch4")
                add("seekSwitch5")
            }
        }
        private val FUEL_LEVEL_LIST = object : ArrayList<String>() {
            init {
                add("seekText1")
                add("seekText2")
                add("seekText3")
                add("seekText4")
                add("seekText5")
            }
        }
        private val fuelLvThreshold = ArrayList<Int>()    // FuelLevelの通知閾値
        private var prevFuelLevel = 0
        private val TIMER_DELAY_MS = 7000 // 画面内UIの(デフォルト時以外の)表示時間

        // 車両停止検知用変数
        private var latestSpeed = -1
        private var latestBreakState = VehicleDataEventStatus.NO
    }

}
