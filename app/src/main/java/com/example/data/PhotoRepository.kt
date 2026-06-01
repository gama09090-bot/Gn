package com.example.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.photoDao()

    val allPhotos: Flow<List<PhotoEntity>> = dao.getAllPhotos()

    suspend fun saveImageToDisk(bytes: ByteArray, filename: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            out.write(bytes)
        }
        file.absolutePath
    }

    suspend fun insert(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        dao.insertPhoto(photo)
    }

    suspend fun delete(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(photo.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
        dao.deletePhotoById(photo.id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "photos")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {}
        dao.deleteAllPhotos()
    }
}
