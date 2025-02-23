package com.alishoumar.androidstorage.domain.repository

import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import com.alishoumar.androidstorage.domain.models.ExternalStoragePhoto

interface ExternalStorageRepository {

    suspend fun loadPhotosFromExternalStorage(collection: Uri): List<ExternalStoragePhoto>

    suspend fun savePhotoToExternalStorage(
        collection: Uri,
        displayName: String,
        bitmap: Bitmap
    )

    suspend fun deletePhotoFromExternalStorage(
        photoUri: Uri
    )

    suspend fun deletePhotoFromExternalStorageApi29AndAbove(
        photoUri: Uri,
        recoverableSecurityException: RecoverableSecurityException?
    ): IntentSender?
}