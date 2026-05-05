package dji.sampleV5.aircraft.pages

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.SpeakerPayloadApplication
import dji.sampleV5.aircraft.databinding.ActivitySpeakerPayloadBinding
import dji.sampleV5.aircraft.models.MegaphoneVM
import dji.sampleV5.aircraft.util.ToastUtils
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.megaphone.*
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.LogPath

class SpeakerPayloadActivity : AppCompatActivity() {

    private val megaphoneVM: MegaphoneVM by viewModels()
    private lateinit var binding: ActivitySpeakerPayloadBinding

    private var curPlayMode: PlayMode = PlayMode.UNKNOWN
    private var isPlaying: Boolean = false

    // Tracks which ports we have already auto-set the index for, so we don't
    // spam setMegaphoneIndex every time the LiveData re-emits the same value.
    private var autoIndexApplied: MegaphoneIndex? = null

    // Spinner positions match megaphone_index_array order
    private val indexSpinnerPositions = mapOf(
        MegaphoneIndex.PORTSIDE to 0,
        MegaphoneIndex.STARBOARD to 1,
        MegaphoneIndex.UPSIDE to 2,
        MegaphoneIndex.OSDK to 3,
        MegaphoneIndex.PORT_1 to 4,
        MegaphoneIndex.PORT_2 to 5,
        MegaphoneIndex.PORT_3 to 6,
        MegaphoneIndex.PORT_4 to 7
    )

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) ToastUtils.showToast("Microphone permission is required for recording")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeakerPayloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ensureAudioPermission()
        initControls()
        observeViewModel()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CustomRecordFragment())
                .commit()
        }

        // Only start observing payload connections after DJI SDK registration succeeds.
        // MegaphoneManager rejects all calls (including setMegaphoneIndex) until the SDK
        // is registered with DJI's servers.
        SpeakerPayloadApplication.sdkRegistered.observe(this) { registered ->
            if (registered) megaphoneVM.addListener()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        megaphoneVM.removeRealTimeListener()
        megaphoneVM.removeMegaphoneInfoListener()
    }

    // -------------------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------------------

    private fun ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // -------------------------------------------------------------------------
    // UI wiring
    // -------------------------------------------------------------------------

    private fun initControls() {
        binding.btnBack.setOnClickListener { finish() }

        binding.sbVolumeControl.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvVolumeValue.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val vol = seekBar?.progress ?: return
                megaphoneVM.setVolume(vol, object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        ToastUtils.showToast("Volume set to $vol")
                    }

                    override fun onFailure(error: IDJIError) {
                        ToastUtils.showToast("Set volume failed: $error")
                    }
                })
            }
        })

        binding.btnPlayMode.setOnClickListener { togglePlayMode() }

        binding.btnPlayControl.setOnClickListener { togglePlayControl() }

        // Manual port override via spinner
        binding.spMegaphoneSwitch.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    val selected = parent?.getItemAtPosition(position).toString()
                    val newIndex = MegaphoneIndex.valueOf(selected)
                    // Skip if this is just the spinner syncing to the auto-detected value
                    if (newIndex == autoIndexApplied) return
                    applyMegaphoneIndex(newIndex)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    // -------------------------------------------------------------------------
    // ViewModel observations
    // -------------------------------------------------------------------------

    private fun observeViewModel() {
        megaphoneVM.curRealTimeSentBytes.observe(this) { refreshUploadStatus() }
        megaphoneVM.curRealTimeTotalBytes.observe(this) { refreshUploadStatus() }
        megaphoneVM.curRealTimeUploadedState.observe(this) { refreshUploadStatus() }

        // Auto-set the megaphone index as soon as a port connection is detected.
        // This is required before ANY MegaphoneManager call can succeed.
        megaphoneVM.isLeftPayloadConnect.observe(this) { connected ->
            refreshConnectionStatus()
            if (connected == true && autoIndexApplied == null)
                applyMegaphoneIndex(MegaphoneIndex.PORTSIDE)
        }
        megaphoneVM.isRightPayloadConnect.observe(this) { connected ->
            refreshConnectionStatus()
            if (connected == true && autoIndexApplied == null)
                applyMegaphoneIndex(MegaphoneIndex.STARBOARD)
        }
        megaphoneVM.isUpPayloadConnect.observe(this) { connected ->
            refreshConnectionStatus()
            if (connected == true && autoIndexApplied == null)
                applyMegaphoneIndex(MegaphoneIndex.UPSIDE)
        }
        megaphoneVM.isOSDKPayloadConnect.observe(this) { connected ->
            refreshConnectionStatus()
            if (connected == true && autoIndexApplied == null)
                applyMegaphoneIndex(MegaphoneIndex.OSDK)
        }
        megaphoneVM.isPort1Connect.observe(this) { connected ->
            refreshConnectionStatus()
            if (connected == true && autoIndexApplied == null)
                applyMegaphoneIndex(MegaphoneIndex.PORT_1)
        }
        megaphoneVM.isPort2Connect.observe(this) { connected ->
            refreshConnectionStatus()
            if (connected == true && autoIndexApplied == null)
                applyMegaphoneIndex(MegaphoneIndex.PORT_2)
        }
        megaphoneVM.isPort3Connect.observe(this) { connected ->
            refreshConnectionStatus()
            if (connected == true && autoIndexApplied == null)
                applyMegaphoneIndex(MegaphoneIndex.PORT_3)
        }
        megaphoneVM.isPort4Connect.observe(this) { connected ->
            refreshConnectionStatus()
            if (connected == true && autoIndexApplied == null)
                applyMegaphoneIndex(MegaphoneIndex.PORT_4)
        }

        megaphoneVM.megaphonePlayState.observe(this) { refreshPlayState() }
    }

    // -------------------------------------------------------------------------
    // Index — called both from auto-detect and manual spinner
    // -------------------------------------------------------------------------

    private fun applyMegaphoneIndex(index: MegaphoneIndex) {
        LogUtils.i(LogPath.SAMPLE, "applyMegaphoneIndex → $index")
        megaphoneVM.setMegaphoneIndex(
            MegaphoneIndex.UPSIDE,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    autoIndexApplied = index
                    LogUtils.i(LogPath.SAMPLE, "setMegaphoneIndex $index success")
                    runOnUiThread {
                        binding.tvCurMegaphoneIndex.text = index.name
                        // Sync spinner without re-triggering the listener
                        indexSpinnerPositions[index]?.let { pos ->
                            binding.spMegaphoneSwitch.setSelection(pos, false)
                        }
                        refreshDeviceStatus()
                    }
                    ToastUtils.showToast("Speaker ready: ${index.name}")
                }

                override fun onFailure(error: IDJIError) {
                    LogUtils.e(LogPath.SAMPLE, "setMegaphoneIndex $index failed: $error")
                    runOnUiThread {
                        binding.tvMegaphoneState.text = "NOT CONNECTED"
                    }
                    ToastUtils.showToast("Speaker not ready on ${index.name}: ${error.description()}")
                }
            })
    }

    // -------------------------------------------------------------------------
    // Playback helpers
    // -------------------------------------------------------------------------

    private fun togglePlayMode() {
        val newMode = if (curPlayMode == PlayMode.SINGLE) PlayMode.LOOP else PlayMode.SINGLE
        megaphoneVM.setPlayMode(newMode, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                curPlayMode = newMode
                runOnUiThread { applyPlayModeIcon() }
                ToastUtils.showToast("Play mode: ${newMode.name}")
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("Set play mode failed: ${error.description()}")
            }
        })
    }

    private fun togglePlayControl() {
        if (!isPlaying) {
            megaphoneVM.startPlay(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    isPlaying = true
                    runOnUiThread { binding.btnPlayControl.setImageResource(R.drawable.ic_media_stop) }
                    ToastUtils.showToast("Playing on speaker")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("Play failed: ${error.description()}")
                }
            })
        } else {
            megaphoneVM.stopPlay(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    isPlaying = false
                    runOnUiThread { binding.btnPlayControl.setImageResource(R.drawable.ic_media_play) }
                    ToastUtils.showToast("Playback stopped")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("Stop failed: ${error.description()}")
                }
            })
        }
    }

    private fun applyPlayModeIcon() {
        binding.btnPlayMode.setImageResource(
            if (curPlayMode == PlayMode.SINGLE) R.drawable.ic_action_playback_repeat_1
            else R.drawable.ic_action_playback_repeat
        )
    }

    // -------------------------------------------------------------------------
    // Refresh helpers
    // -------------------------------------------------------------------------

    private fun refreshDeviceStatus() {
        megaphoneVM.getStatus(
            object : CommonCallbacks.CompletionCallbackWithParam<MegaphoneStatus> {
                override fun onSuccess(t: MegaphoneStatus?) {
                    runOnUiThread { binding.tvMegaphoneState.text = t?.name ?: "UNKNOWN" }
                }

                override fun onFailure(error: IDJIError) {
                    runOnUiThread { binding.tvMegaphoneState.text = "UNKNOWN" }
                }
            }
        )

        megaphoneVM.getVolume(object : CommonCallbacks.CompletionCallbackWithParam<Int> {
            override fun onSuccess(t: Int?) {
                runOnUiThread {
                    t?.let {
                        binding.sbVolumeControl.progress = it
                        binding.tvVolumeValue.text = it.toString()
                    }
                }
            }

            override fun onFailure(error: IDJIError) {}
        })

        megaphoneVM.getPlayMode(object : CommonCallbacks.CompletionCallbackWithParam<PlayMode> {
            override fun onSuccess(t: PlayMode?) {
                curPlayMode = t ?: PlayMode.UNKNOWN
                runOnUiThread { applyPlayModeIcon() }
            }

            override fun onFailure(error: IDJIError) {}
        })
    }

    private fun refreshUploadStatus() {
        val sent = megaphoneVM.curRealTimeSentBytes.value ?: 0L
        val total = megaphoneVM.curRealTimeTotalBytes.value ?: 0L
        val state = megaphoneVM.curRealTimeUploadedState.value?.name ?: "UNKNOWN"
        runOnUiThread {
            binding.tvUploadStatus.text =
                "State: $state\nSent: ${sent / 1024} KB\nTotal: ${total / 1024} KB"
        }
    }

    private fun refreshConnectionStatus() {
        runOnUiThread {
            fun tag(label: String, connected: Boolean): String {
                val c = if (connected) "#00ff00" else "#ff4444"
                return "<font color='$c'><small>$label</small></font>"
            }

            val html = buildString {
                append(tag("L", megaphoneVM.isLeftPayloadConnect.value == true)); append("&nbsp;")
                append(tag("R", megaphoneVM.isRightPayloadConnect.value == true)); append("&nbsp;")
                append(tag("UP", megaphoneVM.isUpPayloadConnect.value == true)); append("&nbsp;")
                append(
                    tag(
                        "OSDK",
                        megaphoneVM.isOSDKPayloadConnect.value == true
                    )
                ); append("&nbsp;")
                append(tag("P1", megaphoneVM.isPort1Connect.value == true)); append("&nbsp;")
                append(tag("P2", megaphoneVM.isPort2Connect.value == true)); append("&nbsp;")
                append(tag("P3", megaphoneVM.isPort3Connect.value == true)); append("&nbsp;")
                append(tag("P4", megaphoneVM.isPort4Connect.value == true))
            }
            binding.tvConnectionStatus.text = Html.fromHtml(html)
        }
    }

    private fun refreshPlayState() {
        runOnUiThread {
            val info = megaphoneVM.megaphonePlayState.value
            curPlayMode = info?.playMode ?: PlayMode.UNKNOWN
            applyPlayModeIcon()
            binding.tvMegaphoneState.text = info?.status?.name ?: "N/A"
            info?.volume?.let {
                binding.sbVolumeControl.progress = it
                binding.tvVolumeValue.text = it.toString()
            }
            isPlaying = info?.status == MegaphoneStatus.PLAYING
            binding.btnPlayControl.setImageResource(
                if (isPlaying) R.drawable.ic_media_stop else R.drawable.ic_media_play
            )
        }
    }
}
