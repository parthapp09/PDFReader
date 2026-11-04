package com.itspark.pdfreader.presentation.home

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.itspark.pdfreader.R
import com.itspark.pdfreader.data.repository.DocumentRepositoryImpl
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.data.source.MediaStoreScanner
import com.itspark.pdfreader.databinding.ActivityHomeBinding
import com.itspark.pdfreader.databinding.BottomSheetFileOptionsBinding
import com.itspark.pdfreader.databinding.DialogDeleteConfirmBinding
import com.itspark.pdfreader.databinding.DialogFileInfoBinding
import com.itspark.pdfreader.databinding.DialogRenameFileBinding
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.domain.model.NavigationTab
import com.itspark.pdfreader.monetization.MonetizationManager
import com.itspark.pdfreader.presentation.home.adapter.DocumentAdapter
import com.itspark.pdfreader.presentation.home.dialog.PermissionAccessDialog
import com.itspark.pdfreader.presentation.language.LanguageActivity
import com.itspark.pdfreader.presentation.tools.ImageToPdfConverter
import com.itspark.pdfreader.presentation.tools.ImageToPdfPreviewActivity
import com.itspark.pdfreader.presentation.tools.PdfToolItem
import com.itspark.pdfreader.presentation.tools.PdfToolType
import com.itspark.pdfreader.presentation.tools.PdfToolsAdapter
import com.itspark.pdfreader.presentation.tools.PdfToolsDataProvider
import com.itspark.pdfreader.presentation.utils.FormatUtils
import com.itspark.pdfreader.presentation.viewer.PdfViewerActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.itspark.pdfreader.databinding.DialogExitAppBinding
import kotlinx.coroutines.launch
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: PreferencesRepository
    private var permissionDialog: PermissionAccessDialog? = null

    // ViewModel factory — AndroidViewModel so it accesses application context & preferences
    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val scanner = MediaStoreScanner(applicationContext)
                val repo = DocumentRepositoryImpl(scanner)
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(application, repo) as T
            }
        }
    }

    private lateinit var documentAdapter: DocumentAdapter
    private lateinit var toolsAdapter: PdfToolsAdapter

    private val previewImageToPdfLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val createdPath = result.data?.getStringExtra("created_pdf_path")
            // Automatically switch to Home bottom navigation item
            binding.bottomNavigationView.selectedItemId = R.id.nav_home
            // Explicitly select the PDF format tab (index 1)
            val pdfTab = binding.tabLayout.getTabAt(1)
            pdfTab?.select()
            // Force ViewModel to switch to PDF format and fetch documents
            viewModel.onTabChanged(DocumentFormat.PDF, force = true)
            viewModel.fetchDocuments(createdPath)
        }
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val intent = ImageToPdfPreviewActivity.createIntent(this, uris)
            previewImageToPdfLauncher.launch(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesRepository(this)

        // 1. First-Time User Flow: Redirect to Language Selection Screen
        if (prefs.isFirstLaunch) {
            startActivity(Intent(this, LanguageActivity::class.java))
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDualColorTitle()
        setupTabs()
        setupRecyclerView()
        setupToolsRecyclerView()
        setupTopBarActions()
        setupSearch()
        setupRecentlyAddedBanner()
        setupBottomNavigation()
        setupBackNavigation()
        observeViewModel()
        applyCohortMonetizationRules()

        // 2. Initial Permission Evaluation
        evaluatePermissionAndLoad()
    }

    private fun applyCohortMonetizationRules() {
        // Enforce cohort-based ad display policy (e.g. suppression or tailored display for paid users)
        binding.layoutBottomBannerAd.visibility =
            if (MonetizationManager.shouldShowBanner(this)) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            if (permissionDialog?.isShowing == true) permissionDialog?.dismiss()
            viewModel.fetchDocuments()
        }
    }

    private fun evaluatePermissionAndLoad() {
        if (hasStoragePermission()) {
            viewModel.fetchDocuments()
        } else {
            // No permission: show sample files (backed by cacheDir) + permission modal
            viewModel.loadDummyDocuments()
            showPermissionModalDialog()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showPermissionModalDialog() {
        if (isFinishing || isDestroyed) return
        if (permissionDialog?.isShowing == true) return
        permissionDialog = PermissionAccessDialog(this) { openSystemFileAccessSettings() }
        permissionDialog?.show()
    }

    private fun openSystemFileAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    // ─── UI Setup ────────────────────────────────────────────────────────────

    private fun setupDualColorTitle() {
        val title = "PDF READER"
        val spannable = SpannableString(title)
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#EA3434")),
            0, 3,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvHeaderTitle.text = spannable
    }

    private fun setupTabs() {
        val tabs = listOf(
            DocumentFormat.ALL,
            DocumentFormat.PDF,
            DocumentFormat.WORD,
            DocumentFormat.PPT,
            DocumentFormat.EXCEL
        )
        tabs.forEach { format ->
            binding.tabLayout.addTab(
                binding.tabLayout.newTab().setText(format.title).also { it.tag = format }
            )
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.onTabChanged(tab?.tag as? DocumentFormat ?: DocumentFormat.ALL)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                viewModel.onTabChanged(tab?.tag as? DocumentFormat ?: DocumentFormat.ALL, force = true)
            }
        })
    }

    private fun setupRecyclerView() {
        documentAdapter = DocumentAdapter(
            onItemClick      = { doc -> openDocument(doc) },
            onBookmarkToggle = { doc -> viewModel.toggleBookmark(doc) },
            onMenuClick      = { doc -> showFileOptionsBottomSheet(doc) }
        ).apply {
            stateRestorationPolicy = androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
        binding.rvDocuments.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            adapter = documentAdapter
        }
    }

    private fun setupTopBarActions() {
        binding.btnSearch.setOnClickListener {
            binding.searchContainer.visibility = View.VISIBLE
            binding.etSearchQuery.requestFocus()
        }
        binding.btnCloseSearch.setOnClickListener {
            binding.etSearchQuery.text?.clear()
            binding.searchContainer.visibility = View.GONE
            viewModel.onSearchQuery("")
        }
        binding.btnCrown.setOnClickListener {
            Toast.makeText(this, "Premium VIP features", Toast.LENGTH_SHORT).show()
        }
        binding.btnFilter.setOnClickListener {
            Toast.makeText(this, "Sort by Name, Date, Size", Toast.LENGTH_SHORT).show()
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
        }
    }

    private fun setupSearch() {
        binding.etSearchQuery.addTextChangedListener { text ->
            viewModel.onSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun setupRecentlyAddedBanner() {
        binding.layoutRecentlyAdded.setOnClickListener {
            Toast.makeText(this, "Opening Recently Added Files", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupToolsRecyclerView() {
        toolsAdapter = PdfToolsAdapter(PdfToolsDataProvider.getTools()) { tool ->
            onToolClicked(tool)
        }
        binding.rvTools.apply {
            layoutManager = GridLayoutManager(this@HomeActivity, 3)
            adapter = toolsAdapter
            setHasFixedSize(true)
        }

        binding.btnToolsCrown.setOnClickListener {
            Toast.makeText(this, "Premium VIP features", Toast.LENGTH_SHORT).show()
        }
        binding.btnToolsSettings.setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
        }
    }

    private fun onToolClicked(tool: PdfToolItem) {
        if (tool.type == PdfToolType.IMAGE_TO_PDF) {
            pickImagesLauncher.launch("image/*")
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchContainer.visibility == View.VISIBLE) {
                    binding.etSearchQuery.text?.clear()
                    binding.searchContainer.visibility = View.GONE
                    viewModel.onSearchQuery("")
                } else if (binding.bottomNavigationView.selectedItemId != R.id.nav_home) {
                    binding.bottomNavigationView.selectedItemId = R.id.nav_home
                } else {
                    showExitAppDialog()
                }
            }
        })
    }

    private fun showExitAppDialog() {
        if (isFinishing || isDestroyed) return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val exitBinding = DialogExitAppBinding.inflate(layoutInflater)
        dialog.setContentView(exitBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        exitBinding.btnCancelExit.setOnClickListener {
            dialog.dismiss()
        }
        exitBinding.btnConfirmExit.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    viewModel.fetchDocuments()
                    viewModel.setNavigationTab(NavigationTab.HOME)
                    true
                }
                R.id.nav_bookmarks -> {
                    viewModel.setNavigationTab(NavigationTab.BOOKMARKS)
                    true
                }
                R.id.nav_tools -> {
                    viewModel.setNavigationTab(NavigationTab.TOOLS)
                    true
                }
                R.id.nav_recent -> {
                    Toast.makeText(this, "Recent files feature coming soon", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> false
            }
        }
    }

    // ─── ViewModel observation ────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HomeUiState.Loading -> {
                            binding.tvEmptyState.visibility = View.GONE
                        }
                        is HomeUiState.Success -> {
                            val isTools = state.navTab == NavigationTab.TOOLS
                            val isBookmarks = state.navTab == NavigationTab.BOOKMARKS

                            if (isTools) {
                                binding.tvHeaderTitle.text = "PDF TOOLS"
                                binding.layoutHomeActions.visibility = View.GONE
                                binding.layoutToolsActions.visibility = View.VISIBLE

                                binding.tabLayout.visibility = View.GONE
                                binding.layoutRecentlyAdded.visibility = View.GONE
                                binding.rvDocuments.visibility = View.GONE
                                binding.tvEmptyState.visibility = View.GONE
                                binding.searchContainer.visibility = View.GONE
                                binding.rvTools.visibility = View.VISIBLE
                            } else {
                                binding.layoutToolsActions.visibility = View.GONE
                                binding.layoutHomeActions.visibility = View.VISIBLE
                                binding.rvTools.visibility = View.GONE
                                binding.tabLayout.visibility = View.VISIBLE
                                binding.rvDocuments.visibility = View.VISIBLE

                                if (isBookmarks) {
                                    binding.tvHeaderTitle.text = "Bookmarks"
                                    binding.layoutRecentlyAdded.visibility = View.GONE
                                } else {
                                    setupDualColorTitle()
                                    binding.layoutRecentlyAdded.visibility =
                                        if (state.isSearching) View.GONE else View.VISIBLE
                                }

                                documentAdapter.submitList(state.documents) {
                                    if (state.navTab == NavigationTab.HOME && !state.isSearching) {
                                        val layoutManager = binding.rvDocuments.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                        val firstPos = layoutManager?.findFirstVisibleItemPosition() ?: 0
                                        if (firstPos <= 1) {
                                            binding.rvDocuments.scrollToPosition(0)
                                        }
                                    }
                                }

                                if (state.documents.isEmpty()) {
                                    binding.tvEmptyState.visibility = View.VISIBLE
                                    binding.tvEmptyState.text = if (isBookmarks) {
                                        "No bookmarked files yet\nTap the star icon on any document to add it here"
                                    } else {
                                        "No files found"
                                    }
                                } else {
                                    binding.tvEmptyState.visibility = View.GONE
                                }
                            }
                        }
                        is HomeUiState.Empty -> {
                            documentAdapter.submitList(emptyList())
                            binding.tvEmptyState.visibility = View.VISIBLE
                            binding.tvEmptyState.text = state.message
                        }
                        is HomeUiState.Error -> {
                            Toast.makeText(this@HomeActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // ─── 3-Dots Options Menu & Dialogs ────────────────────────────────────────

    private fun showFileOptionsBottomSheet(item: DocumentModel) {
        val sheetDialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetFileOptionsBinding.inflate(layoutInflater)
        sheetDialog.setContentView(sheetBinding.root)

        // Bind document info in the header
        sheetBinding.tvSheetDocName.text = item.name
        sheetBinding.tvSheetDocMeta.text = "${FormatUtils.formatDate(item.dateModifiedMs)} • ${item.formattedSize}"
        val badgeDrawable = when (item.format) {
            DocumentFormat.PDF -> R.drawable.ic_doc_page_pdf
            DocumentFormat.WORD -> R.drawable.ic_doc_page_word
            DocumentFormat.EXCEL -> R.drawable.ic_doc_page_excel
            DocumentFormat.PPT -> R.drawable.ic_doc_page_ppt
            DocumentFormat.TXT, DocumentFormat.ALL -> R.drawable.ic_doc_page_pdf
        }
        sheetBinding.ivSheetBadge.setImageResource(badgeDrawable)

        sheetBinding.actionShare.setOnClickListener {
            sheetDialog.dismiss()
            shareDocument(item)
        }

        sheetBinding.actionRename.setOnClickListener {
            sheetDialog.dismiss()
            showRenameDialog(item)
        }

        sheetBinding.actionDelete.setOnClickListener {
            sheetDialog.dismiss()
            showDeleteDialog(item)
        }

        sheetBinding.actionInfo.setOnClickListener {
            sheetDialog.dismiss()
            showFileInfoDialog(item)
        }

        sheetDialog.show()
    }

    /**
     * Dialog showing file details: Name, Size, Path, and Date Modified with OK button.
     */
    private fun showFileInfoDialog(item: DocumentModel) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val infoBinding = DialogFileInfoBinding.inflate(layoutInflater)
        dialog.setContentView(infoBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        infoBinding.tvInfoName.text = item.name
        infoBinding.tvInfoSize.text = "${item.formattedSize} (${item.sizeBytes} bytes)"
        infoBinding.tvInfoPath.text = item.path
        infoBinding.tvInfoDate.text = FormatUtils.formatDateTime(item.dateModifiedMs)

        infoBinding.btnInfoOk.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Dialog to input a new file name without altering the extension.
     */
    private fun showRenameDialog(item: DocumentModel) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val renameBinding = DialogRenameFileBinding.inflate(layoutInflater)
        dialog.setContentView(renameBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val nameWithoutExt = if (item.name.contains(".")) {
            item.name.substringBeforeLast(".")
        } else {
            item.name
        }
        renameBinding.etNewFileName.setText(nameWithoutExt)
        renameBinding.etNewFileName.selectAll()
        renameBinding.tvExtensionSuffix.text = ".${item.extension}"

        renameBinding.btnRenameCancel.setOnClickListener {
            dialog.dismiss()
        }

        renameBinding.btnRenameConfirm.setOnClickListener {
            val newName = renameBinding.etNewFileName.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.renameDocument(item, newName) { success, errorMsg ->
                if (success) {
                    dialog.dismiss()
                    Toast.makeText(this, "File renamed successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, errorMsg ?: "Failed to rename", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    /**
     * Confirmation dialog before deleting a file.
     */
    private fun showDeleteDialog(item: DocumentModel) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val deleteBinding = DialogDeleteConfirmBinding.inflate(layoutInflater)
        dialog.setContentView(deleteBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        deleteBinding.tvDeleteMessage.text =
            "Are you sure you want to delete '${item.name}'? This action cannot be undone."

        deleteBinding.btnDeleteCancel.setOnClickListener {
            dialog.dismiss()
        }

        deleteBinding.btnDeleteConfirm.setOnClickListener {
            viewModel.deleteDocument(item) { success, errorMsg ->
                dialog.dismiss()
                if (success) {
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, errorMsg ?: "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    // ─── File Opening & Sharing ───────────────────────────────────────────────

    private fun openDocument(item: DocumentModel) {
        // Evaluate cohort-based interstitial ad frequency and cooldown
        if (MonetizationManager.canShowInterstitial(this, "open_document")) {
            MonetizationManager.recordInterstitialShown(this, "open_document")
        }

        // Built-in PDF Viewer: opens PDF directly inside our app
        if (item.format == DocumentFormat.PDF || item.extension.equals("pdf", ignoreCase = true)) {
            if (item.id >= 0 && !hasStoragePermission()) {
                showPermissionModalDialog()
                return
            }
            PdfViewerActivity.launch(this, item.path, item.name, item.uri)
            return
        }

        // For non-PDF documents (Word, Excel, PPT, TXT), open in external viewer app
        val contentUri: Uri = if (item.id < 0) {
            try {
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    File(item.path)
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            if (!hasStoragePermission()) {
                showPermissionModalDialog()
                return
            }
            item.uri
        }

        val mime = getMimeType(item.extension)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No app found to open ${item.extension.uppercase()} files", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDocument(item: DocumentModel) {
        val contentUri: Uri = if (item.id < 0) {
            try {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", File(item.path))
            } catch (_: Exception) {
                item.uri
            }
        } else {
            item.uri
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(item.extension)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share ${item.name}"))
    }

    private fun getMimeType(ext: String): String = when (ext.lowercase()) {
        "pdf"       -> "application/pdf"
        "doc"       -> "application/msword"
        "docx"      -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls"       -> "application/vnd.ms-excel"
        "xlsx"      -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt"       -> "application/vnd.ms-powerpoint"
        "pptx"      -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt"       -> "text/plain"
        else        -> "*/*"
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionDialog?.takeIf { it.isShowing }?.dismiss()
        permissionDialog = null
    }
}
