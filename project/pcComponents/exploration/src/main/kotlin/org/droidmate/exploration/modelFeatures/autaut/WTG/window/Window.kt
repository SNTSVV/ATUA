package org.droidmate.exploration.modelFeatures.autaut.WTG.window

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.WTG.Input
import org.droidmate.exploration.modelFeatures.autaut.WTG.StaticWidget
import org.droidmate.explorationModel.emptyUUID
import java.io.File
import java.nio.file.Path

abstract class Window(val classType: String,
                      val windowId: String,
                      val fromModel: Boolean)
{
    var activityClass = ""
    val widgets = arrayListOf<StaticWidget>()
    val events = arrayListOf<Input>()
    val widgetState = HashMap<StaticWidget, Boolean>()
    val mappedStates = arrayListOf<AbstractState>()
    var portraitDimension: Rectangle = Rectangle.empty()
    var landscapeDimension: Rectangle = Rectangle.empty()
    var portraitKeyboardDimension: Rectangle = Rectangle.empty()
    var landscapeKeyboardDimension: Rectangle = Rectangle.empty()

    open fun isStatic() = false
    open fun addWidget(staticWidget: StaticWidget): StaticWidget {
        if (widgets.contains(staticWidget))
            return staticWidget
        widgets.add(staticWidget)
        staticWidget.activity = classType
        staticWidget.wtgNode = this
        return staticWidget
    }

    override fun toString(): String {
        return "$classType-$windowId"
    }

    fun dumpStructure(windowsFolder: Path) {
        File(windowsFolder.resolve("Widgets_$windowId.csv").toUri()).bufferedWriter().use { all ->
            all.write(strucuterHeader())
            widgets.forEach {
                all.newLine()
                all.write("${it.widgetId};${it.resourceIdName};${it.className};${it.activity};${it.createdAtRuntime};${getAttributeValuationSetOrNull(it)}")
            }
        }
    }

    private fun getAttributeValuationSetOrNull(it: StaticWidget) =
            if (it.attributeValuationSetId == emptyUUID)
                null
            else
                it.attributeValuationSetId

    fun dumpEvents(windowsFolder: Path,autAutMF: AutAutMF) {
        File(windowsFolder.resolve("Events_$windowId.csv").toUri()).bufferedWriter().use { all ->
            all.write(eventHeader())
            events.forEach {
                all.newLine()
                all.write("${it.eventType};${it.widget?.widgetId};${it.sourceWindow.windowId};${it.createdAtRuntime};" +
                        "\"${it.eventHandlers.map { autAutMF.statementMF!!.getMethodName(it) }.joinToString(";")}\";" +
                        "\"${it.modifiedMethods.map { autAutMF.statementMF!!.getMethodName(it.key)}.joinToString(";")}\"")
            }
        }
    }
    fun strucuterHeader(): String {
        return "widgetId;resourceIdName;className;activity;createdAtRuntime;attributeValuationSetId"
    }
    fun eventHeader(): String {
        return "eventType;widgetId;sourceWindowId;createdAtRuntime;eventHandlers;modifiedMethods"
    }

    abstract fun getWindowType(): String

    companion object{

    }
}