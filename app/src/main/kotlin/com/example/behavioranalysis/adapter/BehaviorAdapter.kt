package com.example.behavioranalysis.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.behavioranalysis.R
import com.example.behavioranalysis.data.entity.Behavior

class BehaviorAdapter(
    private val onItemClick: (Behavior) -> Unit,
    private val onItemLongClick: (Behavior) -> Unit = {},
    private val onEditClick: (Behavior) -> Unit = {},
    private val onDeleteClick: (Behavior) -> Unit = {}
) : RecyclerView.Adapter<BehaviorAdapter.BehaviorViewHolder>() {

    private var behaviors: List<Behavior> = emptyList()

    fun updateBehaviors(newBehaviors: List<Behavior>) {
        behaviors = newBehaviors
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BehaviorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_behavior, parent, false)
        return BehaviorViewHolder(view)
    }

    override fun onBindViewHolder(holder: BehaviorViewHolder, position: Int) {
        holder.bind(behaviors[position])
    }

    override fun getItemCount() = behaviors.size

    inner class BehaviorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_behavior_name)
        private val tvDefinition: TextView = itemView.findViewById(R.id.tv_behavior_definition)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)

        fun bind(behavior: Behavior) {
            tvName.text = behavior.name
            tvDefinition.text = behavior.operationalDefinition

            itemView.setOnClickListener { onItemClick(behavior) }
            itemView.setOnLongClickListener {
                onItemLongClick(behavior)
                true
            }

            btnMore.setOnClickListener { anchor ->
                val popup = PopupMenu(anchor.context, anchor)
                popup.menuInflater.inflate(R.menu.menu_item_actions, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> { onEditClick(behavior); true }
                        R.id.action_delete -> { onDeleteClick(behavior); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}
