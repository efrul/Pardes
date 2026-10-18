package io.github.kdroidfilter.seforimapp.buildlogic;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Rewrites androidx.compose.ui.graphics.vector.ImageVector$Builder: addPath and addGroup pass each
 * List parameter (the path data, the clip path data) through
 * io.github.kdroidfilter.seforimapp.android.VectorPathCompat.absoluteMoves at their entry. Fails
 * the build when the class has neither method any more (a Compose update renamed them).
 */
public abstract class ComposeVectorMovesFactory
    implements AsmClassVisitorFactory<InstrumentationParameters.None> {
  static final String BUILDER = "androidx.compose.ui.graphics.vector.ImageVector$Builder";
  static final String COMPAT = "io/github/kdroidfilter/seforimapp/android/VectorPathCompat";

  @Override
  public boolean isInstrumentable(ClassData classData) {
    return classData.getClassName().equals(BUILDER);
  }

  @Override
  public ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor next) {
    return new BuilderVisitor(next);
  }

  private static final class BuilderVisitor extends ClassVisitor {
    private final List<String> patched = new ArrayList<>();

    BuilderVisitor(ClassVisitor next) {
      super(Opcodes.ASM9, next);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      final MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
      if ((access & Opcodes.ACC_STATIC) != 0
          || !(name.startsWith("addPath") || name.startsWith("addGroup"))) {
        return visitor;
      }
      // The local slot of each List parameter (slot 0 is this; long and double take two)
      final List<Integer> slots = new ArrayList<>();
      int slot = 1;
      for (Type type : Type.getArgumentTypes(descriptor)) {
        if (type.getDescriptor().equals("Ljava/util/List;")) {
          slots.add(slot);
        }
        slot += type.getSize();
      }
      if (slots.isEmpty()) {
        return visitor;
      }
      patched.add(name);
      return new MethodVisitor(Opcodes.ASM9, visitor) {
        @Override
        public void visitCode() {
          super.visitCode();
          for (int list : slots) {
            super.visitVarInsn(Opcodes.ALOAD, list);
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC, COMPAT, "absoluteMoves",
                "(Ljava/util/List;)Ljava/util/List;", false);
            super.visitVarInsn(Opcodes.ASTORE, list);
          }
        }
      };
    }

    @Override
    public void visitEnd() {
      if (patched.stream().noneMatch(name -> name.startsWith("addPath"))) {
        throw new IllegalStateException(
            BUILDER + " has no addPath(List, ...) any more: see build-logic/ComposeVectorMovesFactory");
      }
      super.visitEnd();
    }
  }
}
