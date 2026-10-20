import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Helper of ../check-min-api.sh (single-file program, JDK 17+, no dependencies).
 *
 * <pre>
 * java DexApiLevels.java split &lt;in.jar&gt; &lt;class prefix&gt; &lt;rest.jar&gt; &lt;matching.jar&gt;
 * java DexApiLevels.java levels &lt;api-versions.xml&gt; &lt;max level&gt; &lt;program.jar&gt;
 *     &lt;dex file or dir&gt;... [--defined &lt;dex file or dir&gt;...]
 * </pre>
 *
 * <p>split: copies the classes of a jar whose internal name starts with the prefix into one jar and
 * every other entry into the other, so the classes that may call newer APIs can be dexed and
 * checked on their own. Prints the number of matching classes; with none, matching.jar is not
 * written.
 *
 * <p>levels: lists every type, field and method the dex files take from the platform (ids of
 * classes neither defined in the dex files nor in the program jar) with the API level
 * api-versions.xml gives it, declared or inherited through extends/implements (the lowest level at
 * which it resolves). Prints the references above the max level, then as info those
 * api-versions.xml does not list (dalvik annotations, covariant-return overloads such as
 * ByteBuffer.flip()ByteBuffer, members inherited from hidden classes such as
 * StringBuilder.getChars): only TraceReferences can check those. The last line is "&lt;n&gt; above
 * API &lt;max level&gt;". The program jar may be "-" (none: an APK's dex files). The dex files after
 * --defined only define classes, which the others may use, and are not checked themselves: the
 * desugared library's dex (L8), whose j$ classes the program's dex calls.
 */
public class DexApiLevels {
  public static void main(String[] args) throws Exception {
    System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    if (args.length == 5 && args[0].equals("split")) {
      split(Path.of(args[1]), args[2], Path.of(args[3]), Path.of(args[4]));
    } else if (args.length >= 5 && args[0].equals("levels")) {
      final List<Path> dexes = new ArrayList<>();
      final List<Path> defining = new ArrayList<>();
      List<Path> into = dexes;
      for (int i = 4; i < args.length; i++) {
        if (args[i].equals("--defined")) {
          into = defining;
          continue;
        }
        try (Stream<Path> files = Files.walk(Path.of(args[i]))) {
          files.filter(p -> p.toString().endsWith(".dex")).sorted().forEach(into::add);
        }
      }
      levels(Path.of(args[1]), Double.parseDouble(args[2]), args[3], dexes, defining);
    } else {
      throw new IllegalArgumentException(
          "usage: DexApiLevels split <in.jar> <class prefix> <rest.jar> <matching.jar>\n"
              + "       DexApiLevels levels <api-versions.xml> <max level> <program.jar|-> <dex|dir>..."
              + " [--defined <dex|dir>...]");
    }
  }

  private static void split(Path in, String prefix, Path rest, Path matching) throws IOException {
    final List<ZipEntry> matched = new ArrayList<>();
    try (ZipFile zip = new ZipFile(in.toFile())) {
      try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(rest))) {
        for (ZipEntry entry : Collections.list(zip.entries())) {
          if (entry.isDirectory()) {
            continue;
          }
          if (entry.getName().endsWith(".class") && entry.getName().startsWith(prefix)) {
            matched.add(entry);
          } else {
            copy(zip, entry, out);
          }
        }
      }
      if (!matched.isEmpty()) { // a zip without entries cannot be written
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(matching))) {
          for (ZipEntry entry : matched) {
            copy(zip, entry, out);
          }
        }
      }
    }
    System.out.println(matched.size());
  }

  private static void copy(ZipFile zip, ZipEntry entry, ZipOutputStream out) throws IOException {
    out.putNextEntry(new ZipEntry(entry.getName()));
    try (InputStream data = zip.getInputStream(entry)) {
      data.transferTo(out);
    }
    out.closeEntry();
  }

  // ---- dex ----

  /** The ids of a dex file: types, fields and methods (owner, name, type or descriptor). */
  private record Dex(List<String> types, List<String[]> fields, List<String[]> methods, Set<String> defined) {}

  private static Dex readDex(Path path) throws IOException {
    final ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
    final int stringIdsSize = buf.getInt(56);
    final int stringIdsOff = buf.getInt(60);
    final int typeIdsSize = buf.getInt(64);
    final int typeIdsOff = buf.getInt(68);
    final int protoIdsSize = buf.getInt(72);
    final int protoIdsOff = buf.getInt(76);
    final int fieldIdsSize = buf.getInt(80);
    final int fieldIdsOff = buf.getInt(84);
    final int methodIdsSize = buf.getInt(88);
    final int methodIdsOff = buf.getInt(92);
    final int classDefsSize = buf.getInt(96);
    final int classDefsOff = buf.getInt(100);
    final String[] strings = new String[stringIdsSize];
    for (int i = 0; i < stringIdsSize; i++) {
      strings[i] = mutf8(buf, buf.getInt(stringIdsOff + 4 * i));
    }
    final List<String> types = new ArrayList<>();
    for (int i = 0; i < typeIdsSize; i++) {
      types.add(strings[buf.getInt(typeIdsOff + 4 * i)]);
    }
    final String[] protos = new String[protoIdsSize];
    for (int i = 0; i < protoIdsSize; i++) {
      final int base = protoIdsOff + 12 * i;
      final StringBuilder desc = new StringBuilder("(");
      final int paramsOff = buf.getInt(base + 8);
      if (paramsOff != 0) {
        final int count = buf.getInt(paramsOff);
        for (int j = 0; j < count; j++) {
          desc.append(types.get(Short.toUnsignedInt(buf.getShort(paramsOff + 4 + 2 * j))));
        }
      }
      protos[i] = desc.append(')').append(types.get(buf.getInt(base + 4))).toString();
    }
    final List<String[]> fields = new ArrayList<>();
    for (int i = 0; i < fieldIdsSize; i++) {
      final int base = fieldIdsOff + 8 * i;
      fields.add(
          new String[] {
            types.get(Short.toUnsignedInt(buf.getShort(base))),
            strings[buf.getInt(base + 4)],
            types.get(Short.toUnsignedInt(buf.getShort(base + 2)))
          });
    }
    final List<String[]> methods = new ArrayList<>();
    for (int i = 0; i < methodIdsSize; i++) {
      final int base = methodIdsOff + 8 * i;
      methods.add(
          new String[] {
            types.get(Short.toUnsignedInt(buf.getShort(base))),
            strings[buf.getInt(base + 4)],
            protos[Short.toUnsignedInt(buf.getShort(base + 2))]
          });
    }
    final Set<String> defined = new HashSet<>();
    for (int i = 0; i < classDefsSize; i++) {
      defined.add(types.get(buf.getInt(classDefsOff + 32 * i)));
    }
    return new Dex(types, fields, methods, defined);
  }

  /** A string_data_item: ULEB128 length in UTF-16 units, then MUTF-8 bytes up to a 0 byte. */
  private static String mutf8(ByteBuffer buf, int off) {
    int p = off;
    while ((buf.get(p++) & 0x80) != 0) {}
    final StringBuilder s = new StringBuilder();
    while (true) {
      final int a = buf.get(p++) & 0xFF;
      if (a == 0) {
        return s.toString();
      } else if (a < 0x80) {
        s.append((char) a);
      } else if ((a & 0xE0) == 0xC0) {
        s.append((char) (((a & 0x1F) << 6) | (buf.get(p++) & 0x3F)));
      } else {
        final int b = buf.get(p++) & 0x3F;
        s.append((char) (((a & 0x0F) << 12) | (b << 6) | (buf.get(p++) & 0x3F)));
      }
    }
  }

  // ---- api-versions.xml ----

  private static final class ApiClass {
    double since = 1;
    final List<String> supers = new ArrayList<>();
    final List<Double> superSince = new ArrayList<>();
    final Map<String, Double> methods = new HashMap<>();
    final Map<String, Double> fields = new HashMap<>();
  }

  private static Map<String, ApiClass> readApi(Path xml) throws Exception {
    final Map<String, ApiClass> classes = new HashMap<>();
    final XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    try (InputStream in = Files.newInputStream(xml)) {
      final XMLStreamReader reader = factory.createXMLStreamReader(in);
      ApiClass current = null;
      while (reader.hasNext()) {
        if (reader.next() != XMLStreamConstants.START_ELEMENT) {
          continue;
        }
        final String tag = reader.getLocalName();
        final String name = reader.getAttributeValue(null, "name");
        final String sinceText = reader.getAttributeValue(null, "since");
        if (tag.equals("class")) {
          current = new ApiClass();
          current.since = sinceText == null ? 1 : Double.parseDouble(sinceText);
          classes.put(name, current);
        } else if (current != null) {
          // Members without since are as old as their class.
          final double since = sinceText == null ? current.since : Double.parseDouble(sinceText);
          switch (tag) {
            case "extends", "implements" -> {
              current.supers.add(name);
              current.superSince.add(since);
            }
            case "method" -> current.methods.put(name, since);
            case "field" -> current.fields.put(name, since);
            default -> {}
          }
        }
      }
    }
    return classes;
  }

  /** Lowest API level at which the member resolves on the class (declared or inherited), or null. */
  private static Double memberLevel(
      Map<String, ApiClass> api, String owner, String key, boolean method, Set<String> seen) {
    final ApiClass info = api.get(owner);
    if (info == null || !seen.add(owner)) {
      return null;
    }
    Double best = (method ? info.methods : info.fields).get(key);
    for (int i = 0; i < info.supers.size(); i++) {
      final Double inherited = memberLevel(api, info.supers.get(i), key, method, new HashSet<>(seen));
      if (inherited != null) {
        final double level = Math.max(inherited, info.superSince.get(i));
        best = best == null ? level : Math.min(best, level);
      }
    }
    return best == null ? null : Math.max(best, info.since);
  }

  private static void levels(
      Path xml, double max, String programJar, List<Path> dexes, List<Path> defining) throws Exception {
    if (dexes.isEmpty()) {
      throw new IllegalArgumentException("no .dex files");
    }
    final Map<String, ApiClass> api = readApi(xml);
    // Classes of the program: the jar's, and those any of the dex files define (an APK's classes.dex
    // calls classes2.dex's).
    final Set<String> local = new HashSet<>();
    if (!programJar.equals("-")) {
      try (ZipFile zip = new ZipFile(programJar)) {
        for (ZipEntry entry : Collections.list(zip.entries())) {
          if (entry.getName().endsWith(".class")) {
            final String name = entry.getName();
            local.add("L" + name.substring(0, name.length() - ".class".length()) + ";");
          }
        }
      }
    }
    final List<Dex> read = new ArrayList<>();
    for (Path path : dexes) {
      final Dex dex = readDex(path);
      read.add(dex);
      local.addAll(dex.defined());
    }
    for (Path path : defining) {
      local.addAll(readDex(path).defined());
    }
    // "level<TAB>kind<TAB>owner<TAB>member", level "?" when api-versions.xml does not list it.
    final Set<String> above = new TreeSet<>();
    final Set<String> unknown = new TreeSet<>();
    final Set<String> all = new TreeSet<>();
    for (Dex dex : read) {
      for (String type : dex.types()) {
        final String element = type.replaceFirst("^\\[+", "");
        if (element.startsWith("L") && !local.contains(element)) {
          final ApiClass info = api.get(internal(element));
          record(all, above, unknown, max, info == null ? null : info.since, "type", internal(element), "");
        }
      }
      for (String[] field : dex.fields()) {
        if (!local.contains(field[0])) {
          final String owner = internal(field[0]);
          final Double level = memberLevel(api, owner, field[1], false, new HashSet<>());
          record(all, above, unknown, max, level, "field", owner, field[1] + ":" + field[2]);
        }
      }
      for (String[] method : dex.methods()) {
        if (method[0].startsWith("[")) {
          continue; // clone() of an array
        }
        if (!local.contains(method[0])) {
          final String owner = internal(method[0]);
          final String key = method[1] + method[2];
          Double level = memberLevel(api, owner, key, true, new HashSet<>());
          if (level == null && api.containsKey(owner)) {
            // Interfaces are listed without java/lang/Object, whose methods they still resolve.
            level = memberLevel(api, "java/lang/Object", key, true, new HashSet<>());
          }
          record(all, above, unknown, max, level, "method", owner, key);
        }
      }
    }
    System.out.println("external references: " + all.size() + " (dex files: " + dexes.size() + ")");
    System.out.println("above API " + format(max) + ": " + above.size());
    above.forEach(line -> System.out.println("  " + line));
    final List<String> annotations = new ArrayList<>();
    final List<String> others = new ArrayList<>();
    unknown.forEach(line -> (line.contains("\tdalvik/annotation/") ? annotations : others).add(line));
    System.out.println(
        "not in api-versions.xml (info): "
            + unknown.size()
            + " ("
            + annotations.size()
            + " dalvik annotations, left out below)");
    others.forEach(line -> System.out.println("  " + line));
    System.out.println(above.size() + " above API " + format(max));
  }

  private static void record(
      Set<String> all, Set<String> above, Set<String> unknown, double max, Double level, String kind,
      String owner, String member) {
    final String line = (level == null ? "?" : format(level)) + "\t" + kind + "\t" + owner + "\t" + member;
    all.add(kind + owner + member);
    if (level == null) {
      unknown.add(line);
    } else if (level > max) {
      above.add(line);
    }
  }

  private static String internal(String descriptor) {
    return descriptor.substring(1, descriptor.length() - 1);
  }

  private static String format(double level) {
    return level == Math.rint(level) ? Integer.toString((int) level) : Double.toString(level);
  }
}
