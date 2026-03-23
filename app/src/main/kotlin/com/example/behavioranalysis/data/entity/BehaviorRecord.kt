package com.example.behavioranalysis.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 行動記録エンティティ
 * 観察セッションの記録データを管理
 *
 * notes フィールドの形式:
 * - 連続記録: null
 * - インターバル記録: "インターバル: 25回, 各回数: 2,1,3,0,2,..."
 */
@Entity(
    tableName = "behavior_records",
    foreignKeys = [
        ForeignKey(
            entity = Behavior::class,
            parentColumns = ["id"],
            childColumns = ["behaviorId"],
            onDelete = ForeignKey.CASCADE  // 行動削除時に記録も削除
        )
    ],
    indices = [Index("behaviorId")]
)
data class BehaviorRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val behaviorId: Long,                   // 外部キー: 行動ID
    val timestamp: Long = System.currentTimeMillis(),  // 記録開始日時
    val count: Int = 1,                     // 合計発生回数
    val notes: String? = null               // インターバルデータ等
)
