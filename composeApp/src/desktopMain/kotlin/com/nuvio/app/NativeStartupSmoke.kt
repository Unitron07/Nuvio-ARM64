package com.nuvio.app

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.evaluate
import com.nuvio.app.features.plugins.configureDesktopQuickJsLibrary
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import com.sun.jna.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Surface

/** Opt-in prerequisite check before normal services, provider data or UI startup. */
internal fun runWindowsNativeStartupSmoke() = runBlocking {
    check(System.getProperty("os.name").startsWith("Windows"))
    check(System.getProperty("os.arch") == "aarch64") { "ARM64 JVM required" }
    println("PASS: packaged JVM ${System.getProperty("java.version")} / aarch64")
    check(Native.POINTER_SIZE == 8)
    println("PASS: JNA native initialization")
    Surface.makeRasterN32Premul(16, 16).use { surface -> surface.canvas.clear(0xff000000.toInt()) }
    println("PASS: Skiko raster initialization")
    configureDesktopQuickJsLibrary()
    QuickJs.create(Dispatchers.Default).use { engine ->
        check(engine.evaluate<String>("String(21 * 2)") == "42")
    }
    println("PASS: packaged QuickJS JNI evaluation and disposal")
    NativePlayerBridge.ensureLoaded()
    repeat(5) { println("PASS: ${NativePlayerBridge.verifyWindowsRuntime()}") }
    println("PASS: native prerequisite smoke (main UI/provider services are separate checks)")
}
