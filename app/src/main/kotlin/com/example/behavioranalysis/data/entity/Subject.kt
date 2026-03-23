package com.example.behavioranalysis.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 対象者エンティティ
 * 観察対象となる個人の基本情報を管理
 */
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,              // 必須: 対象者名
    val age: Int? = null,          // 任意: 年齢
    val notes: String? = null,     // 任意: メモ（特記事項など）
    val createdAt: Long = System.currentTimeMillis()  // 作成日時
)
