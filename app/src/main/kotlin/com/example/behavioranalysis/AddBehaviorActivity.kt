package com.example.behavioranalysis

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.Behavior
import com.example.behavioranalysis.databinding.ActivityAddBehaviorBinding
import kotlinx.coroutines.launch

class AddBehaviorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBehaviorBinding
    private lateinit var database: AppDatabase
    private var subjectId: Long = -1

    /** 編集モード時に既存エンティティを保持する（null = 新規作成） */
    private var editingBehavior: Behavior? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBehaviorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        subjectId = intent.getLongExtra("SUBJECT_ID", -1)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        database = AppDatabase.getDatabase(this)

        // 編集モード: intent に BEHAVIOR_ID が渡されていれば既存データを復元
        val behaviorId = intent.getLongExtra("BEHAVIOR_ID", -1L)
        if (behaviorId != -1L) {
            supportActionBar?.title = "行動を編集"
            val recordType = intent.getStringExtra("BEHAVIOR_RECORD_TYPE") ?: "EVENT"
            val trialSettings = intent.getStringExtra("BEHAVIOR_TRIAL_SETTINGS")
            editingBehavior = Behavior(
                id = behaviorId,
                subjectId = subjectId,
                name = intent.getStringExtra("BEHAVIOR_NAME") ?: "",
                operationalDefinition = intent.getStringExtra("BEHAVIOR_DEFINITION") ?: "",
                createdAt = intent.getLongExtra("BEHAVIOR_CREATED_AT", System.currentTimeMillis()),
                recordType = recordType,
                trialSettings = trialSettings
            )
            binding.etBehaviorName.setText(editingBehavior!!.name)
            binding.etDefinition.setText(editingBehavior!!.operationalDefinition)
            restoreRecordTypeUI(recordType, trialSettings)
        } else {
            supportActionBar?.title = "行動を追加"
        }

        setupRecordTypeUI()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, navBar.bottom)
            insets
        }

        binding.btnSave.setOnClickListener { saveBehavior() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun setupRecordTypeUI() {
        binding.rgRecordType.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutTrialOptions.visibility =
                if (checkedId == R.id.rb_trial) View.VISIBLE else View.GONE
        }
        binding.rgTrialMode.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutFixedCount.visibility =
                if (checkedId == R.id.rb_trial_fixed) View.VISIBLE else View.GONE
        }
    }

    private fun restoreRecordTypeUI(recordType: String, trialSettings: String?) {
        when (recordType) {
            "TRIAL" -> {
                binding.rbTrial.isChecked = true
                binding.layoutTrialOptions.visibility = View.VISIBLE
                if (trialSettings != null && trialSettings.startsWith("FIXED:")) {
                    binding.rbTrialFixed.isChecked = true
                    binding.layoutFixedCount.visibility = View.VISIBLE
                    binding.etTrialCount.setText(trialSettings.removePrefix("FIXED:"))
                } else {
                    binding.rbTrialFree.isChecked = true
                }
            }
            "DURATION" -> binding.rbDuration.isChecked = true
            "LATENCY"  -> binding.rbLatency.isChecked = true
            else       -> binding.rbEvent.isChecked = true
        }
    }

    private fun saveBehavior() {
        val name = binding.etBehaviorName.text?.toString()?.trim() ?: ""
        val definition = binding.etDefinition.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.message_behavior_name_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (definition.isEmpty()) {
            Toast.makeText(this, getString(R.string.message_definition_required), Toast.LENGTH_SHORT).show()
            return
        }

        val recordType = when {
            binding.rbTrial.isChecked    -> "TRIAL"
            binding.rbDuration.isChecked -> "DURATION"
            binding.rbLatency.isChecked  -> "LATENCY"
            else                         -> "EVENT"
        }
        val trialSettings: String? = if (recordType == "TRIAL") {
            if (binding.rbTrialFixed.isChecked) {
                val countStr = binding.etTrialCount.text?.toString()?.trim() ?: ""
                val count = countStr.toIntOrNull()
                if (count == null || count <= 0) {
                    Toast.makeText(this, "試行数を正しく入力してください", Toast.LENGTH_SHORT).show()
                    return
                }
                "FIXED:$count"
            } else {
                "FREE"
            }
        } else null

        lifecycleScope.launch {
            val existing = editingBehavior
            if (existing != null) {
                database.behaviorDao().update(
                    existing.copy(
                        name = name,
                        operationalDefinition = definition,
                        recordType = recordType,
                        trialSettings = trialSettings
                    )
                )
            } else {
                database.behaviorDao().insert(
                    Behavior(
                        subjectId = subjectId,
                        name = name,
                        operationalDefinition = definition,
                        recordType = recordType,
                        trialSettings = trialSettings
                    )
                )
            }
            Toast.makeText(this@AddBehaviorActivity, getString(R.string.message_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
