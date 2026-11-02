package com.itspark.pdfreader.presentation.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object ImageToPdfConverter {

    private const val MAX_PAGE_WIDTH = 1200

    /**
     * Converts selected image Uris into a standardized multi-page PDF document.
     * Uses inSampleSize pre-downsampling and RGB_565 to prevent OutOfMemoryError on large camera photos.
     */
    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFileName: String? = null
    ): File? = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext null

        val document = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        try {
            var pageNumber = 1
            for (uri in imageUris) {
                val bitmap = loadOptimizedBitmap(context, uri, MAX_PAGE_WIDTH) ?: continue

                val targetWidth = bitmap.width.coerceAtLeast(1)
                val targetHeight = bitmap.height.coerceAtLeast(1)

                val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, pageNumber).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                document.finishPage(page)

                bitmap.recycle()
                pageNumber++
            }

            if (pageNumber == 1) {
                document.close()
                return@withContext null
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseFileName = outputFileName?.trim()?.takeIf { it.isNotEmpty() } ?: "ImageToPdf_$timeStamp.pdf"
            val validFileName = if (!baseFileName.endsWith(".pdf", ignoreCase = true)) "$baseFileName.pdf" else baseFileName

            // Target public Documents directory: /storage/emulated/0/Documents/PDF Reader/
            val publicDocsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "PDF Reader"
            )

            val outputDir = if (publicDocsDir.exists() || publicDocsDir.mkdirs()) {
                publicDocsDir
            } else {
                // Fallback to app external or internal storage if public Documents is unmounted
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: File(context.filesDir, "CreatedPDFs").apply { mkdirs() }
            }

            var outputFile = File(outputDir, validFileName)
            if (outputFile.exists()) {
                val nameWithoutExt = validFileName.substringBeforeLast(".")
                val ext = validFileName.substringAfterLast(".", "pdf")
                var counter = 1
                while (outputFile.exists()) {
                    outputFile = File(outputDir, "${nameWithoutExt}_$counter.$ext")
                    counter++
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            outputFile
        } catch (_: Exception) {
            try {
                document.close()
            } catch (_: Exception) {}
            null
        }
    }

    private fun loadOptimizedBitmap(context: Context, uri: Uri, targetMaxDim: Int): Bitmap? {
        return try {
            val resolver = context.contentResolver

            // 1. Read EXIF Orientation
            var orientation = ExifInterface.ORIENTATION_NORMAL
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }

            // 2. Decode Bounds only (allocates zero pixel memory)
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) return null

            // 3. Compute inSampleSize for memory-safe decoding
            var inSampleSize = 1
            val maxRawDim = max(rawWidth, rawHeight)
            while ((maxRawDim / inSampleSize) > (targetMaxDim * 2)) {
                inSampleSize *= 2
            }

            // 4. Decode downsampled bitmap directly into RGB_565 (50% memory savings)
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val sampledBitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 5. Apply EXIF Rotation if necessary
            val rotatedBitmap = applyExifRotation(sampledBitmap, orientation)

            // 6. Final scale to exact max target bounds if needed
            val targetWidth = rotatedBitmap.width.coerceAtMost(targetMaxDim)
            val targetHeight = (rotatedBitmap.height * (targetWidth.toFloat() / rotatedBitmap.width)).toInt().coerceAtLeast(1)

            if (rotatedBitmap.width != targetWidth || rotatedBitmap.height != targetHeight) {
                val finalScaled = Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, targetHeight, true)
                if (finalScaled != rotatedBitmap) {
                    rotatedBitmap.recycle()
                }
                finalScaled
            } else {
                rotatedBitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
