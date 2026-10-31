package com.itspark.pdfreader.presentation.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.itspark.pdfreader.data.repository.DocumentRepositoryImpl
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.data.source.MediaStoreScanner
import com.itspark.pdfreader.databinding.ActivityHomeBinding
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.presentation.home.adapter.DocumentAdapter
import com.itspark.pdfreader.presentation.home.dialog.PermissionAccessDialog
import com.itspark.pdfreader.presentation.language.LanguageActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: PreferencesRepository
    private var permissionDialog: PermissionAccessDialog? = null

    // ViewModel factory — uses AndroidViewModel so the VM can access applicationContext
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            permissionDialog?.dismiss()
            viewModel.fetchDocuments()
        } else {
            showPermissionModalDialog()
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
        setupTopBarActions()
        setupSearch()
        setupRecentlyAddedBanner()
        observeViewModel()

        // 2. Initial Permission Evaluation
        evaluatePermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        // If user returns from Settings with permission now granted, load real files
        if (hasStoragePermission()) {
            if (permissionDialog?.isShowing == true) permissionDialog?.dismiss()
            if (viewModel.isUsingDummyData) viewModel.fetchDocuments()
        }
    }

    private fun evaluatePermissionAndLoad() {
        if (hasStoragePermission()) {
            viewModel.fetchDocuments()
        } else {
            // No permission: show dummy files (backed by real internal storage files) + modal
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
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        documentAdapter = DocumentAdapter(
            onItemClick    = { doc -> openDocument(doc) },
            onBookmarkToggle = { doc -> viewModel.toggleBookmark(doc) },
            onMenuClick    = { doc -> showFileMenu(doc) }
        )
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
                            binding.layoutRecentlyAdded.visibility =
                                if (state.isSearching) View.GONE else View.VISIBLE
                            documentAdapter.submitList(state.documents)
                            binding.tvEmptyState.visibility =
                                if (state.documents.isEmpty()) View.VISIBLE else View.GONE
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

    // ─── File Opening ─────────────────────────────────────────────────────────

    /**
     * Opens a document.
     *
     * Dummy files (id < 0): they live in internal storage (filesDir/samples/).
     *   → Use FileProvider to get a content:// URI so external apps can read them.
     *
     * Real files (id >= 0): they were scanned from external storage.
     *   → Use the URI/path from MediaStore directly (permission already granted).
     */
    private fun openDocument(item: DocumentModel) {
        val contentUri: Uri = if (item.id < 0) {
            // Sample file stored in internal storage — use FileProvider
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
            // Real device file — need storage permission
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

    private fun showFileMenu(item: DocumentModel) {
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
