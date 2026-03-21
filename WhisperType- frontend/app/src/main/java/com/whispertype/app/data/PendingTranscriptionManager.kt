package com.whispertype.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages locally saved audio recordings that failed transcription.
 * Audio files are stored in internal storage, metadata in SharedPreferences as JSON.
 *
 * Use [getInstance] to get the singleton instance. Using application context
 * ensures a single shared cache across all callers (OverlayService, PendingScreen, etc.).
 */
class PendingTranscriptionManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PendingTxnMgr"
        private const val PREFS_NAME = "pending_transcriptions"
        private const val KEY_ENTRIES = "entries"
        private const val AUDIO_DIR = "pending_audio"
        private const val MAX_ENTRIES = 50

        @Volatile
        private var instance: PendingTranscriptionManager? = null

        fun getInstance(context: Context): PendingTranscriptionManager {
            return instance ?: synchronized(this) {
                instance ?: PendingTranscriptionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class PendingTranscription(
        val id: String,
        val timestamp: Long,
        val durationMs: Long,
        val failedModelTier: String,
        val errorMessage: String,
        val audioFilePath: String,
        val audioFormat: String,
        var status: Status = Status.PENDING,
        var transcribedText: String? = null
    ) {
        enum class Status { PENDING, COMPLETED }

        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("durationMs", durationMs)
            put("failedModelTier", failedModelTier)
            put("errorMessage", errorMessage)
            put("audioFilePath", audioFilePath)
            put("audioFormat", audioFormat)
            put("status", status.name)
            put("transcribedText", transcribedText ?: JSONObject.NULL)
        }

        companion object {
            fun fromJson(json: JSONObject): PendingTranscription? {
                return try {
                    PendingTranscription(
                        id = json.getString("id"),
                        timestamp = json.getLong("timestamp"),
                        durationMs = json.getLong("durationMs"),
                        failedModelTier = json.getString("failedModelTier"),
                        errorMessage = json.getString("errorMessage"),
                        audioFilePath = json.getString("audioFilePath"),
                        audioFormat = json.getString("audioFormat"),
                        status = try { Status.valueOf(json.getString("status")) } catch (_: Exception) { Status.PENDING },
                        transcribedText = if (json.isNull("transcribedText")) null else json.getString("transcribedText")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse PendingTranscription", e)
                    null
                }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cachedEntries: MutableList<PendingTranscription>? = null

    private fun getAudioDir(): File {
        val dir = File(context.filesDir, AUDIO_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Save a failed audio recording for later retry.
     * Returns the created PendingTranscription entry.
     */
    fun save(
        audioBytes: ByteArray,
        audioFormat: String,
        durationMs: Long,
        failedModelTier: String,
        errorMessage: String
    ): PendingTranscription {
        val id = UUID.randomUUID().toString()
        val fileName = "audio_${id}.${audioFormat}"
        val audioFile = File(getAudioDir(), fileName)
        audioFile.writeBytes(audioBytes)

        val entry = PendingTranscription(
            id = id,
            timestamp = System.currentTimeMillis(),
            durationMs = durationMs,
            failedModelTier = failedModelTier,
            errorMessage = errorMessage,
            audioFilePath = audioFile.absolutePath,
            audioFormat = audioFormat
        )

        val entries = getOrLoadEntries().toMutableList()
        entries.add(0, entry) // newest first
        // Evict oldest entries beyond cap
        while (entries.size > MAX_ENTRIES) {
            val removed = entries.removeAt(entries.size - 1)
            File(removed.audioFilePath).delete()
        }
        persistEntries(entries)

        Log.d(TAG, "Saved pending transcription: $id (${audioBytes.size} bytes)")
        return entry
    }

    /**
     * Load the audio bytes for a pending transcription.
     */
    fun loadAudioBytes(entry: PendingTranscription): ByteArray? {
        val file = File(entry.audioFilePath)
        return if (file.exists()) file.readBytes() else null
    }

    /**
     * Get all pending transcription entries.
     */
    fun getAll(): List<PendingTranscription> = getOrLoadEntries()

    /**
     * Get count of pending (not yet completed) entries.
     */
    fun getPendingCount(): Int = getOrLoadEntries().count { it.status == PendingTranscription.Status.PENDING }

    /**
     * Mark an entry as completed with the transcribed text.
     */
    fun markCompleted(id: String, transcribedText: String) {
        val entries = getOrLoadEntries().toMutableList()
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) {
            entries[index] = entries[index].copy(
                status = PendingTranscription.Status.COMPLETED,
                transcribedText = transcribedText
            )
            persistEntries(entries)
            Log.d(TAG, "Marked completed: $id")
        }
    }

    /**
     * Delete an entry and its audio file.
     */
    fun delete(id: String) {
        val entries = getOrLoadEntries().toMutableList()
        val entry = entries.find { it.id == id }
        if (entry != null) {
            // Delete audio file
            val file = File(entry.audioFilePath)
            if (file.exists()) file.delete()
            entries.remove(entry)
            persistEntries(entries)
            Log.d(TAG, "Deleted pending transcription: $id")
        }
    }

    private fun getOrLoadEntries(): List<PendingTranscription> {
        cachedEntries?.let { return it }
        val loaded = loadFromDisk()
        cachedEntries = loaded.toMutableList()
        return loaded
    }

    private fun loadFromDisk(): List<PendingTranscription> {
        val jsonStr = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).mapNotNull { PendingTranscription.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load entries", e)
            emptyList()
        }
    }

    private fun persistEntries(entries: List<PendingTranscription>) {
        cachedEntries = entries.toMutableList()
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }
}
