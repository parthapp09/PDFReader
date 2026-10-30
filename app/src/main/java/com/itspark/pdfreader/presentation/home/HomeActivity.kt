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
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.itspark.pdfreader.data.repository.DocumentRepositoryImpl
import com.itspark.pdfreader.data.source.MediaStoreScanner
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.presentation.home.adapter.DocumentAdapter
import com.google.android.material.tabs.TabLayout
import com.itspark.pdfreader.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val scanner = MediaStoreScanner(applicationContext)
                val repo = DocumentRepositoryImpl(scanner)
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repo) as T
            }
        }
    }

    private lateinit var documentAdapter: DocumentAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.fetchDocuments()
        } else {
            Toast.makeText(this, "Permission required to list documents", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDualColorTitle()
        setupTabs()
        setupRecyclerView()
        setupTopBarActions()
        setupSearch()
        setupRecentlyAddedBanner()
        observeViewModel()
        checkPermissionsAndLoad()
    }

    /**
     * Styles title as "PDF READER" with "PDF" in RED (#EA3434) and "READER" in BLACK
     */
    private fun setupDualColorTitle() {
        val title = "PDF READER"
        val spannable = SpannableString(title)
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#EA3434")),
            0,
            3,
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
            val tab = binding.tabLayout.newTab().setText(format.title)
            tab.tag = format
            binding.tabLayout.addTab(tab)
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val format = tab?.tag as? DocumentFormat ?: DocumentFormat.ALL
                viewModel.onTabChanged(format)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        documentAdapter = DocumentAdapter(
            onItemClick = { doc -> openDocument(doc) },
            onBookmarkToggle = { doc -> viewModel.toggleBookmark(doc) },
            onMenuClick = { doc -> showFileMenu(doc) }
        )
        binding.rvDocuments.adapter = documentAdapter
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
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show()
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

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.fetchDocuments()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                viewModel.fetchDocuments()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    private fun openDocument(item: DocumentModel) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, getMimeType(item.extension))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No application found to open this document", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFileMenu(item: DocumentModel) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(item.extension)
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share ${item.name}"))
    }

    private fun getMimeType(ext: String): String {
        return when (ext.lowercase()) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }
}
