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

val dexkitCxxAar = configurations.create("dexkitCxxAar") {
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
    add(dexkitCxxAar.name, "dev.rikka.ndk.thirdparty:cxx:1.2.0@aar")

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


// Reproducible DexKit staging for the manual Legacy and Universal D8/APK pipelines.
// APK integration remains independently verified by each build script after packaging.
tasks.register("stageDexKitArtifacts") {
    val stageDirectory = layout.buildDirectory.dir("dexkit-stage")
    outputs.dir(stageDirectory)

    doLast {
        val dexkitArchive = dexkitAar.singleFile
        val flatbuffersArchive = dexkitFlatbuffers.singleFile
        val cxxArchive = dexkitCxxAar.singleFile
        val dexkitTree = zipTree(dexkitArchive)
        val cxxTree = zipTree(cxxArchive)
        val classesJars = dexkitTree.matching { include("classes.jar") }.files
        val nativeLibraries = dexkitTree.matching { include("jni/**/*.so") }.files
        val cxxPrefabFiles = cxxTree.matching { include("prefab/**") }.files

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
        val expectedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        check(abiNames == expectedAbis) {
            "DexKit ABI set mismatch; expected: ${expectedAbis.sorted().joinToString()}, found: ${abiNames.joinToString()}"
        }
        check(cxxPrefabFiles.isNotEmpty()) {
            "CXX AAR does not expose the expected Prefab payload"
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
            from(cxxArchive)
            into(destination.resolve("native-dependencies"))
        }
        copy {
            from(cxxTree) {
                include("prefab/**")
            }
            into(destination.resolve("cxx-prefab"))
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
            appendLine("cxx.version=1.2.0")
            appendLine("cxx.aar=${cxxArchive.name}")
            appendLine("cxx.prefab.files=${cxxPrefabFiles.size}")
            appendLine("apk.integration=manual_legacy_and_universal_scripts")
            appendLine("note=staging is separate; both manual APK scripts consume DexKit bytecode and JNI payloads")
        }
        destination.resolve("metadata.txt").writeText(metadata)
    }
}
