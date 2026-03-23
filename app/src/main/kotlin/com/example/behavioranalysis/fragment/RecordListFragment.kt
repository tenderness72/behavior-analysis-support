package com.example.behavioranalysis.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.behavioranalysis.CsvExportUtil
import com.example.behavioranalysis.adapter.RecordAdapter
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.BehaviorRecord
import com.example.behavioranalysis.databinding.FragmentRecordListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class RecordListFragment : Fragment() {

    private var _binding: FragmentRecordListBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase
    private lateinit var recordAdapter: RecordAdapter
    private var behaviorId: Long = -1
    private var behaviorName: String = ""
    private var subjectName: String = ""

    /** Flow から最新の記録リストをキャッシュ（エクスポート時に使用） */
    private var currentRecords: List<BehaviorRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        behaviorId = arguments?.getLong("BEHAVIOR_ID", -1) ?: -1
        behaviorName = arguments?.getString("BEHAVIOR_NAME") ?: ""
        subjectName = arguments?.getString("SUBJECT_NAME") ?: ""
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
                currentRecords = records
                recordAdapter.updateRecords(records)
                binding.tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.fabExportCsv.setOnClickListener {
            exportCsv()
        }
    }

    // ── CSV エクスポート ──────────────────────────────────────────────

    private fun exportCsv() {
        if (currentRecords.isEmpty()) {
            Toast.makeText(requireContext(), "エクスポートするデータがありません", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val csvContent = CsvExportUtil.buildCsvContent(currentRecords, subjectName, behaviorName)
            val fileName = CsvExportUtil.buildFileName(subjectName, behaviorName)

            val uri = withContext(Dispatchers.IO) {
                CsvExportUtil.saveToDownloads(requireContext(), csvContent, fileName)
            }

            if (uri != null) {
                // 保存成功 → 共有オプションを提示
                Toast.makeText(
                    requireContext(),
                    "Downloads/Muninn/$fileName に保存しました",
                    Toast.LENGTH_LONG
                ).show()

                AlertDialog.Builder(requireContext())
                    .setTitle("CSV を保存しました")
                    .setMessage("Downloads/Muninn/ に保存しました。\n共有しますか？")
                    .setPositiveButton("共有") { _, _ ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "CSV を共有"))
                    }
                    .setNegativeButton("閉じる", null)
                    .show()
            } else {
                Toast.makeText(requireContext(), "エクスポートに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 記録詳細ダイアログ ───────────────────────────────────────────

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

    // ── 削除ダイアログ ────────────────────────────────────────────────

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
