package dji.sampleV5.aircraft

import android.app.Activity
import android.os.Bundle

/**
 * Transparent trampoline activity that handles USB accessory attachment events.
 * The DJI SDK intercepts the USB intent; this activity exists only to receive it.
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
