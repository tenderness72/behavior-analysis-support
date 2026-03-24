package com.example.behavioranalysis.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.behavioranalysis.R
import com.example.behavioranalysis.TimingNotesUtil
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.BehaviorRecord
import com.example.behavioranalysis.databinding.FragmentLatencyModeBinding
import kotlinx.coroutines.launch

/**
 * 潜時記録法フラグメント
 *
 * 刺激提示〜反応開始までの時間を計測・記録する。
 * 1セッション中に複数試行を積み上げ可能。
 * 保存データ: 各試行の刺激提示タイムスタンプ・潜時（ms）
 */
class LatencyModeFragment : Fragment() {

    private var _binding: FragmentLatencyModeBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase

    private var behaviorId: Long = -1
    private var behaviorName: String = ""
    private var behaviorDefinition: String = ""

    private val entries = mutableListOf<TimingNotesUtil.TimingEntry>()
    private var waitingForResponse = false
    private var stimulusMs: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (waitingForResponse) {
                val elapsed = System.currentTimeMillis() - stimulusMs
                _binding?.tvTimer?.text = TimingNotesUtil.formatSeconds(elapsed / 1000.0)
                handler.postDelayed(this, 100)
            }
        }
    }

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
        _binding = FragmentLatencyModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())
        binding.tvBehaviorName.text = behaviorName
        binding.tvBehaviorDefinition.text = behaviorDefinition

        binding.btnStimulus.setOnClickListener { onStimulusPressed() }
        binding.btnResponse.setOnClickListener { onResponsePressed() }
        binding.btnUndo.setOnClickListener { undoLast() }
        binding.btnSave.setOnClickListener { saveSession() }

        updateUI()
    }

    private fun onStimulusPressed() {
        if (waitingForResponse) return   // 二重押し防止
        waitingForResponse = true
        stimulusMs = System.currentTimeMillis()
        handler.post(tickRunnable)
        updateUI()
    }

    private fun onResponsePressed() {
        if (!waitingForResponse) return
        handler.removeCallbacks(tickRunnable)
        val latencyMs = System.currentTimeMillis() - stimulusMs
        entries.add(TimingNotesUtil.TimingEntry(stimulusMs, latencyMs))
        waitingForResponse = false
        updateUI()
    }

    private fun undoLast() {
        if (entries.isNotEmpty()) {
            entries.removeAt(entries.lastIndex)
            updateUI()
        }
    }

    private fun saveSession() {
        if (entries.isEmpty()) {
            Toast.makeText(requireContext(), "記録がありません", Toast.LENGTH_SHORT).show()
            return
        }
        val record = BehaviorRecord(
            behaviorId = behaviorId,
            count = entries.size,
            notes = TimingNotesUtil.encodeLatency(entries)
        )
        lifecycleScope.launch {
            database.behaviorRecordDao().insert(record)
            Toast.makeText(requireContext(), getString(R.string.message_saved), Toast.LENGTH_SHORT).show()
            entries.clear()
            waitingForResponse = false
            updateUI()
        }
    }

    private fun updateUI() {
        val trialNum = entries.size + if (waitingForResponse) 1 else 0
        binding.tvTrialCount.text = "第 $trialNum 試行"

        if (!waitingForResponse) {
            binding.tvTimer.text = "0.0秒"
        }

        // ステータスメッセージ
        binding.tvStatus.text = when {
            waitingForResponse -> "反応開始ボタンを押してください"
            entries.isEmpty()  -> "刺激提示ボタンを押してください"
            else               -> "次の試行: 刺激提示ボタンを押してください"
        }

        // ボタン状態
        binding.btnStimulus.isEnabled = !waitingForResponse
        binding.btnResponse.isEnabled = waitingForResponse
        binding.btnUndo.isEnabled = entries.isNotEmpty() && !waitingForResponse
        binding.btnSave.isEnabled = entries.isNotEmpty() && !waitingForResponse

        // 記録一覧
        if (entries.isEmpty()) {
            binding.tvRecordsList.text = "（まだ記録がありません）"
            binding.dividerSummary.visibility = View.GONE
            binding.tvSummary.visibility = View.GONE
        } else {
            val lines = entries.mapIndexed { i, e ->
                "第${i + 1}試行  刺激: ${TimingNotesUtil.formatTimestamp(e.startMs)}  " +
                        "反応: ${TimingNotesUtil.formatTimestamp(e.endMs)}  " +
                        "[${TimingNotesUtil.formatSeconds(e.durationSeconds)}]"
            }.joinToString("\n")
            binding.tvRecordsList.text = lines

            binding.dividerSummary.visibility = View.VISIBLE
            binding.tvSummary.visibility = View.VISIBLE
            val avg = TimingNotesUtil.averageSeconds(entries)
            binding.tvSummary.text =
                "試行数: ${entries.size}  " +
                        "平均潜時: ${TimingNotesUtil.formatSeconds(avg)}  " +
                        "最長: ${TimingNotesUtil.formatSeconds(TimingNotesUtil.maxSeconds(entries))}  " +
                        "最短: ${TimingNotesUtil.formatSeconds(TimingNotesUtil.minSeconds(entries))}"
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroyView()
        _binding = null
    }
}
