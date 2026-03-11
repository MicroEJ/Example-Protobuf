/*
 * Kotlin
 *
 * Copyright 2024-2026 MicroEJ Corp. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be found with this software.
 * Build: 7E4D1F7C
 */

plugins {
    id("com.microej.gradle.application") version "1.5.0"
}

group = "com.microej.example"
version = "3.0.0"

microej {
    applicationEntryPoint = "com.microej.example.protobufexample.Main"
    skippedCheckers = "nullanalysis"
}

// --- Protobuf library version selection ---
// --- Either "protobuf4" or "protobuf3". Favor Protobuf4 over Protobuf3 for security and maintainability reasons. ---
val protobufLibraryVersion =
    "protobuf4" // /!\ Run gradle clean after changing the library version, or the build will fail because of stale files

if (protobufLibraryVersion != "protobuf3" && protobufLibraryVersion != "protobuf4") {
    error("Unsupported protobufLibraryVersion '$protobufLibraryVersion': use \"protobuf3\" or \"protobuf4\"")
}

val isV4 = protobufLibraryVersion == "protobuf4"

val protocTools by configurations.creating { isTransitive = false }

// Detect platform for protoc binary download from Maven Central
val protocPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osTag = when {
        os.contains("windows") -> "windows"
        os.contains("linux") -> "linux"
        os.contains("mac") || os.contains("darwin") -> "osx"
        else -> error("Unsupported OS: $os")
    }
    val archTag = when {
        arch.contains("aarch64") || arch.contains("arm64") -> "aarch_64"
        arch.contains("amd64") || arch.contains("x86_64") -> "x86_64"
        else -> error("Unsupported architecture: $arch")
    }
    "$osTag-$archTag"
}

dependencies {
    implementation("ej.api:edc:1.3.7")
    implementation("ej.library.eclasspath:logging:1.2.1")
    implementation("ej.library.eclasspath:bytebuffer:1.2.0")

    if (isV4) {
        val protobuf4Version = "1.0.1"
        implementation("com.google:protobuf4:$protobuf4Version")
        protocTools("com.google:protobuf4:$protobuf4Version:tools@zip")
    } else {
        implementation("com.google:protobuf3:2.0.3")
    }

    microejVee("com.nxp.vee.mimxrt1170:vee-port:3.0.0")
}

val generatedProtoDir = layout.buildDirectory.dir("generated/proto")

// --- Extract protoc-gen-microej from Protobuf4 tools zip (v4 only) ---

val extractProtocTools by tasks.registering(Copy::class) {
    description = "Extract protoc-gen-microej plugin from Protobuf4 dependency"
    enabled = isV4
    from(protocTools.elements.map { files -> files.map { zipTree(it) } })
    into(layout.buildDirectory.dir("protoc-tools"))
}

// --- Download protoc from Maven Central ---

val resolveProtoc by tasks.registering {
    description = "Download protoc binary from Maven Central"

    if (isV4) {
        dependsOn(extractProtocTools)
    }

    val toolsDir = layout.buildDirectory.dir("protoc-tools").get().asFile
    val protocFile = layout.buildDirectory.file("protoc-tools/protoc.exe")

    outputs.file(protocFile)

    doLast {
        val protocVersion = if (isV4) {
            val versionFile = File(toolsDir, "protoc.version")
            if (!versionFile.exists()) {
                throw GradleException("protoc.version file not found in tools package")
            }
            versionFile.readText().trim()
        } else {
            "3.7.1"
        }
        logger.lifecycle("Protoc version required: $protocVersion ($protocPlatform)")

        // Download protoc
        val protocUrl = "https://repo.maven.apache.org/maven2/com/google/protobuf/protoc" +
                "/$protocVersion/protoc-$protocVersion-$protocPlatform.exe"
        val dest = protocFile.get().asFile
        dest.parentFile.mkdirs()
        ant.invokeMethod("get", mapOf("src" to protocUrl, "dest" to dest, "skipexisting" to true))
        dest.setExecutable(true)
        logger.lifecycle("Downloaded protoc $protocVersion to ${dest.name}")
    }
}

// --- generateProto task ---

val generateProto by tasks.registering {
    description = "Generate Java sources from .proto files"
    group = "protobuf"

    dependsOn(resolveProtoc)

    val outDir = generatedProtoDir.get().asFile

    // Auto-discover all directories under src/ that contain .proto files
    val protoDirs = listOf(file("src/main"), file("src/test")).flatMap { srcDir ->
        srcDir.listFiles { f ->
            f.isDirectory && f.name != "java" && f.name != "resources"
        }?.filter { dir ->
            fileTree(dir).matching { include("**/*.proto") }.files.isNotEmpty()
        } ?: emptyList()
    }

    protoDirs.forEach { inputs.dir(it) }
    outputs.dir(outDir)

    doLast {
        if (isV4) {
            project.exec {
                commandLine("pip3", "install", "protobuf")
            }
        }

        var count = 0
        protoDirs.forEach { protoDir ->
            fileTree(protoDir).matching { include("**/*.proto") }.forEach { protoFile ->
                logger.lifecycle("Generating ${protoFile.relativeTo(protoDir)} ...")

                val toolsDir = layout.buildDirectory.dir("protoc-tools").get().asFile
                val protoc = File(toolsDir, "protoc.exe")

                if (isV4) {
                    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                    val pluginPath = if (isWindows) {
                        fileTree(toolsDir).matching { include("**/protoc-gen-microej.bat") }.singleFile.absolutePath
                    } else {
                        val pyScript = fileTree(toolsDir).matching { include("**/protoc-gen-microej.py") }.singleFile
                        pyScript.setExecutable(true)
                        pyScript.absolutePath
                    }
                    project.exec {
                        commandLine(
                            protoc.absolutePath,
                            "--plugin=protoc-gen-microej=$pluginPath",
                            "--microej_out=${outDir.absolutePath}",
                            "--proto_path=${protoDir.absolutePath}",
                            protoFile.absolutePath
                        )
                    }
                } else {
                    project.exec {
                        commandLine(
                            protoc.absolutePath,
                            "--java_out=${outDir.absolutePath}",
                            "--proto_path=${protoDir.absolutePath}",
                            protoFile.absolutePath
                        )
                    }
                }
                count++
            }
        }

        if (count == 0) {
            logger.lifecycle("No .proto files found, skipping.")
        } else {
            logger.lifecycle("Done: $count proto files generated.")
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(generatedProtoDir)
        }
    }
}

tasks.named("compileJava") { dependsOn(generateProto) }
tasks.named("sourcesJar") { dependsOn(generateProto) }
tasks.named("shrinkRuntimeEnvironment") { dependsOn(generateProto) }
