package com.itspark.pdfreader.presentation.tools

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.databinding.ActivityImageToPdfPreviewBinding
import com.itspark.pdfreader.databinding.DialogConvertPdfNameBinding
import com.itspark.pdfreader.presentation.viewer.PdfViewerActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageToPdfPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageToPdfPreviewBinding
    private lateinit var adapter: ImageReorderAdapter

    private val pickMoreImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { newUris: List<Uri> ->
        if (newUris.isNotEmpty()) {
            adapter.addItems(newUris)
            updateHeaderAndBottomState(adapter.itemCount)
        }
    }

    companion object {
        const val EXTRA_IMAGE_URIS = "extra_image_uris"

        fun createIntent(context: Context, uris: List<Uri>): Intent {
            return Intent(context, ImageToPdfPreviewActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_IMAGE_URIS, ArrayList(uris))
            }
        }

        fun launch(context: Context, uris: List<Uri>) {
            context.startActivity(createIntent(context, uris))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageToPdfPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uris: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_IMAGE_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_IMAGE_URIS)
        } ?: emptyList()

        setupViews(uris)
        setupDragAndDrop()
    }

    private fun setupViews(initialUris: List<Uri>) {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAddMoreImages.setOnClickListener {
            pickMoreImagesLauncher.launch("image/*")
        }

        binding.btnEmptyAddImages.setOnClickListener {
            pickMoreImagesLauncher.launch("image/*")
        }

        adapter = ImageReorderAdapter(
            initialUris = initialUris,
            scope = lifecycleScope,
            onListChanged = { count ->
                updateHeaderAndBottomState(count)
            }
        )

        binding.rvSelectedImages.apply {
            layoutManager = GridLayoutManager(this@ImageToPdfPreviewActivity, 2)
            adapter = this@ImageToPdfPreviewActivity.adapter
            setHasFixedSize(true)
        }

        binding.btnConvertToPdf.setOnClickListener {
            val currentItems = adapter.getItems()
            if (currentItems.isEmpty()) {
                Toast.makeText(this, "Please select at least 1 image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showConvertNameDialog(currentItems)
        }

        updateHeaderAndBottomState(initialUris.size)
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    adapter.moveItem(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.animate()
                        ?.scaleX(1.04f)
                        ?.scaleY(1.04f)
                        ?.setDuration(120)
                        ?.start()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate()
                    ?.scaleX(1.0f)
                    ?.scaleY(1.0f)
                    ?.setDuration(120)
                    ?.start()
            }
        }

        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.rvSelectedImages)
    }

    private fun updateHeaderAndBottomState(itemCount: Int) {
        if (itemCount == 0) {
            binding.rvSelectedImages.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvTopSubtitle.text = "0 images selected"
            binding.tvBottomPageCount.text = "0 Pages"
            binding.btnConvertToPdf.isEnabled = false
            binding.btnConvertToPdf.alpha = 0.5f
        } else {
            binding.rvSelectedImages.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            binding.tvTopSubtitle.text = "$itemCount image${if (itemCount > 1) "s" else ""} • Drag to reorder"
            binding.tvBottomPageCount.text = "$itemCount Page${if (itemCount > 1) "s" else ""}"
            binding.btnConvertToPdf.isEnabled = true
            binding.btnConvertToPdf.alpha = 1.0f
        }
    }

    private fun showConvertNameDialog(orderedUris: List<Uri>) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogConvertPdfNameBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val defaultName = "ImageToPdf_$timeStamp"
        dialogBinding.etPdfFileName.setText(defaultName)
        dialogBinding.tvConvertSubtitle.text = "${orderedUris.size} image(s) will be merged into this PDF"

        dialogBinding.btnCancelConvert.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmConvert.setOnClickListener {
            var inputName = dialogBinding.etPdfFileName.text.toString().trim()
            if (inputName.isEmpty()) {
                inputName = defaultName
            }
            if (!inputName.endsWith(".pdf", ignoreCase = true)) {
                inputName += ".pdf"
            }
            dialog.dismiss()
            performConversion(orderedUris, inputName)
        }

        dialog.show()
    }

    private fun performConversion(orderedUris: List<Uri>, outputFileName: String) {
        binding.layoutLoadingOverlay.visibility = View.VISIBLE
        binding.tvLoadingMessage.text = "Creating ${orderedUris.size}-page PDF..."

        lifecycleScope.launch {
            val pdfFile = ImageToPdfConverter.convertImagesToPdf(
                context = this@ImageToPdfPreviewActivity,
                imageUris = orderedUris,
                outputFileName = outputFileName
            )

            binding.layoutLoadingOverlay.visibility = View.GONE

            if (pdfFile != null && pdfFile.exists()) {
                Toast.makeText(this@ImageToPdfPreviewActivity, "Saved to Documents: ${pdfFile.name}", Toast.LENGTH_LONG).show()

                // Await MediaScanner confirmation so MediaStore is updated immediately
                try {
                    suspendCancellableCoroutine<Uri?> { cont ->
                        MediaScannerConnection.scanFile(
                            applicationContext,
                            arrayOf(pdfFile.absolutePath),
                            arrayOf("application/pdf")
                        ) { _, scannedUri ->
                            if (cont.isActive) cont.resume(scannedUri)
                        }
                    }
                } catch (_: Exception) {}

                val resultIntent = Intent().apply {
                    putExtra("created_pdf_path", pdfFile.absolutePath)
                    putExtra("created_pdf_name", pdfFile.name)
                }
                setResult(RESULT_OK, resultIntent)

                // Open directly in PDF viewer
                PdfViewerActivity.launch(
                    context = this@ImageToPdfPreviewActivity,
                    path = pdfFile.absolutePath,
                    name = pdfFile.name,
                    uri = Uri.fromFile(pdfFile)
                )

                finish()
            } else {
                Toast.makeText(this@ImageToPdfPreviewActivity, "Failed to create PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
