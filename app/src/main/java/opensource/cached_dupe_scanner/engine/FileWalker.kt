package opensource.cached_dupe_scanner.engine

import java.io.File

class FileWalker {
    fun walk(root: File, ignore: (File) -> Boolean = { false }): List<File> {
        if (!root.exists()) {
            return emptyList()
        }

        val files = mutableListOf<File>()

        fun visit(file: File) {
            if (ignore(file)) {
                return
            }

            if (file.isFile) {
                files.add(file)
                return
            }

            file.listFiles()?.forEach { child ->
                visit(child)
            }
        }

        visit(root)
        return files
    }
}
