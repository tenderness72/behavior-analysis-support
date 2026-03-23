package com.example.behavioranalysis

import android.content.ContentValues
import android.content.Context
import com.example.behavioranalysis.IntervalNotesUtil
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.behavioranalysis.data.entity.BehaviorRecord
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportUtil {

    /**
     * BehaviorRecord リストから CSV 文字列を生成する。
     * 先頭に BOM を付与し、Excel で文字化けしないようにする。
     *
     * 列構成:
     *   対象者名, 行動名, 記録日時, 合計回数, 記録タイプ, インターバル数,
     *   inter1, inter2, ... interN  （全レコード中の最大インターバル数に合わせて動的に生成）
     */
    fun buildCsvContent(
        records: List<BehaviorRecord>,
        subjectName: String,
        behaviorName: String
    ): String {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

        // 各レコードのインターバルカウントを先に解析しておく
        data class ParsedRecord(
            val record: BehaviorRecord,
            val recordType: String,
            val intervalCounts: List<Int>   // 連続記録は空リスト
        )

        val parsed = records.map { record ->
            if (record.notes != null) {
                ParsedRecord(record, "インターバル記録", IntervalNotesUtil.decode(record.notes))
            } else {
                ParsedRecord(record, "連続記録", emptyList())
            }
        }

        // 全レコード中の最大インターバル数 → ヘッダー列数を決定
        val maxIntervals = parsed.maxOfOrNull { it.intervalCounts.size } ?: 0

        val sb = StringBuilder()

        // BOM: Excel で開いたときに文字化けを防ぐ
        sb.append('\uFEFF')

        // ヘッダー行
        val interHeaders = (1..maxIntervals).joinToString(",") { "inter$it" }
        val headerSuffix = if (maxIntervals > 0) ",$interHeaders" else ""
        sb.appendLine("対象者名,行動名,記録日時,合計回数,記録タイプ,インターバル数$headerSuffix")

        // データ行
        for (p in parsed) {
            val date = dateFormat.format(Date(p.record.timestamp))
            val intervalCount = if (p.intervalCounts.isNotEmpty()) p.intervalCounts.size.toString() else ""

            // inter列: 値があれば数値、なければ空文字。最大列数に満たない分は空埋め
            val interCells = if (maxIntervals > 0) {
                "," + (0 until maxIntervals).joinToString(",") { idx ->
                    p.intervalCounts.getOrNull(idx)?.toString() ?: ""
                }
            } else ""

            sb.appendLine(
                "${escapeCsv(subjectName)}," +
                "${escapeCsv(behaviorName)}," +
                "$date," +
                "${p.record.count}," +
                "${p.recordType}," +
                "$intervalCount" +
                interCells
            )
        }

        return sb.toString()
    }

    /**
     * CSV をダウンロードフォルダ（Downloads/Muninn/）に保存し、
     * MediaStore URI を返す。失敗時は null。
     */
    fun saveToDownloads(
        context: Context,
        content: String,
        fileName: String
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Muninn"
            )
        }
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return null

        resolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                writer.write(content)
            }
        }
        return uri
    }

    /**
     * CSV セル値のエスケープ処理:
     * カンマ・ダブルクォート・改行が含まれる場合はダブルクォートで囲む
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /** エクスポートファイル名を生成する（例: muninn_対象者名_行動名_20260323_143022.csv） */
    fun buildFileName(subjectName: String, behaviorName: String): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safe = Regex("[\\\\/:*?\"<>|]")
        val safeSub = safe.replace(subjectName, "_")
        val safeBeh = safe.replace(behaviorName, "_")
        return "muninn_${safeSub}_${safeBeh}_$dateStr.csv"
    }
}
