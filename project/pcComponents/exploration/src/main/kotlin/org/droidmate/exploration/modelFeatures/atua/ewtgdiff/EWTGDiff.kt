package org.droidmate.exploration.modelFeatures.atua.ewtgdiff

import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

class EWTGDiff private constructor(){
    val windowDifferentSets: HashMap<String,DifferentSet<Window>> = HashMap()
    val widgetDifferentSets: HashMap<String,DifferentSet<EWTGWidget>> = HashMap()
    fun getWidgetAdditions(): List<EWTGWidget> {
        if (widgetDifferentSets.containsKey("AdditionSet")) {
            return (widgetDifferentSets["AdditionSet"]!! as AdditionSet<EWTGWidget>).addedElements
        }
        return emptyList<EWTGWidget>()
    }
    fun getWidgetReplacement(): List<EWTGWidget> {
        if (widgetDifferentSets.containsKey("ReplacementSet")) {
            return (widgetDifferentSets["ReplacementSet"]!! as ReplacementSet<EWTGWidget>).replacedElements.map { it.replacing }
        }
        return emptyList<EWTGWidget>()
    }
    fun loadFromFile(filePath: Path) {
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
        }
        if (windowDifferentSets.containsKey("DeletionSet")) {
            for (deleted in (windowDifferentSets.get("DeletionSet")!! as DeletionSet<Window>).deletedElements) {
                AbstractStateManager.instance.ABSTRACT_STATES.removeIf { it.window == deleted }
            }
            val deletedWindows = (windowDifferentSets.get("DeletionSet")!! as DeletionSet<Window>).deletedElements
            AbstractStateManager.instance.ABSTRACT_STATES.forEach {
                it.abstractTransitions.forEach {
                    if (deletedWindows.contains(it.prevWindow)) {
                        it.prevWindow = null
                    }
                }
            }
        }
        if (windowDifferentSets.containsKey("ReplacementSet")) {
            for (replacement in (windowDifferentSets.get("ReplacementSet")!! as ReplacementSet<Window>).replacedElements) {
                AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == replacement.replaced }.forEach {
                    it.window = replacement.replacing
                }
            }
            val replacements = (windowDifferentSets.get("ReplacementSet")!! as ReplacementSet<Window>).replacedElements.map { Pair(it.replaced,it.replacing) }.toMap()
            AbstractStateManager.instance.ABSTRACT_STATES.forEach {
                it.abstractTransitions.forEach {
                    if (replacements.contains(it.prevWindow)) {
                        it.prevWindow = replacements.get(it.prevWindow)
                    }
                }
            }
        }
        if (windowDifferentSets.containsKey("RetainerSet")) {
            for (replacement in (windowDifferentSets.get("RetainerSet")!! as RetainerSet<Window>).replacedElements) {
                AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == replacement.replaced }.forEach {
                    it.window = replacement.replacing
                }
            }
            val replacements = (windowDifferentSets.get("RetainerSet")!! as RetainerSet<Window>).replacedElements.map { Pair(it.replaced,it.replacing) }.toMap()
            AbstractStateManager.instance.ABSTRACT_STATES.forEach {
                it.abstractTransitions.forEach {
                    if (replacements.contains(it.prevWindow)) {
                        it.prevWindow = replacements.get(it.prevWindow)
                    }
                }
            }
        }

        if (widgetDifferentSets.containsKey("DeletionSet")) {
            for (deleted in (windowDifferentSets.get("DeletionSet")!! as DeletionSet<EWTGWidget>).deletedElements) {
                AbstractStateManager.instance.ABSTRACT_STATES.forEach {
                    val toDeleteAvms = it.EWTGWidgetMapping.filter { it.value == deleted }.keys
                    toDeleteAvms.forEach { avm->
                        it.EWTGWidgetMapping.remove(avm)
                    }
                    it.attributeValuationMaps.removeIf { toDeleteAvms.contains(it) }
                }
            }
        }
        if (widgetDifferentSets.containsKey("ReplacementSet")) {
            for (replacement in (widgetDifferentSets.get("ReplacementSet")!! as ReplacementSet<EWTGWidget>).replacedElements) {
                AbstractStateManager.instance.ABSTRACT_STATES.forEach {
                    val toBeReplacedAvms = it.EWTGWidgetMapping.filter { it.value == replacement.replaced }.keys
                    toBeReplacedAvms.forEach { avm->
                        it.EWTGWidgetMapping.put(avm, replacement.replacing)
                    }
                }
            }
        }
        if (widgetDifferentSets.containsKey("RetainerSet")) {
            for (replacement in (widgetDifferentSets.get("RetainerSet")!! as RetainerSet<EWTGWidget>).replacedElements) {
                AbstractStateManager.instance.ABSTRACT_STATES.forEach {
                    val toBeReplacedAvms = it.EWTGWidgetMapping.filter { it.value == replacement.replaced }.keys
                    toBeReplacedAvms.forEach { avm->
                        it.EWTGWidgetMapping.put(avm, replacement.replacing)
                    }
                }
            }
        }
        AbstractStateManager.instance.ABSTRACT_STATES.forEach {
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
                throw Exception("Cannot get the window with id $oldWindowId")
            }
            val oldWidget =  oldWindow.widgets.find { it.widgetId == oldWidgetId }
            if (oldWidget == null) {
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
                throw Exception("Cannot get the window with id $oldWindowId")
            }
            val oldWidget =  oldWindow.widgets.find { it.widgetId == oldWidgetId }
            if (oldWidget == null) {
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
            widgetReplacementSet.replacedElements.add(Replacement<EWTGWidget>(oldWidget,newWidget))
        }

    }

    private fun loadWidgetDeletionSet(jsonArray: JSONArray, widgetDeletionSet: DeletionSet<EWTGWidget>) {
        jsonArray.forEach {item ->
            val widgetFullId = item.toString()
            val windowId = widgetFullId.split("_")[0]!!.replace("WIN","")
            val widgetId = widgetFullId.split("_")[1]!!.replace("WID","")
            val window = WindowManager.instance.baseModelWindows.find { it.windowId == windowId }
            if (window==null) {
                throw Exception("Cannot get the window with id $windowId")
            }
            val widget =  window.widgets.find { it.widgetId == widgetId }
            if (widget != null) {
                widgetDeletionSet.deletedElements.add(widget)
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
                throw Exception("Cannot get the window with id $windowId")
            }
            val widget =  window.widgets.find { it.widgetId == widgetId }
            if (widget != null) {
                widgetAdditionSet.addedElements.add(widget)
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
            if (oldWindow==null) {
                throw Exception("Cannot get the old window with id $windowOldId")
            }
            if (newWindow==null) {
                throw Exception("Cannot get the new window with id $windowNewId")
            }
            windowRetainerSet.replacedElements.add(Replacement<Window>(oldWindow,newWindow))
        }
    }

    private fun loadWindowReplacementSet(jsonArray: JSONArray, windowReplacementSet: ReplacementSet<Window>) {
        jsonArray.forEach {item ->
            val replacementJson = item as JSONObject
            val windowOldId = replacementJson.get("oldElement").toString().replace("WIN","")
            val windowNewId = replacementJson.get("newElement").toString().replace("WIN","")
            val oldWindow = WindowManager.instance.baseModelWindows.find { it.windowId == windowOldId }
            val newWindow = WindowManager.instance.updatedModelWindows.find { it.windowId == windowNewId }
            if (oldWindow==null) {
                throw Exception("Cannot get the window with id $windowOldId")
            }
            if (newWindow==null) {
                throw Exception("Cannot get the window with id $windowNewId")
            }
            windowReplacementSet.replacedElements.add(Replacement<Window>(oldWindow,newWindow))
        }
    }

    private fun loadWindowDeletionSet(jsonArray: JSONArray, windowDeletionSet: DeletionSet<Window>) {
        jsonArray.forEach {item ->
            val windowId = item.toString().replace("WIN","")
            val window = WindowManager.instance.baseModelWindows.find { it.windowId == windowId }
            if (window==null) {
                throw Exception("Cannot get the window with id $windowId")
            }
            windowDeletionSet.deletedElements.add(window)
        }
    }

    private fun loadWindowAdditionSet(jsonArray: JSONArray, windowAdditionSet: AdditionSet<Window>) {
        jsonArray.forEach {item ->
            val windowId = item.toString().replace("WIN","")
            val window = WindowManager.instance.updatedModelWindows.find { it.windowId == windowId }
            if (window==null) {
                throw Exception("Cannot get the window with id $windowId")
            }
            windowAdditionSet.addedElements.add(window)
        }
    }

    companion object {
        val instance: EWTGDiff by lazy {
            EWTGDiff()
        }

    }
}