package io.github.kdroidfilter.buildsrc

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Patches Compose's `SelectionController.draw()` so a text's selection is drawn from its lower offset to its higher
 * one, whatever its `handlesCrossed` says, as `SelectionManager.getSelectedText()` already reads it.
 *
 * A selection with both ends in one text, in a layout holding another text laid over it (the find bar's texts over the
 * book's first lines), is crossed by the layout's slots, not by its offsets: a drag past the end of a right-to-left
 * row, into the margin, gives a crossed selection whose start comes before its end, which `createSubSelections` keeps
 * as it is for one text. `draw` then asks `getPathForRange` for a reversed range, which throws ("Start(3638) or
 * End(3594) is out of range [0..3638), or start > end!") and closes the app.
 */
object SelectionDrawPatch {
    const val SELECTION_CONTROLLER = "androidx/compose/foundation/text/modifiers/SelectionController.class"
    private const val OWNER = "androidx/compose/foundation/text/modifiers/SelectionController"
    private const val PARAMS = "androidx/compose/foundation/text/modifiers/StaticTextSelectionParams"
    private const val PATH = "androidx/compose/ui/graphics/Path"
    private const val PATH_FOR_RANGE = "(II)L$PATH;"
    private const val HELPER = "zayit\$pathForRange"
    private const val HELPER_DESCRIPTOR = "(L$PARAMS;II)L$PATH;"

    fun patchSelectionController(bytes: ByteArray): ByteArray {
        var patches = 0
        val writer = ClassWriter(0)
        val visitor =
            object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    if (name != "draw") return mv
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            val isPathForRange = owner == PARAMS && name == "getPathForRange" && descriptor == PATH_FOR_RANGE
                            if (opcode == Opcodes.INVOKEVIRTUAL && isPathForRange) {
                                // params.getPathForRange(start, end) -> the same range, its offsets in order
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, HELPER, HELPER_DESCRIPTOR, false)
                                patches++
                            } else {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                            }
                        }
                    }
                }

                override fun visitEnd() {
                    val access = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
                    writeHelper(super.visitMethod(access, HELPER, HELPER_DESCRIPTOR, null, null))
                    super.visitEnd()
                }
            }
        ClassReader(bytes).accept(visitor, 0)
        check(patches == 1) {
            "SelectionDrawPatch: expected 1 getPathForRange call in SelectionController.draw, found $patches. " +
                "Compose changed: check whether a selection crossed against its offsets is now drawn " +
                "(CrossedSelectionUiTest) and drop this patch, or update it."
        }
        return writer.toByteArray()
    }

    /**
     * `static Path zayit$pathForRange(StaticTextSelectionParams params, int start, int end)`: calls
     * `params.getPathForRange(min(start, end), max(start, end))`.
     */
    private fun writeHelper(mv: MethodVisitor) {
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitVarInsn(Opcodes.ILOAD, 2)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitVarInsn(Opcodes.ILOAD, 2)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PARAMS, "getPathForRange", PATH_FOR_RANGE, false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(4, 3)
        mv.visitEnd()
    }
}
