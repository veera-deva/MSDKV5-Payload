package dji.sampleV5.aircraft.models

import androidx.lifecycle.ViewModel
import dji.sampleV5.aircraft.util.DJIToastUtil
import dji.v5.utils.common.LogUtils

open class DJIViewModel : ViewModel() {
    val toastResult get() = DJIToastUtil.dJIToastLD
    val logTag: String = LogUtils.getTag(this)
}
