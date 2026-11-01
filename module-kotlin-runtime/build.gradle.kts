import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

group = "ds.dsh"
version = "0.1.0"

base {
    archivesName.set("dsh-kotlin-runtime")
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
