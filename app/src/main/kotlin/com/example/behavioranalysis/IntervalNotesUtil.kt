package com.example.behavioranalysis

/**
 * BehaviorRecord.notes フィールドのエンコード／デコードを一元管理するユーティリティ。
 *
 * フォーマット例: "インターバル: 3回, 各回数: 2,1,3"
 *
 * このオブジェクトを経由することで、フォーマット変更時の修正箇所を1か所に限定できる。
 */
object IntervalNotesUtil {

    /** インターバル記録リストを notes 文字列にエンコードする */
    fun encode(counts: List<Int>): String =
        "インターバル: ${counts.size}回, 各回数: ${counts.joinToString(",")}"

    /** notes 文字列から各インターバルの発生回数リストを復元する */
    fun decode(notes: String): List<Int> {
        val countsStr = notes.substringAfter("各回数: ", missingDelimiterValue = "")
        return countsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    /** notes 文字列からインターバル数（区切り数）だけを返す */
    fun intervalCount(notes: String): Int = decode(notes).size
}
