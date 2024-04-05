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

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    kotlin("jvm")
    id("kotlinx-atomicfu")
}

val hostOS: OS by lazy {
    when {
        Os.isFamily(Os.FAMILY_WINDOWS) -> OS.WINDOWS
        Os.isFamily(Os.FAMILY_MAC) -> OS.MACOS
        Os.isFamily(Os.FAMILY_UNIX) -> OS.LINUX
        else -> error("Unsupported OS: ${System.getProperty("os.name")}")
    }
}

val hostArch: String by lazy {
    when (val arch = System.getProperty("os.arch")) {
        "x86_64" -> "amd64"
        "amd64" -> "amd64"
        "arm64" -> "arm64"
        "aarch64" -> "arm64"
        else -> error("Unsupported host architecture: $arch")
    }
}


enum class OS(
    val isUnix: Boolean,
) {
    WINDOWS(false),
    MACOS(true),
    LINUX(true),
}


val namer = ArtifactNamer()

class ArtifactNamer {
    private val APP_NAME = "ani"

    fun getFullVersionFromTag(tag: String): String {
        return tag.substringAfter("v")
    }

    // fullVersion example: 2.0.0-beta03
    fun androidApp(fullVersion: String): String {
        return "$APP_NAME-$fullVersion.apk"
    }

    fun androidAppQR(fullVersion: String): String {
        return "${androidApp(fullVersion)}.qrcode.png"
    }

    // Ani-2.0.0-beta03-macos-amd64.dmg
    // Ani-2.0.0-beta03-macos-arm64.dmg
    // Ani-2.0.0-beta03-windows-amd64.msi
    // Ani-2.0.0-beta03-debian-amd64.deb
    // Ani-2.0.0-beta03-redhat-amd64.rpm
    fun desktopDistributionFile(
        fullVersion: String,
        osName: String,
        archName: String = hostArch,
        extension: String
    ): String {
        return "$APP_NAME-$fullVersion-$osName-$archName.$extension"
    }

    fun server(fullVersion: String, extension: String): String {
        return "$APP_NAME-server-$fullVersion.$extension"
    }
}

tasks.register("uploadAndroidApk") {
    doLast {
        ReleaseEnvironment().run {
            uploadReleaseAsset(
                name = namer.androidApp(fullVersion),
                contentType = "application/vnd.android.package-archive",
                file = project(":app:android").layout.buildDirectory.file("outputs/apk/release")
                    .get().asFile.walk()
                    .single { it.extension == "apk" && it.name.contains("release") },
            )
        }
    }
}

tasks.register("uploadAndroidApkQR") {
    doLast {
        ReleaseEnvironment().run {
            uploadReleaseAsset(
                name = namer.androidAppQR(fullVersion),
                contentType = "image/png",
                file = rootProject.file("apk-qrcode.png"),
            )
        }
    }
}

val zipDesktopDistribution = tasks.register("zipDesktopDistribution", Zip::class) {
    dependsOn(":app:desktop:createDistributable")
    from(project(":app:desktop").layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveBaseName.set("desktop")
}

tasks.register("uploadDesktopDistributionZip") {
    dependsOn(zipDesktopDistribution)

    doLast {
        ReleaseEnvironment().run {
            uploadReleaseAsset(
                name = namer.desktopDistributionFile(
                    fullVersion,
                    osName = hostOS.name.lowercase(),
                    extension = "zip"
                ),
                contentType = "application/octet-stream",
                file = zipDesktopDistribution.get().archiveFile.get().asFile,
            )
        }
    }
}

tasks.register("uploadDesktopInstallers") {
    dependsOn(
        ":app:desktop:createDistributable",
        ":app:desktop:packageDistributionForCurrentOS",
        ":app:desktop:packageUberJarForCurrentOS"
    )

    doLast {
        ReleaseEnvironment().uploadDesktopDistributions()
    }
}

//tasks.register("uploadServerDistribution") {
//    dependsOn(
//        ":server:distZip",
//        ":server:distTar",
//    )
//
//    doLast {
//        val distZip = project(":server").tasks.getByName("distZip", Zip::class).archiveFile.get().asFile
//        val distTar = project(":server").tasks.getByName("distTar", Tar::class).archiveFile.get().asFile
//
//        ReleaseEnvironment().run {
//            uploadReleaseAsset(namer.server(fullVersion, "tar"), "application/x-tar", distTar)
//            uploadReleaseAsset(namer.server(fullVersion, "zip"), "application/zip", distZip)
//        }
//    }
//}

tasks.register("prepareArtifactsForManualUpload") {
    dependsOn(
        ":app:desktop:createDistributable",
        ":app:desktop:packageDistributionForCurrentOS",
        ":app:desktop:packageUberJarForCurrentOS"
    )
    dependsOn(zipDesktopDistribution)

    doLast {
        val distributionDir = project.layout.buildDirectory.dir("distribution").get().asFile.apply { mkdirs() }

        object : ReleaseEnvironment() {
            override val fullVersion: String = project.version.toString()
            override fun uploadReleaseAsset(name: String, contentType: String, file: File) {
                val target = distributionDir.resolve(name)
                target.delete()
                file.copyTo(target)
                println("File written: ${target.absoluteFile}")
            }
        }.run {
            uploadDesktopDistributions()
            uploadReleaseAsset(
                name = namer.desktopDistributionFile(
                    fullVersion,
                    osName = hostOS.name.lowercase(),
                    extension = "zip"
                ),
                contentType = "application/octet-stream",
                file = zipDesktopDistribution.get().archiveFile.get().asFile,
            )
        }
    }
}

fun getProperty(name: String) =
    System.getProperty(name)
        ?: System.getenv(name)
        ?: properties[name]?.toString()
//        ?: getLocalProperty(name)
        ?: ext.get(name).toString()

// do not use `object`, compiler bug
open class ReleaseEnvironment {
    private val tag by lazy {
        getProperty("CI_TAG").also { println("tag = $it") }
    }
    private val branch by lazy {
        getProperty("GITHUB_REF").substringAfterLast("/").also { println("branch = $it") }
    }
    private val shaShort by lazy {
        getProperty("GITHUB_SHA").take(8).also { println("shaShort = $it") }
    }
    open val fullVersion by lazy {
        namer.getFullVersionFromTag(tag).also { println("fullVersion = $it") }
    }
    val releaseId by lazy {
        getProperty("CI_RELEASE_ID").also { println("releaseId = $it") }
    }
    val repository by lazy {
        getProperty("GITHUB_REPOSITORY").also { println("repository = $it") }
    }
    val token by lazy {
        getProperty("GITHUB_TOKEN").also { println("token = ${it.isNotEmpty()}") }
    }

    open fun uploadReleaseAsset(
        name: String,
        contentType: String,
        file: File,
    ) {
        check(file.exists()) { "File '${file.absolutePath}' does not exist when attempting to upload '$name'." }
        val tag = getProperty("CI_TAG")
        val fullVersion = namer.getFullVersionFromTag(tag)
        val releaseId = getProperty("CI_RELEASE_ID")
        val repository = getProperty("GITHUB_REPOSITORY")
        val token = getProperty("GITHUB_TOKEN")
        println("tag = $tag")
        return uploadReleaseAsset(repository, releaseId, token, fullVersion, name, contentType, file)
    }

    fun uploadReleaseAsset(
        repository: String,
        releaseId: String,
        token: String,
        fullVersion: String,

        name: String,
        contentType: String,
        file: File,
    ) {
        println("fullVersion = $fullVersion")
        println("releaseId = $releaseId")
        println("token = ${token.isNotEmpty()}")
        println("repository = $repository")

        runBlocking {
            val url = "https://uploads.github.com/repos/$repository/releases/$releaseId/assets"
            val resp = HttpClient().post(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
                parameter("name", name)
                contentType(ContentType.parse(contentType))
                setBody(object : OutgoingContent.ReadChannelContent() {
                    override val contentType: ContentType get() = ContentType.parse(contentType)
                    override val contentLength: Long = file.length()
                    override fun readFrom(): ByteReadChannel {
                        return file.readChannel()
                    }

                })
            }
            check(resp.status.isSuccess()) {
                "Request $url failed with ${resp.status}. Response: ${
                    resp.runCatching { bodyAsText() }.getOrNull()
                }"
            }
        }
    }

    fun generateDevVersionName(
        base: String,
    ): String {
        return "$base-${branch}-${shaShort}"
    }

    fun generateReleaseVersionName(): String = tag.removePrefix("v")
}

fun ReleaseEnvironment.uploadDesktopDistributions() {
    fun uploadBinary(
        kind: String,

        osName: String,
        archName: String = hostArch,
    ) {
        uploadReleaseAsset(
            name = namer.desktopDistributionFile(
                fullVersion,
                osName,
                archName,
                extension = kind
            ),
            contentType = "application/octet-stream",
            file = project(":app:desktop").layout.buildDirectory.dir("compose/binaries/main/$kind").get().asFile
                .walk()
                .single { it.extension == kind },
        )
    }

    // jar
    uploadReleaseAsset(
        name = namer.desktopDistributionFile(
            fullVersion,
            osName = hostOS.name.lowercase(),
            extension = "jar"
        ),
        contentType = "application/octet-stream",
        file = project(":app:desktop").layout.buildDirectory.dir("compose/jars").get().asFile
            .walk()
            .single { it.extension == "jar" },
    )

    // installers
    when (hostOS) {
        OS.WINDOWS -> {
            uploadBinary("exe", osName = "windows") // all-in-one executable
            uploadBinary("msi", osName = "windows")
        }

        OS.MACOS -> {
            uploadBinary("dmg", osName = "macos")
        }

        OS.LINUX -> {
            uploadBinary("deb", osName = "debian")
            uploadBinary("rpm", osName = "redhat")
        }
    }
}

// ./gradlew updateDevVersionNameFromGit -DGITHUB_REF=refs/heads/master -DGITHUB_SHA=123456789 --no-configuration-cache
tasks.register("updateDevVersionNameFromGit") {
    doLast {
        val gradlePropertiesFile = rootProject.file("gradle.properties")
        val properties = file(gradlePropertiesFile).readText()
        val baseVersion =
            (Regex("version.name=(.+)").find(properties)
                ?: error("Failed to find base version. Check version.name in gradle.properties"))
                .groupValues[1]
                .substringBefore("-")
        val new = ReleaseEnvironment().generateDevVersionName(base = baseVersion)
        println("New version name: $new")
        file(gradlePropertiesFile).writeText(
            properties.replaceFirst(Regex("version.name=(.+)"), "version.name=$new")
        )
    }
}

// ./gradlew updateReleaseVersionNameFromGit -DGITHUB_REF=refs/heads/master -DGITHUB_SHA=123456789 --no-configuration-cache
tasks.register("updateReleaseVersionNameFromGit") {
    doLast {
        val gradlePropertiesFile = rootProject.file("gradle.properties")
        val properties = file(gradlePropertiesFile).readText()
        val new = ReleaseEnvironment().generateReleaseVersionName()
        println("New version name: $new")
        file(gradlePropertiesFile).writeText(
            properties.replaceFirst(Regex("version.name=(.+)"), "version.name=$new")
        )
    }
}
