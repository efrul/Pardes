package io.github.kdroidfilter.seforimapp.buildlogic;

import com.android.build.api.instrumentation.FramesComputationMode;
import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import kotlin.Unit;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Android 11's Skia moves a relative moveTo that follows a close() from the contour's last point,
 * where Compose (and Skia from Android 13) moves it from the contour's start: the icons whose
 * shapes start that way (most Material Symbols paths, "...Zm-280-80v440...") were drawn askew.
 * Compose's ImageVector.Builder hands every path and clip path of a vector icon, whatever built
 * it, to VectorPathCompat.absoluteMoves first, which makes those moves absolute below Android 13.
 */
public final class ComposeVectorMovesPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getPluginManager().withPlugin("com.android.application", applied -> {
      ApplicationAndroidComponentsExtension components =
          project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class);
      components.onVariants(components.selector().all(), variant -> {
        variant.getInstrumentation().transformClassesWith(
            ComposeVectorMovesFactory.class, InstrumentationScope.ALL, parameters -> Unit.INSTANCE);
        variant.getInstrumentation().setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES);
        return Unit.INSTANCE;
      });
    });
  }
}
