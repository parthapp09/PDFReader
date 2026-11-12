# PDF Reader - PDF Editor & Viewer 📑

Production-ready Native Android application built with **Kotlin**, **Clean Architecture**, **MVVM**, **StateFlow**, and **ViewBinding** (`minSdk = 23`, `compileSdk = 34`, `targetSdk = 34`).

Replicates and extends the functionality of top-tier Android office document suites with built-in PDF rendering, text searching, Image-to-PDF conversion, an advanced ad monetization stack, contextual notification triggers, and 47 Firebase Remote Config parameters.

---

## 📑 Table of Contents
1. [Core Features](#-core-features)
2. [Architecture & Project Structure](#-architecture--project-structure)
3. [Ad Monetization Engine](#-ad-monetization-engine)
4. [Notification Engine & Triggers](#-notification-engine--triggers)
5. [Firebase Remote Configuration](#-firebase-remote-configuration)
6. [Setup & Build Instructions](#-setup--build-instructions)

---

## 🌟 Core Features

### 1. Multi-Format Office Document Manager
- **Format Support**: Scans and organizes **PDF**, Word (**DOC**, **DOCX**), Excel (**XLS**, **XLSX**), PowerPoint (**PPT**, **PPTX**), and Plain Text (**TXT**).
- **MediaStore Scanner**: High-performance `ContentResolver` queries with index-backed projections, sorting, and format categorization.
- **Categorized Tabs**: Quick switching across `ALL`, `PDF`, `WORD`, `EXCEL`, `PPT` with smooth animated tab indicators.
- **Search & Filtering**: Real-time debounce text query search and sort filtering by name, date modified, and file size.
- **Document Actions**: Star/Bookmark toggle, rename, delete, file details, and native system share intent.

### 2. High-Performance Built-in PDF Viewer
- **Native PDF Rendering**: Powered by Android's hardware-accelerated `PdfRenderer` and PDFium with dynamic page caching and zoom.
- **Full-Text In-Document Search**: High-performance text search engine powered by PDFBox-Android (`PdfTextSearchEngine`) with exact match highlighting, match count, and previous/next navigation.
- **Thumbnail Grid Bar**: Smooth horizontal thumbnail preview bar for instant page jumping.
- **Reading Modes**: Vertical continuous scrolling, double-tap zoom, and bookmark saving.

### 3. Image to PDF Scanner & Converter
- **Batch Image Selection**: Android Photo Picker / `ActivityResultContracts.GetMultipleContents()` integration.
- **Interactive Preview Screen** (`ImageToPdfPreviewActivity`): Reorder pages via drag-and-drop, delete unwanted images, and set page orientation.
- **Engine** (`ImageToPdfConverter`): Generates standards-compliant, compressed multi-page PDF documents saved directly to `Environment.DIRECTORY_DOCUMENTS`.

### 4. System Integrations & Shortcuts
- **Dynamic App Shortcuts** (`DynamicShortcutManager`): Long-press launcher shortcuts for *Open Recent PDF*, *Scan / Images to PDF*, and *Starred Documents*.
- **Launcher Identity Switcher** (`LauncherAliasManager`): Dynamic Activity Alias allowing the app's launcher title and icon to switch between *Document Reader* and *PDF Scanner & Editor* via Remote Config.
- **Home Screen App Widget** (`RecentDocsWidgetProvider`): Displays recently accessed documents and 1-tap utility action buttons on the user's home screen.
- **Multi-Language Support** (`LanguageActivity`, `LocaleManager`): In-app language switching with runtime configuration updates.

---

## 🏛️ Architecture & Project Structure

The project follows Clean Architecture with strict separation of concerns across Data, Domain, and Presentation layers:

```
com.itspark.pdfreader/
├── PdfReaderApp.kt                         // Application class, lifecycle observer & dependency init
├── config/
│   ├── RemoteConfigManager.kt              // 47 Firebase Remote Config keys & fallback defaults
│   └── LauncherAliasManager.kt             // Dynamic launcher icon & title alias switcher
├── monetization/
│   ├── MonetizationManager.kt              // Session caps, ad cooldowns & user cohort tuning
│   ├── AdConfigRepository.kt               // Ad server waterfall JSON parsing & ad unit mappings
│   ├── AppOpenAdManager.kt                 // Cold start & warm resume App Open ads
│   ├── BannerAdManager.kt                  // Collapsible & Standard adaptive banner ads
│   ├── InterstitialAdManager.kt            // Contextual interstitial ads with cooldown gates
│   ├── NativeAdManager.kt                  // In-feed list, search result & exit dialog native ads
│   └── RewardedAdManager.kt                // VIP feature rewarded unlock ads
├── notification/
│   ├── NotificationEngine.kt               // Notification channels, heads-up, full-screen dispatch
│   ├── AppNotificationChannel.kt           // Android 8.0+ notification channels enum
│   ├── NotificationTemplate.kt             // Rotating time-of-day & retention copy templates
│   ├── fullscreen/
│   │   └── FullScreenPromotionalActivity.kt// Full-screen lock-screen alert interface
│   ├── observers/
│   │   ├── DirectoryWatcherManager.kt      // Linux inotify observer manager
│   │   ├── AppDirectoryFileObserver.kt     // Per-directory inotify Linux file watcher
│   │   ├── DocumentDownloadsObserver.kt    // ContentObserver for MediaStore downloads
│   │   ├── ScreenshotObserver.kt           // Contextual screenshot detection observer
│   │   └── MessagingApp.kt                 // Classifier for WhatsApp, Telegram, etc.
│   ├── receivers/
│   │   ├── CloseSystemDialogsReceiver.kt   // Home key, Recent Apps, gesture navigation listener
│   │   ├── DeviceUnlockReceiver.kt         // Dynamic ACTION_USER_PRESENT & SCREEN_ON listener
│   │   ├── NotificationActionReceiver.kt   // Analytics tracking for notification clicks/dismissals
│   │   └── AppRestorationReceiver.kt       // Boot completed / app updated channel re-registration
│   └── work/
│       ├── EngagementScheduler.kt          // WorkManager background job orchestrator
│       └── EngagementNotificationWorker.kt // Periodic retention reminder worker
├── domain/
│   └── model/
│       ├── DocumentFormat.kt               // Document category enum (ALL, PDF, WORD, PPT, EXCEL, TXT)
│       └── DocumentModel.kt                // File model with size & timestamp formatting
├── data/
│   ├── repository/
│   │   ├── DocumentRepository.kt           // Repository contract & implementation
│   │   └── PreferencesRepository.kt        // User preferences & first-launch flags
│   └── source/
│       └── MediaStoreScanner.kt            // MediaStore ContentResolver scanner
├── presentation/
│   ├── home/
│   │   ├── HomeActivity.kt                 // Main screen, search, tabs, permissions & exit dialog
│   │   ├── HomeViewModel.kt                // StateFlow-driven MVVM ViewModel
│   │   └── adapter/                        // ListAdapter implementations with DiffUtil
│   ├── viewer/
│   │   ├── PdfViewerActivity.kt            // Built-in PDF reader UI
│   │   ├── PdfViewerViewModel.kt           // Page rendering & zoom state management
│   │   └── PdfTextSearchEngine.kt          // Apache PDFBox text extraction & highlight engine
│   └── tools/
│       ├── ImageToPdfPreviewActivity.kt    // Image to PDF reordering & conversion screen
│       └── ImageToPdfConverter.kt          // PDF document generation utility
└── service/
    └── DocumentSyncForegroundService.kt    // Android 14 dataSync background indexing service
```

---

## 💰 Ad Monetization Engine

The monetization engine is designed for high revenue potential while respecting user retention and Google Play policies.

### 1. Ad Formats Supported
| Format | Class | Placement Contexts |
|---|---|---|
| **App Open Ad** | `AppOpenAdManager` | Shown on cold app launch and warm app resume from background (with configurable background cooldown). |
| **Adaptive Banner** | `BannerAdManager` | Anchored at the bottom of the Home screen. Dynamically toggles between **Collapsible Banner** and **Standard Banner** via Remote Config (`ad_home_banner_format`). |
| **Interstitial Ad** | `InterstitialAdManager` | Shown during high-intent user actions: opening documents (`INTERSTITIAL_OPEN_DOC`), switching tabs (`INTERSTITIAL_TAB_SWITCH`), and completing PDF conversion (`INTERSTITIAL_EXPORT_PDF`). |
| **Native Ad** | `NativeAdManager` | Embedded organically inside the document list, search results, and inside the custom **Exit Confirmation Dialog**. |
| **Rewarded Ad** | `RewardedAdManager` | Triggers VIP tool unlock (`REWARDED_VIP_FEATURE`) granting temporary access to premium tools. |

### 2. Monetization Rules & Safeguards
- **Cohort Ad Aggressiveness** (`cohort_ad_aggressiveness`):
  - `AGGRESSIVE`: 25s cooldown between interstitials, 15 ads/session max.
  - `BALANCED` (Default): 45s cooldown, 10 ads/session max.
  - `LIGHT`: 90s cooldown, 5 ads/session max.
- **Organic User Protection** (`ad_organic_protection_enabled`): Automatically suppresses ads during a user's first 24 hours or first 3 app launches.
- **Waterfalls & Remote Ad Unit IDs** (`ad_server_config_json`): Ad IDs and mediator priority are retrieved remotely without requiring app updates.

---

## 🔔 Notification Engine & Triggers

A comprehensive notification framework drives retention and re-engagement while strictly respecting foreground user activity.

### 1. System Notification Channels (API 26+)
- `FILES` (`channel_file_events`): Importance High. For detected incoming documents and file downloads.
- `TOOLS` (`channel_tools_conversion`): Importance Default. For PDF export completion and conversion shortcuts.
- `RE_ENGAGEMENT` (`channel_reengagement`): Importance Default. For reading reminders, starred file shortcuts, and workspace tips.
- `FOREGROUND_SERVICE` (`channel_foreground_service`): Importance Low. For background document sync (`DocumentSyncForegroundService`).

### 2. User Activity-Based Triggers
The notification engine monitors device events to send contextual, high-conversion notifications:

| Trigger | Component | How It Works |
|---|---|---|
| **Messaging App Downloads** | `DirectoryWatcherManager`<br>`AppDirectoryFileObserver` | Uses Linux kernel `inotify` file watchers on internal and shared storage to detect new documents received via **10 messaging apps** (WhatsApp, Telegram, Messenger, LINE, Viber, Discord, Teams, Zalo, WeChat/QQ, Gapo) and system Download folders. Dispatches instant notifications allowing 1-tap viewing. |
| **Device Unlock / Screen On** | `DeviceUnlockReceiver` | Dynamically registered broadcast receiver listening for `ACTION_USER_PRESENT` and `ACTION_SCREEN_ON`. When the user unlocks their phone (and the app is closed), evaluates an engagement opportunity with a 3-second debounce window. |
| **Screenshot Detection** | `ScreenshotObserver` | Observes `Images.Media.EXTERNAL_CONTENT_URI`. When a screenshot is taken, displays a contextual *"Convert Screenshot to PDF"* notification deep-linking directly into the conversion preview. |
| **Home Button / Recent Apps Exit** | `CloseSystemDialogsReceiver`<br>`HomeActivity.onUserLeaveHint()` | Listens for system UI dismissals (`homekey`, `recentapps`, `fs_gesture`) and in-app exit confirmation. Schedules a delayed exit alert (`notif_exit_delay_ms`) that fires cleanly on the launcher screen after the user exits. |
| **Periodic Retention** | `EngagementNotificationWorker` | WorkManager periodic worker delivering time-of-day appropriate templates (Morning reading resume, afternoon productivity, evening bookmarks). |

### 3. Foreground Suppression Shield
To ensure notifications never interrupt the user:
- **Active Navigation Guard**: When viewing a PDF, selecting photos, or adjusting settings, all exit alerts are completely suppressed.
- **Lifecycle Invalidation**: Whenever any activity in the app starts or resumes (`onActivityStarted`, `onActivityResumed`), all pending delayed exit runnables are instantly cleared.
- **Hard Foreground Check**: Before dispatching any promotional, re-engagement, or full-screen urgency alert, `NotificationEngine.isAppInForeground()` checks if the app is active and aborts delivery if true.

---

## ⚙️ Firebase Remote Configuration

The app includes **47 parameters** configured in [`remote_config_template.json`](file:///C:/Users/Partha/.gemini/antigravity/scratch/PdfReaderHomeScreen/remote_config_template.json). Key parameter groups:

### 1. Ad Configuration
- `ad_enabled_global`: Master ad killswitch (`true`/`false`).
- `ad_home_banner_format`: Format for the bottom banner (`COLLAPSIBLE` or `STANDARD`).
- `ad_interstitial_cooldown_seconds`: Minimum cooldown between interstitial ads (default: `45`).
- `ad_organic_protection_enabled`: Suppresses ads for fresh organic installs (`true`/`false`).
- `cohort_ad_aggressiveness`: Ad density cohort (`AGGRESSIVE`, `BALANCED`, `LIGHT`).
- `ad_server_config_json`: Embedded JSON configuration with AdMob ad unit IDs.

### 2. Notification Triggers & Cooldowns
- `notif_out_of_app_enabled`: Master switch for background notifications (`true`/`false`).
- `notif_exit_alert_enabled`: Enable delayed exit notifications on app exit (`true`/`false`).
- `notif_exit_delay_ms`: Delay after exiting before showing notification (default: `1200` ms).
- `notif_unlock_enabled`: Enable notification trigger on device unlock (`true`/`false`).
- `notif_messaging_apps_enabled`: Enable directory watchers for WhatsApp, Telegram, etc. (`true`/`false`).
- `notif_file_observer_enabled`: Enable low-level Linux inotify file observers (`true`/`false`).
- `notif_system_dialogs_enabled`: Enable `ACTION_CLOSE_SYSTEM_DIALOGS` listener (`true`/`false`).
- `notif_screenshot_enabled`: Enable screenshot detection observer (`true`/`false`).
- `notif_min_interval_seconds`: Minimum global cooldown between notifications (default: `7200` s / 2 hrs).

### 3. Feature & UI Flags
- `feature_image_to_pdf_limit`: Max images allowed in a single PDF conversion (default: `50`).
- `ui_launcher_alias_variant`: Dynamic app identity (`DEFAULT` = Document Reader, `SCANNER` = PDF Scanner & Editor).
- `ui_exit_dialog_variant`: Exit dialog layout (`STANDARD`, `RETENTION`, `CLEAN_CACHE`).

---

## 🛠️ Setup & Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34 (`compileSdk = 34`, `minSdk = 23`)

### Build Steps
1. Clone or extract the project to your workspace.
2. Place your `google-services.json` file inside the `app/` directory for Firebase Remote Config and Analytics.
3. Open the project in Android Studio and let Gradle sync.
4. Run the app:
   ```bash
   ./gradlew assembleDebug
   ```

### Importing Remote Config Template
1. Navigate to the **Firebase Console** -> **Remote Config**.
2. Click the three dots (More options) -> **Publish template**.
3. Select `remote_config_template.json` from the root directory and publish. All 47 parameters will be initialized with tested defaults.
