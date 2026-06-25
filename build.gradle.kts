plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "org.eltech"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val vertxVersion = "5.1.2"
val nativeOutputDir = layout.buildDirectory.dir("native")
fun nativeLibraryName(baseName: String) = providers.systemProperty("os.name").map { osName ->
    when {
        osName.startsWith("Mac", ignoreCase = true) -> "lib$baseName.dylib"
        osName.startsWith("Windows", ignoreCase = true) -> "$baseName.dll"
        else -> "lib$baseName.so"
    }
}

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-pg-client")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")
    implementation("io.vertx:vertx-circuit-breaker")
    implementation("io.vertx:vertx-micrometer-metrics")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(23)
}

application {
    mainClass.set("org.eltech.MainKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    dependsOn("compileNativeValidation")
    useJUnitPlatform()
}

tasks.register<Exec>("compileNativeValidation") {
    val javaHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(23))
    }.map { it.metadata.installationPath.asFile.absolutePath }
    val sourceFile = layout.projectDirectory.file("src/main/c/payment_validation.c")
    val outputFile = nativeOutputDir.map { it.file(nativeLibraryName("payment_validation").get()) }
    val osName = providers.systemProperty("os.name").get()
    val includePlatform = when {
        osName.startsWith("Mac", ignoreCase = true) -> "darwin"
        osName.startsWith("Windows", ignoreCase = true) -> "win32"
        else -> "linux"
    }

    inputs.file(sourceFile)
    outputs.file(outputFile)
    doFirst {
        nativeOutputDir.get().asFile.mkdirs()
        val compiler = if (osName.startsWith("Windows", ignoreCase = true)) "cl" else "cc"
        commandLine(
            compiler,
            "-O3",
            "-fPIC",
            "-shared",
            "-I${javaHome.get()}/include",
            "-I${javaHome.get()}/include/$includePlatform",
            sourceFile.asFile.absolutePath,
            "-o",
            outputFile.get().asFile.absolutePath
        )
    }
}

tasks.register<Exec>("compileNativeRouting") {
    val javaHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(23))
    }.map { it.metadata.installationPath.asFile.absolutePath }
    val sourceFile = layout.projectDirectory.file("src/main/cpp/payment_routing.cpp")
    val outputFile = nativeOutputDir.map { it.file(nativeLibraryName("payment_routing").get()) }
    val osName = providers.systemProperty("os.name").get()
    val includePlatform = when {
        osName.startsWith("Mac", ignoreCase = true) -> "darwin"
        osName.startsWith("Windows", ignoreCase = true) -> "win32"
        else -> "linux"
    }

    inputs.file(sourceFile)
    outputs.file(outputFile)
    doFirst {
        nativeOutputDir.get().asFile.mkdirs()
        val compiler = if (osName.startsWith("Windows", ignoreCase = true)) "cl" else "c++"
        commandLine(
            compiler,
            "-std=c++20",
            "-O3",
            "-fPIC",
            "-shared",
            "-I${javaHome.get()}/include",
            "-I${javaHome.get()}/include/$includePlatform",
            sourceFile.asFile.absolutePath,
            "-o",
            outputFile.get().asFile.absolutePath
        )
    }
}

tasks.register("stage") {
    dependsOn("compileNativeValidation", "compileNativeRouting", "installDist")
}

tasks.register("nativeStage") {
    dependsOn("stage")
}
