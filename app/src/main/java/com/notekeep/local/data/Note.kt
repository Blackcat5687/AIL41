package com.notekeep.local.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val color: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /** content:// URI (persisted permission) of an optional background image for the note card. */
    val backgroundImageUri: String? = null
) {
    /** Extracts #hashtags from the title and content, used to build the relationship graph. */
    fun extractTags(): Set<String> {
        val regex = Regex("#[\\p{L}0-9_]+")
        val found = LinkedHashSet<String>()
        regex.findAll(title).forEach { found.add(it.value) }
        regex.findAll(content).forEach { found.add(it.value) }
        return found
    }

    /** Title/content with #hashtags stripped, used for the card preview (tags only show inside the note). */
    fun previewTitle(): String = stripTags(title)
    fun previewContent(): String = stripTags(content)

    companion object {
        private val TAG_REGEX = Regex("#[\\p{L}0-9_]+")

        private fun stripTags(text: String): String {
            return TAG_REGEX.replace(text, "")
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n[ \\t]*\\n+"), "\n")
                .trim()
        }
    }
}
