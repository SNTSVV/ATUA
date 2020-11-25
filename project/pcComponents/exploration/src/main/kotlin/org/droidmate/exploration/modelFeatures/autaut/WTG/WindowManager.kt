package org.droidmate.exploration.modelFeatures.autaut.WTG

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.FakeWindow
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.State
import java.io.File
import java.nio.file.Files

class WindowManager {
    private constructor()
    val allWindows = ArrayList<Window>()
    val allMeaningWindows
        get()= allWindows.filter { it !is FakeWindow && it !is Launcher && it !is OutOfApp }

    fun getWindowByState(state: State<*>): Window?{
        return allWindows.find { it.mappedStates.find { it.equals(state)}!=null }
    }
    fun dump(config: ModelConfig, autAutMF: AutAutMF) {
        val wtgFolder = config.baseDir.resolve("WTG")
        Files.createDirectory(wtgFolder)
        File(wtgFolder.resolve("WTG_WindowList.csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            allWindows.forEach {
                all.newLine()
                all.write("${it.windowId};${it.getWindowType()};${it.classType};${it.activityClass};${!it.fromModel};${it.portraitDimension};${it.landscapeDimension};" +
                        "${it.portraitKeyboardDimension};${it.landscapeKeyboardDimension}")
            }
        }
        val widgetsFolder = wtgFolder.resolve("WindowsWidget")
        Files.createDirectory(widgetsFolder)
        allWindows.forEach {
            it.dumpStructure(widgetsFolder)
        }
        val eventsFolder = wtgFolder.resolve("WindowsEvents")
        Files.createDirectory(eventsFolder)
        allWindows.forEach {
            it.dumpEvents(eventsFolder,autAutMF)
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