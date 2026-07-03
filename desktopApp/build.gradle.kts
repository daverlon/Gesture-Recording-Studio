plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

compose.desktop {
    application {
        mainClass = "ai.symly.MainKt"
        
        jvmArgs += listOf(
            "-Xdock:name=Gesture Recording Studio",
            "-Dapple.awt.application.name=Gesture Recording Studio"
        )

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "Gesture Recording Studio"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "ai.symly.gesture-recording-studio"
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleName</key>
                        <string>Gesture Recording Studio</string>
                        <key>NSBluetoothAlwaysUsageDescription</key>
                        <string>Connect to gesture recording devices over Bluetooth.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
