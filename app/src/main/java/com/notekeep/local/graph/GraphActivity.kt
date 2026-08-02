package com.notekeep.local.graph

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.notekeep.local.R
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.databinding.ActivityGraphBinding
import com.notekeep.local.databinding.BottomsheetGraphSettingsBinding
import com.notekeep.local.databinding.ItemGraphGroupRowBinding
import com.notekeep.local.ui.NoteEditActivity
import kotlinx.coroutines.launch

class GraphActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGraphBinding
    private var hideOrphans = false
    private var showTags = true
    private val groups = mutableListOf<GraphGroup>()

    private val groupPalette = intArrayOf(
        0xFFE57373.toInt(), 0xFF64B5F6.toInt(), 0xFFFFD54F.toInt(),
        0xFF81C784.toInt(), 0xFFBA68C8.toInt(), 0xFF4DB6AC.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGraphBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.graphView.onNoteTapped = { noteId ->
            val intent = Intent(this, NoteEditActivity::class.java)
            intent.putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId)
            startActivity(intent)
        }

        binding.fabGraphSettings.setOnClickListener { showSettingsSheet() }

        loadGraph()
    }

    override fun onResume() {
        super.onResume()
        loadGraph()
    }

    private fun loadGraph() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val notes = db.noteDao().getAllOnce()
            val labels = db.labelDao().getAllOnce()
            val crossRefs = db.labelDao().getAllCrossRefsOnce()
            val noteLabelPairs = crossRefs.map { it.noteId to it.labelId }
            val graphData = GraphData.build(
                notes, hideOrphans, showTags, labels, noteLabelPairs,
                previous = binding.graphView.data
            )
            binding.graphView.updateData(graphData)
            binding.graphView.groups = groups
            binding.emptyView.visibility =
                if (graphData.nodes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun showSettingsSheet() {
        val sheet = BottomSheetDialog(this)
        val sb = BottomsheetGraphSettingsBinding.inflate(layoutInflater)
        sheet.setContentView(sb.root)

        // --- accordion expand/collapse ---
        fun bindAccordion(header: android.view.View, chevron: android.view.View, section: android.view.View) {
            header.setOnClickListener {
                val expand = section.visibility != android.view.View.VISIBLE
                section.visibility = if (expand) android.view.View.VISIBLE else android.view.View.GONE
                chevron.animate().rotation(if (expand) 270f else 90f).setDuration(150).start()
            }
        }
        bindAccordion(sb.headerFilter, sb.chevronFilter, sb.sectionFilter)
        bindAccordion(sb.headerGroups, sb.chevronGroups, sb.sectionGroups)
        bindAccordion(sb.headerDisplay, sb.chevronDisplay, sb.sectionDisplay)
        bindAccordion(sb.headerForces, sb.chevronForces, sb.sectionForces)
        // Display and Forces start expanded, like the previous flat panel
        sb.sectionDisplay.visibility = android.view.View.VISIBLE
        sb.chevronDisplay.rotation = 270f
        sb.sectionForces.visibility = android.view.View.VISIBLE
        sb.chevronForces.rotation = 270f

        sb.buttonClose.setOnClickListener { sheet.dismiss() }

        // --- filter section ---
        sb.editGraphSearch.setText(binding.graphView.searchQuery)
        sb.editGraphSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.graphView.searchQuery = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        sb.switchTags.isChecked = showTags
        sb.switchTags.setOnCheckedChangeListener { _, checked ->
            showTags = checked
            loadGraph()
        }
        sb.switchOrphans.isChecked = hideOrphans
        sb.switchOrphans.setOnCheckedChangeListener { _, checked ->
            hideOrphans = checked
            loadGraph()
        }

        // --- groups section ---
        fun renderGroups() {
            sb.groupRows.removeAllViews()
            groups.forEach { group ->
                val row = ItemGraphGroupRowBinding.inflate(layoutInflater, sb.groupRows, false)
                row.groupQueryText.text = group.query
                (row.groupColorDot.background.mutate() as GradientDrawable).setColor(group.color)
                row.buttonDeleteGroup.setOnClickListener {
                    groups.remove(group)
                    binding.graphView.groups = groups.toList()
                    binding.graphView.invalidate()
                    renderGroups()
                }
                sb.groupRows.addView(row.root)
            }
        }
        renderGroups()
        sb.buttonAddGroup.setOnClickListener {
            val input = android.widget.EditText(this)
            input.hint = getString(R.string.graph_group_query_hint)
            AlertDialog.Builder(this)
                .setTitle(R.string.graph_group_add)
                .setView(input)
                .setPositiveButton(R.string.labels_create) { _, _ ->
                    val query = input.text.toString().trim()
                    if (query.isNotEmpty()) {
                        val color = groupPalette[groups.size % groupPalette.size]
                        groups.add(GraphGroup(query, color))
                        binding.graphView.groups = groups.toList()
                        binding.graphView.invalidate()
                        renderGroups()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // --- display + forces sections ---
        sb.switchArrows.isChecked = binding.graphView.showArrows
        sb.switchArrows.setOnCheckedChangeListener { _, checked ->
            binding.graphView.showArrows = checked
            binding.graphView.invalidate()
        }

        fun applyForces() {
            binding.graphView.applyForceSettings(
                sb.sliderCenter.value,
                sb.sliderRepel.value,
                sb.sliderLinkStrength.value,
                sb.sliderLinkDistance.value
            )
        }
        sb.sliderCenter.addOnChangeListener { _, _, _ -> applyForces() }
        sb.sliderRepel.addOnChangeListener { _, _, _ -> applyForces() }
        sb.sliderLinkStrength.addOnChangeListener { _, _, _ -> applyForces() }
        sb.sliderLinkDistance.addOnChangeListener { _, _, _ -> applyForces() }

        sb.sliderNodeSize.addOnChangeListener { _, value, _ ->
            binding.graphView.nodeSizeSetting = value
            binding.graphView.invalidate()
        }
        sb.sliderLinkThickness.addOnChangeListener { _, value, _ ->
            binding.graphView.linkThicknessSetting = value
            binding.graphView.invalidate()
        }
        sb.sliderFade.addOnChangeListener { _, value, _ ->
            binding.graphView.fadeThreshold = value
            binding.graphView.invalidate()
        }

        sb.btnRestart.setOnClickListener {
            applyForces()
            binding.graphView.restart()
        }

        sb.buttonResetView.setOnClickListener {
            sb.switchTags.isChecked = true
            sb.switchOrphans.isChecked = false
            sb.switchArrows.isChecked = false
            sb.editGraphSearch.setText("")
            sb.sliderFade.value = 0.8f
            sb.sliderNodeSize.value = 14f
            sb.sliderLinkThickness.value = 2f
            sb.sliderCenter.value = 0.3f
            sb.sliderRepel.value = 1200f
            sb.sliderLinkStrength.value = 0.4f
            sb.sliderLinkDistance.value = 140f
            applyForces()
            binding.graphView.restart()
        }

        applyForces()
        sheet.show()
    }
}
