package com.notekeep.local.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notekeep.local.R
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.data.BackupManager
import com.notekeep.local.databinding.ActivitySettingsBinding
import com.notekeep.local.graph.GraphActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val createBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeBackup(uri)
        }

    private val openBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) confirmRestore(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowGraph.setOnClickListener {
            startActivity(Intent(this, GraphActivity::class.java))
        }

        binding.rowArchive.setOnClickListener {
            startActivity(Intent(this, com.notekeep.local.ui.ArchiveActivity::class.java))
        }

        binding.rowLabels.setOnClickListener {
            showManageLabelsDialog()
        }

        binding.rowBackup.setOnClickListener {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            createBackupLauncher.launch("notes_backup_$stamp.json")
        }

        binding.rowRestore.setOnClickListener {
            openBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    private fun showManageLabelsDialog() {
        val dialogBinding = com.notekeep.local.databinding.DialogLabelsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        val labelDao = AppDatabase.getInstance(applicationContext).labelDao()

        lateinit var adapter: LabelSelectAdapter

        fun reload() {
            lifecycleScope.launch {
                val all = labelDao.getAllOnce()
                dialogBinding.textLabelsEmpty.visibility = if (all.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                adapter.submitList(all.map { LabelRow(it, false) })
            }
        }

        adapter = LabelSelectAdapter(
            onToggle = { _, _ -> },
            onDelete = { label ->
                lifecycleScope.launch {
                    labelDao.clearAssignmentsForLabel(label.id)
                    labelDao.delete(label)
                    reload()
                }
            },
            showCheckbox = false
        )
        dialogBinding.recyclerLabels.adapter = adapter
        dialogBinding.recyclerLabels.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        dialogBinding.buttonCreateLabel.setOnClickListener {
            val name = dialogBinding.editNewLabel.text.toString().trim()
            if (name.isNotEmpty()) {
                lifecycleScope.launch {
                    labelDao.insert(com.notekeep.local.data.Label(name = name))
                    dialogBinding.editNewLabel.setText("")
                    reload()
                }
            }
        }
        dialogBinding.buttonLabelsDone.setOnClickListener { dialog.dismiss() }

        reload()
        dialog.show()
    }

    private fun writeBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val notes = db.noteDao().getAllIncludingArchivedOnce()
                val labels = db.labelDao().getAllOnce().associateBy { it.id }
                val crossRefs = db.labelDao().getAllCrossRefsOnce()
                val labelsByNoteId = crossRefs.groupBy({ it.noteId }, { it.labelId })
                    .mapValues { (_, ids) -> ids.mapNotNull { labels[it]?.name } }
                val json = BackupManager.toJson(notes, labelsByNoteId)
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this@SettingsActivity, R.string.backup_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRestore(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_choice_title)
            .setPositiveButton(R.string.restore_merge) { _, _ -> performRestore(uri, replace = false) }
            .setNegativeButton(R.string.restore_replace) { _, _ -> performRestore(uri, replace = true) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun performRestore(uri: Uri, replace: Boolean) {
        lifecycleScope.launch {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("empty")
                val notes = BackupManager.fromJson(text)
                val labelsPerNote = BackupManager.labelsPerNote(text)
                val db = AppDatabase.getInstance(applicationContext)
                val noteDao = db.noteDao()
                val labelDao = db.labelDao()
                if (replace) noteDao.deleteAll()

                val newIds = noteDao.insertAll(notes)

                val nameToId = labelDao.getAllOnce().associate { it.name to it.id }.toMutableMap()
                newIds.forEachIndexed { index, noteId ->
                    val names = labelsPerNote.getOrNull(index).orEmpty()
                    if (names.isEmpty()) return@forEachIndexed
                    val ids = names.map { name ->
                        nameToId[name] ?: labelDao.insert(com.notekeep.local.data.Label(name = name)).also { nameToId[name] = it }
                    }
                    labelDao.setLabelsForNote(noteId, ids)
                }

                Toast.makeText(this@SettingsActivity, R.string.restore_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
