# OwnTV release keep rules (R8). Each rule guards a runtime lookup that R8 can't see
# statically — trim only with a full on-device regression (sync, playback, backup).

# --- Crash reports: keep readable stack traces (mapping.txt still needed for names) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- WorkManager: workers are re-instantiated by FQCN string (KoinWorkerFactory matches
#     workerClassName against ::class.java.name, and WorkManager persists the name in its DB
#     across app updates) ---
-keep class * extends androidx.work.ListenableWorker

# --- Room: Room.getGeneratedImplementation() derives this exact class name and loads it
#     reflectively. Keep the generated database entry point in optimized release builds. ---
-keep class tv.own.owntv.core.database.OwnTVDatabase_Impl { *; }

# --- Persisted enum names: DataStore prefs, backup JSON ("t" = MediaType.name), and
#     per-setting modes (ZoomMode, ThemeMode, StartupMode, AnimationLevel, ResumeMode,
#     EpgAutoRefresh, PlaylistAutoRefresh, ...) round-trip through Enum.name/valueOf.
#     Renaming a constant would silently reset settings and break old backups. ---
-keep enum tv.own.myowntv.** { *; }

# --- media3 analytics bookkeeping: FlagSet$Builder owns a mutable SparseBooleanArray. R8's class
#     merging folded it into an unrelated Room class and erased that field to Object. On the cheap
#     Android 11 vendor ROMs (Amlogic S905W2, reported as Google W2/Stick) the merged path appeared
#     in anonymous reports as a corrupted SparseBooleanArray. Keep it a standalone class: renaming
#     and obfuscation are fine, merging/optimization of this class are not. ---
-keep,allowobfuscation class androidx.media3.common.FlagSet$Builder { *; }

# --- kotlinx-coroutines 1.11 synthetic channel segment factory. R8 9.4.14 left an invalid
#     descriptor for this class in the 1.12 universal release; 32-bit ART on Amlogic W2/API 30
#     then crashed ExoPlayer's playback thread with NoClassDefFoundError. Keep its real name
#     and class identity: the object is tiny, and a valid descriptor is required on vendor ROMs. ---
-keep class kotlinx.coroutines.channels.BufferedChannelKt$createSegmentFunction$1 { *; }
