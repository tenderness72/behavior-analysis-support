package com.example.behavioranalysis.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.behavioranalysis.adapter.RecordAdapter
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.BehaviorRecord
import com.example.behavioranalysis.databinding.FragmentRecordListBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class RecordListFragment : Fragment() {

    private var _binding: FragmentRecordListBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase
    private lateinit var recordAdapter: RecordAdapter
    private var behaviorId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        behaviorId = arguments?.getLong("BEHAVIOR_ID", -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getDatabase(requireContext())

        recordAdapter = RecordAdapter(
            onItemClick = { record -> showDetailDialog(record) },
            onItemLongClick = { record -> showDeleteDialog(record) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recordAdapter
        }

        lifecycleScope.launch {
            database.behaviorRecordDao().getRecordsByBehavior(behaviorId).collect { records ->
                recordAdapter.updateRecords(records)
                binding.tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showDetailDialog(record: BehaviorRecord) {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val message = buildString {
            appendLine("日時: ${dateFormat.format(record.timestamp)}")
            appendLine("合計回数: ${record.count}回")
            if (record.notes != null) {
                appendLine()
                appendLine("インターバル記録:")
                val countsStr = record.notes.substringAfter("各回数: ")
                val counts = countsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                counts.forEachIndexed { index, count ->
                    appendLine("  第${index + 1}インターバル: ${count}回")
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("記録詳細")
            .setMessage(message)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showDeleteDialog(record: BehaviorRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("記録を削除")
            .setMessage("この記録を削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                lifecycleScope.launch {
                    database.behaviorRecordDao().delete(record)
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
