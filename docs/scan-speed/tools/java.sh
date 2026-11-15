#!/usr/bin/env bash
# Compiles and runs one of the Java tools beside it (PdfiumBench, SkiaBench, CutPages) against the jars Pardes for
# desktop builds with: Nucleus's PDFium binding, Skia (skiko) with its Linux natives, the Kotlin stdlib and
# coroutines, all from Gradle's cache, so after one build of the app (pardes-desktop-run's gradle.sh).
#
#   docs/scan-speed/tools/java.sh PdfiumBench <pdf>...
#
# The tools call the binding's JNI bridge (dev.nucleusframework.pdfium.jvm.PdfiumBridge) directly: an internal Kotlin
# object, public to Java, the same calls its PdfDocument makes. Classes go to $OUT (default a temporary folder).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
cache="$HOME/.gradle/caches/modules-2/files-2.1"
jar() { find "$cache/$1" -name "$2" ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | sort | tail -1; }
cp=""
for spec in "dev.nucleusframework/pdfium-jvm:pdfium-jvm-*.jar" \
  "org.jetbrains.kotlin/kotlin-stdlib:kotlin-stdlib-2*.jar" \
  "org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm:kotlinx-coroutines-core-jvm-*.jar" \
  "org.jetbrains.skiko/skiko-awt:skiko-awt-[0-9]*.jar" \
  "org.jetbrains.skiko/skiko-awt-runtime-linux-x64:skiko-awt-runtime-linux-x64-*.jar"; do
  found=$(jar "${spec%%:*}" "${spec#*:}")
  [ -n "$found" ] || { echo "no ${spec%%:*} in Gradle's cache: build the app once first"; exit 1; }
  cp="$cp:$found"
done
out="${OUT:-$(mktemp -d)}"
javac -nowarn --add-exports java.base/sun.nio.ch=ALL-UNNAMED -cp "${cp#:}" -d "$out" "$here/$1.java"
exec java --enable-native-access=ALL-UNNAMED --add-exports java.base/sun.nio.ch=ALL-UNNAMED -cp "${cp#:}:$out" "$@"
