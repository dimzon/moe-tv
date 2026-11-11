package tv.own.owntv.core.sync.local

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Builds and applies the containers of an automatic sync, the same way on both ends of a pass.
 *
 * Since the sync-matrix redesign (phase ③) what travels is decided by this device's own
 * [SettingsRepository.syncMatrix] ticks, not by a per-device section negotiation: the export carries
 * the categories this device **gives** ([giveSections]), and an incoming file is merged here only for
 * the categories this device **reads** ([readSections]) — as a [BackupManager.ImportMode.MERGE], so
 * deletions made here still stand, and never another device's hardware settings. PINs and
 * device-local switches never travel.
 */
class AutoSyncTransfer(
    private val backups: BackupManager,
    private val settings: SettingsRepository,
    private val cacheDir: () -> File,
) {
    /** This device's export, sealed with the pairing [secret]. Delete it after use. */
    suspend fun exportFor(secret: String): File {
        val folder = File(cacheDir(), "auto-sync/${UUID.randomUUID()}").apply { mkdirs() }
        val path = backups.export(folder, settings.syncMatrix.first().giveSections(), secret, recordAsBackup = false).getOrThrow()
        return File(path)
    }

    /** Merges [file] into this device as its matrix reads allow; the number of rows that landed. */
    suspend fun importFrom(file: File, secret: String): Int {
        val read = settings.syncMatrix.first().readSections()
        if (read.isEmpty()) return 0
        return backups.import(
            file, read, secret, BackupManager.ImportMode.MERGE,
            deviceSettings = false,
        ).getOrThrow().items
    }

    /** A place for a container on its way in. Delete it after use. */
    fun incomingFile(): File = File(cacheDir(), "auto-sync").apply { mkdirs() }.let { File(it, "${UUID.randomUUID()}.own") }
}

/**
 * One pass with a paired device at a time, on this device — whichever side started it. A second one
 * (this device's runner, or the other device's request) finds it taken and is answered "busy".
 */
class AutoSyncLocks {
    private val held = ConcurrentHashMap.newKeySet<String>()

    fun tryLock(deviceId: String): Boolean = held.add(deviceId)

    fun unlock(deviceId: String) {
        held.remove(deviceId)
    }
}
