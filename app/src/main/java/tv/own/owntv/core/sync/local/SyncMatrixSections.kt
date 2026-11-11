package tv.own.owntv.core.sync.local

import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.settings.SyncMatrix

/**
 * The sync-matrix categories this device **gives** — the sections its export carries (phase ③).
 * A category whose "give" tick is off simply never leaves this device: the other side cannot apply
 * what was never sent.
 */
fun SyncMatrix.giveSections(): Set<BackupManager.Section> = buildSet {
    if (giveFavorites) add(BackupManager.Section.FAVORITES)
    if (giveOrder) add(BackupManager.Section.MANUAL_REORDER)
    if (giveHistory) add(BackupManager.Section.HISTORY)
    if (giveReminders) add(BackupManager.Section.REMINDERS)
}

/**
 * The sync-matrix categories this device **reads** — the sections an incoming container is allowed
 * to touch here (phase ③). A category whose "read" tick is off is left exactly as it is, whatever
 * the other device sent.
 */
fun SyncMatrix.readSections(): Set<BackupManager.Section> = buildSet {
    if (readFavorites) add(BackupManager.Section.FAVORITES)
    if (readOrder) add(BackupManager.Section.MANUAL_REORDER)
    if (readHistory) add(BackupManager.Section.HISTORY)
    if (readReminders) add(BackupManager.Section.REMINDERS)
}
