package opensource.cached_dupe_scanner.engine

import androidx.documentfile.provider.DocumentFile

class DocumentFileWalker {
    fun walk(root: DocumentFile, ignore: (DocumentFile) -> Boolean = { false }): List<DocumentFile> {
        if (!root.exists()) {
            return emptyList()
        }

        val files = mutableListOf<DocumentFile>()

        fun visit(doc: DocumentFile) {
            if (ignore(doc)) {
                return
            }

            if (doc.isFile) {
                files.add(doc)
                return
            }

            doc.listFiles().forEach { child ->
                visit(child)
            }
        }

        visit(root)
        return files
    }
}
