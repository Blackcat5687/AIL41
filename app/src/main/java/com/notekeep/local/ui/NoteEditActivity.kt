package com.notekeep.local.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.notekeep.local.R
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.data.Label
import com.notekeep.local.data.Note
import com.notekeep.local.databinding.ActivityNoteEditBinding
import com.notekeep.local.databinding.DialogLabelsBinding
import kotlinx.coroutines.launch

class NoteEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditBinding
    private var noteId: Long = -1
    private var currentNote: Note? = null
    private var selectedColor: Int = 0
    private var backgroundImageUri: String? = null
    private var isPinned: Boolean = false
    private var isArchived: Boolean = false

    private val tagRegex = Regex("#[\\p{L}0-9_]+")

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // some providers don't support persistable permissions; the uri may still work this session
                }
                backgroundImageUri = uri.toString()
                applyBackgroundPreview()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { saveAndFinish() }

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1)
        buildColorRow()
        applyBackgroundPreview()
        attachTagHighlighter(binding.editTitle)
        attachTagHighlighter(binding.editContent)

        if (noteId != -1L) {
            lifecycleScope.launch {
                val note = AppDatabase.getInstance(applicationContext).noteDao().getById(noteId)
                if (note != null) {
                    currentNote = note
                    selectedColor = note.color
                    backgroundImageUri = note.backgroundImageUri
                    isPinned = note.pinned
                    isArchived = note.archived
                    binding.editTitle.setText(note.title)
                    binding.editContent.setText(note.content)
                    highlightSelectedColor()
                    applyBackgroundPreview()
                    invalidateOptionsMenu()
                    refreshLabelChips()
                }
            }
        }
    }

    /** Applies the currently selected color / background image to the whole editor screen, live. */
    private fun applyBackgroundPreview() {
        val uri = backgroundImageUri
        if (uri != null) {
            binding.imageEditBackground.visibility = View.VISIBLE
            binding.imageEditScrim.visibility = View.VISIBLE
            try {
                binding.imageEditBackground.setImageURI(android.net.Uri.parse(uri))
            } catch (e: Exception) {
                binding.imageEditBackground.visibility = View.GONE
                binding.imageEditScrim.visibility = View.GONE
            }
        } else {
            binding.imageEditBackground.visibility = View.GONE
            binding.imageEditScrim.visibility = View.GONE
        }
        val colorRes = NoteColors.palette.getOrElse(selectedColor) { R.color.note_0 }
        binding.rootFrame.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    private fun attachTagHighlighter(editText: android.widget.EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val spans = s.getSpans(0, s.length, ForegroundColorSpan::class.java)
                spans.forEach { s.removeSpan(it) }
                val color = ContextCompat.getColor(this@NoteEditActivity, R.color.tag_highlight)
                tagRegex.findAll(s).forEach { match ->
                    s.setSpan(
                        ForegroundColorSpan(color),
                        match.range.first,
                        match.range.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        })
    }

    private fun buildColorRow() {
        binding.colorRow.removeAllViews()
        val size = (34 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()

        NoteColors.palette.forEachIndexed { index, colorRes ->
            val circle = View(this)
            val params = android.widget.LinearLayout.LayoutParams(size, size)
            params.marginEnd = margin
            circle.layoutParams = params
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(ContextCompat.getColor(this, colorRes))
            if (index == selectedColor) {
                drawable.setStroke((2 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(this, R.color.white))
            }
            circle.background = drawable
            circle.tag = index
            circle.setOnClickListener {
                selectedColor = index
                highlightSelectedColor()
                applyBackgroundPreview()
            }
            binding.colorRow.addView(circle)
        }

        // extra circular button, appended to the same row, to attach/remove a background image
        val imageButton = ImageView(this)
        val params = android.widget.LinearLayout.LayoutParams(size, size)
        imageButton.layoutParams = params
        val bgDrawable = GradientDrawable()
        bgDrawable.shape = GradientDrawable.OVAL
        bgDrawable.setColor(ContextCompat.getColor(this, R.color.surface_dark))
        bgDrawable.setStroke((1 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(this, R.color.on_surface_dark))
        imageButton.background = bgDrawable
        imageButton.setImageResource(R.drawable.ic_image)
        imageButton.setPadding(size / 5, size / 5, size / 5, size / 5)
        imageButton.scaleType = ImageView.ScaleType.FIT_CENTER
        imageButton.contentDescription = getString(R.string.content_desc_background_image)
        imageButton.setOnClickListener { showImageOptions() }
        binding.colorRow.addView(imageButton)
    }

    private fun showImageOptions() {
        val options = if (backgroundImageUri != null) {
            arrayOf(getString(R.string.content_desc_background_image), getString(R.string.content_desc_remove_background_image))
        } else {
            arrayOf(getString(R.string.content_desc_background_image))
        }
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                if (which == 0) {
                    pickImageLauncher.launch(arrayOf("image/*"))
                } else {
                    backgroundImageUri = null
                    applyBackgroundPreview()
                }
            }
            .show()
    }

    private fun highlightSelectedColor() {
        for (i in 0 until binding.colorRow.childCount - 1) {
            val child = binding.colorRow.getChildAt(i)
            val index = child.tag as? Int ?: continue
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(ContextCompat.getColor(this, NoteColors.palette[index]))
            if (index == selectedColor) {
                drawable.setStroke((2 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(this, R.color.white))
            }
            child.background = drawable
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        if (noteId != -1L) {
            menuInflater.inflate(R.menu.menu_note_edit, menu)
            menu.findItem(R.id.action_pin)?.title =
                getString(if (isPinned) R.string.action_unpin else R.string.action_pin)
            menu.findItem(R.id.action_pin)?.setIcon(
                if (isPinned) R.drawable.ic_pin else R.drawable.ic_pin_outline
            )
            menu.findItem(R.id.action_archive)?.title =
                getString(if (isArchived) R.string.action_unarchive else R.string.action_archive)
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete -> confirmDelete()
            R.id.action_pin -> {
                isPinned = !isPinned
                invalidateOptionsMenu()
            }
            R.id.action_archive -> {
                isArchived = !isArchived
                invalidateOptionsMenu()
            }
            R.id.action_labels -> showLabelsDialog()
        }
        return true
    }

    override fun onBackPressed() {
        saveAndFinish()
    }

    private fun saveAndFinish() {
        val title = binding.editTitle.text.toString().trim()
        val content = binding.editContent.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).noteDao()
            val existing = currentNote
            if (existing != null) {
                dao.update(
                    existing.copy(
                        title = title,
                        content = content,
                        color = selectedColor,
                        updatedAt = System.currentTimeMillis(),
                        pinned = isPinned,
                        archived = isArchived,
                        backgroundImageUri = backgroundImageUri
                    )
                )
            } else {
                dao.insert(
                    Note(
                        title = title,
                        content = content,
                        color = selectedColor,
                        pinned = isPinned,
                        archived = isArchived,
                        backgroundImageUri = backgroundImageUri
                    )
                )
            }
            finish()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete_confirm_positive) { _, _ -> deleteAndFinish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteAndFinish() {
        val existing = currentNote ?: run { finish(); return }
        lifecycleScope.launch {
            AppDatabase.getInstance(applicationContext).noteDao().delete(existing)
            finish()
        }
    }

    // ---- labels ----

    private fun refreshLabelChips() {
        val id = noteId
        if (id == -1L) return
        lifecycleScope.launch {
            val labels = AppDatabase.getInstance(applicationContext).labelDao().labelsForNote(id)
            binding.labelChipRow.removeAllViews()
            if (labels.isEmpty()) {
                binding.labelsScroll.visibility = View.GONE
                return@launch
            }
            binding.labelsScroll.visibility = View.VISIBLE
            for (label in labels) {
                val chip = layoutInflater.inflate(R.layout.item_label_chip, binding.labelChipRow, false) as android.widget.TextView
                chip.text = label.name
                binding.labelChipRow.addView(chip)
            }
        }
    }

    private fun showLabelsDialog() {
        lifecycleScope.launch {
            if (noteId == -1L) {
                // note not saved yet; insert it now so it has an id to attach labels to
                val dao = AppDatabase.getInstance(applicationContext).noteDao()
                val newId = dao.insert(
                    Note(
                        title = binding.editTitle.text.toString().trim(),
                        content = binding.editContent.text.toString().trim(),
                        color = selectedColor,
                        pinned = isPinned,
                        archived = isArchived,
                        backgroundImageUri = backgroundImageUri
                    )
                )
                noteId = newId
                currentNote = dao.getById(newId)
                invalidateOptionsMenu()
            }
            openLabelsDialog()
        }
    }

    private fun openLabelsDialog() {
        val dialogBinding = DialogLabelsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        val labelDao = AppDatabase.getInstance(applicationContext).labelDao()

        lateinit var adapter: LabelSelectAdapter

        fun reload() {
            lifecycleScope.launch {
                val all = labelDao.getAllOnce()
                val assigned = labelDao.labelIdsForNote(noteId).toSet()
                dialogBinding.textLabelsEmpty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(all.map { LabelRow(it, assigned.contains(it.id)) })
            }
        }

        adapter = LabelSelectAdapter(
            onToggle = { label, checked ->
                lifecycleScope.launch {
                    val current = labelDao.labelIdsForNote(noteId).toMutableSet()
                    if (checked) current.add(label.id) else current.remove(label.id)
                    labelDao.setLabelsForNote(noteId, current.toList())
                    refreshLabelChips()
                }
            },
            onDelete = { label ->
                lifecycleScope.launch {
                    labelDao.clearAssignmentsForLabel(label.id)
                    labelDao.delete(label)
                    reload()
                    refreshLabelChips()
                }
            }
        )
        dialogBinding.recyclerLabels.adapter = adapter
        dialogBinding.recyclerLabels.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        dialogBinding.buttonCreateLabel.setOnClickListener {
            val name = dialogBinding.editNewLabel.text.toString().trim()
            if (name.isNotEmpty()) {
                lifecycleScope.launch {
                    labelDao.insert(Label(name = name))
                    dialogBinding.editNewLabel.setText("")
                    reload()
                }
            }
        }
        dialogBinding.buttonLabelsDone.setOnClickListener { dialog.dismiss() }

        reload()
        dialog.show()
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }
}
