package com.example.behavioranalysis

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.behavioranalysis.databinding.ActivityBehaviorDetailBinding
import com.example.behavioranalysis.fragment.CountModeFragment
import com.example.behavioranalysis.fragment.DurationModeFragment
import com.example.behavioranalysis.fragment.GraphFragment
import com.example.behavioranalysis.fragment.LatencyModeFragment
import com.example.behavioranalysis.fragment.RecordListFragment
import com.example.behavioranalysis.fragment.TrialModeFragment
import com.google.android.material.tabs.TabLayoutMediator

class BehaviorDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBehaviorDetailBinding
    private var countModeFragment: CountModeFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBehaviorDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val behaviorId = intent.getLongExtra("BEHAVIOR_ID", -1)
        val behaviorName = intent.getStringExtra("BEHAVIOR_NAME") ?: ""
        val behaviorDefinition = intent.getStringExtra("BEHAVIOR_DEFINITION") ?: ""
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        val recordType = intent.getStringExtra("BEHAVIOR_RECORD_TYPE") ?: "EVENT"
        val trialSettings = intent.getStringExtra("BEHAVIOR_TRIAL_SETTINGS") ?: "FREE"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = behaviorName

        val bundle = Bundle().apply {
            putLong("BEHAVIOR_ID", behaviorId)
            putString("BEHAVIOR_NAME", behaviorName)
            putString("BEHAVIOR_DEFINITION", behaviorDefinition)
            putString("SUBJECT_NAME", subjectName)
            putString("RECORD_TYPE", recordType)
            putString("TRIAL_SETTINGS", trialSettings)
        }

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> when (recordType) {
                        "TRIAL"    -> TrialModeFragment().apply { arguments = bundle }
                        "DURATION" -> DurationModeFragment().apply { arguments = bundle }
                        "LATENCY"  -> LatencyModeFragment().apply { arguments = bundle }
                        else       -> CountModeFragment().apply {
                            arguments = bundle
                        }.also { countModeFragment = it }
                    }
                    1 -> GraphFragment().apply { arguments = bundle }
                    2 -> RecordListFragment().apply { arguments = bundle }
                    else -> throw IllegalStateException("Invalid position: $position")
                }
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_record)
                1 -> getString(R.string.tab_graph)
                2 -> getString(R.string.tab_history)
                else -> ""
            }
        }.attach()

        ViewCompat.setOnApplyWindowInsetsListener(binding.viewPager) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, navBar.bottom)
            insets
        }
    }

    // ボリュームキーをCountModeFragmentに委譲（事象記録法のみ）
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val fragment = countModeFragment
        if (fragment != null && fragment.isVisible) {
            if (fragment.handleKeyDown(keyCode)) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSupportNavigateUp(): Boolean {
        val fragment = countModeFragment
        if (fragment != null && fragment.isVisible && fragment.isActive) {
            Toast.makeText(this, "カウント中は画面を移動できません", Toast.LENGTH_SHORT).show()
            return false
        }
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
