package dji.sampleV5.aircraft.pages

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragmentCustomRecordBinding
import dji.sampleV5.aircraft.models.MegaphoneVM
import dji.sampleV5.aircraft.util.ToastUtils
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.megaphone.MegaphoneManager
import dji.v5.manager.aircraft.megaphone.UploadState
import dji.v5.manager.aircraft.megaphone.WorkMode

/**
 * Custom voice recording fragment for the Speaker Payload feature.
 *
 * Provides an animated microphone button for recording, a live elapsed-time
 * chronometer, upload progress feedback, and one-tap playback on the speaker.
 * Designed to be hosted inside [SpeakerPayloadActivity].
 */
class CustomRecordFragment : DJIFragment() {

    private val megaphoneVM: MegaphoneVM by activityViewModels()
    private var binding: FragmentCustomRecordBinding? = null

    private var isRecording = false
    private var hasRecording = false
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCustomRecordBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        observeViewModel()
        applyIdleState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelPulse()
        binding = null
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private fun setupControls() {
        binding?.btnRecord?.setOnClickListener {
            if (!isRecording) startRecording() else stopRecording()
        }

        binding?.btnPlayOnSpeaker?.setOnClickListener { playOnSpeaker() }
        binding?.btnStopSpeaker?.setOnClickListener { stopSpeaker() }

        binding?.cbQuickPlay?.setOnCheckedChangeListener { _, checked ->
            megaphoneVM.isQuickPlay = checked
        }

        setSpeakerButtonsEnabled(playEnabled = false, stopEnabled = false)
    }

    // -------------------------------------------------------------------------
    // ViewModel observations
    // -------------------------------------------------------------------------

    private fun observeViewModel() {
        megaphoneVM.curRealTimeUploadedState.observe(viewLifecycleOwner) { state ->
            when (state) {
                UploadState.UPLOAD_SUCCESS -> onUploadSuccess()
                UploadState.UNKNOWN -> if (!isRecording) applyIdleState()
                else -> setUploadStatusText("Uploading to speaker…", R.color.yellow)
            }
        }

        megaphoneVM.curRealTimeSentBytes.observe(viewLifecycleOwner) {
            refreshProgressBar()
        }

        megaphoneVM.curRealTimeTotalBytes.observe(viewLifecycleOwner) {
            refreshProgressBar()
        }
    }

    // -------------------------------------------------------------------------
    // Recording lifecycle
    // -------------------------------------------------------------------------

    private fun startRecording() {
        isRecording = true
        hasRecording = false
        applyRecordingActiveState()

        megaphoneVM.setWorkMode(WorkMode.VOICE, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                megaphoneVM.startRecord()
                mainHandler.post {
                    binding?.chronometer?.base = SystemClock.elapsedRealtime()
                    binding?.chronometer?.start()
                    binding?.uploadProgressBar?.progress = 0
                    binding?.tvBytesInfo?.text = ""
                    setSpeakerButtonsEnabled(playEnabled = false, stopEnabled = false)
                    startPulse()
                }
            }
            override fun onFailure(error: IDJIError) {
                isRecording = false
                mainHandler.post { applyIdleState() }
                ToastUtils.showToast("Cannot start recording: ${error.description()}")
            }
        })
    }

    private fun stopRecording() {
        isRecording = false
        megaphoneVM.stopRecord()

        binding?.chronometer?.stop()
        cancelPulse()
        applyRecordingStoppedState()
        setUploadStatusText("Processing and uploading…", R.color.yellow)
    }

    // -------------------------------------------------------------------------
    // Speaker playback
    // -------------------------------------------------------------------------

    private fun playOnSpeaker() {
        MegaphoneManager.getInstance().startPlay(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                mainHandler.post {
                    setSpeakerButtonsEnabled(playEnabled = false, stopEnabled = true)
                    setUploadStatusText("Playing on speaker…", R.color.blue)
                    ToastUtils.showToast("Playing on speaker")
                }
            }
            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("Play failed: $error")
            }
        })
    }

    private fun stopSpeaker() {
        MegaphoneManager.getInstance().stopPlay(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                mainHandler.post {
                    setSpeakerButtonsEnabled(playEnabled = true, stopEnabled = false)
                    setUploadStatusText("Ready to play", R.color.green)
                    ToastUtils.showToast("Playback stopped")
                }
            }
            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("Stop failed: $error")
            }
        })
    }

    // -------------------------------------------------------------------------
    // State helpers
    // -------------------------------------------------------------------------

    private fun applyIdleState() {
        setMicTint(R.color.green)
        binding?.tvRecordStatus?.text =
            if (hasRecording) "Recording saved — tap to record again"
            else "Tap to Start Recording"
        binding?.tvRecordStatus?.setTextColor(colorOf(R.color.white))
        setUploadStatusText(
            if (hasRecording) "Ready to play" else "Tap mic to start recording",
            R.color.lighter_gray
        )
    }

    private fun applyRecordingActiveState() {
        setMicTint(R.color.red)
        binding?.tvRecordStatus?.text = "Recording…  Tap to Stop"
        binding?.tvRecordStatus?.setTextColor(colorOf(R.color.red))
        setUploadStatusText("Recording in progress", R.color.red)
    }

    private fun applyRecordingStoppedState() {
        setMicTint(R.color.green)
        binding?.tvRecordStatus?.text = "Recording complete"
        binding?.tvRecordStatus?.setTextColor(colorOf(R.color.white))
    }

    private fun onUploadSuccess() {
        hasRecording = true
        binding?.uploadProgressBar?.progress = 100
        setSpeakerButtonsEnabled(playEnabled = true, stopEnabled = false)
        setUploadStatusText("Upload complete — ready to play", R.color.green)
    }

    private fun refreshProgressBar() {
        val sent = megaphoneVM.curRealTimeSentBytes.value ?: 0L
        val total = megaphoneVM.curRealTimeTotalBytes.value ?: 0L
        if (total > 0L) {
            val pct = ((sent.toFloat() / total.toFloat()) * 100).toInt()
            binding?.uploadProgressBar?.progress = pct
            binding?.tvBytesInfo?.text = "${sent / 1024} KB / ${total / 1024} KB"
        }
    }

    private fun setMicTint(colorRes: Int) {
        binding?.btnRecord?.imageTintMode = PorterDuff.Mode.SRC_ATOP
        binding?.btnRecord?.imageTintList =
            android.content.res.ColorStateList.valueOf(colorOf(colorRes))
    }

    private fun setUploadStatusText(text: String, colorRes: Int) {
        mainHandler.post {
            binding?.tvUploadState?.text = text
            binding?.tvUploadState?.setTextColor(colorOf(colorRes))
        }
    }

    private fun setSpeakerButtonsEnabled(playEnabled: Boolean, stopEnabled: Boolean) {
        binding?.btnPlayOnSpeaker?.isEnabled = playEnabled
        binding?.btnStopSpeaker?.isEnabled = stopEnabled
    }

    private fun colorOf(colorRes: Int) =
        ContextCompat.getColor(requireContext(), colorRes)

    // -------------------------------------------------------------------------
    // Pulse animation on mic button while recording
    // -------------------------------------------------------------------------

    private fun startPulse() {
        val ring = binding?.vPulseRing ?: return
        ring.alpha = 0f
        pulseAnimator = ObjectAnimator.ofFloat(ring, "alpha", 0f, 0.85f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun cancelPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding?.vPulseRing?.alpha = 0f
    }
}
