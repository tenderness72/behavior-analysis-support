package com.example.behavioranalysis.fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.behavioranalysis.IntervalNotesUtil
import com.example.behavioranalysis.R
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.BehaviorRecord
import com.example.behavioranalysis.databinding.FragmentCountModeBinding
import kotlinx.coroutines.launch

class CountModeFragment : Fragment() {

    private var _binding: FragmentCountModeBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase

    private var behaviorId: Long = -1
    private var behaviorName: String = ""
    private var behaviorDefinition: String = ""

    // カウント関連
    private var currentCount: Int = 0
    private var isCountingMode: Boolean = false

    // ロック中かどうか（Activity から参照）
    val isActive: Boolean get() = isCountingMode

    // インターバル関連
    private var selectedIntervalSeconds: Int = 0  // 0 = 連続記録
    private var currentIntervalCount: Int = 0
    private val intervalCounts: MutableList<Int> = mutableListOf()
    private var countInCurrentInterval: Int = 0
    private var countDownTimer: CountDownTimer? = null

    // インターバル選択肢
    private val intervalOptions = listOf(
        Pair("連続記録（インターバルなし）", 0),
        Pair("10秒", 10),
        Pair("30秒", 30),
        Pair("1分", 60),
        Pair("5分", 300),
        Pair("10分", 600)
    )

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

        database = AppDatabase.getDatabase(requireContext())

        setupBehaviorInfo()
        setupIntervalSpinner()
        setupButtons()
        updateUI()
    }

    private fun setupBehaviorInfo() {
        binding.tvBehaviorName.text = behaviorName
        binding.tvBehaviorDefinition.text = behaviorDefinition
    }

    private fun setupIntervalSpinner() {
        val spinnerItems = intervalOptions.map { it.first }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            spinnerItems
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

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
        // カウントモードトグル
        binding.btnCountMode.setOnClickListener {
            if (!isCountingMode) {
                startCountingMode()
            } else {
                stopCountingMode()
            }
        }

        // +ボタン
        binding.btnIncrement.setOnClickListener {
            if (isCountingMode) incrementCount()
        }

        // -ボタン
        binding.btnDecrement.setOnClickListener {
            if (isCountingMode) decrementCount()
        }

        // 保存ボタン
        binding.btnSave.setOnClickListener {
            saveRecord()
        }

        // リセットボタン
        binding.btnReset.setOnClickListener {
            resetAll()
        }
    }

    private fun startCountingMode() {
        isCountingMode = true
        currentCount = 0
        currentIntervalCount = 0
        intervalCounts.clear()
        countInCurrentInterval = 0

        binding.spinnerInterval.isEnabled = false

        // 画面を常時点灯 & ロック
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requireActivity().startLockTask()

        if (selectedIntervalSeconds > 0) {
            startIntervalTimer()
        }

        updateUI()
    }

    private fun stopCountingMode() {
        isCountingMode = false
        countDownTimer?.cancel()
        countDownTimer = null
        binding.spinnerInterval.isEnabled = true

        // 画面点灯フラグ解除 & ロック解除
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try { requireActivity().stopLockTask() } catch (_: Exception) {}

        updateUI()
    }

    private fun startIntervalTimer() {
        countDownTimer?.cancel()
        val totalMs = selectedIntervalSeconds * 1000L

        countDownTimer = object : CountDownTimer(totalMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000.0
                binding.tvTimer.text = "残り %.1f 秒".format(seconds)
            }

            override fun onFinish() {
                // インターバル終了
                intervalCounts.add(countInCurrentInterval)
                currentIntervalCount++
                countInCurrentInterval = 0
                binding.tvIntervalInfo.text = "インターバル: ${currentIntervalCount}"
                // 次のインターバルを開始
                startIntervalTimer()
            }
        }.start()
    }

    fun incrementCount() {
        currentCount++
        if (selectedIntervalSeconds > 0 && isCountingMode) {
            countInCurrentInterval++
        }
        updateCountDisplay()
    }

    fun decrementCount() {
        if (currentCount > 0) {
            currentCount--
            if (selectedIntervalSeconds > 0 && isCountingMode && countInCurrentInterval > 0) {
                countInCurrentInterval--
            }
            updateCountDisplay()
        }
    }

    // BehaviorDetailActivity からボリュームキーを受け取る
    fun handleKeyDown(keyCode: Int): Boolean {
        if (!isCountingMode) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                incrementCount()
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                decrementCount()
                true
            }
            else -> false
        }
    }

    private fun saveRecord() {
        if (currentCount == 0) {
            Toast.makeText(requireContext(), "カウントが0です", Toast.LENGTH_SHORT).show()
            return
        }

        val notes: String? = if (selectedIntervalSeconds > 0 && intervalCounts.isNotEmpty()) {
            // 現在のインターバルの途中カウントも追加
            val allCounts = intervalCounts.toMutableList()
            if (countInCurrentInterval > 0) allCounts.add(countInCurrentInterval)
            IntervalNotesUtil.encode(allCounts)
        } else {
            null
        }

        val record = BehaviorRecord(
            behaviorId = behaviorId,
            count = currentCount,
            notes = notes
        )

        lifecycleScope.launch {
            database.behaviorRecordDao().insert(record)
            Toast.makeText(requireContext(), getString(R.string.message_saved), Toast.LENGTH_SHORT).show()
            stopCountingMode()
            resetAll()
        }
    }

    private fun resetAll() {
        stopCountingMode()
        currentCount = 0
        currentIntervalCount = 0
        intervalCounts.clear()
        countInCurrentInterval = 0
        updateUI()
    }

    private fun updateUI() {
        updateCountDisplay()
        updateCountingModeUI()
        updateIntervalVisibility()
    }

    private fun updateCountDisplay() {
        binding.tvCount.text = currentCount.toString()
    }

    private fun updateCountingModeUI() {
        if (isCountingMode) {
            binding.btnCountMode.text = "カウント中 ▶ タップで停止"
            binding.btnCountMode.setBackgroundColor(requireContext().getColor(R.color.counting_active_dark))
            binding.tvCount.setTextColor(requireContext().getColor(R.color.counting_active))
            binding.btnIncrement.isEnabled = true
            binding.btnDecrement.isEnabled = true
            binding.btnSave.isEnabled = true
        } else {
            binding.btnCountMode.text = "カウント開始"
            binding.btnCountMode.setBackgroundColor(requireContext().getColor(R.color.teal_700))
            binding.tvCount.setTextColor(requireContext().getColor(R.color.teal_700))
            binding.btnIncrement.isEnabled = false
            binding.btnDecrement.isEnabled = false
            binding.btnSave.isEnabled = false
        }
    }

    private fun updateIntervalVisibility() {
        val showTimer = selectedIntervalSeconds > 0 && isCountingMode
        binding.cardTimer.visibility = if (showTimer) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}
