package dji.sampleV5.aircraft

import android.app.Application
import android.content.Context
import androidx.lifecycle.MutableLiveData
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.network.DJINetworkManager
import dji.v5.utils.common.LogUtils

class SpeakerPayloadApplication : Application() {

    companion object {
        val sdkRegistered = MutableLiveData(false)
    }

    private var isSdkInit = false

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        com.cySdkyc.clx.Helper.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        SDKManager.getInstance().init(this, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                LogUtils.i("SpeakerPayload", "SDK registration success")
                sdkRegistered.postValue(true)
            }
            override fun onRegisterFailure(error: IDJIError) {
                LogUtils.e("SpeakerPayload", "SDK registration failed: $error")
            }
            override fun onProductDisconnect(productId: Int) {
                LogUtils.i("SpeakerPayload", "Product disconnected: $productId")
            }
            override fun onProductConnect(productId: Int) {
                LogUtils.i("SpeakerPayload", "Product connected: $productId")
            }
            override fun onProductChanged(productId: Int) {
                LogUtils.i("SpeakerPayload", "Product changed: $productId")
            }
            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                LogUtils.i("SpeakerPayload", "Init process: $event ($totalProcess%)")
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    isSdkInit = true
                    SDKManager.getInstance().registerApp()
                }
            }
            override fun onDatabaseDownloadProgress(current: Long, total: Long) {}
        })

        DJINetworkManager.getInstance().addNetworkStatusListener { isAvailable ->
            if (isSdkInit && isAvailable && !SDKManager.getInstance().isRegistered) {
                SDKManager.getInstance().registerApp()
            }
        }
    }
}
