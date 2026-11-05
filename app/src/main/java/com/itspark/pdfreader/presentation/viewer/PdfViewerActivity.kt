package com.itspark.pdfreader.presentation.viewer

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.PdfReaderApp
import com.itspark.pdfreader.R
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.databinding.ActivityPdfViewerBinding
import com.itspark.pdfreader.databinding.DialogGoToPageBinding
import com.itspark.pdfreader.monetization.AdPlacement
import com.itspark.pdfreader.monetization.BannerAdManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private lateinit var prefs: PreferencesRepository
    private var bannerAdManager: BannerAdManager? = null

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pageAdapter: PdfPageAdapter? = null
    private var thumbnailAdapter: PdfThumbnailAdapter? = null

    private var docPath: String = ""
    private var docName: String = ""
    private var docUriString: String = ""
    private var totalPages: Int = 0
    private var currentPage: Int = 0

    // Search state
    private var searchResults: List<PdfSearchResult> = emptyList()
    private var currentSearchIndex: Int = 0
    private var searchJob: Job? = null

    companion object {
        const val EXTRA_DOC_PATH = "extra_doc_path"
        const val EXTRA_DOC_NAME = "extra_doc_name"
        const val EXTRA_DOC_URI = "extra_doc_uri"

        fun launch(context: Context, path: String, name: String, uri: Uri) {
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra(EXTRA_DOC_PATH, path)
                putExtra(EXTRA_DOC_NAME, name)
                putExtra(EXTRA_DOC_URI, uri.toString())
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesRepository(this)

        docPath = intent.getStringExtra(EXTRA_DOC_PATH).orEmpty()
        docName = intent.getStringExtra(EXTRA_DOC_NAME).orEmpty()
        docUriString = intent.getStringExtra(EXTRA_DOC_URI).orEmpty()

        binding.tvViewerTitle.text = docName

        if (!initializePdfRenderer()) {
            Toast.makeText(this, "Unable to open PDF document", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupTopBar()
        setupPageRecyclerView()
        setupThumbnailRecyclerView()
        setupFloatingNavigation()
        setupBottomBar()
        setupSearch()
        setupBackNavigation()
        setupMonetization()
    }

    private fun setupMonetization() {
        // Standard Adaptive Banner pinned at bottom of viewer
        bannerAdManager = BannerAdManager(this)
        bannerAdManager?.loadStandardBanner(binding.layoutViewerBannerAd)

        // Preload Interstitial for document close
        PdfReaderApp.instance.interstitialAdManager.preload(AdPlacement.INTERSTITIAL_CLOSE_DOC)
    }

    override fun onResume() {
        super.onResume()
        bannerAdManager?.resume()
    }

    override fun onPause() {
        super.onPause()
        bannerAdManager?.pause()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchTopBar.visibility == View.VISIBLE) {
                    closeSearch()
                } else {
                    handleExitViewer()
                }
            }
        })
    }

    private fun handleExitViewer() {
        PdfReaderApp.instance.interstitialAdManager.showIfAllowed(
            activity = this,
            placement = AdPlacement.INTERSTITIAL_CLOSE_DOC,
            onAdClosed = {
                finish()
            }
        )
    }

    private fun initializePdfRenderer(): Boolean {
        val file = File(docPath)
        try {
            fileDescriptor = if (file.exists()) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                val uri = Uri.parse(docUriString)
                contentResolver.openFileDescriptor(uri, "r")
            }
            val pfd = fileDescriptor ?: return false
            pdfRenderer = PdfRenderer(pfd)
            totalPages = pdfRenderer?.pageCount ?: 0
            return totalPages > 0
        } catch (_: Exception) {
            return false
        }
    }

    // ─── Top Bar Setup ────────────────────────────────────────────────────────

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            handleExitViewer()
        }

        updateBookmarkIcon()
        binding.btnViewerBookmark.setOnClickListener {
            if (prefs.isBookmarked(docPath)) {
                prefs.removeBookmark(docPath)
                Toast.makeText(this, "Removed from Bookmarks", Toast.LENGTH_SHORT).show()
            } else {
                prefs.addBookmark(docPath)
                Toast.makeText(this, "Added to Bookmarks", Toast.LENGTH_SHORT).show()
            }
            updateBookmarkIcon()
        }

        binding.btnViewerShare.setOnClickListener {
            shareCurrentDocument()
        }

        binding.btnViewerMore.setOnClickListener {
            Toast.makeText(this, "File: $docName\nPages: $totalPages", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBookmarkIcon() {
        val isBookmarked = prefs.isBookmarked(docPath)
        binding.btnViewerBookmark.setImageResource(
            if (isBookmarked) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    // ─── Main Pages RecyclerView ──────────────────────────────────────────────

    private fun setupPageRecyclerView() {
        val renderer = pdfRenderer ?: return
        pageAdapter = PdfPageAdapter(renderer, lifecycleScope)
        val layoutManager = LinearLayoutManager(this)

        binding.rvPdfPages.apply {
            this.layoutManager = layoutManager
            adapter = pageAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(6)
            itemAnimator = null

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisible != RecyclerView.NO_POSITION && firstVisible != currentPage) {
                        currentPage = firstVisible
                        updatePageIndicator()
                        thumbnailAdapter?.updateSelection(currentPage)
                        binding.rvThumbnails.scrollToPosition(currentPage)
                    }
                }
            })
        }

        updatePageIndicator()
    }

    private fun updatePageIndicator() {
        binding.tvPageIndicator.text = "${currentPage + 1} / $totalPages"
    }

    // ─── Horizontal Thumbnails ────────────────────────────────────────────────

    private fun setupThumbnailRecyclerView() {
        val renderer = pdfRenderer ?: return
        thumbnailAdapter = PdfThumbnailAdapter(renderer, lifecycleScope) { selectedIdx ->
            scrollToPage(selectedIdx)
        }

        binding.rvThumbnails.apply {
            layoutManager = LinearLayoutManager(this@PdfViewerActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = thumbnailAdapter
            setHasFixedSize(true)
        }
    }

    // ─── Floating Prev/Next Controls ──────────────────────────────────────────

    private fun setupFloatingNavigation() {
        binding.btnFloatPrev.setOnClickListener {
            if (currentPage > 0) {
                scrollToPage(currentPage - 1)
            }
        }

        binding.btnFloatNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                scrollToPage(currentPage + 1)
            }
        }
    }

    private fun scrollToPage(pageIndex: Int, forceScroll: Boolean = true) {
        val target = pageIndex.coerceIn(0, totalPages - 1)
        currentPage = target

        val layoutManager = binding.rvPdfPages.layoutManager as? LinearLayoutManager
        val first = layoutManager?.findFirstVisibleItemPosition() ?: -1
        val last = layoutManager?.findLastVisibleItemPosition() ?: -1

        // Avoid jerky scroll snaps if the target page is already in the viewport
        if (forceScroll || target < first || target > last) {
            binding.rvPdfPages.scrollToPosition(target)
        }

        updatePageIndicator()
        thumbnailAdapter?.updateSelection(target)
        binding.rvThumbnails.scrollToPosition(target)
    }

    // ─── Bottom Actions (Edit, Search, Go, Thumbnail) ─────────────────────────

    private fun setupBottomBar() {
        // 1. Edit
        binding.btnBottomEdit.setOnClickListener {
            Toast.makeText(this, "Premium Edit & Annotate tools", Toast.LENGTH_SHORT).show()
        }

        // 2. Search
        binding.btnBottomSearch.setOnClickListener {
            openSearch()
        }

        // 3. Go (Page navigator)
        binding.btnBottomGo.setOnClickListener {
            showGoToPageDialog()
        }

        // 4. Thumbnail toggle
        binding.btnBottomThumbnail.setOnClickListener {
            val isVisible = binding.layoutThumbnails.visibility == View.VISIBLE
            binding.layoutThumbnails.visibility = if (isVisible) View.GONE else View.VISIBLE
            if (!isVisible) {
                binding.rvThumbnails.scrollToPosition(currentPage)
            }
        }
    }

    // ─── Search Functionality with Visual Highlighting ───────────────────────

    private fun setupSearch() {
        binding.etSearchPdf.addTextChangedListener { text ->
            val query = text?.toString().orEmpty().trim()
            searchJob?.cancel()

            if (query.length < 2) {
                searchResults = emptyList()
                currentSearchIndex = 0
                binding.tvSearchMatches.visibility = View.GONE
                pageAdapter?.clearSearchHighlights()
                return@addTextChangedListener
            }

            searchJob = lifecycleScope.launch {
                delay(300) // Debounce
                val results = PdfTextSearchEngine.search(
                    context = this@PdfViewerActivity,
                    pdfPath = docPath,
                    pdfUriString = docUriString,
                    query = query,
                    totalPages = totalPages
                )
                searchResults = results
                currentSearchIndex = 0

                if (results.isNotEmpty()) {
                    binding.tvSearchMatches.visibility = View.VISIBLE
                    updateSearchMatchCount()

                    // Group all match rects by page
                    val rectsByPage = results.groupBy(
                        keySelector = { it.pageIndex },
                        valueTransform = { it.matchRect }
                    )
                    val matchingPages = rectsByPage.keys
                    val firstMatch = results[0]
                    pageAdapter?.setSearchHighlights(
                        pages = matchingPages,
                        rects = rectsByPage,
                        activePage = firstMatch.pageIndex,
                        activeMatchRect = firstMatch.matchRect
                    )

                    scrollToPage(firstMatch.pageIndex, forceScroll = false)
                } else {
                    binding.tvSearchMatches.visibility = View.VISIBLE
                    binding.tvSearchMatches.text = "0 matches"
                    pageAdapter?.clearSearchHighlights()
                }
            }
        }

        binding.btnSearchNext.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
                updateSearchMatchCount()
                val currentMatch = searchResults[currentSearchIndex]
                pageAdapter?.setActiveMatch(currentMatch.pageIndex, currentMatch.matchRect)
                scrollToPage(currentMatch.pageIndex, forceScroll = false)
            }
        }

        binding.btnSearchPrev.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex - 1 < 0) searchResults.size - 1 else currentSearchIndex - 1
                updateSearchMatchCount()
                val currentMatch = searchResults[currentSearchIndex]
                pageAdapter?.setActiveMatch(currentMatch.pageIndex, currentMatch.matchRect)
                scrollToPage(currentMatch.pageIndex, forceScroll = false)
            }
        }

        binding.btnSearchCancel.setOnClickListener {
            closeSearch()
        }
    }

    private fun openSearch() {
        binding.topBar.visibility = View.GONE
        binding.searchTopBar.visibility = View.VISIBLE
        binding.etSearchPdf.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etSearchPdf, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        binding.searchTopBar.visibility = View.GONE
        binding.topBar.visibility = View.VISIBLE
        binding.etSearchPdf.text?.clear()
        binding.tvSearchMatches.visibility = View.GONE
        searchResults = emptyList()
        currentSearchIndex = 0
        pageAdapter?.clearSearchHighlights()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etSearchPdf.windowToken, 0)
    }

    private fun updateSearchMatchCount() {
        binding.tvSearchMatches.text = "${currentSearchIndex + 1} / ${searchResults.size}"
    }

    // ─── Go to Page Dialog ────────────────────────────────────────────────────

    private fun showGoToPageDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val goBinding = DialogGoToPageBinding.inflate(layoutInflater)
        dialog.setContentView(goBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        goBinding.tvGoPageHint.text = "Enter page number between 1 and $totalPages"
        goBinding.etTargetPage.hint = "${currentPage + 1}"

        goBinding.btnGoCancel.setOnClickListener {
            dialog.dismiss()
        }

        goBinding.btnGoConfirm.setOnClickListener {
            val input = goBinding.etTargetPage.text.toString().trim()
            val pageNum = input.toIntOrNull()
            if (pageNum == null || pageNum < 1 || pageNum > totalPages) {
                Toast.makeText(this, "Please enter a valid page (1 - $totalPages)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            scrollToPage(pageNum - 1)
        }

        dialog.show()
    }

    // ─── Share ────────────────────────────────────────────────────────────────

    private fun shareCurrentDocument() {
        val file = File(docPath)
        val contentUri: Uri = if (file.exists()) {
            try {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            } catch (_: Exception) {
                Uri.parse(docUriString)
            }
        } else {
            Uri.parse(docUriString)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share $docName"))
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerAdManager?.destroy()
        bannerAdManager = null
        searchJob?.cancel()
        pageAdapter?.clearCache()
        thumbnailAdapter?.clearCache()

        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        try {
            fileDescriptor?.close()
        } catch (_: Exception) {}
    }
}
