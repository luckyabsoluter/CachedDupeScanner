package opensource.cached_dupe_scanner.engine

import java.io.File

open class FileWalker {
    open fun walk(
        root: File,
        ignore: (File) -> Boolean = { false },
        onFile: (File) -> Unit = {},
        shouldContinue: () -> Boolean = { true }
    ): List<File> {
        if (!root.exists()) {
            return emptyList()
        }

        val files = mutableListOf<File>()

        fun visit(file: File): Boolean {
            if (!shouldContinue()) {
                return false
            }
            if (ignore(file)) {
                return true
            }

            if (file.isFile) {
                files.add(file)
                onFile(file)
                return true
            }

            file.listFiles()?.forEach { child ->
                if (!visit(child)) {
                    return false
                }
            }
            return true
        }

        visit(root)
        return files
    }
}
