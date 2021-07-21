/*
 * ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
 * Copyright (C) 2019 - 2021 University of Luxembourg
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package org.droidmate.exploration.modelFeatures.atua.ewtg.window

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.ewtg.Input
import org.droidmate.exploration.modelFeatures.atua.ewtg.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager
import java.io.File
import java.nio.file.Path

abstract class Window(var classType: String,
                      var windowId: String,
                      val isRuntimeCreated: Boolean,
                      baseModel: Boolean)
{
    //var activityClass = ""
    val widgets = arrayListOf<EWTGWidget>()
    val inputs = arrayListOf<Input>()
    val mappedStates = arrayListOf<AbstractState>()
    var portraitDimension: Rectangle = Rectangle.empty()
    var landscapeDimension: Rectangle = Rectangle.empty()
    var portraitKeyboardDimension: Rectangle = Rectangle.empty()
    var landscapeKeyboardDimension: Rectangle = Rectangle.empty()
    val windowRuntimeIds = HashSet<String>()
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
        EWTGWidget.window = this
        return EWTGWidget
    }

    override fun toString(): String {
        return "$classType-$windowId"
    }

    fun dumpStructure(windowsFolder: Path) {
        val obsoleteWidgets = ArrayList<EWTGWidget>()
        val visualizedWidgets = WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.get(this)?.values?.distinct()?: emptyList()
        this.widgets.filter { !visualizedWidgets.contains(it) }.forEach {
            obsoleteWidgets.add(it)
        }
        File(windowsFolder.resolve("Widgets_$windowId.csv").toUri()).bufferedWriter().use { all ->
            all.write(structureHeader())
            widgets.forEach {
                all.newLine()
                all.write("${it.widgetId};${it.resourceIdName};${it.className};${it.parent?.widgetId};${it.window.classType};${it.createdAtRuntime};${getAttributeValuationSetOrNull(it)};${obsoleteWidgets.contains(it)}")
            }
        }
    }

    private fun getAttributeValuationSetOrNull(it: EWTGWidget) =
            if (it.structure == "")
                null
            else
                it.structure

    fun isTargetWindowCandidate(): Boolean{
        return this !is Launcher
                && this !is OutOfApp
                && (this !is Dialog ||
                (((this as Dialog).dialogType == DialogType.APPLICATION_DIALOG
                                || (this as Dialog).dialogType == DialogType.DIALOG_FRAGMENT)))
    }
    fun dumpEvents(windowsFolder: Path, atuaMF: ATUAMF) {
        File(windowsFolder.resolve("Events_$windowId.csv").toUri()).bufferedWriter().use { all ->
            all.write(eventHeader())
            inputs.forEach {
                all.newLine()
                all.write("${it.eventType};${it.widget?.widgetId};${it.sourceWindow.windowId};${it.createdAtRuntime};" +
                        "\"${it.verifiedEventHandlers.map { atuaMF.statementMF!!.getMethodName(it) }.joinToString(";")}\";" +
                        "\"${it.modifiedMethods.map { atuaMF.statementMF!!.getMethodName(it.key)}.joinToString(";")}\"")
            }
        }
    }
    fun structureHeader(): String {
        return "[1]widgetId;[2]resourceIdName;[3]className;[4]parent;[5]activity;[6]createdAtRuntime;[7]attributeValuationSetId;[8]isObsolete"
    }
    fun eventHeader(): String {
        return "[1]eventType;[2]widgetId;[3]sourceWindowId;[4]createdAtRuntime;[5]eventHandlers;[6]modifiedMethods"
    }

    abstract fun getWindowType(): String
    abstract fun copyToRunningModel(): Window
    companion object{

    }
}