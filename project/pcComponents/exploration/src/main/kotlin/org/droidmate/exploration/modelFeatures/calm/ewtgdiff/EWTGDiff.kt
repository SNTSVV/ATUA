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

package org.droidmate.exploration.modelFeatures.calm.ewtgdiff

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributePath
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.AbstractionFunction2
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.DecisionNode2
import org.droidmate.exploration.modelFeatures.atua.ewtg.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.ewtg.EventType
import org.droidmate.exploration.modelFeatures.atua.ewtg.Input
import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager
import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowTransition
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Activity
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

class EWTGDiff private constructor(){
    val windowDifferentSets: HashMap<String,DifferentSet<Window>> = HashMap()
    val widgetDifferentSets: HashMap<String,DifferentSet<EWTGWidget>> = HashMap()
    val transitionDifferentSets: HashMap<String, DifferentSet<Edge<Window,WindowTransition>>> = HashMap()
    val removedAbstractStates = ArrayList<AbstractState>()

    fun getWidgetAdditions(): List<EWTGWidget> {
        if (widgetDifferentSets.containsKey("AdditionSet")) {
            return (widgetDifferentSets["AdditionSet"]!! as AdditionSet<EWTGWidget>).addedElements
        }
        return emptyList<EWTGWidget>()
    }

    fun getWidgetReplacement(): List<EWTGWidget> {
        if (widgetDifferentSets.containsKey("ReplacementSet")) {
            return (widgetDifferentSets["ReplacementSet"]!! as ReplacementSet<EWTGWidget>).replacedElements.map { it.new }
        }
        return emptyList<EWTGWidget>()
    }

    fun loadFromFile(filePath: Path, atuamf: ATUAMF) {
        if (!Files.exists(filePath))
            return
        val jsonData = String(Files.readAllBytes(filePath))
        val ewtgdiffJson = JSONObject(jsonData)
        ewtgdiffJson.keys().forEach { key->
            if (key == "windowDifferences") {
                loadWindowDifferences(ewtgdiffJson.get(key) as JSONObject)
            }
            if (key == "widgetDifferences") {
                loadWidgetDifferences(ewtgdiffJson.get(key) as JSONObject)
            }
            if (key == "transitionDifferences") {
                loadTransitionDifferences(ewtgdiffJson.get(key) as JSONObject,atuamf)
            }
        }
        if (windowDifferentSets.containsKey("DeletionSet")) {
            for (deleted in (windowDifferentSets.get("DeletionSet")!! as DeletionSet<Window>).deletedElements) {
                val toRemoves = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter { it.window == deleted }
                removedAbstractStates.addAll(toRemoves)
                AbstractStateManager.INSTANCE.ABSTRACT_STATES.removeAll(toRemoves)
                AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.remove(deleted)
                AttributeValuationMap.allWidgetAVMHashMap.remove(deleted)
                AttributePath.allAttributePaths.remove(deleted)
                WindowManager.instance.baseModelWindows.remove(deleted)
                toRemoves.forEach {
                    atuamf.dstg.removeVertex(it)
                }


            }
        }
        if (windowDifferentSets.containsKey("ReplacementSet")) {
            for (replacement in (windowDifferentSets.get("ReplacementSet")!! as ReplacementSet<Window>).replacedElements) {
                replaceWindow(replacement, atuamf)
                WindowManager.instance.baseModelWindows.remove(replacement.old)
            }
            val replacements = (windowDifferentSets.get("ReplacementSet")!! as ReplacementSet<Window>).replacedElements.map { Pair(it.old,it.new) }.toMap()
            AbstractStateManager.INSTANCE.ABSTRACT_STATES.forEach {
                it.abstractTransitions.forEach {
                    /*if (replacements.contains(it.prevWindow)) {
                        it.prevWindow = replacements.get(it.prevWindow)
                    }*/
                    // TODO check
                }
            }

        }
        if (windowDifferentSets.containsKey("RetainerSet")) {
            for (replacement in (windowDifferentSets.get("RetainerSet")!! as RetainerSet<Window>).replacedElements) {
                replaceWindow(replacement, atuamf)
                WindowManager.instance.baseModelWindows.remove(replacement.old)
            }

            val replacements = (windowDifferentSets.get("RetainerSet")!! as RetainerSet<Window>).replacedElements.map { Pair(it.old,it.new) }.toMap()
            AbstractStateManager.INSTANCE.ABSTRACT_STATES.forEach {
                it.abstractTransitions.forEach {
                    /*if (replacements.contains(it.prevWindow)) {
                        it.prevWindow = replacements.get(it.prevWindow)
                    }*/
                    // TODO check
                }
            }
        }
        WindowManager.instance.baseModelWindows.filter {
                    it is Dialog || it is OutOfApp || it is Activity
        }.forEach {w->
            val exisitingWindow = WindowManager.instance.updatedModelWindows.find { it.javaClass == w.javaClass && it.classType == w.classType }
            if (exisitingWindow != null) {
                // replace old window with the exisiting one
                replaceWindow(Replacement(w,exisitingWindow),atuamf)
            } else {
                val newWindow = w.copyToRunningModel()
                replaceWindow(Replacement(w,newWindow),atuamf)
                WindowManager.instance.updatedModelWindows.add(newWindow)
                WindowManager.instance.baseModelWindows.remove(w)
            }
        }

        if (widgetDifferentSets.containsKey("DeletionSet")) {
            for (deleted in (widgetDifferentSets.get("DeletionSet")!! as DeletionSet<EWTGWidget>).deletedElements) {
                deleted.window.widgets.remove(deleted)
                updateWindowHierarchyWithDeleted(deleted)
                updateEWTGWidgetAVMMappingWithDeleted(deleted,atuamf)
            }
        }

        if (widgetDifferentSets.containsKey("ReplacementSet")) {
            for (replacement in (widgetDifferentSets.get("ReplacementSet")!! as ReplacementSet<EWTGWidget>).replacedElements) {
                // update avm-ewtgwidget mapping
                updateInputs(replacement)
                updateAbstractionFunction(replacement)
                // update EWTGWidget structure
                updateWindowHierarchy(replacement)
                updateEWTGWidgetAVMMapping(replacement)
            }
        }

        if (widgetDifferentSets.containsKey("RetainerSet")) {
            for (replacement in (widgetDifferentSets.get("RetainerSet")!! as RetainerSet<EWTGWidget>).replacedElements) {
                updateInputs(replacement)
                // update EWTGWidget structure
                updateWindowHierarchy(replacement)
                updateAbstractionFunction(replacement)
                updateEWTGWidgetAVMMapping(replacement)
            }
        }
        if (transitionDifferentSets.containsKey("RetainerSet")) {
            for (replacement in (transitionDifferentSets.get("RetainerSet")!! as RetainerSet<Edge<Window,Input>>).replacedElements) {
                replacement.new.label.eventHandlers.addAll(replacement.old.label.eventHandlers)
            }
        }

        AbstractStateManager.INSTANCE.ABSTRACT_STATES.forEach {
            val toRemoveMappings = ArrayList<AttributeValuationMap>()
            it.EWTGWidgetMapping.forEach {
                if (WindowManager.instance.baseModelWindows.contains(it.value.window)) {
                    toRemoveMappings.add(it.key)
                }
            }
            toRemoveMappings.forEach { avm->
                it.EWTGWidgetMapping.remove(avm)
            }
        }
    }

    private fun updateEWTGWidgetAVMMappingWithDeleted(deleted: EWTGWidget, atuamf: ATUAMF) {
        AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter { it.window == deleted.window }.forEach { abstractState ->
            val toDeleteAvms = abstractState.EWTGWidgetMapping.filter { ewtgWidget -> ewtgWidget.value == deleted }.keys
            val toRemoveAbstractTransitions = ArrayList<AbstractTransition>()
            toDeleteAvms.forEach { avm ->
                abstractState.EWTGWidgetMapping.remove(avm)
                toRemoveAbstractTransitions.addAll(abstractState.abstractTransitions.filter { it.abstractAction.isWidgetAction()
                        && it.abstractAction.attributeValuationMap == avm})
            }
            abstractState.attributeValuationMaps.removeIf { toDeleteAvms.contains(it) }
            toRemoveAbstractTransitions.forEach {
                abstractState.abstractTransitions.remove(it)
                val edge = atuamf.dstg.edge(it.source,it.dest,it)
                if (edge!=null)
                    atuamf.dstg.remove(edge)
            }
        }
    }

    private fun updateWindowHierarchyWithDeleted(deleted: EWTGWidget) {
        val parent = deleted.parent
        if (parent != null)
            parent.children.remove(deleted)
        deleted.children.forEach {
            it.parent = parent
        }
    }

    private fun updateEWTGWidgetAVMMapping(replacement: Replacement<EWTGWidget>) {
        AbstractStateManager.INSTANCE.ABSTRACT_STATES.forEach {
            val toBeReplacedAvms = it.EWTGWidgetMapping.filter { it.value == replacement.old }.keys
            toBeReplacedAvms.forEach { avm ->
                it.EWTGWidgetMapping.put(avm, replacement.new)
            }
        }
    }

    private fun updateWindowHierarchy(replacement: Replacement<EWTGWidget>) {
        replacement.old.children.forEach {
            it.parent = replacement.new
        }
        val parent = replacement.old.parent
        if (parent != null) {
            parent.children.remove(replacement.old)
            replacement.new.parent = parent
        }
    }

    private fun updateInputs(replacement: Replacement<EWTGWidget>) {
        replacement.old.window.inputs.filter { it.widget == replacement.old }.forEach { input ->
            val existingInputInUpdateVers = replacement.new.window.inputs
                    .find { it.widget == replacement.new && it.eventType == input.eventType }
            if (existingInputInUpdateVers == null)
                input.widget = replacement.new
            else {
                replacement.new.window.inputs.remove(input)
                existingInputInUpdateVers.eventHandlers.addAll(input.eventHandlers)
                existingInputInUpdateVers.modifiedMethods.putAll(input.modifiedMethods)

            }
        }
    }

    private fun updateAbstractionFunction(replacement: Replacement<EWTGWidget>) {
        var currentDecisionNode: DecisionNode2? = AbstractionFunction2.INSTANCE.root
        while (currentDecisionNode != null) {
            if (currentDecisionNode.ewtgWidgets.remove(replacement.old)) {
                currentDecisionNode.ewtgWidgets.add(replacement.new)
            }
            currentDecisionNode = currentDecisionNode!!.nextNode
        }
    }

    private fun resolveWindowNameConflict(window: Window): Boolean {
        if (WindowManager.instance.updatedModelWindows.any {
                    it != window
                            && it.windowId == window.windowId
                }) {
            if (window is Dialog)
                window.windowId = Dialog.getNodeId()
            else if (window is OutOfApp)
                window.windowId = OutOfApp.getNodeId()
            else if (window is Activity)
                window.windowId = Activity.getNodeId()
            return true
        } else
            return false
    }

    private fun replaceWindow(replacement: Replacement<Window>, atuamf: ATUAMF) {
        AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter { it.window == replacement.old }.forEach {
            it.window = replacement.new
        }
        val oldWindowAVMs = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.get(replacement.old)
        if (oldWindowAVMs!=null) {
            AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.put(replacement.new, oldWindowAVMs)
            oldWindowAVMs.forEach { t, u ->
                u.window = replacement.new
            }
        }
        val oldWindowAttributePaths = AttributePath.allAttributePaths.get(replacement.old)
        if (oldWindowAttributePaths!=null) {
            AttributePath.allAttributePaths.put(replacement.new,oldWindowAttributePaths )
            oldWindowAttributePaths.forEach { t, u ->
                u.window = replacement.new
            }
        }
        atuamf.modifiedMethodsByWindow.remove(replacement.old)
        replacement.old.widgets.filter { it.createdAtRuntime }.forEach {
            replacement.new.widgets.add(it)
            replacement.old.widgets.remove(it)
            it.window = replacement.new
        }
        replacement.old.inputs.filter { it.createdAtRuntime }.forEach {
            it.sourceWindow = replacement.new
            replacement.old.inputs.remove(it)
            replacement.new.inputs.add(it)
        }
        val toRemoveEdges = ArrayList<Edge<Window, WindowTransition>>()
        atuamf.wtg.edges(replacement.old).forEach {
            atuamf.wtg.add(replacement.new, it.destination?.data, it.label)
            toRemoveEdges.add(it)
        }
        atuamf.wtg.edges().forEach {
            if (it.destination?.data == replacement.old) {
                atuamf.wtg.add(it.source.data, replacement.new, it.label)
                toRemoveEdges.add(it)
            }
        }
        toRemoveEdges.forEach {
            atuamf.wtg.remove(it)
        }
    }

    private fun loadTransitionDifferences(jsonObject: JSONObject,atuamf: ATUAMF) {
        jsonObject.keys().forEach {key->
            if (key == "transitionAdditions") {
                val transitionAdditionSet = AdditionSet<Edge<Window,WindowTransition>>()
                transitionDifferentSets.put("AdditionSet",transitionAdditionSet)
                loadTransitionAdditionSet(jsonObject.get(key) as JSONArray, transitionAdditionSet,atuamf)
            }
            if (key == "transitionDeletions") {
                val transitionDeletionSet = DeletionSet<Edge<Window,WindowTransition>>()
                transitionDifferentSets.put("DeletionSet",transitionDeletionSet)
                loadTransitionDeletionSet(jsonObject.get(key) as JSONArray, transitionDeletionSet,atuamf)
            }
            if (key == "transitionReplacements") {
                val transitionReplacementSet = ReplacementSet<Edge<Window,WindowTransition>>()
                transitionDifferentSets.put("ReplacementSet",transitionReplacementSet)
                loadTransitionReplacementSet(jsonObject.get(key) as JSONArray, transitionReplacementSet,atuamf)

            }
            if (key == "transitionRetainers") {
                val transitionRetainSet = RetainerSet<Edge<Window,WindowTransition>>()
                transitionDifferentSets.put("RetainerSet",transitionRetainSet)
                loadTransitionRetainSet(jsonObject.get(key) as JSONArray, transitionRetainSet,atuamf)
            }
        }
    }

    private fun loadTransitionRetainSet(jsonArray: JSONArray, transitionRetainSet: RetainerSet<Edge<Window, WindowTransition>>, atuamf: ATUAMF) {
        for (item in jsonArray) {
            val replacementJson = item as JSONObject
            val oldTransitionFullId = replacementJson.get("oldElement").toString()
            val newTransitionFullId = replacementJson.get("newElement").toString()
            val oldTransition = parseTransition(oldTransitionFullId,atuamf,WindowManager.instance.baseModelWindows)
            val newTransition = parseTransition(newTransitionFullId,atuamf,WindowManager.instance.updatedModelWindows)
            if (oldTransition != null && newTransition!=null) {
                transitionRetainSet.replacedElements.add(Replacement(oldTransition,newTransition))
                newTransition.label.input.eventHandlers.addAll(oldTransition.label.input.eventHandlers)
            } else {
                val oldInput = parseInput(oldTransitionFullId,atuamf,WindowManager.instance.baseModelWindows)
                val newInput = parseInput(newTransitionFullId,atuamf,WindowManager.instance.updatedModelWindows)
                if (oldInput!=null && newInput!=null) {
                    newInput.eventHandlers.addAll(oldInput.eventHandlers)
                }
            }
        }
    }

    private fun loadTransitionReplacementSet(jsonArray: JSONArray, transitionReplacementSet: ReplacementSet<Edge<Window, WindowTransition>>, atuamf: ATUAMF) {
        for (item in jsonArray) {
            val replacementJson = item as JSONObject
            val oldTransitionFullId = replacementJson.get("oldElement").toString()
            val newTransitionFullId = replacementJson.get("newElement").toString()
            val oldTransition = parseTransition(oldTransitionFullId,atuamf,WindowManager.instance.baseModelWindows)
            val newTransition = parseTransition(newTransitionFullId,atuamf,WindowManager.instance.updatedModelWindows)
            if (oldTransition != null && newTransition!=null) {
                transitionReplacementSet.replacedElements.add(Replacement(oldTransition,newTransition))
                newTransition.label.input.eventHandlers.addAll(oldTransition.label.input.eventHandlers)
            } else {
                val oldInput = parseInput(oldTransitionFullId,atuamf,WindowManager.instance.baseModelWindows)
                val newInput = parseInput(newTransitionFullId,atuamf,WindowManager.instance.updatedModelWindows)
                if (oldInput!=null && newInput!=null) {
                    newInput.eventHandlers.addAll(oldInput.eventHandlers)
                }
            }
        }
    }

    private fun loadTransitionDeletionSet(jsonArray: JSONArray, transitionDeletionSet: DeletionSet<Edge<Window, WindowTransition>>, atuamf: ATUAMF) {
        jsonArray.forEach {item ->
            var transition : Edge<Window,WindowTransition>? = null
            transition = parseTransition(item.toString(), atuamf, WindowManager.instance.baseModelWindows)
            if (transition!=null) {
                transitionDeletionSet.deletedElements.add(transition)
            }
        }
    }

    private fun loadTransitionAdditionSet(jsonArray: JSONArray, transitionAdditionSet: AdditionSet<Edge<Window, WindowTransition>>, atuamf: ATUAMF) {
        jsonArray.forEach {item ->
            var transition : Edge<Window,WindowTransition>? = null
            transition = parseTransition(item.toString(), atuamf, WindowManager.instance.updatedModelWindows)
            if (transition!=null) {
                transitionAdditionSet.addedElements.add(transition)
            }
        }
    }

    private fun parseTransition(item: String, atuamf: ATUAMF, windowList: ArrayList<Window>): Edge<Window, WindowTransition>? {
        var transition: Edge<Window,WindowTransition>? = null
        val transitionFullId = item.toString()
        val split = transitionFullId.split("_")
        val sourceWindowId = split[0]!!.replace("WIN", "")
        val destWindowId = split[1]!!.replace("WIN", "")
        val action = split[2]!!.replace("-","_")
        if (!Input.isIgnoreEvent(action)) {
            var widgetId = ""
            var ignoreWidget = false
            if (Input.isNoWidgetEvent(action) || split.size < 4) {
                ignoreWidget = true
            } else {
                widgetId = split[3]!!.replace("WID", "")
            }
            val sourceWindow = windowList.find { it.windowId == sourceWindowId }
            val destWindow = windowList.find { it.windowId == destWindowId }
            if (sourceWindow == null || destWindow == null)
                return transition
            val widget = if (ignoreWidget)
                null
            else
                sourceWindow.widgets.find { it.widgetId == widgetId }
            val input = sourceWindow.inputs.find {
                it.eventType == EventType.valueOf(action)
                        && it.widget == widget
            }
            transition = atuamf.wtg.edges(sourceWindow).find {
                it.destination?.data == destWindow
                        && it.label.input == input
            }

        }
        return transition
    }

    private fun parseInput(item: String, atuamf: ATUAMF, windowList: ArrayList<Window>): Input? {
        var input: Input? = null
        val transitionFullId = item.toString()
        val split = transitionFullId.split("_")
        val sourceWindowId = split[0]!!.replace("WIN", "")
        val destWindowId = split[1]!!.replace("WIN", "")
        val action = split[2]!!.replace("-","_")
        if (!Input.isIgnoreEvent(action)) {
            var widgetId = ""
            var ignoreWidget = false
            if (Input.isNoWidgetEvent(action) || split.size < 4) {
                ignoreWidget = true
            } else {
                widgetId = split[3]!!.replace("WID", "")
            }
            val sourceWindow = windowList.find { it.windowId == sourceWindowId }
            val destWindow = windowList.find { it.windowId == destWindowId }
            if (sourceWindow == null || destWindow == null)
                return input
            val widget = if (ignoreWidget)
                null
            else
                sourceWindow.widgets.find { it.widgetId == widgetId }
            input = sourceWindow.inputs.find {
                it.eventType == EventType.valueOf(action)
                        && it.widget == widget
            }
        }
        return input
    }
    private fun loadWidgetDifferences(jsonObject: JSONObject) {
        jsonObject.keys().forEach {key->
            if (key == "widgetAdditions") {
                val widgetAdditionSet = AdditionSet<EWTGWidget>()
                widgetDifferentSets.put("AdditionSet",widgetAdditionSet)
                loadWidgetAdditionSet(jsonObject.get(key) as JSONArray, widgetAdditionSet)
            }
            if (key == "widgetDeletions") {
                val widgetDeletionSet = DeletionSet<EWTGWidget>()
                widgetDifferentSets.put("DeletionSet",widgetDeletionSet)
                loadWidgetDeletionSet(jsonObject.get(key) as JSONArray, widgetDeletionSet)
            }
            if (key == "widgetReplacements") {
                val widgetReplacementSet = ReplacementSet<EWTGWidget>()
                widgetDifferentSets.put("ReplacementSet", widgetReplacementSet)
                loadWidgetReplacementSet(jsonObject.get(key) as JSONArray, widgetReplacementSet)
            }
            if (key == "widgetRetainers") {
                val widgetRetainerSet = RetainerSet<EWTGWidget>()
                widgetDifferentSets.put("RetainerSet",widgetRetainerSet)
                loadWidgetRetainerSet(jsonObject.get(key) as JSONArray, widgetRetainerSet)
            }
        }
    }

    private fun loadWidgetRetainerSet(jsonArray: JSONArray, widgetRetainerSet: RetainerSet<EWTGWidget>) {
        for (item in jsonArray) {
            val replacementJson = item as JSONObject
            val oldWidgetFullId = replacementJson.get("oldElement").toString()
            val newWidgetFullId = replacementJson.get("newElement").toString()
            val oldWindowId = oldWidgetFullId.split("_")[0]!!.replace("WIN","")
            val oldWidgetId = oldWidgetFullId.split("_")[1]!!.replace("WID","")
            val oldWindow = WindowManager.instance.baseModelWindows.find { it.windowId == oldWindowId }
            if (oldWindow==null) {
               log.warn("Cannot get the old window with id $oldWindowId")
                continue
            }
            val oldWidget =  oldWindow.widgets.find { it.widgetId == oldWidgetId }
            if (oldWidget == null) {
                log.warn("Cannot get the old widget with id $oldWidgetId")
                continue
            }
            val newWindowId = newWidgetFullId.split("_")[0]!!.replace("WIN","")
            val newWidgetId = newWidgetFullId.split("_")[1]!!.replace("WID","")
            val newWindow = WindowManager.instance.updatedModelWindows.find { it.windowId == newWindowId }
            if (newWindow==null) {
                throw Exception("Cannot get the window with id $newWidgetId")
            }
            val newWidget =  newWindow.widgets.find { it.widgetId == newWidgetId }
            if (newWidget == null) {
                continue
            }
            widgetRetainerSet.replacedElements.add(Replacement<EWTGWidget>(oldWidget,newWidget))
        }
    }

    private fun loadWidgetReplacementSet(jsonArray: JSONArray, widgetReplacementSet: ReplacementSet<EWTGWidget>) {
        for (item in jsonArray) {
            val replacementJson = item as JSONObject
            val oldWidgetFullId = replacementJson.get("oldElement").toString()
            val newWidgetFullId = replacementJson.get("newElement").toString()
            val oldWindowId = oldWidgetFullId.split("_")[0]!!.replace("WIN","")
            val oldWidgetId = oldWidgetFullId.split("_")[1]!!.replace("WID","")
            val oldWindow = WindowManager.instance.baseModelWindows.find { it.windowId == oldWindowId }
            if (oldWindow==null) {
                log.warn("Cannot get the old window with id $oldWindowId")
                continue
            }
            val oldWidget =  oldWindow.widgets.find { it.widgetId == oldWidgetId }
            if (oldWidget == null) {
               log.warn("Cannot not get the old widget with id $oldWidgetId")
                continue
            }
            val newWindowId = newWidgetFullId.split("_")[0]!!.replace("WIN","")
            val newWidgetId = newWidgetFullId.split("_")[1]!!.replace("WID","")
            val newWindow = WindowManager.instance.updatedModelWindows.find { it.windowId == newWindowId }
            if (newWindow==null) {
                log.warn("Cannot get the old window with id $newWidgetId")
            } else {

                val newWidget =  newWindow.widgets.find { it.widgetId == newWidgetId }
                if (newWidget == null) {
                    continue
                }
                widgetReplacementSet.replacedElements.add(Replacement<EWTGWidget>(oldWidget,newWidget))
            }
        }

    }

    private fun loadWidgetDeletionSet(jsonArray: JSONArray, widgetDeletionSet: DeletionSet<EWTGWidget>) {
        jsonArray.forEach {item ->
            val widgetFullId = item.toString()
            val windowId = widgetFullId.split("_")[0]!!.replace("WIN","")
            val widgetId = widgetFullId.split("_")[1]!!.replace("WID","")
            val window = WindowManager.instance.baseModelWindows.find { it.windowId == windowId }
            if (window==null) {
                log.warn("Cannot get the old window with id $windowId")
            } else{

                val widget =  window.widgets.find { it.widgetId == widgetId }
                if (widget != null) {
                    widgetDeletionSet.deletedElements.add(widget)
                }
            }

        }
    }

    private fun loadWidgetAdditionSet(jsonArray: JSONArray, widgetAdditionSet: AdditionSet<EWTGWidget>) {
        jsonArray.forEach {item ->
            val widgetFullId = item.toString()
            val windowId = widgetFullId.split("_")[0]!!.replace("WIN","")
            val widgetId = widgetFullId.split("_")[1]!!.replace("WID","")
            val window = WindowManager.instance.updatedModelWindows.find { it.windowId == windowId }
            if (window==null) {
                log.warn("Cannot get the old window with id $windowId")
            } else {
                val widget = window.widgets.find { it.widgetId == widgetId }
                if (widget != null) {
                    widgetAdditionSet.addedElements.add(widget)
                }
            }

        }
    }

    private fun loadWindowDifferences(jsonObject: JSONObject) {
        jsonObject.keys().forEach {key->
            if (key == "windowAdditions") {
                val windowAdditionSet = AdditionSet<Window>()
                windowDifferentSets.put("AdditionSet",windowAdditionSet)
                loadWindowAdditionSet(jsonObject.get(key) as JSONArray, windowAdditionSet)
            }
            if (key == "windowDeletions") {
                val windowDeletionSet = DeletionSet<Window>()
                windowDifferentSets.put("DeletionSet",windowDeletionSet)
                loadWindowDeletionSet(jsonObject.get(key) as JSONArray, windowDeletionSet)
            }
            if (key == "windowReplacements") {
                val windowReplacementSet = ReplacementSet<Window>()
                windowDifferentSets.put("ReplacementSet",windowReplacementSet)
                loadWindowReplacementSet(jsonObject.get(key) as JSONArray, windowReplacementSet)
            }
            if (key == "windowRetainers") {
                val windowRetainerSet = RetainerSet<Window>()
                windowDifferentSets.put("RetainerSet",windowRetainerSet)
                loadWindowRetainerSet(jsonObject.get(key) as JSONArray, windowRetainerSet)
            }
        }
    }

    private fun loadWindowRetainerSet(jsonArray: JSONArray, windowRetainerSet: RetainerSet<Window>) {
        jsonArray.forEach {item ->
            val replacementJson = item as JSONObject
            val windowOldId = replacementJson.get("oldElement").toString().replace("WIN","")
            val windowNewId = replacementJson.get("newElement").toString().replace("WIN","")
            val oldWindow = WindowManager.instance.baseModelWindows.find { it.windowId == windowOldId }
            val newWindow = WindowManager.instance.updatedModelWindows.find { it.windowId == windowNewId }
            if (oldWindow != null) {
                if (newWindow==null) {
                    log.warn ("Cannot get the new window with id $windowNewId")
                } else
                    windowRetainerSet.replacedElements.add(Replacement<Window>(oldWindow,newWindow))
            }
            else {
                log.warn("Cannot get the old window with id $windowOldId")
            }


        }
    }

    private fun loadWindowReplacementSet(jsonArray: JSONArray, windowReplacementSet: ReplacementSet<Window>) {
        jsonArray.forEach {item ->
            val replacementJson = item as JSONObject
            val windowOldId = replacementJson.get("oldElement").toString().replace("WIN","")
            val windowNewId = replacementJson.get("newElement").toString().replace("WIN","")
            val oldWindow = WindowManager.instance.baseModelWindows.find { it.windowId == windowOldId }
            val newWindow = WindowManager.instance.updatedModelWindows.find { it.windowId == windowNewId }
            if (oldWindow != null && newWindow != null) {
                windowReplacementSet.replacedElements.add(Replacement<Window>(oldWindow,newWindow))
            } else {
                if (oldWindow == null) {
                    log.warn("Cannot get the old window with id $windowOldId")
                }
                if (newWindow == null) {
                    log.warn("Cannot get the new window with id $windowNewId")
                }
            }

        }
    }

    private fun loadWindowDeletionSet(jsonArray: JSONArray, windowDeletionSet: DeletionSet<Window>) {
        jsonArray.forEach {item ->
            val windowId = item.toString().replace("WIN","")
            val window = WindowManager.instance.baseModelWindows.find { it.windowId == windowId }
            if (window==null) {
                log.warn("Cannot get the old window with id $windowId")
            } else
                windowDeletionSet.deletedElements.add(window)
        }
    }

    private fun loadWindowAdditionSet(jsonArray: JSONArray, windowAdditionSet: AdditionSet<Window>) {
        jsonArray.forEach {item ->
            val windowId = item.toString().replace("WIN","")
            val window = WindowManager.instance.updatedModelWindows.find { it.windowId == windowId }
            if (window==null) {
                log.warn("Cannot get the new window with id $windowId")
            } else
                windowAdditionSet.addedElements.add(window)
        }
    }

    companion object {
        @JvmStatic
        val log: Logger by lazy { LoggerFactory.getLogger(this::class.java) }
        val instance: EWTGDiff by lazy {
            EWTGDiff()
        }

    }
}