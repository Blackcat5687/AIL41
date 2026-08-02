package com.notekeep.local.data

import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    fun toJson(notes: List<Note>, labelsByNoteId: Map<Long, List<String>> = emptyMap()): String {
        val array = JSONArray()
        for (note in notes) {
            val obj = JSONObject()
            obj.put("title", note.title)
            obj.put("content", note.content)
            obj.put("color", note.color)
            obj.put("updatedAt", note.updatedAt)
            obj.put("pinned", note.pinned)
            obj.put("archived", note.archived)
            obj.put("backgroundImageUri", note.backgroundImageUri)
            val labelsArray = JSONArray()
            labelsByNoteId[note.id]?.forEach { labelsArray.put(it) }
            obj.put("labels", labelsArray)
            array.put(obj)
        }
        val root = JSONObject()
        root.put("app", "NotesLink")
        root.put("version", 2)
        root.put("notes", array)
        return root.toString()
    }

    /** Parses a backup file. Ignores original ids so imported notes get fresh ones. */
    fun fromJson(json: String): List<Note> {
        val root = JSONObject(json)
        val array = root.optJSONArray("notes") ?: JSONArray(json)
        val notes = ArrayList<Note>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            notes.add(
                Note(
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    color = obj.optInt("color", 0),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    pinned = obj.optBoolean("pinned", false),
                    archived = obj.optBoolean("archived", false),
                    backgroundImageUri = obj.optString("backgroundImageUri", null).takeUnless { it.isNullOrEmpty() || it == "null" }
                )
            )
        }
        return notes
    }

    /** Label names attached to each note in a backup file, keyed by the note's position/order in the file. */
    fun labelsPerNote(json: String): List<List<String>> {
        val root = JSONObject(json)
        val array = root.optJSONArray("notes") ?: JSONArray(json)
        val result = ArrayList<List<String>>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val labelsArray = obj.optJSONArray("labels")
            val labels = ArrayList<String>()
            if (labelsArray != null) {
                for (j in 0 until labelsArray.length()) labels.add(labelsArray.getString(j))
            }
            result.add(labels)
        }
        return result
    }
}
