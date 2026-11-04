import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

group = "ds.dsh"
version = "0.1.0"


base {
    archivesName.set("dsh-kotlin-runtime")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

// The manual Legacy and Universal D8 pipelines consume the compiled runtime JAR and
// the exact Kotlin standard library resolved for this compilation.
tasks.register<Sync>("stageRuntimeClasspath") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("runtime-classpath"))
}

// Resolve DexKit separately from runtimeClasspath. The project uses manual D8/APK
// assembly, so an AAR must be unpacked explicitly rather than added to this JVM module.
val dexkitAar = configurations.create("dexkitAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val dexkitFlatbuffers = configurations.create("dexkitFlatbuffers") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val r8Tool = configurations.create("r8Tool") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(r8Tool.name, "com.android.tools:r8:9.1.29")
    add(dexkitAar.name, "org.luckypray:dexkit:2.3.0@aar")
    add(dexkitFlatbuffers.name, "com.google.flatbuffers:flatbuffers-java:23.5.26")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("stageR8Tool") {
    from(r8Tool)
    into(layout.buildDirectory.dir("r8-tool"))
    rename { "r8-9.1.29.jar" }
}


// Reproducible dependency staging prototype. This validates the published AAR layout
// and exposes bytecode/JNI payloads without yet enabling DexKit in either shipped APK.
tasks.register("stageDexKitArtifacts") {
    val stageDirectory = layout.buildDirectory.dir("dexkit-stage")
    outputs.dir(stageDirectory)

    doLast {
        val dexkitArchive = dexkitAar.singleFile
        val flatbuffersArchive = dexkitFlatbuffers.singleFile
        val dexkitTree = zipTree(dexkitArchive)
        val classesJars = dexkitTree.matching { include("classes.jar") }.files
        val nativeLibraries = dexkitTree.matching { include("jni/**/*.so") }.files

        check(classesJars.size == 1) {
            "Expected exactly one classes.jar in DexKit 2.3.0 AAR; found ${classesJars.size}"
        }
        check(nativeLibraries.any { it.name == "libdexkit.so" }) {
            "DexKit AAR does not contain jni/<abi>/libdexkit.so"
        }

        val abiNames = nativeLibraries
            .filter { it.name == "libdexkit.so" }
            .map { it.parentFile.name }
            .toSortedSet()
        check("arm64-v8a" in abiNames) {
            "DexKit AAR is missing arm64-v8a; found ABIs: ${abiNames.joinToString()}"
        }

        val destination = stageDirectory.get().asFile
        delete(destination)
        copy {
            from(classesJars.single())
            into(destination.resolve("libs"))
            rename { "dexkit-2.3.0.jar" }
        }
        copy {
            from(flatbuffersArchive)
            into(destination.resolve("libs"))
        }
        copy {
            from(dexkitTree) {
                include("jni/**/*.so")
            }
            into(destination)
        }

        val metadata = buildString {
            appendLine("dexkit.version=2.3.0")
            appendLine("flatbuffers.version=23.5.26")
            appendLine("dexkit.abis=${abiNames.joinToString(",")}")
            appendLine("dexkit.aar=${dexkitArchive.name}")
            appendLine("flatbuffers.jar=${flatbuffersArchive.name}")
            appendLine("apk.integration=disabled")
            appendLine("note=JNI payload staged only; manual APK integration remains gated")
        }
        destination.resolve("metadata.txt").writeText(metadata)
    }
}
