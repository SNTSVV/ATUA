package org.droidmate.exploration.modelFeatures.atua.EWTG

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.FakeWindow
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.inputRepo.intent.IntentFilter
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.State
import java.io.File
import java.nio.file.Files

class WindowManager {
    private constructor()
    val updatedModelWindows = ArrayList<Window>()
    val baseModelWindows = ArrayList<Window>()
    val userlikedWidgets = ArrayList<EWTGWidget>()
    val intentFilter = HashMap<Window, ArrayList<IntentFilter>>()
    val allMeaningWindows
        get()= updatedModelWindows.filter { it !is FakeWindow && it !is Launcher && it !is OutOfApp }

    fun dump(config: ModelConfig, atuaMF: ATUAMF) {
        val wtgFolder = config.baseDir.resolve("EWTG")
        Files.createDirectory(wtgFolder)
        File(wtgFolder.resolve("EWTG_WindowList.csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            updatedModelWindows.forEach {
                all.newLine()
                all.write("${it.windowId};${it.getWindowType()};${it.classType};${it.activityClass};${it.isRuntimeCreated};${it.portraitDimension};${it.landscapeDimension};" +
                        "${it.portraitKeyboardDimension};${it.landscapeKeyboardDimension}")
            }
        }
        val widgetsFolder = wtgFolder.resolve("WindowsWidget")
        Files.createDirectory(widgetsFolder)
        updatedModelWindows.forEach {
            it.dumpStructure(widgetsFolder)
        }
        val eventsFolder = wtgFolder.resolve("WindowsEvents")
        Files.createDirectory(eventsFolder)
        updatedModelWindows.forEach {
            it.dumpEvents(eventsFolder,atuaMF)
        }
    }
    fun header(): String {
        return "WindowID;WindowType;classType;activityClass;createdAtRuntime;portraitDimension;landscapeDimension;portraitKeyboardDimension;landscapeKeyboardDimension;"
    }
    companion object{
        val instance: WindowManager by lazy {
            WindowManager()
        }

    }
}