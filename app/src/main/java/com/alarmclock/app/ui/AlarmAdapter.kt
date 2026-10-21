package com.alarmclock.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alarmclock.app.data.Alarm
import com.alarmclock.app.databinding.ItemAlarmBinding

/**
 * 闹钟列表适配器
 */
class AlarmAdapter(
    private val onItemClick: (Alarm) -> Unit,
    private val onToggle: (Alarm, Boolean) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val binding = ItemAlarmBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlarmViewHolder(private val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: Alarm) {
            binding.tvTime.text = alarm.getTimeText()

            val label = alarm.label
            if (!label.isNullOrBlank()) {
                binding.tvLabel.text = label
                binding.tvLabel.visibility = android.view.View.VISIBLE
            } else {
                binding.tvLabel.visibility = android.view.View.GONE
            }

            binding.tvRepeatInfo.text = alarm.getRepeatDescription()

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = alarm.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onToggle(alarm, isChecked)
                }
            }

            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(alarm)
                }
            }
        }
    }

    class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
            return oldItem == newItem
        }
    }
}
