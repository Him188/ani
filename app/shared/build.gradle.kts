/*
 * Ani
 * Copyright (C) 2022-2024 Him188
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

@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.google.devtools.ksp.gradle.KspTaskJvm
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask


plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    // 注意! 前几个插件顺序非常重要, 调整后可能导致 compose multiplatform resources 生成错误
    `flatten-source-sets`

    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.google.devtools.ksp")
    id("androidx.room")
    idea
}

extra.set("ani.jvm.target", 17)

kotlin {
    androidTarget()
    jvm("desktop")

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Workaround for MPP compose bug, don't change
        removeIf { it.name == "androidAndroidTestRelease" }
        removeIf { it.name == "androidTestFixtures" }
        removeIf { it.name == "androidTestFixturesDebug" }
        removeIf { it.name == "androidTestFixturesRelease" }
    }
}

compose.resources {
    packageOfResClass = "me.him188.ani.app"
    generateResClass = always
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.json)
        implementation(libs.atomicfu) // room runtime

        // Compose
        api(compose.foundation)
        api(compose.animation)
        api(compose.ui)
        api(compose.material3)
        api(compose.materialIconsExtended)
        api(compose.runtime)
        implementation(compose.components.resources)

        // Data sources
        api(projects.dataSources.api)
        api(projects.dataSources.core)
        api(projects.dataSources.dmhy)
        api(projects.dataSources.acgRip)
        api(projects.dataSources.mikan)
        api(projects.dataSources.bangumi)
        api(projects.dataSources.nyafun)
        api(projects.dataSources.mxdongman)
        api(projects.dataSources.ntdm)
        api(projects.dataSources.jellyfin)
        api(projects.dataSources.ikaros)

        api(projects.utils.slf4jKt)
        api(projects.utils.coroutines)
        api(projects.utils.io)
        api(projects.danmaku.ani.protocol)
        api(projects.utils.ktorClient)

        // Ktor
        api(libs.ktor.client.okhttp)
        api(libs.ktor.client.websockets)
        api(libs.ktor.client.logging)
        api(libs.ktor.client.content.negotiation)
        api(libs.ktor.serialization.kotlinx.json)

        // Others
        api(libs.koin.core) // dependency injection
        api(libs.directories) // Data directories on all OSes
        api(libs.coil.core) // Image loading
        api(libs.coil.svg) // Image loading
        api(libs.coil.compose.core) // Image loading
        api(libs.coil.network.okhttp) // Image loading
        api(libs.datastore.core) // Data Persistence
        api(libs.datastore.preferences.core) // Preferences
        api(libs.precompose) // Navigator
        api(libs.precompose.koin) // Navigator
        api(libs.precompose.viewmodel) // Navigator
        implementation(libs.androidx.room.runtime.get().toString()) {
            exclude("org.jetbrains.kotlinx", "atomicfu")
        } // multi-platform database
        api(libs.sqlite.bundled) // database driver implementation

        // Torrent
        implementation(libs.bencode)

//        api(libs.okhttp)
//        api(libs.okhttp.logging)
        implementation(libs.reorderable)
        implementation(libs.constraintlayout.compose)

        implementation(libs.jna)

        implementation(libs.slf4j.api)
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.utils.testing)
    }

    sourceSets.androidMain.dependencies {
        api(libs.kotlinx.coroutines.android)
        api(libs.datastore)
        api(libs.datastore.preferences)
        api(libs.androidx.appcompat)
        api(libs.androidx.media)
        api(libs.androidx.core.ktx)
        api(libs.koin.android)
        implementation(libs.androidx.browser)
//        api(projects.torrent.impl.libtorrent4j)

        // Compose
        api(libs.androidx.compose.ui.tooling.preview)
        api(libs.androidx.compose.material3)

        api(libs.coil)

        implementation(libs.androidx.media3.ui)
        implementation(libs.androidx.media3.exoplayer)

        api(libs.logback.android)
    }

    sourceSets.named("desktopMain").dependencies {
        api(compose.desktop.currentOs) {
            exclude(compose.material) // We use material3
        }
        api(compose.material3)
        api("org.jetbrains.compose.ui:ui-graphics-desktop:${libs.versions.compose.multiplatform.get()}")
        api(projects.utils.slf4jKt)
        api(libs.kotlinx.coroutines.swing)
        implementation(libs.vlcj)
        implementation(libs.jna) // required and don't change version, otherwise vlcj might crash the VM 
//        implementation(libs.vlcj.javafx)
//        implementation(libs.javafx.controls)
//        implementation(libs.javafx.graphics)

        // https://repo1.maven.org/maven2/org/openjfx/javafx-graphics/17.0.11/
        val os = getOs()
        val classifier = when (os) {
            Os.MacOS -> {
                // check aarch
                if (System.getProperty("os.arch").contains("aarch")) {
                    "mac-aarch64"
                } else {
                    "mac"
                }
            }

            Os.Windows -> "win"
            Os.Linux -> "linux"
            else -> {
                null
            }
        }

        runtimeOnly(libs.kotlinx.coroutines.debug)

        implementation(libs.log4j.core)
        implementation(libs.log4j.slf4j.impl)

        implementation(libs.ktor.server.cio)
        implementation(libs.ktor.server.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)

        implementation(libs.selenium.java)
        implementation(libs.webdrivermanager)
//        implementation(libs.htmlunit)
//        implementation("org.openjfx:javafx-base:17.0.11:$classifier") {
//            exclude("org.openjfx")
//        }
//        implementation("org.openjfx:javafx-controls:17.0.11:$classifier") {
//            exclude("org.openjfx")
//        }
//        implementation("org.openjfx:javafx-graphics:17.0.11:$classifier") {
//            exclude("org.openjfx")
//        }
//        implementation("org.openjfx:javafx-media:17.0.11:$classifier") {
//            exclude("org.openjfx")
//        }
//        implementation("org.openjfx:javafx-web:17.0.11:$classifier") {
//            exclude("org.openjfx")
//        }
    }

    sourceSets {
        // TODO: 临时解决方案, KT-65362 Cannot resolve declarations from a dependency when there are multiple JVM-only project dependencies in a JVM-Android MPP
        //  https://youtrack.jetbrains.com/issue/KT-65362
        // Danmaku

        fun submodule(dir: String) {
            commonMain {
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/src/"))
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/commonMain/"))
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/common/"))
                resources.srcDirs(rootProject.projectDir.resolve("$dir/resources/"))
            }
            commonTest {
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/test/"))
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/commonTest/"))
            }
            androidMain {
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/androidMain/"))
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/android/"))
            }
            getByName("desktopMain") {
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/desktopMain/"))
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/desktop/"))
            }
            commonTest {
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/commonTest/"))
            }
            getByName("androidUnitTest") {
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/androidUnitTest/"))
            }
            getByName("desktopTest") {
                kotlin.srcDirs(rootProject.projectDir.resolve("$dir/desktopTest/"))
            }
        }

        submodule("danmaku/api")
        submodule("danmaku/ani/client")
        submodule("danmaku/dandanplay")
        submodule("danmaku/ui")

        submodule("torrent/api")
        submodule("torrent/impl/libtorrent4j")
        submodule("torrent/impl/qbittorrent")
        submodule("torrent/impl/anitorrent")

        submodule("app/shared/placeholder")
        submodule("app/shared/video-player")

        commonMain {
            kotlin.srcDirs(rootProject.projectDir.resolve("torrent/anitorrent/java"))
        }
    }


    // 以下为 libtorrent4j 的依赖

    sourceSets.commonMain.dependencies {
//        api(projects.torrent.api)
        implementation(libs.libtorrent4j)
        implementation(projects.utils.slf4jKt)
        implementation(projects.utils.coroutines)
        api(projects.utils.io)
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }

    sourceSets.androidMain.dependencies {
        implementation(libs.libtorrent4j.android.arm64)
    }

//    sourceSets.named("desktopMain").dependencies {
//        implementation(
//            when (getOs()) {
//                Os.Windows -> libs.libtorrent4j.windows
//                Os.MacOS -> libs.libtorrent4j.macos
//                Os.Linux -> libs.libtorrent4j.linux
//                else -> {
//                    logger.warn("Unrecognized architecture, libtorrent4j will not be included")
//                }
//            }
//        )
//    }
}

// RESOURCES


//kotlin.sourceSets.commonMain {
//    kotlin.srcDirs(generatedResourcesDir)
//}
idea {
    val generatedResourcesDir = file("build/generated/compose/resourceGenerator/kotlin")
    module {
        generatedSourceDirs.add(generatedResourcesDir.resolve("commonMainResourceAccessors"))
        generatedSourceDirs.add(generatedResourcesDir.resolve("commonResClass"))
    }
}
// compose bug
tasks.named("generateComposeResClass") {
    dependsOn("generateResourceAccessorsForAndroidUnitTest")
}
tasks.withType(KotlinCompilationTask::class) {
    dependsOn("generateComposeResClass")
    dependsOn("generateResourceAccessorsForAndroidRelease")
    dependsOn("generateResourceAccessorsForAndroidUnitTest")
    dependsOn("generateResourceAccessorsForAndroidUnitTestRelease")
    dependsOn("generateResourceAccessorsForAndroidUnitTestDebug")
    dependsOn("generateResourceAccessorsForAndroidDebug")
}

room {
    schemaDirectory("$projectDir/schemas")
}


// BUILD CONFIG

val bangumiClientAndroidAppId = getPropertyOrNull("bangumi.oauth.client.android.appId")
val bangumiClientAndroidSecret = getPropertyOrNull("bangumi.oauth.client.android.secret")

val bangumiClientDesktopAppId = getPropertyOrNull("bangumi.oauth.client.desktop.appId")
val bangumiClientDesktopSecret = getPropertyOrNull("bangumi.oauth.client.desktop.secret")

if (bangumiClientAndroidAppId == null || bangumiClientAndroidSecret == null) {
    logger.warn("bangumi.oauth.client.android.appId or bangumi.oauth.client.android.secret is not set. Bangumi authorization will not work. Get a token from https://bgm.tv/dev/app and set them in local.properties.")
}

if (bangumiClientDesktopAppId == null || bangumiClientDesktopSecret == null) {
    logger.warn("bangumi.oauth.client.desktop.appId or bangumi.oauth.client.desktop.secret is not set. Bangumi authorization will not work. Get a token from https://bgm.tv/dev/app and set them in local.properties.")
}

android {
    namespace = "me.him188.ani"
    compileSdk = getIntProperty("android.compile.sdk")
    defaultConfig {
        minSdk = getIntProperty("android.min.sdk")
        buildConfigField("String", "VERSION_NAME", "\"${getProperty("version.name")}\"")
        buildConfigField("String", "BANGUMI_OAUTH_CLIENT_APP_ID", "\"$bangumiClientAndroidAppId\"")
        buildConfigField("String", "BANGUMI_OAUTH_CLIENT_SECRET", "\"$bangumiClientAndroidSecret\"")
    }
    buildTypes.getByName("release") {
        isMinifyEnabled = false // shared 不能 minify, 否则构建 app 会失败
        isShrinkResources = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            *sharedAndroidProguardRules(),
        )
        buildConfigField("String", "APP_APPLICATION_ID", "\"me.him188.ani\"")
    }
    buildTypes.getByName("debug") {
        buildConfigField("String", "APP_APPLICATION_ID", "\"me.him188.ani.debug2\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    add("kspDesktop", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}


val buildConfigDesktopDir = layout.buildDirectory.file("generated/source/buildConfigDesktop")

idea {
    module {
        generatedSourceDirs.add(buildConfigDesktopDir.get().asFile)
    }
}

kotlin.sourceSets.getByName("desktopMain") {
    kotlin.srcDirs(buildConfigDesktopDir)
}

//tasks.register("generateBuildConfigForDesktop") {
//    doLast {
//        
//    }
//}
val generateAniBuildConfigDesktop = tasks.register("generateAniBuildConfigDesktop") {
    val file = buildConfigDesktopDir.get().asFile.resolve("AniBuildConfig.kt").apply {
        parentFile.mkdirs()
        createNewFile()
    }

    inputs.property("project.version", project.version)
    inputs.property("bangumiClientAppIdDesktop", bangumiClientDesktopAppId).optional(true)
    inputs.property("bangumiClientSecret", bangumiClientDesktopSecret).optional(true)

    outputs.file(file)

    val text = """
            package me.him188.ani.app.platform
            object AniBuildConfigDesktop : AniBuildConfig {
                override val versionName = "${project.version}"
                override val bangumiOauthClientAppId = "$bangumiClientDesktopAppId"
                override val bangumiOauthClientSecret = "$bangumiClientDesktopSecret"
                override val isDebug = System.getenv("ANI_DEBUG") == "true" || System.getProperty("ani.debug") == "true"
            }
            """.trimIndent()

    outputs.upToDateWhen {
        file.exists() && file.readText().trim() == text.trim()
    }

    doLast {
        file.writeText(text)
    }
}

tasks.named("compileKotlinDesktop") {
    dependsOn(generateAniBuildConfigDesktop)
}

// :app:shared:kspKotlinDesktop
tasks.withType(KspTaskJvm::class.java) {
    dependsOn(generateAniBuildConfigDesktop)
}