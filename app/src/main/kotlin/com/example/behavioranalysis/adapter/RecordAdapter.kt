package com.example.behavioranalysis.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.behavioranalysis.IntervalNotesUtil
import com.example.behavioranalysis.R
import com.example.behavioranalysis.data.entity.BehaviorRecord
import java.text.SimpleDateFormat
import java.util.Locale

class RecordAdapter(
    private val onItemClick: (BehaviorRecord) -> Unit = {},
    private val onItemLongClick: (BehaviorRecord) -> Unit = {}
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    private var records: List<BehaviorRecord> = emptyList()
    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    fun updateRecords(newRecords: List<BehaviorRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount() = records.size

    inner class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_record_date)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_record_count)
        private val tvInterval: TextView = itemView.findViewById(R.id.tv_record_interval)

        fun bind(record: BehaviorRecord) {
            tvDate.text = dateFormat.format(record.timestamp)
            tvCount.text = "合計: ${record.count}回"

            if (record.notes != null) {
                tvInterval.visibility = View.VISIBLE
                val intervalCount = IntervalNotesUtil.intervalCount(record.notes)
                tvInterval.text = "インターバル記録 (${intervalCount}回)"
            } else {
                tvInterval.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(record) }
            itemView.setOnLongClickListener {
                onItemLongClick(record)
                true
            }
        }
    }
}
