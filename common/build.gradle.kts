/*
 * Animation Garden App
 * Copyright (C) 2022  Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("OPT_IN_IS_NOT_ENABLED", "UnstableApiUsage")
@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.compose.compose

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.android.library")
    id("kotlinx-atomicfu")
    kotlin("plugin.serialization")
}

kotlin {
    targets {
        android()
        jvm("desktop") {
            compilations.all {
                kotlinOptions.jvmTarget = "11"
//                languageSettings.languageVersion = "1.7"
            }
        }
    }

    sourceSets {
        removeIf { it.name == "androidAndroidTestRelease" }
        removeIf { it.name == "androidTestFixtures" }
        removeIf { it.name == "androidTestFixturesDebug" }
        removeIf { it.name == "androidTestFixturesRelease" }
        val commonMain by getting {
            dependencies {
                api(compose.foundation)
                api(compose.ui)
//                api(compose.uiTooling)
                api(compose.material3)
                api(compose.runtime)
//                api("org.jetbrains.compose.ui:ui-text:${ComposeBuildConfig.composeVersion}")

                api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.3")
                api("net.mamoe.yamlkt:yamlkt:0.12.0")

//    implementation("org.jetbrains.exposed:exposed-core:0.39.1")
//    implementation("org.jetbrains.exposed:exposed-dao:0.39.1")
//    implementation("org.jetbrains.exposed:exposed-jdbc:0.39.1")
//    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.39.1")
//    // https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
//    implementation("org.xerial:sqlite-jdbc:3.39.2.0")

                api(projects.api)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(compose.uiTestJUnit4)
            }
        }

        val androidMain by getting {
            dependencies {
                api("androidx.appcompat:appcompat:1.5.0")
                api("androidx.core:core-ktx:1.8.0")
                api("androidx.compose.ui:ui-tooling-preview:1.2.1")
                implementation("androidx.compose.material3:material3:1.0.0-alpha14")
            }
        }

        val desktopMain by getting {
            dependencies {
                api(compose.desktop.currentOs) {
                    exclude(compose.material)
                }
//                api(compose.preview)
                api(compose.material3)
            }
        }
    }
}

kotlin.sourceSets.all {
    languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
    languageSettings.optIn("androidx.compose.ui.ExperimentalComposeUiApi")
    languageSettings.optIn("androidx.compose.animation.ExperimentalAnimationApi")
    languageSettings.optIn("androidx.compose.foundation.ExperimentalFoundationApi")
}


android {
    compileSdk = 32
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 26
        targetSdk = 32
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
dependencies {
    debugImplementation("androidx.compose.ui:ui-tooling:1.2.1")
}
