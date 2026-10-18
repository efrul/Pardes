plugins {
    `java-gradle-plugin`
}

// Runs inside the Android build, next to AGP: its API and the ASM it bundles are there at runtime.
dependencies {
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    compileOnly("org.ow2.asm:asm:9.9.1")
}

tasks.withType<JavaCompile>().configureEach { options.release = 17 }

gradlePlugin {
    plugins {
        create("composeVectorMoves") {
            id = "pardes.compose-vector-moves"
            implementationClass = "io.github.kdroidfilter.seforimapp.buildlogic.ComposeVectorMovesPlugin"
        }
    }
}
