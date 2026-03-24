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
import com.example.behavioranalysis.databinding.FragmentDurationModeBinding
import kotlinx.coroutines.launch

/**
 * 持続時間記録法フラグメント
 *
 * 行動の開始〜終了までの時間を計測・記録する。
 * 1セッション中に複数回記録可能。
 * 保存データ: 各回の開始タイムスタンプ・継続時間（ms）
 */
class DurationModeFragment : Fragment() {

    private var _binding: FragmentDurationModeBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase

    private var behaviorId: Long = -1
    private var behaviorName: String = ""
    private var behaviorDefinition: String = ""

    private val entries = mutableListOf<TimingNotesUtil.TimingEntry>()
    private var isMeasuring = false
    private var startMs: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isMeasuring) {
                val elapsed = System.currentTimeMillis() - startMs
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
        _binding = FragmentDurationModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())
        binding.tvBehaviorName.text = behaviorName
        binding.tvBehaviorDefinition.text = behaviorDefinition

        binding.btnToggle.setOnClickListener { toggleMeasurement() }
        binding.btnUndo.setOnClickListener { undoLast() }
        binding.btnSave.setOnClickListener { saveSession() }

        updateUI()
    }

    private fun toggleMeasurement() {
        if (!isMeasuring) {
            // 計測開始
            isMeasuring = true
            startMs = System.currentTimeMillis()
            handler.post(tickRunnable)
        } else {
            // 計測停止 → エントリ追加
            isMeasuring = false
            handler.removeCallbacks(tickRunnable)
            val durationMs = System.currentTimeMillis() - startMs
            entries.add(TimingNotesUtil.TimingEntry(startMs, durationMs))
        }
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
            notes = TimingNotesUtil.encodeDuration(entries)
        )
        lifecycleScope.launch {
            database.behaviorRecordDao().insert(record)
            Toast.makeText(requireContext(), getString(R.string.message_saved), Toast.LENGTH_SHORT).show()
            entries.clear()
            updateUI()
        }
    }

    private fun updateUI() {
        // タイマー表示
        if (!isMeasuring) {
            binding.tvTimer.text = "0.0秒"
            binding.tvStatus.text = if (entries.isEmpty()) "待機中" else "停止中"
        }

        // 回数
        binding.tvOccurrenceCount.text = "第 ${entries.size + if (isMeasuring) 1 else 0} 回"

        // ボタン
        binding.btnToggle.text = if (isMeasuring) "停止" else "計測開始"
        binding.btnToggle.backgroundTintList = requireContext().getColorStateList(
            if (isMeasuring) android.R.color.holo_red_dark else R.color.teal_700
        )
        binding.btnUndo.isEnabled = entries.isNotEmpty() && !isMeasuring
        binding.btnSave.isEnabled = entries.isNotEmpty() && !isMeasuring

        // 記録一覧
        if (entries.isEmpty()) {
            binding.tvRecordsList.text = "（まだ記録がありません）"
            binding.dividerSummary.visibility = View.GONE
            binding.tvSummary.visibility = View.GONE
        } else {
            val lines = entries.mapIndexed { i, e ->
                "第${i + 1}回  ${TimingNotesUtil.formatTimestamp(e.startMs)} 〜 " +
                        "${TimingNotesUtil.formatTimestamp(e.endMs)}  " +
                        "[${TimingNotesUtil.formatSeconds(e.durationSeconds)}]"
            }.joinToString("\n")
            binding.tvRecordsList.text = lines

            binding.dividerSummary.visibility = View.VISIBLE
            binding.tvSummary.visibility = View.VISIBLE
            val avg = TimingNotesUtil.averageSeconds(entries)
            val total = TimingNotesUtil.totalSeconds(entries)
            binding.tvSummary.text =
                "合計: ${TimingNotesUtil.formatSeconds(total)}  " +
                        "平均: ${TimingNotesUtil.formatSeconds(avg)}  " +
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
