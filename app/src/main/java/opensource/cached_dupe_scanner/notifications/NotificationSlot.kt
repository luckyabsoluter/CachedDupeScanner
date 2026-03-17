package opensource.cached_dupe_scanner.notifications

internal enum class NotificationSlot {
    Scan,
    DbTask
}

internal fun notificationIdFor(slot: NotificationSlot): Int {
    return when (slot) {
        NotificationSlot.Scan -> 1001
        NotificationSlot.DbTask -> 1002
    }
}
