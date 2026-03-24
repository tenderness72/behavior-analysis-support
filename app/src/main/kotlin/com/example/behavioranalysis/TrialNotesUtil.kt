package com.example.behavioranalysis

/**
 * 試行記録のエンコード/デコードユーティリティ
 *
 * BehaviorRecord.notes に格納する形式:
 *   "試行: +,±,-,+,+" （各試行の判定をカンマ区切り）
 *
 * 判定記号:
 *   "+"  オペラント正反応
 *   "±"  プロンプトあり正反応
 *   "-"  誤反応
 *
 * 正答率 = (+ の数 + ± の数) / 全試行数 × 100
 */
object TrialNotesUtil {

    const val CORRECT   = "+"
    const val PROMPTED  = "±"
    const val INCORRECT = "-"

    private const val PREFIX = "試行: "

    /** 試行結果リスト → notes 文字列 */
    fun encode(results: List<String>): String =
        "$PREFIX${results.joinToString(",")}"

    /** notes 文字列 → 試行結果リスト */
    fun decode(notes: String): List<String> {
        val data = notes.substringAfter(PREFIX, missingDelimiterValue = "")
        if (data.isEmpty()) return emptyList()
        return data.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 試行記録かどうか判定 */
    fun isTrial(notes: String?): Boolean = notes?.startsWith(PREFIX) == true

    /** 正反応数（＋のみ） */
    fun correctCount(results: List<String>): Int = results.count { it == CORRECT }

    /** プロンプトあり正反応数 */
    fun promptedCount(results: List<String>): Int = results.count { it == PROMPTED }

    /** 誤反応数 */
    fun incorrectCount(results: List<String>): Int = results.count { it == INCORRECT }

    /** 正答率 (%) = (＋ + ±) / 全試行 × 100 */
    fun accuracyRate(results: List<String>): Double {
        if (results.isEmpty()) return 0.0
        return (correctCount(results) + promptedCount(results)).toDouble() / results.size * 100.0
    }

    /** notes から直接正答率を返す（変換不要の場合） */
    fun accuracyRateFromNotes(notes: String): Double = accuracyRate(decode(notes))
}
