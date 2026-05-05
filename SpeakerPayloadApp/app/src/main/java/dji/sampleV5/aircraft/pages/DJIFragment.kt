package dji.sampleV5.aircraft.pages

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment

/**
 * Lightweight base fragment providing a main-thread handler shared by all
 * megaphone sub-fragments.
 */
open class DJIFragment : Fragment() {
    protected val mainHandler = Handler(Looper.getMainLooper())
}
