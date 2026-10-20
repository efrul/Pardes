import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

/**
 * Helper of ../check-min-api.sh below API 21 (single-file program on ASM, asm-tree and asm-analysis):
 *
 * <pre>
 * java -cp asm.jar:asm-tree.jar:asm-analysis.jar DalvikVerify44.java &lt;jar&gt; &lt;levels.txt&gt; [missing member]...
 * </pre>
 *
 * <p>Replays the hard (class-rejecting) failures of the Dalvik verifier of Android 4.4
 * (dalvik/vm/analysis/CodeVerify.cpp, kitkat-release) for the platform references that the dex still has above the
 * min API: the "above API" part of DexApiLevels' levels.txt. ART (Android 5.0 and later) does not need this.
 * <ul>
 *   <li>a type of a signature or field that cannot be resolved is java.lang.Object (lookupClassByDescriptor), so such
 *       a value that is thrown (OP_THROW: "not instanceof Throwable"), or passed, returned or stored where a resolved
 *       class is expected (not an interface, which Dalvik never checks), is VERIFY_ERROR_GENERIC: the whole class is
 *       rejected (VerifyError);
 *   <li>an instruction that names a missing class, method or field itself (new-instance, check-cast, instance-of,
 *       invoke-*, iget/sget...) is a soft failure: replaced by throw-verification-error, and the verifier does not
 *       follow that path past it. Values it would produce are DEAD here (never checked);
 *   <li>a superclass or interface that is missing: the class cannot be linked at all.
 * </ul>
 * Catch handlers whose catch types are all missing (getCaughtExceptionType: GENERIC at move-exception) are checked on
 * the dex by dexcatch.py, not here. The last line is "&lt;n&gt; hard failures in &lt;m&gt; classes: [...]".
 */
public class DalvikVerify44 {
  static final Set<String> missingTypes = new HashSet<>();
  static final Set<String> missingMembers = new HashSet<>(); // owner.name+desc
  static URLClassLoader loader;

  static boolean missing(Type t) {
    if (t == null) return false;
    if (t.getSort() == Type.ARRAY) t = t.getElementType();
    return t.getSort() == Type.OBJECT && missingTypes.contains(t.getInternalName());
  }
  static boolean missing(String internal) { return internal != null && missing(Type.getObjectType(internal)); }

  static final class Dead extends BasicValue {
    Dead() { super(Type.getObjectType("java/lang/Object")); }
    @Override public boolean equals(Object o) { return o == this; }
    @Override public int hashCode() { return System.identityHashCode(this); }
    @Override public String toString() { return "DEAD"; }
  }
  static final BasicValue DEAD = new Dead();

  static class V extends SimpleVerifier {
    V(Type cur, Type sup, List<Type> itfs, boolean isItf) { super(ASM9, cur, sup, itfs, isItf); setClassLoader(loader); }

    @Override protected Class<?> getClass(Type t) {
      if (t.getSort() == Type.OBJECT && missing(t)) return Object.class;
      if (t.getSort() == Type.ARRAY && missing(t)) return Object[].class;
      return super.getClass(t);
    }
    @Override protected boolean isInterface(Type t) { return !missing(t) && super.isInterface(t); }
    @Override protected Type getSuperClass(Type t) {
      if (t.getSort() == Type.OBJECT && missing(t)) return Type.getObjectType("java/lang/Object");
      return super.getSuperClass(t);
    }
    @Override protected boolean isAssignableFrom(Type t1, Type t2) {
      if (t1.getSort() == Type.OBJECT && !missing(t1) && super.isInterface(t1)) return true; // Dalvik: interfaces unchecked
      if (missing(t1)) t1 = t1.getSort() == Type.ARRAY ? Type.getType("[Ljava/lang/Object;") : Type.getObjectType("java/lang/Object");
      if (missing(t2)) t2 = t2.getSort() == Type.ARRAY ? Type.getType("[Ljava/lang/Object;") : Type.getObjectType("java/lang/Object");
      return super.isAssignableFrom(t1, t2);
    }
    @Override protected boolean isSubTypeOf(BasicValue value, BasicValue expected) {
      if (value == DEAD) return true;
      return super.isSubTypeOf(value, expected);
    }
    @Override public BasicValue merge(BasicValue v, BasicValue w) {
      if (v == DEAD) return w;
      if (w == DEAD) return v;
      return super.merge(v, w);
    }
    static boolean softMember(String owner, String name, String desc) {
      return missing(owner) || missingMembers.contains(owner + "." + name + desc);
    }
    @Override public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
      switch (insn.getOpcode()) {
        case NEW: if (missing(((TypeInsnNode) insn).desc)) return DEAD; break;
        case GETSTATIC: { FieldInsnNode f = (FieldInsnNode) insn; if (softMember(f.owner, f.name, f.desc)) return DEAD; break; }
        case LDC: { Object c = ((LdcInsnNode) insn).cst; if (c instanceof Type t && t.getSort() != Type.METHOD && missing(t)) return DEAD; break; }
        default: break;
      }
      return super.newOperation(insn);
    }
    @Override public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue v) throws AnalyzerException {
      switch (insn.getOpcode()) {
        case CHECKCAST: if (missing(Type.getObjectType(((TypeInsnNode) insn).desc))) return DEAD; break;
        case INSTANCEOF: if (missing(Type.getObjectType(((TypeInsnNode) insn).desc))) return BasicValue.INT_VALUE; break;
        case ANEWARRAY: if (missing(Type.getObjectType(((TypeInsnNode) insn).desc))) return DEAD; break;
        case GETFIELD: { FieldInsnNode f = (FieldInsnNode) insn; if (softMember(f.owner, f.name, f.desc)) return DEAD; break; }
        case PUTSTATIC: { FieldInsnNode f = (FieldInsnNode) insn; if (softMember(f.owner, f.name, f.desc)) return null; break; }
        case ATHROW:
          if (v != DEAD && v.getType() != null && missing(v.getType())) {
            throw new AnalyzerException(insn, "HARD throw of " + v.getType().getInternalName() + " (typed java.lang.Object): thrown class not instanceof Throwable");
          }
          break;
        default: break;
      }
      if (v == DEAD) {
        // operand from a path Dalvik stops following: no check, keep going with a plain value
        return switch (insn.getOpcode()) {
          case CHECKCAST -> newValue(Type.getObjectType(((TypeInsnNode) insn).desc));
          case GETFIELD -> newValue(Type.getType(((FieldInsnNode) insn).desc));
          case ARRAYLENGTH, INSTANCEOF -> BasicValue.INT_VALUE;
          default -> null;
        };
      }
      return super.unaryOperation(insn, v);
    }
    @Override public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue v1, BasicValue v2) throws AnalyzerException {
      if (insn.getOpcode() == PUTFIELD) {
        FieldInsnNode f = (FieldInsnNode) insn;
        if (softMember(f.owner, f.name, f.desc) || v1 == DEAD) return null;
      }
      if (v1 == DEAD || v2 == DEAD) {
        if (insn.getOpcode() == AALOAD) return DEAD;
      }
      return super.binaryOperation(insn, v1, v2);
    }
    @Override public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
      if (insn instanceof MethodInsnNode m) {
        if (softMember(m.owner, m.name, m.desc)) {
          Type r = Type.getReturnType(m.desc);
          return r.getSort() == Type.VOID ? null : r.getSort() == Type.OBJECT || r.getSort() == Type.ARRAY ? DEAD : newValue(r);
        }
      } else if (insn instanceof InvokeDynamicInsnNode d) {
        Type r = Type.getReturnType(d.desc);
        return r.getSort() == Type.VOID ? null : newValue(r);
      }
      return super.naryOperation(insn, values);
    }
    /** getCaughtExceptionType: unresolvable catch types are skipped; the handler gets the common superclass of the rest. */
    @Override public BasicValue newExceptionValue(TryCatchBlockNode tcb, Frame<BasicValue> handlerFrame, Type exceptionType) {
      if (missing(exceptionType)) return DEAD;
      return super.newExceptionValue(tcb, handlerFrame, exceptionType);
    }
    @Override public void returnOperation(AbstractInsnNode insn, BasicValue value, BasicValue expected) throws AnalyzerException {
      if (value == DEAD) return;
      super.returnOperation(insn, value, expected);
    }
  }

  public static void main(String[] a) throws Exception {
    String section = null;
    for (String line : Files.readAllLines(Path.of(a[1]))) {
      if (line.startsWith("above API")) { section = "a"; continue; }
      if (line.startsWith("not in api")) { section = "i"; continue; }
      if (!"a".equals(section) || !line.startsWith("  ")) continue;
      String[] p = line.split("\t");
      String kind = p[1], owner = p[2], member = p.length > 3 ? p[3] : "";
      if (kind.equals("type")) missingTypes.add(owner);
      else missingMembers.add(owner + "." + (kind.equals("field") ? member.substring(0, member.indexOf(':')) + member.substring(member.indexOf(':') + 1) : member));
    }
    for (int i = 2; i < a.length; i++) missingMembers.add(a[i]);
    loader = new URLClassLoader(new URL[] {Path.of(a[0]).toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    int hard = 0;
    Map<String, Integer> perClass = new TreeMap<>();
    try (ZipFile zip = new ZipFile(a[0])) {
      for (ZipEntry e : Collections.list(zip.entries())) {
        if (!e.getName().endsWith(".class") || e.getName().endsWith("module-info.class")) continue;
        ClassNode cn = new ClassNode();
        try (InputStream in = zip.getInputStream(e)) { new ClassReader(in).accept(cn, ClassReader.SKIP_FRAMES); }
        List<String> found = new ArrayList<>();
        if (missing(cn.superName)) found.add("<class>\tsuperclass " + cn.superName + " missing: cannot link");
        for (String i : cn.interfaces) if (missing(i)) found.add("<class>\tinterface " + i + " missing: cannot link");
        List<Type> itfs = new ArrayList<>();
        for (String i : cn.interfaces) itfs.add(Type.getObjectType(i));
        for (MethodNode m : cn.methods) {
          if (m.instructions.size() == 0) continue;
          try {
            new Analyzer<>(new V(Type.getObjectType(cn.name), cn.superName == null ? null : Type.getObjectType(cn.superName), itfs,
                (cn.access & Opcodes.ACC_INTERFACE) != 0)).analyze(cn.name, m);
          } catch (AnalyzerException ex) {
            String msg = ex.getMessage().replace('\n', ' ');
            boolean involvesMissing = msg.contains("HARD");
            if (!involvesMissing) {
              for (String t : missingTypes) if (msg.contains(t)) { involvesMissing = true; break; }
            }
            int line = -1;
            if (ex.node != null) for (AbstractInsnNode n = ex.node; n != null; n = n.getPrevious()) if (n instanceof LineNumberNode ln) { line = ln.line; break; }
            found.add(m.name + m.desc + "\t" + (involvesMissing ? "" : "OTHER (not a missing type) ") + msg + (line > 0 ? " [line " + line + "]" : ""));
          } catch (RuntimeException | LinkageError ex) {
            found.add(m.name + m.desc + "\tANALYSIS-ERROR " + ex);
          }
        }
        for (String f : found) {
          System.out.println(cn.name + "\t" + (cn.sourceFile == null ? "" : cn.sourceFile) + "\t" + f);
          if (!f.contains("\tOTHER ") && !f.contains("ANALYSIS-ERROR")) { hard++; perClass.merge(cn.name, 1, Integer::sum); }
        }
      }
    }
    System.out.println(hard + " hard failures in " + perClass.size() + " classes: " + perClass.keySet());
  }
}
