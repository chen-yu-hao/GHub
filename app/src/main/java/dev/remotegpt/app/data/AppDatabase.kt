package dev.remotegpt.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Entity(tableName = "hosts") data class HostRow(@PrimaryKey val id: String, val json: String)
@Entity(tableName = "threads", primaryKeys = ["hostId", "threadId"])
data class ThreadRow(val hostId: String, val threadId: String, val json: String)
@Entity(tableName = "conversations", primaryKeys = ["hostId", "threadId"])
data class ConversationRow(val hostId: String, val threadId: String, val json: String)
@Entity(tableName = "conversation_chunks", primaryKeys = ["hostId", "threadId", "chunkIndex"])
data class ConversationChunkRow(val hostId: String, val threadId: String, val chunkIndex: Int, val json: String)
@Entity(tableName = "drafts", primaryKeys = ["hostId", "threadId"])
data class DraftRow(val hostId: String, val threadId: String, val json: String)

@Dao interface WorkbenchDao {
    @Query("SELECT * FROM hosts") fun hosts(): Flow<List<HostRow>>
    @Upsert suspend fun putHost(row: HostRow)
    @Query("DELETE FROM hosts WHERE id = :id") suspend fun deleteHost(id: String)
    @Query("SELECT * FROM threads WHERE hostId = :host") suspend fun threads(host: String): List<ThreadRow>
    @Upsert suspend fun putThreads(rows: List<ThreadRow>)
    @Query("DELETE FROM threads WHERE hostId = :host AND threadId = :thread") suspend fun deleteThread(host: String, thread: String)
    @Query("SELECT * FROM conversations WHERE hostId = :host AND threadId = :thread") suspend fun conversation(host: String, thread: String): ConversationRow?
    @Upsert suspend fun putConversation(row: ConversationRow)
    @Query("DELETE FROM conversations WHERE hostId = :host AND threadId = :thread") suspend fun deleteConversation(host: String, thread: String)
    @Query("SELECT * FROM conversation_chunks WHERE hostId = :host AND threadId = :thread ORDER BY chunkIndex") suspend fun conversationChunks(host: String, thread: String): List<ConversationChunkRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putConversationChunks(rows: List<ConversationChunkRow>)
    @Query("DELETE FROM conversation_chunks WHERE hostId = :host AND threadId = :thread") suspend fun deleteConversationChunks(host: String, thread: String)
    @Transaction suspend fun replaceConversationChunks(host: String, thread: String, rows: List<ConversationChunkRow>) {
        deleteConversationChunks(host, thread)
        putConversationChunks(rows)
        // Remove the pre-0.1.8 single-row cache after the chunked copy is durable.
        deleteConversation(host, thread)
    }
    @Query("SELECT * FROM drafts WHERE hostId = :host AND threadId = :thread") suspend fun draft(host: String, thread: String): DraftRow?
    @Upsert suspend fun putDraft(row: DraftRow)
    @Query("DELETE FROM threads WHERE hostId = :host") suspend fun deleteThreads(host: String)
    @Query("DELETE FROM conversations WHERE hostId = :host") suspend fun deleteConversations(host: String)
    @Query("DELETE FROM conversation_chunks WHERE hostId = :host") suspend fun deleteConversationChunks(host: String)
    @Query("DELETE FROM drafts WHERE hostId = :host") suspend fun deleteDrafts(host: String)
    @Transaction suspend fun deleteWorkspace(host: String) {
        deleteHost(host); deleteThreads(host); deleteConversations(host); deleteConversationChunks(host); deleteDrafts(host)
    }
    @Query("DELETE FROM conversations") suspend fun clearConversations()
    @Query("DELETE FROM conversation_chunks") suspend fun clearConversationChunks()
    @Query("DELETE FROM threads") suspend fun clearThreads()
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
}

@Database(entities = [HostRow::class, ThreadRow::class, ConversationRow::class, ConversationChunkRow::class, DraftRow::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun workbench(): WorkbenchDao }

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS conversation_chunks (`hostId` TEXT NOT NULL, `threadId` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`hostId`, `threadId`, `chunkIndex`))")
    }
}
