package com.example.privkeyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

data class Clip(
    val id: Long,
    val text: String,
    val tag: String?,
    val pinned: Boolean,
    val created: Long,
    val used: Long
)

object ClipSettings {
    private fun prefs(c: Context) = c.getSharedPreferences("clip_settings", Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean("enabled", true)

    fun setEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("enabled", value).apply()
    }
}

/**
 * Unlimited local clipboard history. Clip text and tags are stored AES-256-GCM
 * encrypted (see [CryptoBox]); only timestamps / pin flag are plain columns.
 * All methods are blocking: call them through [async] / [background].
 */
class ClipStore private constructor(context: Context) {

    private val helper = object : SQLiteOpenHelper(context.applicationContext, "clips.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE clips (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "h TEXT NOT NULL UNIQUE, " +
                    "body BLOB NOT NULL, " +
                    "tag BLOB, " +
                    "pinned INTEGER NOT NULL DEFAULT 0, " +
                    "created INTEGER NOT NULL, " +
                    "used INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX idx_clips_order ON clips (pinned, used)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // ---------------------------------------------------------------- threading helpers

    /** Run [work] on the store thread, then deliver the result on the main thread. */
    fun <T : Any> async(work: () -> T, done: (T) -> Unit) {
        executor.execute {
            try {
                val result = work()
                main.post { done(result) }
            } catch (e: Exception) {
                // Storage problems must never crash the keyboard.
            }
        }
    }

    /** Fire-and-forget on the store thread (operations run in order). */
    fun background(work: () -> Unit) {
        executor.execute {
            try {
                work()
            } catch (e: Exception) {
            }
        }
    }

    // ---------------------------------------------------------------- data access

    fun add(text: String) {
        synchronized(lock) {
            val db = helper.writableDatabase
            val h = CryptoBox.fingerprint(text)
            val now = System.currentTimeMillis()
            val bump = ContentValues()
            bump.put("used", now)
            if (db.update("clips", bump, "h = ?", arrayOf(h)) > 0) return
            val cv = ContentValues()
            cv.put("h", h)
            cv.put("body", CryptoBox.encryptString(text))
            cv.put("pinned", 0)
            cv.put("created", now)
            cv.put("used", now)
            db.insert("clips", null, cv)
        }
    }

    fun query(
        search: String? = null,
        tag: String? = null,
        pinnedOnly: Boolean = false,
        limit: Int = 300
    ): List<Clip> = synchronized(lock) {
        val db = helper.readableDatabase
        val where = if (pinnedOnly) "pinned = 1" else null
        val needle = search?.trim().orEmpty()
        val filtering = needle.isNotEmpty() || tag != null
        val sqlLimit = if (filtering) null else limit.toString()
        val out = ArrayList<Clip>()
        val c = db.query(
            "clips", arrayOf("id", "body", "tag", "pinned", "created", "used"),
            where, null, null, null, "pinned DESC, used DESC", sqlLimit
        )
        c.use {
            while (it.moveToNext()) {
                val text = decryptOrNull(it.getBlob(1)) ?: continue
                val tagText = if (it.isNull(2)) null else decryptOrNull(it.getBlob(2))
                if (tag != null && tagText != tag) continue
                if (needle.isNotEmpty() &&
                    !text.contains(needle, ignoreCase = true) &&
                    tagText?.contains(needle, ignoreCase = true) != true
                ) continue
                out.add(Clip(it.getLong(0), text, tagText, it.getInt(3) == 1, it.getLong(4), it.getLong(5)))
                if (out.size >= limit) break
            }
        }
        out
    }

    fun tags(): List<String> = synchronized(lock) {
        val db = helper.readableDatabase
        val set = LinkedHashSet<String>()
        val c = db.query("clips", arrayOf("tag"), "tag IS NOT NULL", null, null, null, null)
        c.use {
            while (it.moveToNext()) {
                val t = decryptOrNull(it.getBlob(0))
                if (!t.isNullOrBlank()) set.add(t)
            }
        }
        set.sorted()
    }

    fun setPinned(id: Long, pinned: Boolean) {
        synchronized(lock) {
            val cv = ContentValues()
            cv.put("pinned", if (pinned) 1 else 0)
            helper.writableDatabase.update("clips", cv, "id = ?", arrayOf(id.toString()))
        }
    }

    fun setTag(id: Long, tag: String?) {
        synchronized(lock) {
            val cv = ContentValues()
            val clean = tag?.trim()
            if (clean.isNullOrEmpty()) cv.putNull("tag") else cv.put("tag", CryptoBox.encryptString(clean))
            helper.writableDatabase.update("clips", cv, "id = ?", arrayOf(id.toString()))
        }
    }

    fun markUsed(id: Long) {
        synchronized(lock) {
            val cv = ContentValues()
            cv.put("used", System.currentTimeMillis())
            helper.writableDatabase.update("clips", cv, "id = ?", arrayOf(id.toString()))
        }
    }

    fun delete(id: Long) {
        synchronized(lock) {
            helper.writableDatabase.delete("clips", "id = ?", arrayOf(id.toString()))
        }
    }

    fun clearUnpinned() {
        synchronized(lock) {
            helper.writableDatabase.delete("clips", "pinned = 0", null)
        }
    }

    private fun decryptOrNull(blob: ByteArray?): String? {
        if (blob == null) return null
        return try {
            CryptoBox.decryptString(blob)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: ClipStore? = null

        fun get(context: Context): ClipStore =
            instance ?: synchronized(this) {
                instance ?: ClipStore(context.applicationContext).also { instance = it }
            }
    }
}
