package com.example.behavioranalysis

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.behavioranalysis.adapter.SubjectAdapter
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: AppDatabase
    private lateinit var subjectAdapter: SubjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Muninn"
        supportActionBar?.subtitle = "ABA行動記録"

        database = AppDatabase.getDatabase(this)

        subjectAdapter = SubjectAdapter { subject ->
            val intent = Intent(this, BehaviorListActivity::class.java).apply {
                putExtra("SUBJECT_ID", subject.id)
                putExtra("SUBJECT_NAME", subject.name)
            }
            startActivity(intent)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = subjectAdapter
        }

        // ナビゲーションバーの高さ分だけ FAB・RecyclerView を上にずらす
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val fabMargin = (20 * resources.displayMetrics.density).toInt()

            // FAB のマージンにナビゲーションバー高さを加算
            binding.fab.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                bottomMargin = fabMargin + navBottom
            }

            // RecyclerView の下パディング = FAB高さ(56dp) + マージン(20dp) + ナビバー高さ
            val fabHeight = (56 * resources.displayMetrics.density).toInt()
            binding.recyclerView.setPadding(
                binding.recyclerView.paddingLeft,
                binding.recyclerView.paddingTop,
                binding.recyclerView.paddingRight,
                fabHeight + fabMargin + navBottom
            )
            insets
        }

        binding.fab.setOnClickListener {
            startActivity(Intent(this, AddSubjectActivity::class.java))
        }

        lifecycleScope.launch {
            database.subjectDao().getAllSubjects().collect { subjects ->
                subjectAdapter.updateSubjects(subjects)
            }
        }
    }
}
