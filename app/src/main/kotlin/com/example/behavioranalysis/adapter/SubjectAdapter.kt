package com.example.behavioranalysis.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.behavioranalysis.R
import com.example.behavioranalysis.data.entity.Subject

class SubjectAdapter(
    private val onItemClick: (Subject) -> Unit
) : RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {

    private var subjects: List<Subject> = emptyList()

    fun updateSubjects(newSubjects: List<Subject>) {
        subjects = newSubjects
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(subjects[position])
    }

    override fun getItemCount() = subjects.size

    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_subject_name)
        private val tvAge: TextView = itemView.findViewById(R.id.tv_subject_age)
        private val tvNotes: TextView = itemView.findViewById(R.id.tv_subject_notes)

        fun bind(subject: Subject) {
            tvName.text = subject.name

            if (subject.age != null) {
                tvAge.visibility = View.VISIBLE
                tvAge.text = "${subject.age}歳"
            } else {
                tvAge.visibility = View.GONE
            }

            if (!subject.notes.isNullOrEmpty()) {
                tvNotes.visibility = View.VISIBLE
                tvNotes.text = subject.notes
            } else {
                tvNotes.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(subject) }
        }
    }
}
