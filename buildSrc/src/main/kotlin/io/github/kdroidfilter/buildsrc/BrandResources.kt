package io.github.kdroidfilter.buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * A resource folder with a brand's files laid over it (branding/brand.gradle): [base] copied to [outputDir], then
 * every file of [overlay] put in place of the base file at the same path, and every file of the [extra] folders added
 * at its path. A `values/strings.xml` in [overlay] holds only the strings that change; they replace the base strings
 * of the same name, one by one, so the base keeps every string upstream adds.
 *
 * Fails when the overlay names a file or a string the base no longer has: upstream renamed or dropped it, and the
 * brand would silently stop applying. Fails too when an extra file is already in the base (or in two extra folders):
 * upstream added a file at that path, and the brand would silently hide it.
 */
abstract class BrandResources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val base: DirectoryProperty

    /** The brand's files that replace upstream's; none (unset) leaves [base] as it is. */
    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overlay: DirectoryProperty

    /** Folders of the brand's own files, added to [base] at their paths (the licence texts); none may be in [base]. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val extra: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun apply() {
        val baseDir = base.get().asFile
        val out = outputDir.get().asFile
        out.deleteRecursively()
        baseDir.copyRecursively(out)

        val problems = mutableListOf<String>()
        overlay.orNull?.asFile?.let { overlayDir ->
            val missing = replace(overlayDir, out)
            if (missing.isNotEmpty()) problems += "${overlayDir.path} overrides what upstream no longer has: ${missing.joinToString()}"
        }
        val clashes = add(baseDir, out)
        if (clashes.isNotEmpty()) problems += "the brand adds what upstream already has: ${clashes.joinToString()}"
        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("; "))
    }

    /** Puts the files of [overlayDir] in place of those of [out]; returns the paths and strings [out] does not have. */
    private fun replace(overlayDir: File, out: File): List<String> {
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
        return missing
    }

    /** Copies the files of the [extra] folders into [out]; returns those already in [baseDir] or in an earlier folder. */
    private fun add(baseDir: File, out: File): List<String> {
        val clashes = mutableListOf<String>()
        val added = mutableSetOf<String>()
        extra.files.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if (File(baseDir, path).exists() || !added.add(path)) clashes += path else file.copyTo(File(out, path))
            }
        }
        return clashes
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
