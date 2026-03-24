package com.example.behavioranalysis.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.behavioranalysis.R
import com.example.behavioranalysis.TrialNotesUtil
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.BehaviorRecord
import com.example.behavioranalysis.databinding.FragmentTrialModeBinding
import kotlinx.coroutines.launch

/**
 * 試行記録フラグメント
 *
 * 判定: ＋（オペラント正反応）/ ±（プロンプトあり正反応）/ −（誤反応）
 * 正答率 = (＋ + ±) / 全試行 × 100
 * 試行モード: FREE（自由終了）/ FIXED:N（N試行で自動完了）
 */
class TrialModeFragment : Fragment() {

    private var _binding: FragmentTrialModeBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase

    private var behaviorId: Long = -1
    private var behaviorName: String = ""
    private var behaviorDefinition: String = ""
    private var trialSettings: String = "FREE"   // "FREE" or "FIXED:N"

    private val trialResults: MutableList<String> = mutableListOf()
    private var isRecording: Boolean = false
    private var fixedCount: Int = 0  // 0 = FREE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        behaviorId = arguments?.getLong("BEHAVIOR_ID", -1) ?: -1
        behaviorName = arguments?.getString("BEHAVIOR_NAME") ?: ""
        behaviorDefinition = arguments?.getString("BEHAVIOR_DEFINITION") ?: ""
        trialSettings = arguments?.getString("TRIAL_SETTINGS") ?: "FREE"

        fixedCount = if (trialSettings.startsWith("FIXED:")) {
            trialSettings.removePrefix("FIXED:").toIntOrNull() ?: 0
        } else 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrialModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getDatabase(requireContext())

        binding.tvBehaviorName.text = behaviorName
        binding.tvBehaviorDefinition.text = behaviorDefinition

        setupButtons()
        updateUI()
    }

    private fun setupButtons() {
        binding.btnStartTrial.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                // 自由終了モードの場合、途中終了の確認
                if (fixedCount == 0 && trialResults.isNotEmpty()) {
                    stopRecording()
                }
            }
        }

        binding.btnCorrect.setOnClickListener   { recordTrial(TrialNotesUtil.CORRECT) }
        binding.btnPrompted.setOnClickListener  { recordTrial(TrialNotesUtil.PROMPTED) }
        binding.btnIncorrect.setOnClickListener { recordTrial(TrialNotesUtil.INCORRECT) }

        binding.btnUndo.setOnClickListener {
            if (trialResults.isNotEmpty()) {
                trialResults.removeAt(trialResults.lastIndex)
                updateUI()
            }
        }

        binding.btnSaveTrial.setOnClickListener { saveSession() }
    }

    private fun startRecording() {
        isRecording = true
        trialResults.clear()
        updateUI()
    }

    private fun stopRecording() {
        isRecording = false
        updateUI()
    }

    private fun recordTrial(result: String) {
        if (!isRecording) return
        trialResults.add(result)

        // 固定モード: 指定試行数に達したら自動完了
        if (fixedCount > 0 && trialResults.size >= fixedCount) {
            stopRecording()
        } else {
            updateUI()
        }
    }

    private fun saveSession() {
        if (trialResults.isEmpty()) {
            Toast.makeText(requireContext(), "試行がありません", Toast.LENGTH_SHORT).show()
            return
        }

        val record = BehaviorRecord(
            behaviorId = behaviorId,
            count = trialResults.size,
            notes = TrialNotesUtil.encode(trialResults)
        )

        lifecycleScope.launch {
            database.behaviorRecordDao().insert(record)
            Toast.makeText(requireContext(), getString(R.string.message_saved), Toast.LENGTH_SHORT).show()
            // リセット
            trialResults.clear()
            isRecording = false
            updateUI()
        }
    }

    private fun updateUI() {
        val total = trialResults.size
        val isFixed = fixedCount > 0

        // 試行カウント表示
        binding.tvTrialCount.text = "第 $total 試行"
        binding.tvTrialRemaining.text = if (isFixed) {
            "残り ${fixedCount - total} 試行"
        } else ""

        // 正答率
        if (total > 0) {
            val rate = TrialNotesUtil.accuracyRate(trialResults)
            binding.tvAccuracy.text = "正答率 %.1f%%".format(rate)
        } else {
            binding.tvAccuracy.text = "正答率 --.--%"
        }

        // 試行履歴
        if (trialResults.isEmpty()) {
            binding.tvTrialHistory.text = "（まだ試行がありません）"
        } else {
            // 直近20件をグループ表示（5試行ごとに空白区切り）
            val display = trialResults.mapIndexed { i, r ->
                if (i > 0 && i % 5 == 0) " | $r" else r
            }.joinToString("")
            val correct = TrialNotesUtil.correctCount(trialResults)
            val prompted = TrialNotesUtil.promptedCount(trialResults)
            val incorrect = TrialNotesUtil.incorrectCount(trialResults)
            binding.tvTrialHistory.text = "$display\n＋:$correct  ±:$prompted  −:$incorrect"
        }

        // ボタン状態
        val canRecord = isRecording && (fixedCount == 0 || total < fixedCount)
        binding.btnCorrect.isEnabled = canRecord
        binding.btnPrompted.isEnabled = canRecord
        binding.btnIncorrect.isEnabled = canRecord
        binding.btnUndo.isEnabled = isRecording && total > 0

        // 開始ボタン
        binding.btnStartTrial.text = when {
            !isRecording && total == 0 -> "記録開始"
            isRecording && fixedCount == 0 -> "記録を終了する"
            isRecording -> "記録中…"
            else -> "記録開始"
        }
        binding.btnStartTrial.isEnabled = !(isRecording && fixedCount > 0)

        // 保存ボタン
        binding.btnSaveTrial.isEnabled = !isRecording && total > 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
