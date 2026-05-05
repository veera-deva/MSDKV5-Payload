package dji.sampleV5.aircraft.data

class DJIToastResult(var isSuccess: Boolean, var msg: String? = null) {
    companion object {
        fun success(msg: String? = null) = DJIToastResult(true, "success ${msg ?: ""}")
        fun failed(msg: String) = DJIToastResult(false, msg)
    }
}
