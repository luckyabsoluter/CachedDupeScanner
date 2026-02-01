package opensource.cached_dupe_scanner.sample

import java.io.File

object SampleData {
    fun createSampleFiles(root: File) {
        if (!root.exists()) {
            root.mkdirs()
        }

        val alpha = File(root, "alpha.txt")
        val beta = File(root, "beta.txt")
        val gamma = File(root, "gamma.txt")
        val delta = File(root, "delta.txt")

        alpha.writeText("duplicate-group-1")
        beta.writeText("duplicate-group-1")
        gamma.writeText("unique-file")
        delta.writeText("duplicate-group-2")

        val deltaCopy = File(root, "delta_copy.txt")
        deltaCopy.writeText("duplicate-group-2")
    }
}
