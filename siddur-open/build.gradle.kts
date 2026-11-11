plugins {
    alias(libs.plugins.multiplatform)
}

// The engine's sources are kept as their author wrote them (README.md), so the style checks leave them alone
tasks.matching { it.name.contains("ktlint", ignoreCase = true) || it.name.startsWith("detekt") }.configureEach {
    enabled = false
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )
    jvm()

    sourceSets {
        jvmMain {
            // Where the engine's own build keeps them
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                api(libs.kotlinx.datetime)
                implementation(libs.kosherkotlin)
            }
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
