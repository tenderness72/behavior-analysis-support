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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSubjectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        database = AppDatabase.getDatabase(this)

        // ナビゲーションバー下端との重なりを防ぐ
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, navBar.bottom)
            insets
        }

        binding.btnSave.setOnClickListener {
            saveSubject()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
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
            val subject = Subject(
                name = name,
                age = age,
                notes = notes.ifEmpty { null }
            )
            database.subjectDao().insert(subject)
            Toast.makeText(this@AddSubjectActivity, getString(R.string.message_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
