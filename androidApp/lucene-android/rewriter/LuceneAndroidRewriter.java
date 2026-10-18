import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Turns the lucene-core jar into one that links on Android 11 (API 30), see ../build.gradle.kts.
 *
 * <p>Usage: {@code LuceneAndroidRewriter <lucene-core.jar> <patched classes dir> <out dir>
 * <out MANIFEST.MF> [<extra jar> <class,class,...>]...}. Every entry of the original jar is copied to the out dir except: classes
 * whose top-level class was rebuilt from src/main/java (replaced), classes that can never load on
 * ART (stripped), and the manifest (handed to the Jar task). Copied classes get the call-site
 * rewrites below; classes without a match are copied byte for byte.
 */
public final class LuceneAndroidRewriter {

  /**
   * Classes built on java.lang.foreign / jdk.incubator.vector / java.lang.Module. Nothing else in
   * lucene-core links to them: the Panama providers were only looked up reflectively (lookups
   * patched out in VectorizationProvider) and MMapDirectory only from FSDirectory.open (patched to
   * NIOFSDirectory). check-min-api.sh fails if a stripped class is still referenced.
   */
  private static final List<String> STRIPPED =
      List.of(
          "org/apache/lucene/internal/vectorization/Panama",
          "org/apache/lucene/internal/vectorization/Lucene99MemorySegment",
          "org/apache/lucene/internal/vectorization/MemorySegment",
          "org/apache/lucene/store/MemorySegment",
          "org/apache/lucene/store/RefCountedSharedArena",
          "org/apache/lucene/store/NativeAccess",
          "org/apache/lucene/store/PosixNativeAccess",
          "org/apache/lucene/store/MMapDirectory",
          "org/apache/lucene/util/ModuleResourceLoader");

  private static final String VAR_HANDLE = "java/lang/invoke/VarHandle";
  private static final String COMPAT_VAR_HANDLE = "org/apache/lucene/util/CompatVarHandle";

  /** JDK types missing on Android, replaced by look-alikes compiled from src/main/java. */
  private static final Map<String, String> TYPES =
      Map.of(
          "java/lang/ClassValue", "org/apache/lucene/util/CompatClassValue", // API 34
          "java/util/HexFormat", "org/apache/lucene/util/CompatHexFormat", // API 34
          "java/lang/Runtime$Version", "org/apache/lucene/util/CompatRuntimeVersion", // never on ART
          VAR_HANDLE, COMPAT_VAR_HANDLE); // API 33

  private static final String COMPAT = "org/apache/lucene/util/AndroidCompat";

  /**
   * JDK methods missing on Android 11 ("owner.name(desc)" as called) and the static method that
   * replaces them; instance calls pass the receiver as first argument. The API 33 ones hand over
   * to the JDK method on API 33+ (AndroidCompat, CompatVarHandle).
   */
  private static final Map<String, String[]> CALLS =
      Map.ofEntries(
          call(
              "java/util/HashMap.newHashMap(I)Ljava/util/HashMap;", // API 35
              COMPAT, "newHashMap", "(I)Ljava/util/HashMap;"),
          call(
              "java/nio/LongBuffer.put(I[JII)Ljava/nio/LongBuffer;", // API 35
              COMPAT, "put", "(Ljava/nio/LongBuffer;I[JII)Ljava/nio/LongBuffer;"),
          call(
              "java/nio/ByteBuffer.get(I[BII)Ljava/nio/ByteBuffer;", // API 35
              COMPAT, "get", "(Ljava/nio/ByteBuffer;I[BII)Ljava/nio/ByteBuffer;"),
          call(
              "java/util/stream/Stream.toList()Ljava/util/List;", // API 34
              COMPAT, "toList", "(Ljava/util/stream/Stream;)Ljava/util/List;"),
          call(
              "java/util/zip/Checksum.update(Ljava/nio/ByteBuffer;)V", // API 34
              COMPAT, "update", "(Ljava/util/zip/Checksum;Ljava/nio/ByteBuffer;)V"),
          call(
              "java/lang/StringBuilder.isEmpty()Z", // API 35
              COMPAT, "isEmpty", "(Ljava/lang/CharSequence;)Z"),
          call(
              "java/util/List.addFirst(Ljava/lang/Object;)V", // API 35
              COMPAT, "addFirst", "(Ljava/util/List;Ljava/lang/Object;)V"),
          call(
              "java/util/List.getFirst()Ljava/lang/Object;", // API 35
              COMPAT, "getFirst", "(Ljava/util/List;)Ljava/lang/Object;"),
          call(
              "java/lang/Runtime.version()Ljava/lang/Runtime$Version;", // the type is remapped too
              "org/apache/lucene/util/CompatRuntimeVersion",
              "current", "()Ljava/lang/Runtime$Version;"),
          // API 33 from here on.
          arrays("mismatch([BII[BII)I"),
          arrays("mismatch([CII[CII)I"),
          arrays("mismatch([III[III)I"),
          arrays("compareUnsigned([BII[BII)I"),
          arrays("compare([CII[CII)I"),
          arrays("compare([III[III)I"),
          arrays("compare([JII[JII)I"),
          arrays("equals([BII[BII)Z"),
          arrays("equals([CII[CII)Z"),
          arrays("equals([III[III)Z"),
          arrays("equals([JII[JII)Z"),
          call("java/lang/Math.fma(FFF)F", COMPAT, "fma", "(FFF)F"),
          call(
              "java/util/concurrent/atomic/AtomicInteger.getOpaque()I",
              COMPAT, "getOpaque", "(Ljava/util/concurrent/atomic/AtomicInteger;)I"),
          toArray("java/util/Collection"),
          toArray("java/util/List"),
          toArray("java/util/Set"),
          toArray("java/util/ArrayList"),
          toArray("java/util/PriorityQueue"),
          call(
              "java/io/ByteArrayOutputStream.toString(Ljava/nio/charset/Charset;)Ljava/lang/String;",
              COMPAT, "toString",
              "(Ljava/io/ByteArrayOutputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;"),
          // BitUtil's byte-array views: the factory, then plain get/set by call-site descriptor.
          call(
              "java/lang/invoke/MethodHandles.byteArrayViewVarHandle"
                  + "(Ljava/lang/Class;Ljava/nio/ByteOrder;)Ljava/lang/invoke/VarHandle;",
              COMPAT_VAR_HANDLE, "byteArrayViewVarHandle",
              "(Ljava/lang/Class;Ljava/nio/ByteOrder;)L" + COMPAT_VAR_HANDLE + ";"),
          varHandle("get([BI)S", "getShort"),
          varHandle("get([BI)I", "getInt"),
          varHandle("get([BI)J", "getLong"),
          varHandle("set([BIS)V", "setShort"),
          varHandle("set([BII)V", "setInt"),
          varHandle("set([BIJ)V", "setLong"));

  /**
   * {@code new PrintStream(OutputStream, boolean, Charset)} is API 33 (CheckIndex, blocktree
   * Stats.toString). A constructor call cannot become a static call without moving the NEW/DUP in
   * front of its arguments, so on every API level it becomes {@code new PrintStream(out, autoFlush,
   * charset.name())}, API 1: the Charset is on top of the stack and Charset.name() takes its place,
   * the stack depth unchanged. The stream is the same; that constructor looks the charset up again
   * by name (Lucene passes StandardCharsets.UTF_8).
   */
  private static final String PRINT_STREAM_CHARSET =
      "(Ljava/io/OutputStream;ZLjava/nio/charset/Charset;)V";

  private static final String PRINT_STREAM_NAME = "(Ljava/io/OutputStream;ZLjava/lang/String;)V";

  private static Map.Entry<String, String[]> call(
      String jdk, String owner, String name, String desc) {
    return Map.entry(jdk, new String[] {owner, name, desc});
  }

  /** A static java.util.Arrays method, replaced by the AndroidCompat one of the same signature. */
  private static Map.Entry<String, String[]> arrays(String nameDesc) {
    final int paren = nameDesc.indexOf('(');
    return call(
        "java/util/Arrays." + nameDesc,
        COMPAT, nameDesc.substring(0, paren), nameDesc.substring(paren));
  }

  /** {@code toArray(IntFunction)} (Collection's) called on the receiver's static type, owner. */
  private static Map.Entry<String, String[]> toArray(String owner) {
    return call(
        owner + ".toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;",
        COMPAT, "toArray",
        "(Ljava/util/Collection;Ljava/util/function/IntFunction;)[Ljava/lang/Object;");
  }

  /** VarHandle get/set with a call-site descriptor, to the CompatVarHandle method for it. */
  private static Map.Entry<String, String[]> varHandle(String nameDesc, String target) {
    final String args = nameDesc.substring(nameDesc.indexOf('(') + 1);
    return call(
        VAR_HANDLE + "." + nameDesc,
        COMPAT_VAR_HANDLE, target, "(L" + COMPAT_VAR_HANDLE + ";" + args);
  }

  private final Map<String, Set<String>> rewrites = new TreeMap<>();

  public static void main(String[] args) throws IOException {
    if (args.length < 4 || args.length % 2 != 0) {
      throw new IllegalArgumentException(
          "usage: LuceneAndroidRewriter <lucene-core.jar> <patched classes> <out dir> <out manifest>"
              + " [<extra jar> <class,class,...>]...");
    }
    final LuceneAndroidRewriter rewriter = new LuceneAndroidRewriter();
    rewriter.run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
    for (int i = 4; i < args.length; i += 2) {
      rewriter.copyExtra(Path.of(args[i]), List.of(args[i + 1].split(",")), Path.of(args[2]));
    }
  }

  /**
   * Copies [classes] (internal names, with their nested classes) from another Lucene jar, with the
   * same rewrites. Used for the few analysis-common classes the delta updater's index writer
   * needs (the 4-gram analyzer of the generator), so the whole module is not shipped.
   */
  private void copyExtra(Path jar, List<String> classes, Path outDir) throws IOException {
    final Set<String> wanted = new TreeSet<>(classes);
    final Set<String> found = new TreeSet<>();
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        final String name = entry.getName();
        if (entry.isDirectory() || !name.endsWith(".class")) {
          continue;
        }
        final String className = name.substring(0, name.length() - ".class".length());
        final int nested = className.indexOf('$');
        final String topLevel = nested < 0 ? className : className.substring(0, nested);
        if (!wanted.contains(topLevel)) {
          continue;
        }
        final byte[] bytes;
        try (InputStream in = zip.getInputStream(entry)) {
          bytes = in.readAllBytes();
        }
        final Path target = outDir.resolve(name);
        if (Files.exists(target)) {
          throw new IllegalStateException(name + " is already in lucene-core");
        }
        Files.createDirectories(target.getParent());
        Files.write(target, rewrite(className, bytes));
        found.add(topLevel);
      }
    }
    if (!found.equals(wanted)) {
      final Set<String> missing = new TreeSet<>(wanted);
      missing.removeAll(found);
      throw new IllegalStateException("not in " + jar.getFileName() + ": " + missing);
    }
    System.out.println("  added from " + jar.getFileName() + ": " + summarize(new ArrayList<>(found)));
  }

  private void run(Path jar, Path patchedDir, Path outDir, Path manifestOut) throws IOException {
    final Set<String> patched = topLevelClasses(patchedDir);
    deleteRecursively(outDir);
    Files.createDirectories(outDir);
    Files.createDirectories(manifestOut.getParent());
    final Set<String> replaced = new TreeSet<>();
    final List<String> stripped = new ArrayList<>();
    int copied = 0;
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        final String name = entry.getName();
        if (entry.isDirectory()) {
          continue;
        }
        final byte[] bytes;
        try (InputStream in = zip.getInputStream(entry)) {
          bytes = in.readAllBytes();
        }
        if (name.equals("META-INF/MANIFEST.MF")) {
          Files.write(manifestOut, bytes);
          continue;
        }
        byte[] out = bytes;
        if (name.endsWith(".class") && !name.endsWith("module-info.class")) {
          final String className = name.substring(0, name.length() - ".class".length());
          final int nested = className.indexOf('$');
          final String topLevel = nested < 0 ? className : className.substring(0, nested);
          if (patched.contains(topLevel)) {
            replaced.add(className);
            continue;
          }
          if (STRIPPED.stream().anyMatch(className::startsWith)) {
            stripped.add(className);
            continue;
          }
          out = rewrite(className, bytes);
        }
        final Path target = outDir.resolve(name);
        Files.createDirectories(target.getParent());
        Files.write(target, out);
        copied++;
      }
    }
    final Set<String> added = new TreeSet<>(patched);
    replaced.forEach(c -> added.remove(c.contains("$") ? c.substring(0, c.indexOf('$')) : c));
    System.out.println("lucene-android: " + copied + " entries copied from " + jar.getFileName());
    System.out.println("  replaced from src/main/java: " + replaced);
    System.out.println("  added from src/main/java: " + added);
    System.out.println("  stripped (" + stripped.size() + "): " + summarize(stripped));
    rewrites.forEach((rule, classes) -> System.out.println("  " + rule + " <- " + classes));
  }

  private byte[] rewrite(String className, byte[] bytes) {
    final Set<String> hits = new TreeSet<>();
    final ClassWriter writer = new ClassWriter(0);
    final Remapper remapper =
        new Remapper(Opcodes.ASM9) {
          @Override
          public String map(String internalName) {
            final String target = TYPES.get(internalName);
            if (target == null) {
              return internalName;
            }
            hits.add(internalName.replace('/', '.') + " -> " + target.replace('/', '.'));
            return target;
          }
        };
    // Calls are redirected first (they match the original JDK names), then types are remapped.
    final ClassVisitor redirect =
        new ClassVisitor(Opcodes.ASM9, new ClassRemapper(writer, remapper)) {
          @Override
          public void visitInnerClass(String name, String outer, String inner, int access) {
            // The InnerClasses entry of a remapped JDK nested type (Runtime$Version) would make
            // its replacement look like a member of java.lang.Runtime.
            if (!TYPES.containsKey(name)) {
              super.visitInnerClass(name, outer, inner, access);
            }
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String signature, String[] exceptions) {
            return new MethodVisitor(
                Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean isInterface) {
                final String[] target = CALLS.get(owner + "." + name + desc);
                if (opcode == Opcodes.INVOKESPECIAL
                    && owner.equals("java/io/PrintStream")
                    && name.equals("<init>")
                    && desc.equals(PRINT_STREAM_CHARSET)) {
                  hits.add("java.io.PrintStream.<init>(..., Charset) -> (..., String)");
                  super.visitMethodInsn(
                      Opcodes.INVOKEVIRTUAL,
                      "java/nio/charset/Charset", "name", "()Ljava/lang/String;", false);
                  super.visitMethodInsn(opcode, owner, name, PRINT_STREAM_NAME, false);
                } else if (target == null) {
                  // The VarHandle type is remapped as a whole: a JDK call on or with a VarHandle
                  // without a stand-in would call a CompatVarHandle method that does not exist.
                  if (owner.equals(VAR_HANDLE)
                      || owner.startsWith("java/") && desc.contains("L" + VAR_HANDLE + ";")) {
                    throw new IllegalStateException(
                        className + " calls " + owner + "." + name + desc + ", no stand-in");
                  }
                  super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                } else {
                  hits.add(owner.replace('/', '.') + "." + name + " -> " + target[1]);
                  super.visitMethodInsn(Opcodes.INVOKESTATIC, target[0], target[1], target[2], false);
                }
              }

              @Override
              public void visitInvokeDynamicInsn(
                  String name, String desc, Handle bootstrap, Object... args) {
                for (int i = 0; i < args.length; i++) {
                  if (args[i] instanceof Handle handle) {
                    final String[] target =
                        CALLS.get(handle.getOwner() + "." + handle.getName() + handle.getDesc());
                    if (target != null) {
                      hits.add(handle.getOwner().replace('/', '.') + "::" + handle.getName());
                      args[i] =
                          new Handle(Opcodes.H_INVOKESTATIC, target[0], target[1], target[2], false);
                    } else if (handle.getOwner().equals(VAR_HANDLE)) {
                      throw new IllegalStateException(
                          className + " references VarHandle::" + handle.getName() + ": no stand-in");
                    }
                  }
                }
                super.visitInvokeDynamicInsn(name, desc, bootstrap, args);
              }
            };
          }
        };
    new ClassReader(bytes).accept(redirect, 0);
    if (hits.isEmpty()) {
      return bytes;
    }
    hits.forEach(
        rule -> rewrites.computeIfAbsent(rule, r -> new TreeSet<>()).add(simpleName(className)));
    return writer.toByteArray();
  }

  private static Set<String> topLevelClasses(Path dir) throws IOException {
    final Set<String> result = new TreeSet<>();
    try (Stream<Path> files = Files.walk(dir)) {
      files
          .filter(p -> p.toString().endsWith(".class"))
          .map(p -> dir.relativize(p).toString().replace('\\', '/'))
          .map(p -> p.substring(0, p.length() - ".class".length()))
          .map(c -> c.contains("$") ? c.substring(0, c.indexOf('$')) : c)
          .forEach(result::add);
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("no patched classes in " + dir);
    }
    return result;
  }

  private static String summarize(List<String> classes) {
    final Set<String> topLevel = new TreeSet<>();
    classes.forEach(c -> topLevel.add(simpleName(c.contains("$") ? c.substring(0, c.indexOf('$')) : c)));
    return topLevel.toString();
  }

  private static String simpleName(String className) {
    return className.substring(className.lastIndexOf('/') + 1);
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> files = Files.walk(dir)) {
      files
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }
}
