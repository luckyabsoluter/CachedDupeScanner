package opensource.cached_dupe_scanner.core

enum class ResultSortKey(val label: String) {
    Count("Count"),
    TotalSize("Total size (heavy)"),
    PerFileSize("Per-file size"),
    Name("Name")
}
