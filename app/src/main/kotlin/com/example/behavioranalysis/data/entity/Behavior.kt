package com.example.behavioranalysis.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 行動エンティティ
 * 観察対象となる標的行動の定義を管理
 */
@Entity(
    tableName = "behaviors",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE  // 対象者削除時に行動も削除
        )
    ],
    indices = [Index("subjectId")]
)
data class Behavior(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val subjectId: Long,                    // 外部キー: 対象者ID
    val name: String,                       // 必須: 行動名（例: 「離席」）
    val operationalDefinition: String,      // 必須: 操作的定義
    val createdAt: Long = System.currentTimeMillis(),

    // 記録法: "EVENT"（事象記録法）/ "TRIAL"（試行記録）
    val recordType: String = "EVENT",

    // 試行設定（TRIAL のみ使用）: "FREE"（自由終了）/ "FIXED:N"（N試行固定）
    val trialSettings: String? = null
)
