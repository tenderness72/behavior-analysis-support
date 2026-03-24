package com.example.behavioranalysis

import java.text.SimpleDateFormat
import java.util.*

/**
 * 持続時間記録法・潜時記録法のエンコード/デコードユーティリティ
 *
 * BehaviorRecord.notes に格納する形式:
 *   持続時間: "持続時間: startMs:durationMs,startMs:durationMs,..."
 *   潜  時 : "潜時: stimulusMs:latencyMs,stimulusMs:latencyMs,..."
 *
 * endMs / responseMs は startMs + durationMs（or latencyMs）で復元可能
 * 時間の単位はすべてミリ秒（Long）
 */
object TimingNotesUtil {

    private const val DURATION_PREFIX = "持続時間: "
    private const val LATENCY_PREFIX  = "潜時: "

    /** 1回分のタイミングエントリ */
    data class TimingEntry(
        val startMs: Long,       // 開始タイムスタンプ（持続時間）or 刺激提示タイムスタンプ（潜時）
        val durationMs: Long     // 継続時間 or 潜時（ms）
    ) {
        val endMs: Long get() = startMs + durationMs
        val durationSeconds: Double get() = durationMs / 1000.0
    }

    // ---- エンコード ----

    fun encodeDuration(entries: List<TimingEntry>): String =
        DURATION_PREFIX + entries.joinToString(",") { "${it.startMs}:${it.durationMs}" }

    fun encodeLatency(entries: List<TimingEntry>): String =
        LATENCY_PREFIX + entries.joinToString(",") { "${it.startMs}:${it.durationMs}" }

    // ---- デコード ----

    fun decode(notes: String): List<TimingEntry> {
        val data = when {
            notes.startsWith(DURATION_PREFIX) -> notes.removePrefix(DURATION_PREFIX)
            notes.startsWith(LATENCY_PREFIX)  -> notes.removePrefix(LATENCY_PREFIX)
            else -> return emptyList()
        }
        if (data.isBlank()) return emptyList()
        return data.split(",").mapNotNull { entry ->
            val parts = entry.trim().split(":")
            if (parts.size == 2) {
                val start = parts[0].toLongOrNull() ?: return@mapNotNull null
                val dur   = parts[1].toLongOrNull() ?: return@mapNotNull null
                TimingEntry(start, dur)
            } else null
        }
    }

    // ---- 判定 ----

    fun isDuration(notes: String?): Boolean = notes?.startsWith(DURATION_PREFIX) == true
    fun isLatency(notes: String?):  Boolean = notes?.startsWith(LATENCY_PREFIX)  == true

    // ---- 集計 ----

    fun averageSeconds(entries: List<TimingEntry>): Double =
        if (entries.isEmpty()) 0.0 else entries.map { it.durationSeconds }.average()

    fun totalSeconds(entries: List<TimingEntry>): Double =
        entries.sumOf { it.durationSeconds }

    fun maxSeconds(entries: List<TimingEntry>): Double =
        entries.maxOfOrNull { it.durationSeconds } ?: 0.0

    fun minSeconds(entries: List<TimingEntry>): Double =
        entries.minOfOrNull { it.durationSeconds } ?: 0.0

    // ---- フォーマット ----

    /** 秒数を "mm:ss.t" 形式に変換 */
    fun formatSeconds(seconds: Double): String {
        val totalDs = (seconds * 10).toLong()
        val min = totalDs / 600
        val sec = (totalDs % 600) / 10
        val ds  = totalDs % 10
        return if (min > 0) "%d:%02d.%d".format(min, sec, ds)
        else "%d.%d秒".format(sec, ds)
    }

    /** タイムスタンプを "HH:mm:ss" 形式に変換 */
    fun formatTimestamp(ms: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ms))
    }
}
