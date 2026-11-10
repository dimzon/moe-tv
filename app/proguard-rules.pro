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
