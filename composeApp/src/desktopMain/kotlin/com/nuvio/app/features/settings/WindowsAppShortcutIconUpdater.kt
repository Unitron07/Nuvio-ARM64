package com.nuvio.app.features.settings

import com.nuvio.app.features.player.desktop.DesktopHostOs
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

internal object WindowsAppShortcutIconUpdater {
    fun updateAsync(icon: AppIconOption, onComplete: () -> Unit) {
        Thread({
            try {
                update(icon)
            } finally {
                onComplete()
            }
        }, "Nuvio Windows app icon updater").apply { isDaemon = false }.start()
    }

    private fun update(icon: AppIconOption) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        runCatching {
            val resource = "icons/app-icon-${icon.key}-transparent.ico"
            val localAppData = knownFolder("LocalApplicationData") ?: return@runCatching
            val iconDirectory = localAppData.resolve("${windowsShortcutApplicationName(System.getProperty("os.arch"))}/icons")
            Files.createDirectories(iconDirectory)
            val iconFile = iconDirectory.resolve("app-icon-${icon.key}-transparent.ico")
            Thread.currentThread().contextClassLoader.getResourceAsStream(resource)?.use { input ->
                Files.copy(input, iconFile, StandardCopyOption.REPLACE_EXISTING)
            } ?: return@runCatching

            val shortcuts = shortcutPaths().filter(Files::isRegularFile).toList()
            val elevatedRoots = listOfNotNull(
                knownFolder("CommonDesktopDirectory"),
                knownFolder("CommonPrograms"),
            ).map { it.toAbsolutePath().normalize() }
            val elevated = shortcuts.filter { shortcut ->
                elevatedRoots.any { root -> shortcut.toAbsolutePath().normalize().startsWith(root) }
            }
            shortcuts.filterNot(elevated::contains).forEach { setShortcutIcon(it, iconFile) }
            if (elevated.isNotEmpty()) setShortcutIconsElevated(elevated, iconFile)
        }
    }

    private fun shortcutPaths(): Sequence<Path> = windowsShortcutLocations(
        desktop = knownFolder("Desktop"), programs = knownFolder("Programs"),
        applicationData = knownFolder("ApplicationData"),
        commonDesktop = knownFolder("CommonDesktopDirectory"), commonPrograms = knownFolder("CommonPrograms"),
        architecture = System.getProperty("os.arch"),
    ).asSequence()

    private fun knownFolder(name: String): Path? {
        val command = "[Environment]::GetFolderPath([Environment+SpecialFolder]::$name)"
        val process = ProcessBuilder(
            "powershell.exe", "-WindowStyle", "Hidden", "-NoProfile", "-NonInteractive",
            "-ExecutionPolicy", "Bypass", "-Command", command,
        ).redirectErrorStream(true).start()
        val value = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        return value.takeIf { it.isNotBlank() }?.let(Path::of)
    }

    private fun setShortcutIconsElevated(shortcuts: List<Path>, iconFile: Path) {
        runCatching {
            val dollar = '$'
            val iconPath = iconFile.toString().replace("'", "''")
            val paths = shortcuts.joinToString(",") { "'${it.toString().replace("'", "''")}'" }
            val script = listOf(
                "${dollar}icon = '${iconPath}'",
                "${dollar}shell = New-Object -ComObject WScript.Shell",
                "${dollar}shortcuts = @(${paths})",
                "${dollar}shortcuts | ForEach-Object {",
                "    ${dollar}shortcut = ${dollar}shell.CreateShortcut(${dollar}_)",
                "    ${dollar}shortcut.IconLocation = ${dollar}icon + ',0'",
                "    ${dollar}shortcut.Save()",
                "}",
            ).joinToString("\n")
            val scriptFile = Files.createTempFile("nuvio-icon-update-", ".ps1")
            Files.writeString(scriptFile, script)
            val escapedScriptPath = scriptFile.toString().replace("'", "''")
            val launcher = "Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -Wait -ArgumentList " +
                "@('-WindowStyle','Hidden','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File','${escapedScriptPath}')"
            runPowerShell(ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", launcher).start(), 60)
            Files.deleteIfExists(scriptFile)
        }
    }

    private fun setShortcutIcon(shortcut: Path, iconFile: Path) {
        val shortcutArg = shortcut.toString().replace("'", "''")
        val iconArg = (iconFile.toString() + ",0").replace("'", "''")
        val dollar = '$'
        val script = listOf(
            "${dollar}shell = New-Object -ComObject WScript.Shell",
            "${dollar}shortcut = ${dollar}shell.CreateShortcut('${shortcutArg}')",
            "${dollar}shortcut.IconLocation = '${iconArg}'",
            "${dollar}shortcut.Save()",
        ).joinToString("\n")
        runCatching { runPowerShell(ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script).start()) }
    }

    private fun runPowerShell(process: Process, timeoutSeconds: Long = 3) {
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) process.destroyForcibly()
    }
}
// Keep the fork's icon customization away from official Nuvio shortcuts. Profile
// and authentication storage intentionally remain independent of this identity.
internal fun windowsShortcutApplicationName(architecture: String): String =
    if (architecture.lowercase(java.util.Locale.ROOT) in listOf("arm64", "aarch64")) "Nuvio-ARM64" else "Nuvio"

internal fun windowsShortcutLocations(
    desktop: Path?, programs: Path?, applicationData: Path?,
    commonDesktop: Path?, commonPrograms: Path?, architecture: String,
): List<Path> = buildList {
    val name = windowsShortcutApplicationName(architecture)
    desktop?.let { add(it.resolve("$name.lnk")) }
    programs?.let { add(it.resolve("$name.lnk")); add(it.resolve("$name/$name.lnk")) }
    applicationData?.let { add(it.resolve("Microsoft/Internet Explorer/Quick Launch/User Pinned/TaskBar/$name.lnk")) }
    commonDesktop?.let { add(it.resolve("$name.lnk")) }
    commonPrograms?.let { add(it.resolve("$name/$name.lnk")); add(it.resolve("$name.lnk")) }
}
