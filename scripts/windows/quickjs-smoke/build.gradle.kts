plugins {
    kotlin("jvm") version "2.4.10"
    application
}
dependencies {
    implementation("io.github.dokar3:quickjs-kt:1.0.15")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
application { mainClass.set("QuickJsSmokeKt") }
tasks.named<JavaExec>("run") {
    val dll = providers.gradleProperty("quickjsDll").map { file(it) }
    val expectedArch = providers.gradleProperty("expectedArch").getOrElse("aarch64")
    inputs.file(dll)
    doFirst {
        require(System.getProperty("os.arch") == expectedArch) {
            "Smoke test requires $expectedArch JVM; got ${System.getProperty("os.arch")}"
        }
        // Exercise QuickJS-KT's real resource loader, as used by the application.
        val resourceArch = if (expectedArch == "aarch64") "aarch64" else "x64"
        val resourceRoot = layout.buildDirectory.dir("native-resources").get().asFile
        val resource = resourceRoot.resolve("jni/windows_$resourceArch/libquickjs.dll")
        resource.parentFile.mkdirs()
        dll.get().copyTo(resource, overwrite = true)
        classpath += files(resourceRoot)
    }
    jvmArgs("-Xcheck:jni")
}
