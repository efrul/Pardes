import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper of ../check-min-api.sh and ../smoke/run-desugared.sh (single-file program on the class path of AGP's D8, the
 * builder jar):
 *
 * <pre>
 * java -cp builder.jar L8Cf.java &lt;desugar.json&gt; &lt;min api&gt; &lt;android.jar&gt; &lt;out.jar&gt; &lt;library jar&gt;...
 * </pre>
 *
 * <p>Runs L8 over desugar_jdk_libs (and its configuration jar) as AGP does for an app with core library desugaring,
 * but to class files instead of dex: TraceReferences takes them as a library, so the program's j$ references resolve,
 * and a JVM can run them.
 */
public class L8Cf {
  public static void main(String[] args) throws Exception {
    final L8Command.Builder builder =
        L8Command.builder()
            .addDesugaredLibraryConfiguration(Files.readString(Path.of(args[0])))
            .setMinApiLevel(Integer.parseInt(args[1]))
            .addLibraryFiles(Path.of(args[2]))
            .setOutput(Path.of(args[3]), OutputMode.ClassFile);
    for (int i = 4; i < args.length; i++) {
      builder.addProgramFiles(Path.of(args[i]));
    }
    L8.run(builder.build());
  }
}
