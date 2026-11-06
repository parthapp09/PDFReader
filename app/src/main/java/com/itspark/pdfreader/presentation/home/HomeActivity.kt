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
import android.view.ViewGroup
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
import com.itspark.pdfreader.PdfReaderApp
import com.itspark.pdfreader.R
import com.itspark.pdfreader.data.repository.DocumentRepositoryImpl
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.data.source.MediaStoreScanner
import com.itspark.pdfreader.databinding.ActivityHomeBinding
import com.itspark.pdfreader.databinding.BottomSheetFileOptionsBinding
import com.itspark.pdfreader.databinding.DialogAiFeatureBinding
import com.itspark.pdfreader.databinding.DialogDeleteConfirmBinding
import com.itspark.pdfreader.databinding.DialogExitAppBinding
import com.itspark.pdfreader.databinding.DialogFileInfoBinding
import com.itspark.pdfreader.databinding.DialogRenameFileBinding
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.domain.model.NavigationTab
import com.itspark.pdfreader.monetization.AdConfigRepository
import com.itspark.pdfreader.monetization.AdFormatType
import com.itspark.pdfreader.monetization.AdPlacement
import com.itspark.pdfreader.monetization.BannerAdManager
import com.itspark.pdfreader.monetization.MonetizationManager
import com.itspark.pdfreader.notification.NotificationEngine
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
    private var bannerAdManager: BannerAdManager? = null

    // Android 13+ Notification Runtime Permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("HomeActivity", "POST_NOTIFICATIONS granted: $isGranted")
    }

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

        // 3. Request Notification Permission (Android 13+) and Handle Deep Links
        requestNotificationPermissionIfRequired()
        handleDeepLinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val target = intent?.getStringExtra("navigate_to") ?: return
        when (target.lowercase()) {
            "tools" -> {
                binding.bottomNavigationView.selectedItemId = R.id.nav_tools
            }
            "bookmarks" -> {
                binding.bottomNavigationView.selectedItemId = R.id.nav_home
                viewModel.setNavigationTab(NavigationTab.BOOKMARKS)
            }
            "pdf" -> {
                binding.bottomNavigationView.selectedItemId = R.id.nav_home
                binding.tabLayout.getTabAt(1)?.select()
                viewModel.onTabChanged(DocumentFormat.PDF)
            }
        }
    }

    private fun requestNotificationPermissionIfRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun applyCohortMonetizationRules() {
        val configRepo = AdConfigRepository(this)
        if (!MonetizationManager.shouldShowBanner(this) || !configRepo.isPlacementEnabled(AdPlacement.HOME_BOTTOM_BANNER)) {
            binding.layoutBottomBannerAd.visibility = View.GONE
            return
        }

        // Dynamic Ad Format Selection: Evaluates server/remote format preference
        val format = configRepo.getPlacementFormat(AdPlacement.HOME_BOTTOM_BANNER, isFirstLaunch = prefs.isFirstLaunch)

        when (format) {
            AdFormatType.COLLAPSIBLE_BANNER -> {
                bannerAdManager = BannerAdManager(this)
                bannerAdManager?.loadCollapsibleBanner(binding.layoutBottomBannerAd)
            }
            AdFormatType.STANDARD_BANNER -> {
                bannerAdManager = BannerAdManager(this)
                bannerAdManager?.loadStandardBanner(binding.layoutBottomBannerAd)
            }
            AdFormatType.NATIVE -> {
                PdfReaderApp.instance.nativeAdManager.loadIntoContainer(
                    container = binding.layoutBottomBannerAd,
                    placement = AdPlacement.HOME_BOTTOM_BANNER,
                    layoutResId = R.layout.layout_native_ad_medium
                )
            }
            else -> {
                bannerAdManager = BannerAdManager(this)
                bannerAdManager?.loadCollapsibleBanner(binding.layoutBottomBannerAd)
            }
        }

        // Defer secondary ad preloads (interstitials, native dialogs, rewarded) by 1500ms
        // so initial UI inflation, RecyclerView rendering, and file queries have 100% CPU/bandwidth.
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)
            PdfReaderApp.instance.interstitialAdManager.preload(AdPlacement.INTERSTITIAL_OPEN_DOC)
            PdfReaderApp.instance.nativeAdManager.preload(AdPlacement.NATIVE_EXIT_DIALOG)
            PdfReaderApp.instance.nativeAdManager.preload(AdPlacement.NATIVE_AI_FEATURE)
            PdfReaderApp.instance.rewardedAdManager.preload(AdPlacement.REWARDED_VIP_FEATURE)
        }
    }

    override fun onResume() {
        super.onResume()
        bannerAdManager?.resume()
        registerObservers()
        requestNotificationPermissionIfRequired()
        if (hasStoragePermission()) {
            if (permissionDialog?.isShowing == true) permissionDialog?.dismiss()
            viewModel.fetchDocuments()
        }
    }

    override fun onPause() {
        super.onPause()
        bannerAdManager?.pause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Triggered when user presses Home button or switches to Recent Apps overview
        NotificationEngine.showDelayedExitNotification(applicationContext)
    }

    private fun registerObservers() {
        if (hasStoragePermission()) {
            PdfReaderApp.instance.registerFileObservers()
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
            PdfReaderApp.instance.rewardedAdManager.showIfAvailable(
                activity = this,
                placement = AdPlacement.REWARDED_VIP_FEATURE,
                onUserEarnedReward = {
                    Toast.makeText(this, "VIP Feature unlocked for this session!", Toast.LENGTH_LONG).show()
                },
                onAdClosed = {}
            )
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
            PdfReaderApp.instance.rewardedAdManager.showIfAvailable(
                activity = this,
                placement = AdPlacement.REWARDED_VIP_FEATURE,
                onUserEarnedReward = {
                    Toast.makeText(this, "VIP Tool unlocked for this session!", Toast.LENGTH_LONG).show()
                },
                onAdClosed = {}
            )
        }
        binding.btnToolsSettings.setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
        }
    }

    private fun onToolClicked(tool: PdfToolItem) {
        when (tool.type) {
            PdfToolType.IMAGE_TO_PDF -> {
                pickImagesLauncher.launch("image/*")
            }
            PdfToolType.AI -> {
                showAiSupportDialog()
            }
        }
    }

    private fun showAiSupportDialog() {
        if (isFinishing || isDestroyed) return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val aiBinding = DialogAiFeatureBinding.inflate(layoutInflater)
        dialog.setContentView(aiBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        // Load Native Ad in the AI Support Dialog
        PdfReaderApp.instance.nativeAdManager.loadIntoContainer(
            container = aiBinding.flAiNativeAd,
            placement = AdPlacement.NATIVE_AI_FEATURE,
            layoutResId = R.layout.layout_native_ad_dialog
        )

        aiBinding.btnAiClose.setOnClickListener {
            dialog.dismiss()
        }

        aiBinding.btnAiContinue.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "AI features are now active!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
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

        // Load Native Ad in the Exit Dialog
        PdfReaderApp.instance.nativeAdManager.loadIntoContainer(
            container = exitBinding.flExitAdContainer,
            placement = AdPlacement.NATIVE_EXIT_DIALOG,
            layoutResId = R.layout.layout_native_ad_dialog
        )

        exitBinding.btnCancelExit.setOnClickListener {
            dialog.dismiss()
        }

        exitBinding.btnConfirmExit.setOnClickListener {
            dialog.dismiss()
            if (!NotificationEngine.hasNotificationPermission(applicationContext)) {
                Toast.makeText(
                    applicationContext,
                    "⚠️ Notification permission is required. Enable notifications in App Settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
            // Schedules exit notification to fire after closing, cleanly dropping down on the home screen            NotificationEngine.showDelayedExitNotification(applicationContext)
            finish()
        }

        dialog.show()
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
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

                                val showFeedAds = MonetizationManager.shouldShowBanner(this@HomeActivity)
                                documentAdapter.submitDocuments(state.documents, showAds = showFeedAds) {
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
                            documentAdapter.submitDocuments(emptyList(), showAds = false)
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
        PdfReaderApp.instance.interstitialAdManager.showIfAllowed(
            activity = this,
            placement = AdPlacement.INTERSTITIAL_OPEN_DOC,
            onAdClosed = {
                proceedToOpenDocument(item)
            }
        )
    }

    private fun proceedToOpenDocument(item: DocumentModel) {
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
        bannerAdManager?.destroy()
        bannerAdManager = null
        permissionDialog?.takeIf { it.isShowing }?.dismiss()
        permissionDialog = null
    }
}
