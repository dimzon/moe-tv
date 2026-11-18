import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.inject.Inject
import org.gradle.process.ExecOperations

// Packaged locale qualifiers are read from tools/i18n/locales.json entries where packaged = true.
// That catalogue is owned by the core repo (ahXN00/OwnTV_Core), which holds the strings; the copy
// here exists only because Gradle needs the list before any dependency is resolved. Change it there
// first, then copy it across, or the app will package a locale set core does not translate.
// The build consumes the ``resourceQualifier`` field specifically (NOT languageTag, NOT weblateCode): a
// runtime BCP-47 tag fed straight into localeFilters is the bug this schema exists to prevent.
// Parsing uses groovy.json.JsonSlurper, available on every Gradle build script classpath.
val localesCatalogueFile = rootProject.file("tools/i18n/locales.json")
@Suppress("UNCHECKED_CAST")
val packagedLocaleQualifiers: Set<String> = run {
    if (!localesCatalogueFile.isFile) return@run emptySet()
    val raw = groovy.json.JsonSlurper().parseText(localesCatalogueFile.readText()) as List<Map<String, Any>>
    raw.mapNotNull { entry ->
        if ((entry["packaged"] as? Boolean) == true) entry["resourceQualifier"] as? String else null
    }.toSet()
}

plugins {
    alias(libs.plugins.android.application)
    // Kotlin is provided by AGP 9's built-in Kotlin support. KSP 2.3.6+ is compatible with it.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    // Consumes :baselineprofile's output and packages it as baseline.prof.
    alias(libs.plugins.baselineprofile)
}

// Build number auto-increments on every build. The last-used value lives in a git-ignored
// `versioncode.txt` at the repo root; this build uses (stored + 1), and the `bumpVersionCode` task
// writes that value back after the build, so the next build is one higher. Reading the file through
// a provider keeps the configuration cache honest (a changed file re-runs configuration); the write
// happens at execution time, which the cache permits. CI can pin an exact number with VERSION_CODE
// (from the git tag), which wins over the counter. versionName resets to a clean 1.0; CI overrides
// with VERSION_NAME.
val versionCodeFile = rootProject.layout.projectDirectory.file("versioncode.txt")
val storedVersionCode = providers.fileContents(versionCodeFile).asText
    .map { it.trim().toIntOrNull() ?: 0 }
    .orElse(0)
val resolvedVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: (storedVersionCode.get() + 1)
val resolvedVersionName = System.getenv("VERSION_NAME") ?: "1.0"

android {
    namespace = "tv.own.myowntv"
    compileSdk {
        version = release(37)
    }

    // Signing credentials AND local-only build switches, kept in a standalone properties file OUTSIDE
    // the repo. Gradle only reads gradle.properties from GRADLE_USER_HOME or the project dir, so this
    // one is loaded by hand. Declared here because defaultConfig below already needs it.
    val localSigningProps = Properties().apply {
        val f = File("E:/MEGA/CODE/OwnTV_Gradle/owntv-signing.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }

    defaultConfig {
        applicationId = "com.github.dimzon.owntv"
        minSdk = 23
        targetSdk = 36
        // versionCode / versionName come from the top-level vals above (auto-incrementing build
        // number, reset to 1.0; CI can pin VERSION_CODE / VERSION_NAME).
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        // Opt-in local diagnostic APKs keep the rolling playback trace enabled even when they are
        // release-signed (so they can update an installed production build without changing its data).
        buildConfigField(
            "boolean",
            "DIAGNOSTIC_BUILD",
            (providers.gradleProperty("diagnosticBuild").orNull == "true").toString(),
        )

        // Maintainer-only tools (today: the "Rebuild Now Trending" button in Home settings, which
        // bypasses the multi-day Trending fetch timer so a reported problem can be reproduced on the
        // spot). Off unless `owntv.devTools=true` is set as a Gradle property or in the out-of-repo
        // properties file, so CI and every published APK compile it out — R8 drops the dead branch.
        buildConfigField(
            "boolean",
            "DEV_TOOLS",
            (
                (
                    providers.gradleProperty("owntv.devTools").orNull
                        ?: localSigningProps.getProperty("owntv.devTools")
                    ) == "true"
                ).toString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ABI split via product flavors: real Android TV / Fire TV hardware is arm (arm64-v8a covers
    // everything modern; armeabi-v7a keeps the original 32-bit Nvidia Shield TV 2015/2017, which runs
    // Android 9+ but is 32-bit). x86_64 is emulator-only — no real TV box uses it. Shipping them as
    // separate flavors halves the download users get via the Downloader code (~49MB vs the old 104MB
    // universal APK that bundled all 4 ABIs), which fixes the "parse error on install" reports caused
    // by truncated downloads. x86 (32-bit Intel) is dropped entirely — even emulators use x86_64.
    //
    // Local dev: pick a flavor in Android Studio's "Build Variants" panel before Run (standard for
    // real devices / arm emulators, x86_64 for an x86_64 emulator). `assembleRelease` builds BOTH.
    flavorDimensions += "abi"
    productFlavors {
        create("standard") {
            dimension = "abi"
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += listOf("x86_64") }
        }
        // All ABIs in one APK — for measuring the full multi-platform footprint (and for a single
        // sideload-anywhere build). The split flavors stay the shipping default; this is opt-in.
        create("universal") {
            dimension = "abi"
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
    }

    // Release signing: env vars first (that is how CI injects the GitHub secrets), then Gradle
    // properties as a local fallback. Put the local ones in the USER-WIDE file — never in the repo:
    //
    //   C:\Users\<you>\.gradle\gradle.properties
    //     owntv.keystoreFile=E:\\MEGA\\CODE\\Github_Keystore\\owntv.keystore
    //     owntv.keystorePassword=...
    //     owntv.keyAlias=...
    //     owntv.keyPassword=...
    //
    // With those set, `./gradlew :app:assembleStandardRelease` produces a release-signed APK in any
    // terminal with no env-var dance, so a local dev build installs straight over a published
    // release (`adb install -r`) and upgrade/migration testing works with real data.
    // When neither source is configured — fork CI, or a fresh clone — nothing here applies and
    // builds still succeed, just unsigned.
    // Fourth source (the local default): a git-ignored `keystore.properties` at the repo root, the
    // same convention SmartRuTube uses — storeFile/storePassword/keyAlias/keyPassword. storeFile is
    // resolved relative to the repo root, so `./gradlew assembleStandardRelease` is signed out of the
    // box on a dev machine with no env dance. env / gradle-property / the out-of-repo file still win
    // (that is how CI injects the GitHub secrets).
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    fun signingValue(env: String, property: String, ksKey: String): String? =
        System.getenv(env)
            ?: providers.gradleProperty(property).orNull
            ?: localSigningProps.getProperty(property)
            ?: keystoreProps.getProperty(ksKey)

    val releaseKeystore = signingValue("KEYSTORE_FILE", "owntv.keystoreFile", "storeFile")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                // keystore.properties storeFile is repo-root-relative; env/props paths are absolute.
                storeFile = rootProject.file(releaseKeystore)
                storePassword = signingValue("KEYSTORE_PASSWORD", "owntv.keystorePassword", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "owntv.keyAlias", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "owntv.keyPassword", "keyPassword")
            }
        }
    }

    testOptions {
        // JVM unit tests hit android.util.Log / SystemClock in the code under test (StalkerAuthManager
        // etc.); return defaults (no-op log, 0 clock) instead of "not mocked" crashes.
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        debug {
            // Pseudolocales (en-XA / ar-XB) are generated for the debug BuildType, NOT androidResources.
            // They are the pseudolocale QA instrument; localeFilters below would otherwise strip them,
            // so the debug-only qualifiers are added back via the per-variant API in the androidComponents
            // block at the bottom of this file (see docs/internationalization.md 0b, "Pseudolocales must
            // survive the filter").
            isPseudoLocalesEnabled = true
        }
        release {
            // Stable AGP release optimization API. Keep both code and resource shrinking enabled;
            // the optimized default rules are extended by OwnTV's project-specific keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Packages only the catalogue entries marked packaged = true. Strips library locale folders
        // that the app does not officially support: appcompat 1.7.1 alone contributes ~85 locale folders
        // to the APK today, and without this filter they all ship. Standalone filters cannot strip
        // locales once packaged (shrinkResources removes unreferenced resources, never locales). The
        // debug-only pseudolocale qualifiers (`en-rXA`, `ar-rXB`) are added back for debug variants via
        // the androidComponents variant API below; release deliberately ships neither.
        // Equivalent to `localeFilters += ...` but avoids confusion with the SetProperty variant form.
        localeFilters.addAll(packagedLocaleQualifiers)
    }

    lint {
        // CI gates on this (see .github/workflows/android.yml), so an error must mean something.
        abortOnError = true
        warningsAsErrors = false
        // A counted sentence must use Android plural resources; keep this invariant fatal so a new
        // extraction cannot reintroduce English-only quantity wording.
        fatal += "PluralsCandidate"
        checkDependencies = false
        // Media3's player API surface is almost entirely @UnstableApi; this app is built on it, so
        // the check fires ~90 times across the player, Home and Live code and carries no signal.
        // Opting in file-by-file would only move the same acknowledgement into ~12 annotations.
        disable += "UnsafeOptInUsageError"
        // local.properties is developer-local and never committed (its Windows SDK path can't be
        // escaped without breaking the local tooling that writes it). CI has no such file at all.
        disable += "PropertyEscape"
        // The shipped Russian translation is near-complete but omits a few keys (e.g.
        // common_minutes); they fall back to values/ and must not make every default string a lint
        // error.
        disable += "MissingTranslation"
        // Reports are what a failed CI run is inspected from. Since AGP 9.3 the HTML/XML/text
        // reports are always generated, so there is nothing left to switch on here.
    }

    packaging {
        jniLibs {
            // Every .so we package is a prebuilt from a dependency (the bundled SQLite engine from
            // the androidx.sqlite artifacts) and all of them are already stripped at the
            // source — none carries a .debug_info or .symtab section. AGP's strip step therefore has
            // nothing to remove, and on a machine without an NDK it can't run at all, which is where
            // the "Unable to strip the following libraries, packaging them as they are" line on every
            // release task came from. Skipping it packages byte-identical libraries without the noise.
            keepDebugSymbols += "**/*.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // API 23 lacks the Java 8 default methods on Map/ConcurrentHashMap (compute, merge,
        // putIfAbsent, newKeySet, ...). Core library desugaring supplies them as Jetpack's
        // desugar_jdk_libs so the existing code runs unchanged on Android 6.
        isCoreLibraryDesugaringEnabled = true
    }
}

// Re-add the debug-only pseudolocale qualifiers that the shared `localeFilters` set above would
// otherwise strip. This is the per-variant SetProperty form of localeFilters (the androidResources
// block sets the MutableSet extension form, which applies to all variants equally and so cannot keep
// pseudolocales out of release). Release variants ship neither pseudolocale. Verified syntax against
// the AGP 9.2.1 variant API (ApplicationAndroidComponentsExtension.onVariants +
// ApplicationAndroidResources.localeFilters: SetProperty<String>); re-verify before deviating.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.androidResources.localeFilters.addAll("en-rXA", "ar-rXB")
    }
    // The exported Room schemas ride with the instrumentation APK: the migration test replays every
    // shipped version from assets, which is the guard against wiping a user's data on upgrade.
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

// Room exports its schema JSON here (the @Database lives in this module now that core's sources
// moved in). The migration test reads the same directory through androidTest assets above.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The profile is a list of code paths, not machine code, so one recording serves every ABI flavor.
// mergeIntoMain writes it to `src/main/generated/baselineProfiles/` instead of the recording flavor's
// own source set — required here because it has to be recorded on an x86_64 emulator (baseline
// profile collection needs API 33+, and the arm TV boxes this app targets are older) yet shipped in
// the `standard` arm APK.
baselineProfile {
    mergeIntoMain = true
}

// --- hardcoded-literal gate ----------------------------------------------------------------
//
// The same check CI runs, moved onto the developer's own machine. CI is still the enforcing gate —
// this only makes the failure arrive seconds after writing the string instead of minutes after
// pushing it, which matters most for work done here on main, where a hardcoded string used to reach
// a release build with nothing objecting.
//
// Deliberately NOT offered: any flag that records the literal and turns the build green. A red build
// means the string moves to strings_*.xml or is declared technical — those are the only two exits.
abstract class VerifyI18nLiterals : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSources: ConfigurableFileCollection

    /** The checker and its two reviewed manifests: edit any of them and the verdict may change. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val toolInputs: ConfigurableFileCollection

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @get:Inject
    abstract val execOps: ExecOperations

    private fun interpreter(): String? = listOf("python", "python3").firstOrNull { candidate ->
        runCatching {
            execOps.exec {
                commandLine(candidate, "--version")
                isIgnoreExitValue = true
                standardOutput = ByteArrayOutputStream()
                errorOutput = ByteArrayOutputStream()
            }.exitValue == 0
        }.getOrDefault(false)
    }

    @TaskAction
    fun verify() {
        val python = interpreter()
        if (python == null) {
            // Failing here would block anyone without Python from building at all. Warn loudly
            // instead — CI still enforces it, so the worst case is a late failure, not a missed one.
            logger.warn(
                "\n  WARNING: Python was not found, so the hardcoded-text check did not run." +
                    "\n  Install Python 3 to catch untranslatable text before pushing; CI will still catch it.\n",
            )
            stamp.get().asFile.writeText("skipped: no python interpreter\n")
            return
        }
        val output = ByteArrayOutputStream()
        val result = execOps.exec {
            workingDir = repoRoot.get().asFile
            commandLine(python, "tools/i18n/check_hardcoded_strings.py", "verify", "--bootstrap")
            environment("PYTHONIOENCODING", "utf-8")
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = output
        }
        if (result.exitValue != 0) {
            logger.error(output.toString(Charsets.UTF_8))
            throw GradleException("Hardcoded text check failed — see the report above.")
        }
        stamp.get().asFile.writeText("ok\n")
    }
}

val verifyI18nLiterals = tasks.register<VerifyI18nLiterals>("verifyI18nLiterals") {
    group = "verification"
    description = "Fails the build on user-visible text left hardcoded in Kotlin."
    // Only :app lives in this repo now. Core's own Kotlin is gated by the identical task in the
    // core repo, so a literal cannot escape by moving between the two.
    kotlinSources.from(fileTree("src/main/java") { include("**/*.kt") })
    toolInputs.from(
        rootProject.file("tools/i18n/check_hardcoded_strings.py"),
        rootProject.file("tools/i18n/hardcoded_baseline.txt"),
        rootProject.file("tools/i18n/safe_literals.txt"),
    )
    repoRoot.set(rootProject.layout.projectDirectory)
    stamp.set(layout.buildDirectory.file("i18n/literal-inventory.txt"))
}

// preBuild fronts every variant, so debug compile checks and release assembles are both covered.
// Inputs are declared above, so an unchanged source tree makes this UP-TO-DATE and free.
tasks.named("preBuild") { dependsOn(verifyI18nLiterals) }

// Records the versionCode this build used into versioncode.txt, so the next build's (stored + 1) is
// one higher — the "increments on every build" behaviour. Values are captured as task-local inputs
// (no project access at execution), keeping the configuration cache happy. Skipped when CI pins
// VERSION_CODE. Runs once per invocation even when several assemble* tasks are built together.
val bumpVersionCode = tasks.register("bumpVersionCode") {
    description = "Write the used versionCode back so the next build increments it."
    val used = resolvedVersionCode
    val file = versionCodeFile.asFile
    doLast {
        if (System.getenv("VERSION_CODE") == null) file.writeText(used.toString())
    }
}
tasks.matching { it.name.startsWith("assemble") }.configureEach { finalizedBy(bumpVersionCode) }

dependencies {
    // The engine (database, sync, parsers, EPG, playback) used to be a separate :core/:player-core
    // module. Its sources now live in this module under tv/own/owntv/**; nothing of the engine is
    // an external artifact anymore — playback runs on Media3/ExoPlayer, declared below.

    // Core
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)

    // Compose for TV
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tvprovider)

    // Lifecycle. Navigation Compose is intentionally absent: the shell owns section switching, and
    // navigation-compose 2.10 requires minSdk 24, which would block Android 6 support at manifest merge.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Preferences
    implementation(libs.androidx.datastore.preferences)

    // WorkManager (durable background sync)
    implementation(libs.androidx.work.runtime)

    // Baseline profiles: installs the merged library profiles (Compose, Media3, Room, ...) into
    // ART on first launch. OwnTV is sideloaded, so without this the bundled profiles never apply.
    implementation(libs.androidx.profileinstaller)

    // Compat splash screen — branded cold start instead of a blank window.
    implementation(libs.androidx.core.splashscreen)

    // The recorded startup journey. Regenerate with
    // `./gradlew :app:generateBaselineProfile` whenever the startup path changes. `mergeIntoMain`
    // collapses the per-variant tasks into that single one; it records against :app's x86_64 flavor
    // (see baselineprofile/build.gradle.kts) because collection needs an API 33+ device.
    baselineProfile(project(":baselineprofile"))

    // Database (Room, via KSP) + Paging
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    // The SQLiteDriver API on the platform engine (AndroidSQLiteDriver). The bundled engine
    // (sqlite-bundled / libsqliteJni.so) is gone — the two version-gated queries it was needed for
    // were rewritten version-independent (see DatabaseModule).
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    // SAF wrapper for user-chosen download folders; process-lifecycle for the auto-sync listener.
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.process)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.zxing.core) // QR generation for the Remote (companion) add-source flow
    implementation(libs.juniversalchardet) // local subtitle charset detection

    // Media playback — Media3 / ExoPlayer is the app's single playback engine. The external prebuilt
    // engine AAR it used to pair with is gone, and with it the FFmpeg software audio decoder: audio
    // now decodes on the device's own MediaCodec decoders only.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls) // HLS (.m3u8) support for the Live preview engine
    // DASH (.mpd) — the container protected channels use. DefaultMediaSourceFactory only
    // builds a DASH source when this is on the classpath; without it a .mpd fails as "unsupported".
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Dependency injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)


    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.gson)
    testImplementation(libs.kotlinx.coroutines.test)
    // Test-only, never packaged: android.jar's org.json is a stub, and isReturnDefaultValues turns
    // every JSONObject call into a silent null/0. Backup/restore is all JSON, so the unit tests need
    // the real implementation to mean anything.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
}
