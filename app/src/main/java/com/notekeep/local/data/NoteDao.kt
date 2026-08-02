package com.notekeep.local.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** Active (non-archived) notes: pinned first, then newest edited first. */
    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getAllOnce(): List<Note>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllIncludingArchivedOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getById(noteId: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Insert
    suspend fun insertAll(notes: List<Note>): List<Long>

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :noteId")
    suspend fun setPinned(noteId: Long, pinned: Boolean)

    @Query("UPDATE notes SET archived = :archived WHERE id = :noteId")
    suspend fun setArchived(noteId: Long, archived: Boolean)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
