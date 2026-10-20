import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Helper of ../check-min-api.sh below API 26 (single-file program on ASM and asm-tree):
 *
 * <pre>
 * java -cp asm.jar:asm-tree.jar PolyScan.java &lt;jar&gt;
 * </pre>
 *
 * <p>Lists the calls that dex only as invoke-polymorphic (MethodHandle.invoke/invokeExact, VarHandle access modes) and
 * the MethodHandle/MethodType constants (const-method-handle/type), one line each with class, file:line and method.
 * Neither exists before DEX 038 (API 26): below, D8 replaces each call with a throw and AGP stops the build ("Increase
 * the minSdkVersion to 26 or above"). The last line is "&lt;n&gt; signature-polymorphic calls or method handle
 * constants".
 */
public class PolyScan {
  public static void main(String[] args) throws Exception {
    final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    int found = 0;
    try (ZipFile zip = new ZipFile(args[0])) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        if (!entry.getName().endsWith(".class") || entry.getName().endsWith("module-info.class")) {
          continue;
        }
        final ClassNode node = new ClassNode();
        try (InputStream in = zip.getInputStream(entry)) {
          new ClassReader(in).accept(node, 0);
        }
        for (MethodNode method : node.methods) {
          int line = -1;
          for (AbstractInsnNode insn : method.instructions) {
            String what = null;
            if (insn instanceof LineNumberNode number) {
              line = number.line;
            } else if (insn instanceof MethodInsnNode call && polymorphic(call)) {
              what = call.owner + "." + call.name + call.desc;
            } else if (insn instanceof LdcInsnNode ldc
                && (ldc.cst instanceof Handle
                    || ldc.cst instanceof ConstantDynamic
                    || ldc.cst instanceof Type type && type.getSort() == Type.METHOD)) {
              what = "constant " + ldc.cst;
            }
            if (what != null) {
              out.println(node.name + "\t" + node.sourceFile + ":" + line + "\t" + method.name + method.desc + "\t" + what);
              found++;
            }
          }
        }
      }
    }
    out.println(found + " signature-polymorphic calls or method handle constants");
  }

  /** MethodHandle.invoke/invokeExact and the VarHandle access modes (all its methods but these). */
  private static boolean polymorphic(MethodInsnNode call) {
    if (call.owner.equals("java/lang/invoke/MethodHandle")) {
      return call.name.equals("invoke") || call.name.equals("invokeExact");
    }
    return call.owner.equals("java/lang/invoke/VarHandle")
        && !call.name.matches(
            "accessModeType|isAccessModeSupported|toMethodHandle|varType|coordinateTypes|describeConstable"
                + "|hasInvokeExactBehavior|withInvokeExactBehavior|withInvokeBehavior|.*Fence");
  }
}
