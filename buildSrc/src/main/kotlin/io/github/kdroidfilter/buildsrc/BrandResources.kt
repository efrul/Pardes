package io.github.kdroidfilter.buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * A resource folder with a brand's files laid over it (branding/brand.gradle): [base] copied to [outputDir], then
 * every file of [overlay] put in place of the base file at the same path. A `values/strings.xml` in [overlay] holds
 * only the strings that change; they replace the base strings of the same name, one by one, so the base keeps every
 * string upstream adds.
 *
 * Fails when the overlay names a file or a string the base no longer has: upstream renamed or dropped it, and the
 * brand would silently stop applying.
 */
abstract class BrandResources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val base: DirectoryProperty

    /** The brand's files; none (unset) leaves [base] as it is. */
    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overlay: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun apply() {
        val baseDir = base.get().asFile
        val out = outputDir.get().asFile
        out.deleteRecursively()
        baseDir.copyRecursively(out)
        val overlayDir = overlay.orNull?.asFile ?: return

        val missing = mutableListOf<String>()
        overlayDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val path = file.relativeTo(overlayDir).invariantSeparatorsPath
            val target = File(out, path)
            when {
                !target.isFile -> missing += path
                path.endsWith("values/strings.xml") -> missing += mergeStrings(file, target).map { "$path: $it" }
                else -> file.copyTo(target, overwrite = true)
            }
        }
        if (missing.isNotEmpty()) {
            throw GradleException("${overlayDir.path} overrides what upstream no longer has: ${missing.joinToString()}")
        }
    }

    /** Replaces the strings of [target] named in [overrides]; returns the names [target] does not have. */
    private fun mergeStrings(overrides: File, target: File): List<String> {
        val lines = overrides.readLines().mapNotNull { line -> STRING.find(line)?.let { it.groupValues[2] to line.trim() } }.toMap()
        val seen = mutableSetOf<String>()
        val merged =
            target.readLines().map { line ->
                val match = STRING.find(line)
                val name = match?.groupValues?.get(2)
                if (name != null && name in lines) {
                    seen += name
                    match.groupValues[1] + lines.getValue(name)
                } else {
                    line
                }
            }
        target.writeText(merged.joinToString("\n", postfix = "\n"))
        return (lines.keys - seen).toList()
    }

    private companion object {
        // One string per line, as in the app's strings.xml: its indent and name
        val STRING = Regex("""^(\s*)<string name="([^"]+)"[^>]*>.*</string>\s*$""")
    }
}
