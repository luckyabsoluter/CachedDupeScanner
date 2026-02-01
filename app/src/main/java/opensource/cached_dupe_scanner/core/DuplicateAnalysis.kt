package opensource.cached_dupe_scanner.core

data class DuplicateAnalysis(
    val crossDuplicates: List<DuplicateGroup>,
    val existingOnly: List<DuplicateGroup>,
    val newOnly: List<DuplicateGroup>,
    val expandedExisting: List<DuplicateGroup>
) {
    companion object {
        fun analyze(existing: List<FileMetadata>, newFiles: List<FileMetadata>): DuplicateAnalysis {
            val cross = mutableListOf<DuplicateGroup>()
            val existingOnly = mutableListOf<DuplicateGroup>()
            val newOnly = mutableListOf<DuplicateGroup>()
            val expandedExisting = mutableListOf<DuplicateGroup>()

            val combined = (existing + newFiles)
                .filter { it.hashHex != null }
                .groupBy { it.hashHex!! }
                .filterValues { it.size > 1 }

            val existingPaths = existing.map { it.normalizedPath }.toSet()
            val newPaths = newFiles.map { it.normalizedPath }.toSet()

            combined.forEach { (hash, files) ->
                val existingCount = files.count { file -> existingPaths.contains(file.normalizedPath) }
                val newCount = files.count { file -> newPaths.contains(file.normalizedPath) }

                val group = DuplicateGroup(hash, files)

                when {
                    existingCount > 0 && newCount > 0 -> {
                        cross.add(group)
                        if (existingCount > 1) {
                            expandedExisting.add(group)
                        }
                    }
                    existingCount > 1 -> existingOnly.add(group)
                    newCount > 1 -> newOnly.add(group)
                }
            }

            return DuplicateAnalysis(
                crossDuplicates = cross,
                existingOnly = existingOnly,
                newOnly = newOnly,
                expandedExisting = expandedExisting
            )
        }
    }
}
