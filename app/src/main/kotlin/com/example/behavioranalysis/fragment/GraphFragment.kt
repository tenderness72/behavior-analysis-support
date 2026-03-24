package com.example.behavioranalysis.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.behavioranalysis.IntervalNotesUtil
import com.example.behavioranalysis.TimingNotesUtil
import com.example.behavioranalysis.TrialNotesUtil
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.BehaviorRecord
import com.example.behavioranalysis.databinding.FragmentGraphBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class GraphFragment : Fragment() {

    private var _binding: FragmentGraphBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase
    private var behaviorId: Long = -1
    private var behaviorName: String = ""
    private var recordType: String = "EVENT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        behaviorId = arguments?.getLong("BEHAVIOR_ID", -1) ?: -1
        behaviorName = arguments?.getString("BEHAVIOR_NAME") ?: ""
        recordType = arguments?.getString("RECORD_TYPE") ?: "EVENT"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraphBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.behaviorRecordDao().getRecordsByBehavior(behaviorId).collect { records ->
                    updateUI(records)
                }
            }
        }
    }

    private fun updateUI(records: List<BehaviorRecord>) {
        if (records.isEmpty()) {
            binding.tvNoData.visibility = View.VISIBLE
            binding.lineChart.visibility = View.GONE
            binding.barChart.visibility = View.GONE
            binding.tvSectionDaily.visibility = View.GONE
            binding.tvSectionInterval.visibility = View.GONE
            binding.tvSummary.text = "行動名: $behaviorName\n合計: 0 記録"
            return
        }

        binding.tvNoData.visibility = View.GONE

        when (recordType) {
            "TRIAL"    -> updateTrialUI(records)
            "DURATION" -> updateTimingUI(records, isDuration = true)
            "LATENCY"  -> updateTimingUI(records, isDuration = false)
            else       -> updateEventUI(records)
        }
    }

    // ---- 試行記録: 正答率グラフ ----

    private fun updateTrialUI(records: List<BehaviorRecord>) {
        val trialRecords = records.filter { TrialNotesUtil.isTrial(it.notes) }

        if (trialRecords.isEmpty()) {
            binding.tvNoData.visibility = View.VISIBLE
            binding.lineChart.visibility = View.GONE
            binding.barChart.visibility = View.GONE
            binding.tvSectionDaily.visibility = View.GONE
            binding.tvSectionInterval.visibility = View.GONE
            binding.tvSummary.text = "行動名: $behaviorName\n試行記録: 0 セッション"
            return
        }

        val latestRate = TrialNotesUtil.accuracyRateFromNotes(trialRecords.last().notes!!)
        val maxRate = trialRecords.maxOf { TrialNotesUtil.accuracyRateFromNotes(it.notes!!) }

        binding.tvSummary.text = "行動名: $behaviorName\n" +
                "セッション数: ${trialRecords.size}  " +
                "最新正答率: %.1f%%  最高: %.1f%%".format(latestRate, maxRate)

        binding.lineChart.visibility = View.VISIBLE
        binding.tvSectionDaily.visibility = View.VISIBLE
        binding.tvSectionDaily.text = "セッション別正答率"
        binding.barChart.visibility = View.GONE
        binding.tvSectionInterval.visibility = View.GONE

        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        val labels = trialRecords.mapIndexed { i, r ->
            "S${i + 1}(${dateFormat.format(r.timestamp)})"
        }
        val entries = trialRecords.mapIndexed { index, record ->
            Entry(index.toFloat(), TrialNotesUtil.accuracyRateFromNotes(record.notes!!).toFloat())
        }

        val dataSet = LineDataSet(entries, "正答率 (%)").apply {
            color = Color.rgb(46, 125, 50)
            lineWidth = 2f
            circleRadius = 5f
            setCircleColor(Color.rgb(46, 125, 50))
            valueTextSize = 10f
            setDrawValues(true)
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = true
            animateX(800)
            invalidate()
        }
    }

    // ---- 持続時間 / 潜時: セッション別平均グラフ ----

    private fun updateTimingUI(records: List<BehaviorRecord>, isDuration: Boolean) {
        val filtered = records.filter {
            if (isDuration) TimingNotesUtil.isDuration(it.notes)
            else TimingNotesUtil.isLatency(it.notes)
        }

        if (filtered.isEmpty()) {
            binding.tvNoData.visibility = View.VISIBLE
            binding.lineChart.visibility = View.GONE
            binding.barChart.visibility = View.GONE
            binding.tvSectionDaily.visibility = View.GONE
            binding.tvSectionInterval.visibility = View.GONE
            binding.tvSummary.text = "行動名: $behaviorName\n記録: 0 セッション"
            return
        }

        val label = if (isDuration) "平均持続時間 (秒)" else "平均潜時 (秒)"
        val sectionLabel = if (isDuration) "セッション別平均持続時間" else "セッション別平均潜時"

        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        val labels = filtered.mapIndexed { i, r -> "S${i + 1}(${dateFormat.format(r.timestamp)})" }
        val entries = filtered.mapIndexed { index, record ->
            val avg = TimingNotesUtil.averageSeconds(TimingNotesUtil.decode(record.notes!!))
            Entry(index.toFloat(), avg.toFloat())
        }

        val overallAvg = entries.map { it.y }.average()
        val latestAvg = entries.lastOrNull()?.y ?: 0f
        binding.tvSummary.text = "行動名: $behaviorName\nセッション数: ${filtered.size}  " +
                "最新平均: ${"%.2f".format(latestAvg)}秒  全体平均: ${"%.2f".format(overallAvg)}秒"

        binding.lineChart.visibility = View.VISIBLE
        binding.tvSectionDaily.visibility = View.VISIBLE
        binding.tvSectionDaily.text = sectionLabel
        binding.barChart.visibility = View.GONE
        binding.tvSectionInterval.visibility = View.GONE

        val color = if (isDuration) Color.rgb(21, 101, 192) else Color.rgb(130, 0, 130)
        val dataSet = LineDataSet(entries, label).apply {
            this.color = color
            lineWidth = 2f
            circleRadius = 5f
            setCircleColor(color)
            valueTextSize = 10f
            setDrawValues(true)
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = true
            animateX(800)
            invalidate()
        }
    }

    // ---- 事象記録: 従来の頻度グラフ ----

    private fun updateEventUI(records: List<BehaviorRecord>) {
        binding.lineChart.visibility = View.VISIBLE
        binding.tvSectionDaily.visibility = View.VISIBLE
        binding.tvSectionDaily.text = "日ごとの発生回数"

        val totalCount = records.sumOf { it.count }
        binding.tvSummary.text = "行動名: $behaviorName\n合計: $totalCount 回（${records.size} 記録）"

        setupLineChart(records)
        setupBarChart(records)
    }

    private fun setupLineChart(records: List<BehaviorRecord>) {
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        val dailyData = records
            .groupBy {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = it.timestamp
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            .mapValues { entry -> entry.value.sumOf { it.count } }
            .toSortedMap()

        val labels = dailyData.keys.map { dateFormat.format(it) }
        val entries = dailyData.values.mapIndexed { index, count ->
            Entry(index.toFloat(), count.toFloat())
        }

        val dataSet = LineDataSet(entries, "日ごとの発生回数").apply {
            color = Color.BLUE
            lineWidth = 2f
            circleRadius = 4f
            setCircleColor(Color.BLUE)
            valueTextSize = 10f
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = true
            animateX(1000)
            invalidate()
        }
    }

    private fun setupBarChart(records: List<BehaviorRecord>) {
        val latestIntervalRecord = records
            .filter { it.notes != null && !TrialNotesUtil.isTrial(it.notes) }
            .maxByOrNull { it.timestamp }

        if (latestIntervalRecord == null) {
            binding.barChart.visibility = View.GONE
            binding.tvSectionInterval.visibility = View.GONE
            return
        }

        binding.barChart.visibility = View.VISIBLE
        binding.tvSectionInterval.visibility = View.VISIBLE

        val intervalCounts = latestIntervalRecord.notes
            ?.let { IntervalNotesUtil.decode(it) }
            ?: emptyList()

        if (intervalCounts.isEmpty()) {
            binding.barChart.visibility = View.GONE
            return
        }

        val entries = intervalCounts.mapIndexed { index, count ->
            BarEntry((index + 1).toFloat(), count.toFloat())
        }

        val dataSet = BarDataSet(entries, "インターバルごとの発生回数").apply {
            color = Color.GREEN
            valueTextSize = 10f
        }

        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(latestIntervalRecord.timestamp)

        binding.barChart.apply {
            data = BarData(dataSet)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
            }
            axisRight.isEnabled = false
            description.apply {
                isEnabled = true
                text = "最新の記録（$dateStr）"
            }
            legend.isEnabled = true
            animateY(1000)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
