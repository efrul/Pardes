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
 * Turns the lucene-core jar into one that links on Android 13 (API 33), see ../build.gradle.kts.
 *
 * <p>Usage: {@code LuceneAndroidRewriter <lucene-core.jar> <patched classes dir> <out dir>
 * <out MANIFEST.MF> [<extra jar> <class,class,...>]...}. Every entry of the original jar is copied to the out dir except: classes
 * whose top-level class was rebuilt from src/main/java (replaced), classes that can never load on
 * ART (stripped), and the manifest (handed to the Jar task). Copied classes get the API 33 call-site
 * rewrites below; classes without a match are copied byte for byte.
 */
public final class LuceneAndroidRewriter {

  /**
   * Classes built on java.lang.foreign / jdk.incubator.vector / java.lang.Module. Nothing else in
   * lucene-core links to them: the Panama providers were only looked up reflectively (lookups
   * patched out in VectorizationProvider) and MMapDirectory only from FSDirectory.open (patched to
   * NIOFSDirectory). check-api33.sh fails if a stripped class is still referenced.
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

  /** JDK types missing on API 33, replaced by look-alikes compiled from src/main/java. */
  private static final Map<String, String> TYPES =
      Map.of(
          "java/lang/ClassValue", "org/apache/lucene/util/CompatClassValue", // API 34
          "java/util/HexFormat", "org/apache/lucene/util/CompatHexFormat", // API 34
          "java/lang/Runtime$Version", "org/apache/lucene/util/CompatRuntimeVersion"); // never on ART

  private static final String COMPAT = "org/apache/lucene/util/AndroidCompat";

  /**
   * JDK methods missing on API 33 ("owner.name(desc)" as called) and the static method that
   * replaces them; instance calls pass the receiver as first argument.
   */
  private static final Map<String, String[]> CALLS =
      Map.of(
          "java/util/HashMap.newHashMap(I)Ljava/util/HashMap;", // API 35
          new String[] {COMPAT, "newHashMap", "(I)Ljava/util/HashMap;"},
          "java/nio/LongBuffer.put(I[JII)Ljava/nio/LongBuffer;", // API 35
          new String[] {COMPAT, "put", "(Ljava/nio/LongBuffer;I[JII)Ljava/nio/LongBuffer;"},
          "java/nio/ByteBuffer.get(I[BII)Ljava/nio/ByteBuffer;", // API 35
          new String[] {COMPAT, "get", "(Ljava/nio/ByteBuffer;I[BII)Ljava/nio/ByteBuffer;"},
          "java/util/stream/Stream.toList()Ljava/util/List;", // API 34
          new String[] {COMPAT, "toList", "(Ljava/util/stream/Stream;)Ljava/util/List;"},
          "java/util/zip/Checksum.update(Ljava/nio/ByteBuffer;)V", // API 34
          new String[] {COMPAT, "update", "(Ljava/util/zip/Checksum;Ljava/nio/ByteBuffer;)V"},
          "java/lang/StringBuilder.isEmpty()Z", // API 35
          new String[] {COMPAT, "isEmpty", "(Ljava/lang/CharSequence;)Z"},
          "java/util/List.addFirst(Ljava/lang/Object;)V", // API 35
          new String[] {COMPAT, "addFirst", "(Ljava/util/List;Ljava/lang/Object;)V"},
          "java/util/List.getFirst()Ljava/lang/Object;", // API 35
          new String[] {COMPAT, "getFirst", "(Ljava/util/List;)Ljava/lang/Object;"},
          "java/lang/Runtime.version()Ljava/lang/Runtime$Version;", // the type is remapped too
          new String[] {
            "org/apache/lucene/util/CompatRuntimeVersion", "current", "()Ljava/lang/Runtime$Version;"
          });

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
   * same API 33 rewrites. Used for the few analysis-common classes the delta updater's index writer
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
                if (target == null) {
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
