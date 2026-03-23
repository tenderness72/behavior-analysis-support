package com.example.behavioranalysis

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.Subject
import com.example.behavioranalysis.databinding.ActivityAddSubjectBinding
import kotlinx.coroutines.launch

class AddSubjectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSubjectBinding
    private lateinit var database: AppDatabase

    /** 編集モード時に既存エンティティを保持する（null = 新規作成） */
    private var editingSubject: Subject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSubjectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        database = AppDatabase.getDatabase(this)

        // 編集モード: intent に SUBJECT_ID が渡されていれば既存データを復元
        val subjectId = intent.getLongExtra("SUBJECT_ID", -1L)
        if (subjectId != -1L) {
            supportActionBar?.title = "対象者を編集"
            editingSubject = Subject(
                id = subjectId,
                name = intent.getStringExtra("SUBJECT_NAME") ?: "",
                age = intent.getIntExtra("SUBJECT_AGE", -1).takeIf { it != -1 },
                notes = intent.getStringExtra("SUBJECT_NOTES")
            )
            binding.etName.setText(editingSubject!!.name)
            editingSubject!!.age?.let { binding.etAge.setText(it.toString()) }
            editingSubject!!.notes?.let { binding.etNotes.setText(it) }
        } else {
            supportActionBar?.title = "対象者を追加"
        }

        // ナビゲーションバー下端との重なりを防ぐ
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, navBar.bottom)
            insets
        }

        binding.btnSave.setOnClickListener { saveSubject() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun saveSubject() {
        val name = binding.etName.text?.toString()?.trim() ?: ""
        val ageStr = binding.etAge.text?.toString()?.trim() ?: ""
        val notes = binding.etNotes.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.message_name_required), Toast.LENGTH_SHORT).show()
            return
        }

        val age = if (ageStr.isNotEmpty()) ageStr.toIntOrNull() else null

        lifecycleScope.launch {
            val existing = editingSubject
            if (existing != null) {
                // 編集: createdAt を維持したまま更新
                database.subjectDao().update(
                    existing.copy(
                        name = name,
                        age = age,
                        notes = notes.ifEmpty { null }
                    )
                )
            } else {
                // 新規作成
                database.subjectDao().insert(
                    Subject(name = name, age = age, notes = notes.ifEmpty { null })
                )
            }
            Toast.makeText(this@AddSubjectActivity, getString(R.string.message_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
