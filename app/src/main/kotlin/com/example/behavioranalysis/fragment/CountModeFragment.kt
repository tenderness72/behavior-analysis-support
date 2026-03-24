package com.example.behavioranalysis.fragment

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.behavioranalysis.CountingService
import com.example.behavioranalysis.R
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.databinding.FragmentCountModeBinding

class CountModeFragment : Fragment() {

    private var _binding: FragmentCountModeBinding? = null
    private val binding get() = _binding!!

    private var behaviorId: Long = -1
    private var behaviorName: String = ""
    private var behaviorDefinition: String = ""

    // インターバル選択肢（計測開始前は Fragment 側で保持）
    private var selectedIntervalSeconds: Int = 0
    private val intervalOptions = listOf(
        Pair("連続記録（インターバルなし）", 0),
        Pair("10秒", 10),
        Pair("30秒", 30),
        Pair("1分", 60),
        Pair("5分", 300),
        Pair("10分", 600)
    )

    // ── Service バインド ──────────────────────────────────────────────

    private var countingService: CountingService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as CountingService.CountingBinder).getService()
            countingService = service
            isBound = true
            service.callback = object : CountingService.CountingCallback {
                override fun onStateChanged() {
                    if (_binding != null) syncUIFromService(service)
                }
                override fun onTimerTick(remainingMs: Long) {
                    _binding?.tvTimer?.text = "残り %.1f 秒".format(remainingMs / 1000.0)
                }
                override fun onIntervalComplete(intervalNum: Int) {
                    _binding?.tvIntervalInfo?.text = "インターバル: $intervalNum"
                }
            }
            syncUIFromService(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            countingService = null
            isBound = false
        }
    }

    // ── 通知パーミッション（Android 13+） ─────────────────────────────

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                requireContext(),
                "通知が許可されていません。バックグラウンド中は通知ボタンで操作できません。",
                Toast.LENGTH_LONG
            ).show()
        }
        doStartCounting()   // 許可されなくても計測自体は開始
    }

    // ── Fragment ライフサイクル ────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        behaviorId = arguments?.getLong("BEHAVIOR_ID", -1) ?: -1
        behaviorName = arguments?.getString("BEHAVIOR_NAME") ?: ""
        behaviorDefinition = arguments?.getString("BEHAVIOR_DEFINITION") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCountModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBehaviorInfo()
        setupIntervalSpinner()
        setupButtons()
    }

    override fun onStart() {
        super.onStart()
        // Service に接続（BIND_AUTO_CREATE で存在しなければ生成）
        requireContext().bindService(
            Intent(requireContext(), CountingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            countingService?.callback = null
            requireContext().unbindService(serviceConnection)
            isBound = false
            countingService = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── UI セットアップ ───────────────────────────────────────────────

    private fun setupBehaviorInfo() {
        binding.tvBehaviorName.text = behaviorName
        binding.tvBehaviorDefinition.text = behaviorDefinition
    }

    private fun setupIntervalSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            intervalOptions.map { it.first }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerInterval.adapter = adapter
        binding.spinnerInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedIntervalSeconds = intervalOptions[position].second
                updateIntervalVisibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnCountMode.setOnClickListener {
            val service = countingService ?: return@setOnClickListener
            if (!service.isRunning) requestStartCounting() else stopCountingMode()
        }
        binding.btnIncrement.setOnClickListener {
            countingService?.increment()
        }
        binding.btnDecrement.setOnClickListener {
            countingService?.decrement()
        }
        binding.btnSave.setOnClickListener {
            saveRecord()
        }
        binding.btnReset.setOnClickListener {
            resetAll()
        }
    }

    // ── 計測開始（通知パーミッション確認あり） ─────────────────────────

    private fun requestStartCounting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        doStartCounting()
    }

    private fun doStartCounting() {
        val service = countingService ?: return
        // バインドだけでは Service が独立して生きないので明示的に start
        ContextCompat.startForegroundService(
            requireContext(),
            Intent(requireContext(), CountingService::class.java)
        )
        service.startCounting(behaviorId, behaviorName, selectedIntervalSeconds)
        binding.spinnerInterval.isEnabled = false
    }

    // ── 計測停止・保存・リセット ─────────────────────────────────────

    private fun stopCountingMode() {
        countingService?.stopCounting()
        binding.spinnerInterval.isEnabled = true
    }

    private fun saveRecord() {
        val service = countingService ?: return
        if (service.currentCount == 0) {
            Toast.makeText(requireContext(), "カウントが0です", Toast.LENGTH_SHORT).show()
            return
        }
        val db = AppDatabase.getDatabase(requireContext())
        service.saveAndReset(db) {
            if (isAdded) {
                Toast.makeText(requireContext(), getString(R.string.message_saved), Toast.LENGTH_SHORT).show()
                binding.spinnerInterval.isEnabled = true
            }
        }
    }

    private fun resetAll() {
        countingService?.let {
            it.stopCounting()
            it.resetState()
        }
        binding.spinnerInterval.isEnabled = true
    }

    // ── ボリュームキー（フォアグラウンド時のみ有効） ─────────────────

    /** BehaviorDetailActivity.onKeyDown から呼ばれる */
    fun handleKeyDown(keyCode: Int): Boolean {
        val service = countingService ?: return false
        if (!service.isRunning) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { service.increment(); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { service.decrement(); true }
            else -> false
        }
    }

    // ── Service 状態 → UI 同期 ──────────────────────────────────────

    private fun syncUIFromService(service: CountingService) {
        val b = _binding ?: return

        b.tvCount.text = service.currentCount.toString()
        b.tvIntervalInfo.text = if (service.currentIntervalNum > 0)
            "インターバル: ${service.currentIntervalNum}" else ""

        if (service.isRunning) {
            b.btnCountMode.text = "カウント中 ▶ タップで停止"
            b.btnCountMode.setBackgroundColor(requireContext().getColor(R.color.counting_active_dark))
            b.tvCount.setTextColor(requireContext().getColor(R.color.counting_active))
            b.btnIncrement.isEnabled = true
            b.btnDecrement.isEnabled = true
            b.btnSave.isEnabled = true
            if (service.selectedIntervalSeconds > 0) {
                b.tvTimer.text = "残り %.1f 秒".format(service.remainingMs / 1000.0)
            }
        } else {
            b.btnCountMode.text = "カウント開始"
            b.btnCountMode.setBackgroundColor(requireContext().getColor(R.color.teal_700))
            b.tvCount.setTextColor(requireContext().getColor(R.color.teal_700))
            b.btnIncrement.isEnabled = false
            b.btnDecrement.isEnabled = false
            b.btnSave.isEnabled = false
        }
        updateIntervalVisibility()
    }

    private fun updateIntervalVisibility() {
        val service = countingService
        val showTimer = service != null &&
                service.isRunning &&
                service.selectedIntervalSeconds > 0
        _binding?.cardTimer?.visibility = if (showTimer) View.VISIBLE else View.GONE
    }
}
