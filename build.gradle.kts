import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.6.1"
}

group = "com.focusflow"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
}

compose.desktop {
    application {
        mainClass = "com.focusflow.MainKt"

        jvmArgs += listOf(
            "-Xms64m",
            "-Xmx512m",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=50",
            "-Dfile.encoding=UTF-8",
            "-Djava.awt.headless=false",
            "-Dskiko.renderApi=SOFTWARE"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)

            packageName        = "FocusFlow"
            packageVersion     = "1.0.0"
            description        = "Focus & productivity app with real app blocking"
            vendor             = "FocusFlow"
            copyright          = "© 2025 FocusFlow"

            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.sql",
                "jdk.unsupported"
            )

            windows {
                menuGroup     = "FocusFlow"
                shortcut      = true
                dirChooser    = true
                perUserInstall = true
                upgradeUuid   = "B4C3F3A2-8E41-4D9A-B7C6-D1E0F2A34B56"
            }
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}
