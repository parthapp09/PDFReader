package com.itspark.pdfreader.data.source

import android.content.Context
import com.itspark.pdfreader.domain.model.DocumentFormat
import java.io.File

/**
 * Copies the 4 bundled sample files from assets/default_files/ into
 * cacheDir/default_files/ on first launch. No storage permission needed.
 *
 * Files ship inside the APK — no generation logic required.
 */
object SampleFilesManager {

    private val SAMPLE_FILES = listOf(
        "file_example_PDF.pdf"   to DocumentFormat.PDF,
        "file_example_WORD.docx" to DocumentFormat.WORD,
        "file_example_EXCEL.xlsx" to DocumentFormat.EXCEL,
        "file_example_PPT.pptx"  to DocumentFormat.PPT
    )

    /**
     * Returns a map of DocumentFormat → real File in cacheDir.
     * Copies from assets only when the file doesn't exist yet.
     */
    fun ensureSamplesExist(context: Context): Map<DocumentFormat, File> {
        val dir = File(context.cacheDir, "default_files").also { it.mkdirs() }

        return SAMPLE_FILES.associate { (fileName, format) ->
            val dest = File(dir, fileName)
            if (!dest.exists()) {
                context.assets.open("default_files/$fileName").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            format to dest
        }
    }
}
