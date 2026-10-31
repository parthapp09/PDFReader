package com.itspark.pdfreader.presentation.home.model

import android.net.Uri
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import java.io.File

object DummyFilesProvider {

    /**
     * Builds DocumentModels backed by real files copied from assets.
     * These files live in cacheDir so no storage permission is needed.
     *
     * @param sampleFiles  Map produced by SampleFilesManager.ensureSamplesExist()
     */
    fun getDummyDocuments(sampleFiles: Map<DocumentFormat, File>): List<DocumentModel> {
        val now = System.currentTimeMillis()

        return listOfNotNull(
            sampleFiles[DocumentFormat.PDF]?.toDummyModel(
                id = -1L, ext = "pdf", format = DocumentFormat.PDF, offset = 0L, now = now
            ),
            sampleFiles[DocumentFormat.WORD]?.toDummyModel(
                id = -2L, ext = "docx", format = DocumentFormat.WORD, offset = 3_600_000L, now = now
            ),
            sampleFiles[DocumentFormat.EXCEL]?.toDummyModel(
                id = -3L, ext = "xlsx", format = DocumentFormat.EXCEL, offset = 7_200_000L, now = now
            ),
            sampleFiles[DocumentFormat.PPT]?.toDummyModel(
                id = -4L, ext = "pptx", format = DocumentFormat.PPT, offset = 10_800_000L, now = now
            )
        )
    }

    private fun File.toDummyModel(
        id: Long,
        ext: String,
        format: DocumentFormat,
        offset: Long,
        now: Long
    ) = DocumentModel(
        id             = id,
        name           = name,
        path           = absolutePath,
        uri            = Uri.fromFile(this),
        sizeBytes      = length(),
        dateModifiedMs = now - offset,
        extension      = ext,
        format         = format,
        isBookmarked   = false
    )
}
