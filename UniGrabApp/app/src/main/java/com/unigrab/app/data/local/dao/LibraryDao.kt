package com.unigrab.app.data.local.dao

import androidx.room.*
import com.unigrab.app.data.local.entity.LibraryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_items ORDER BY addedAt DESC")
    fun getAllItemsFlow(): Flow<List<LibraryItem>>

    @Query("SELECT * FROM library_items ORDER BY addedAt DESC")
    suspend fun getAllItems(): List<LibraryItem>

    @Query("SELECT * FROM library_items WHERE id = :id")
    suspend fun getItemById(id: Long): LibraryItem?

    @Query("SELECT * FROM library_items WHERE filePath = :filePath LIMIT 1")
    suspend fun getItemByPath(filePath: String): LibraryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: LibraryItem): Long

    @Delete
    suspend fun deleteItem(item: LibraryItem)

    @Query("DELETE FROM library_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)
}
