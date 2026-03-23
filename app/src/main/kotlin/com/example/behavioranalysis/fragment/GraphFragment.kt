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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        behaviorId = arguments?.getLong("BEHAVIOR_ID", -1) ?: -1
        behaviorName = arguments?.getString("BEHAVIOR_NAME") ?: ""
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
            binding.tvSummary.text = "行動名: $behaviorName\n合計: 0 回（0 記録）"
            return
        }

        binding.tvNoData.visibility = View.GONE
        binding.lineChart.visibility = View.VISIBLE
        binding.tvSectionDaily.visibility = View.VISIBLE

        val totalCount = records.sumOf { it.count }
        binding.tvSummary.text = "行動名: $behaviorName\n合計: $totalCount 回（${records.size} 記録）"

        setupLineChart(records)
        setupBarChart(records)
    }

    private fun setupLineChart(records: List<BehaviorRecord>) {
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        // 日付ごとにグループ化して合計
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
        // 最新のインターバル記録を取得
        val latestIntervalRecord = records
            .filter { it.notes != null }
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
