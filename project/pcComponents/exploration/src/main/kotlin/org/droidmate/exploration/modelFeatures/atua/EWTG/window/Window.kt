package org.droidmate.exploration.modelFeatures.atua.EWTG.window

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.EWTG.Input
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager
import java.io.File
import java.nio.file.Path

abstract class Window(val classType: String,
                      val windowId: String,
                      val isRuntimeCreated: Boolean,
                      baseModel: Boolean)
{
    var activityClass = ""
    val widgets = arrayListOf<EWTGWidget>()
    val inputs = arrayListOf<Input>()
    val widgetState = HashMap<EWTGWidget, Boolean>()
    val mappedStates = arrayListOf<AbstractState>()
    var portraitDimension: Rectangle = Rectangle.empty()
    var landscapeDimension: Rectangle = Rectangle.empty()
    var portraitKeyboardDimension: Rectangle = Rectangle.empty()
    var landscapeKeyboardDimension: Rectangle = Rectangle.empty()

    init {
        if (!baseModel)
            WindowManager.instance.updatedModelWindows.add(this)
        else
            WindowManager.instance.baseModelWindows.add(this)
    }
    open fun isStatic() = false
    open fun addWidget(EWTGWidget: EWTGWidget): EWTGWidget {
        if (widgets.contains(EWTGWidget))
            return EWTGWidget
        widgets.add(EWTGWidget)
        EWTGWidget.activity = classType
        EWTGWidget.window = this
        return EWTGWidget
    }

    override fun toString(): String {
        return "$classType-$windowId"
    }

    fun dumpStructure(windowsFolder: Path) {
        val obsoleteWidgets = ArrayList<EWTGWidget>()
        val visualizedWidgets = ArrayList<EWTGWidget>()
        AbstractStateManager.instance.ABSTRACT_STATES.forEach {
            it.EWTGWidgetMapping.map { it.value }.forEach {
                if (!visualizedWidgets.contains(it)) {
                    visualizedWidgets.add(it)
                }
            }
        }
        this.widgets.filter { !visualizedWidgets.contains(it) }.forEach {
            obsoleteWidgets.add(it)
        }
        File(windowsFolder.resolve("Widgets_$windowId.csv").toUri()).bufferedWriter().use { all ->
            all.write(structureHeader())
            widgets.forEach {
                all.newLine()
                all.write("${it.widgetId};${it.resourceIdName};${it.className};${it.parent?.widgetId};${it.activity};${it.createdAtRuntime};${getAttributeValuationSetOrNull(it)};${obsoleteWidgets.contains(it)}")
            }
        }
    }

    private fun getAttributeValuationSetOrNull(it: EWTGWidget) =
            if (it.attributeValuationSetId == "")
                null
            else
                it.attributeValuationSetId

    fun dumpEvents(windowsFolder: Path, atuaMF: ATUAMF) {

        File(windowsFolder.resolve("Events_$windowId.csv").toUri()).bufferedWriter().use { all ->
            all.write(eventHeader())
            inputs.forEach {
                all.newLine()
                all.write("${it.eventType};${it.widget?.widgetId};${it.sourceWindow.windowId};${it.createdAtRuntime};" +
                        "\"${it.eventHandlers.map { atuaMF.statementMF!!.getMethodName(it) }.joinToString(";")}\";" +
                        "\"${it.modifiedMethods.map { atuaMF.statementMF!!.getMethodName(it.key)}.joinToString(";")}\"")
            }
        }
    }
    fun structureHeader(): String {
        return "widgetId;resourceIdName;className;parent;activity;createdAtRuntime;attributeValuationSetId;isObsolete"
    }
    fun eventHeader(): String {
        return "eventType;widgetId;sourceWindowId;createdAtRuntime;eventHandlers;modifiedMethods"
    }

    abstract fun getWindowType(): String

    companion object{

    }
}