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
import com.example.behavioranalysis.adapter.BehaviorAdapter
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.databinding.ActivityBehaviorListBinding
import kotlinx.coroutines.launch

class BehaviorListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBehaviorListBinding
    private lateinit var database: AppDatabase
    private lateinit var behaviorAdapter: BehaviorAdapter
    private var subjectId: Long = -1
    private var subjectName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBehaviorListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        subjectId = intent.getLongExtra("SUBJECT_ID", -1)
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "${subjectName} の行動"

        database = AppDatabase.getDatabase(this)

        behaviorAdapter = BehaviorAdapter { behavior ->
            val intent = Intent(this, BehaviorDetailActivity::class.java).apply {
                putExtra("BEHAVIOR_ID", behavior.id)
                putExtra("BEHAVIOR_NAME", behavior.name)
                putExtra("BEHAVIOR_DEFINITION", behavior.operationalDefinition)
            }
            startActivity(intent)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BehaviorListActivity)
            adapter = behaviorAdapter
        }

        // ナビゲーションバーの高さ分だけ FAB・RecyclerView を上にずらす
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val fabMargin = (20 * resources.displayMetrics.density).toInt()

            binding.fab.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                bottomMargin = fabMargin + navBottom
            }

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
            val intent = Intent(this, AddBehaviorActivity::class.java).apply {
                putExtra("SUBJECT_ID", subjectId)
                putExtra("SUBJECT_NAME", subjectName)
            }
            startActivity(intent)
        }

        lifecycleScope.launch {
            database.behaviorDao().getBehaviorsBySubject(subjectId).collect { behaviors ->
                behaviorAdapter.updateBehaviors(behaviors)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
