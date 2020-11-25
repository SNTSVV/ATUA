package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.autaut.DSTG.InternetStatus
import org.droidmate.exploration.modelFeatures.autaut.WTG.EventType
import org.droidmate.exploration.modelFeatures.autaut.WTG.Helper
import org.droidmate.exploration.modelFeatures.autaut.WTG.Input
import org.droidmate.exploration.modelFeatures.autaut.WTG.StaticWidget
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Activity
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.ContextMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.explorationModel.emptyUUID
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.streams.toList

class AutAutModelLoader {


    companion object {
        fun loadModel(modelPath: Path, autAutMF: AutAutMF) {
            if (!Files.exists(modelPath))
                return
            val ewtgFolderPath: Path = getEWTGFolderPath(modelPath)
            loadEWTG(ewtgFolderPath,autAutMF)
            val dstgFolderPath: Path = getDSTGFolderPath(modelPath)
            loadDSTG(dstgFolderPath)
        }

        private fun loadDSTG(dstgFolderPath: Path) {
            val abstractStateFilePath: Path = getAbstractStateListFilePath(dstgFolderPath)
            if (!abstractStateFilePath.toFile().exists())
                return
            loadAbstractStates(abstractStateFilePath)
            val dstgFilePath: Path  = getDSTGFilePath(dstgFolderPath)
            if (!dstgFilePath.toFile().exists())
                return
            loadDSTGFile(dstgFilePath)
        }

        private fun loadDSTGFile(dstgFilePath: Path) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        private fun loadAbstractStates(abstractStateFilePath: Path) {
            val lines: List<String>
            abstractStateFilePath.toFile().let {file ->
                lines = BufferedReader(FileReader(file)).use {
                    it.lines().skip(1).toList()
                }
            }

            lines.forEach { line ->
                loadAbstractState(line,abstractStateFilePath)
            }
        }

        private fun loadAbstractState(line: String, dstgFolderPath: Path) {
            val data = splitCSVLineToField(line)
            val uuid = UUID.fromString(data[0])
            val activity = data[1]
            val windowId = data[2]
            val rotation = Rotation.values().find { it.name == data[3] }
            val internetStatus = InternetStatus.values().find { it.name == data[4] }
            val isHomeScreen = data[5].toBoolean()
            val isRequestRuntimePermissionDialogBox = data[6].toBoolean()
            val isAppHasStoppedDialogBox = data[7].toBoolean()
            val isOutOfApp = data[8].toBoolean()
            val isOpenningKeyboard = data[9].toBoolean()
            val hasOptionsMenu = data[10].toBoolean()
            val guiStates = data[11]

            val attributeValuationSets = loadAttributeValuationSets(uuid, dstgFolderPath)


        }

        private fun loadAttributeValuationSets(uuid: UUID, dstgFolderPath: Path): List<AttributeValuationSet> {
            val attributeValuationSets = ArrayList<AttributeValuationSet>()
            val abstractStateFilePath = dstgFolderPath.resolve("AbstractStates").resolve("AbstractState_$uuid.csv")
            if (!Files.exists(abstractStateFilePath)) {
                throw Exception("Cannot find the AbstractState $uuid")
            }
            val lines: List<String>
            abstractStateFilePath.toFile().let {file ->
                lines = BufferedReader(FileReader(file)).use {
                    it.lines().skip(1).toList()
                }
            }
            val attributeValuationSetRawData = HashMap<UUID, List<String>>()
            lines.forEach { line ->
                val rawData = splitCSVLineToField(line)
                val avsId = UUID.fromString(rawData[0])
                attributeValuationSetRawData.put(avsId,rawData)
            }
            for (attributeValuationSetRawDatum in attributeValuationSetRawData) {
                val uuid = attributeValuationSetRawDatum.key
                if (attributeValuationSets.any { it.avsId == uuid }) {
                    continue
                }
                //createAttributeValuationSet(attributeValuationSetRawDatum.value,attributeValuationSetRawData,attributeValuationSets)
            }
            return attributeValuationSets
        }

/*        private fun createAttributeValuationSet(attributeValuationSetRawDatum: List<String>, attributeValuationSetRawData: HashMap<UUID, List<String>>, attributeValuationSets: ArrayList<AttributeValuationSet>):AttributeValuationSet {
            //TODO("Not implemented")
            val parentAVSId = if (attributeValuationSetRawDatum[14] !="null")
                    UUID.fromString(attributeValuationSetRawDatum[14])
            else
                emptyUUID
            var parentAVS = if (parentAVSId == emptyUUID) {
                null
            } else {
                attributeValuationSets.find { it.avsId == parentAVSId }
            }
            if (parentAVS == null) {
                val parentAVSRawDatum = attributeValuationSetRawData.get(parentAVSId)!!
                parentAVS = createAttributeValuationSet(parentAVSRawDatum,attributeValuationSetRawData,attributeValuationSets)
            }
            val attributes: HashMap<AttributeType,String> = parseAttributes(attributeValuationSetRawDatum)
            val childAVSIds = splitCSVLineToField(attributeValuationSetRawDatum[15])
            val childAVSs = if (childAVSIds.size == 1 && childAVSIds[0] == "null") {
                null
            } else {
                ArrayList<AttributeValuationSet>()
            }
            if (childAVSs != null) {
                childAVSIds.forEach { avsId ->
                    val avs = attributeValuationSets.find { it.avsId.toString() == avsId }
                    if (avs!=null) {
                        childAVSs.add(avs)
                    } else {
                        val childAVSRawDatum = attributeValuationSetRawData.get(UUID.fromString(avsId))!!
                        val childAVS = createAttributeValuationSet(childAVSRawDatum,attributeValuationSetRawData, attributeValuationSets)
                        //val childAttributePath = createChild
                    }
                }
            }
            val cardinality = Cardinality.values().find { it.name == attributeValuationSetRawDatum[16] }!!
            val captured = attributeValuationSetRawDatum[17]
            val ewtgWidgetId = attributeValuationSetRawDatum[18]
        }*/

        private fun parseAttributes(attributeValuationSetRawDatum: List<String>): HashMap<AttributeType, String> {
            val attributes = HashMap<AttributeType,String>()

            val className = attributeValuationSetRawDatum[1]
            val resourceId = attributeValuationSetRawDatum[2]
            val contentDesc = attributeValuationSetRawDatum[3]
            val text = attributeValuationSetRawDatum[4] //TODO consider "null" text and null value
            val enabled = attributeValuationSetRawDatum[5]
            val selected = attributeValuationSetRawDatum[6]
            val checkable = attributeValuationSetRawDatum[7]
            val isInputField = attributeValuationSetRawDatum[8]
            val clickable = attributeValuationSetRawDatum[9]
            val longClickable = attributeValuationSetRawDatum[10]
            val scrollable = attributeValuationSetRawDatum[11]
            val checked = attributeValuationSetRawDatum[12]
            val isLeaf = attributeValuationSetRawDatum[13]

            addAttributeIfNotNull(AttributeType.className, className,attributes)
            addAttributeIfNotNull(AttributeType.resourceId, resourceId,attributes)
            addAttributeIfNotNull(AttributeType.contentDesc, contentDesc,attributes)
            addAttributeIfNotNull(AttributeType.text, text,attributes)
            addAttributeIfNotNull(AttributeType.enabled, enabled,attributes)
            addAttributeIfNotNull(AttributeType.selected, selected,attributes)
            addAttributeIfNotNull(AttributeType.checkable, checkable,attributes)
            addAttributeIfNotNull(AttributeType.isInputField, isInputField,attributes)
            addAttributeIfNotNull(AttributeType.clickable, clickable,attributes)
            addAttributeIfNotNull(AttributeType.longClickable, longClickable,attributes)
            addAttributeIfNotNull(AttributeType.scrollable, scrollable,attributes)
            addAttributeIfNotNull(AttributeType.checked, checked,attributes)
            addAttributeIfNotNull(AttributeType.isLeaf, isLeaf,attributes)

            return attributes
        }

        private fun addAttributeIfNotNull(attributeType: AttributeType, attributeValue: String, attributes: HashMap<AttributeType,String>) {
            if (attributeValue!= "null" ) {
                attributes.put(attributeType,attributeValue)
            }
        }


        private fun getDSTGFilePath(dstgFolderPath: Path): Path {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        private fun getAbstractStateListFilePath(dstgFolderPath: Path): Path {
           return dstgFolderPath.resolve("AbstractStateList.csv")
        }

        private fun getDSTGFolderPath(modelPath: Path): Path {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }


        private fun loadEWTG(ewtgFolderPath: Path, autAutMF: AutAutMF) {
            val ewtgWindowsFilePath: Path = getEWTGWindowsFilePath(ewtgFolderPath)
            if (!Files.exists(ewtgFolderPath))
                return
            parseEWTGListFile(ewtgWindowsFilePath,autAutMF)
        }

        private fun parseEWTGListFile(ewtgWindowsFilePath: Path,autAutMF: AutAutMF) {
            val lines: List<String>
            ewtgWindowsFilePath.toFile().let {file ->
                lines = BufferedReader(FileReader(file)).use {
                    it.lines().skip(1).toList()
                }
            }

            lines.forEach { line ->
                loadWindow(line,ewtgWindowsFilePath.parent,autAutMF)
            }
        }

        private fun loadWindow(line: String, ewtgFolderPath: Path,autAutMF: AutAutMF) {
            val data = splitCSVLineToField(line)
            val windowId = data[0]
            val createdAtRuntime = data[4].toBoolean()
            if (createdAtRuntime) {
                val newWindow: Window = createNewWindow(data)
            }
            val window = WindowManager.instance.allWindows.find { it.windowId == windowId }
            if (window == null) {
                AutAutMF.log.debug("Cannot find window")
            } else {
                loadWindowStructure(window, ewtgFolderPath)
                loadWindowEvents(window, ewtgFolderPath,autAutMF)
            }
        }

        private fun loadWindowEvents(window: Window, ewtgFolderPath: Path, autautMF: AutAutMF) {
            val eventFilePath = ewtgFolderPath.resolve("WindowsEvents").resolve("Events_${window.windowId}.csv")
            if (!Files.exists(eventFilePath)) {
                AutAutMF.log.debug("Window $window 'events does not exist")
                return
            }
            val lines: List<String>
            eventFilePath.toFile().let {file ->
                lines = BufferedReader(FileReader(file)).use {
                    it.lines().skip(1).toList()
                }
            }
            lines.forEach { line ->
                val data = splitCSVLineToField(line)
                val eventType = data[0]
                val widgetId = data[1]
                val widget = if (widgetId != "null") {
                    window.widgets.find { it.widgetId == widgetId}
                } else
                    null
                if (widgetId != "null" && widget == null)
                    throw Exception("Cannot find widget $widgetId in the window $window ")
                val existingEvent = if (widgetId == "null")
                        window.events.find { it.eventType.toString() == eventType }
                else
                {
                    window.events.find { it.eventType.toString() == eventType && it.widget == widget }
                }
                val event = if (existingEvent == null) {
                    createNewEvent(data,widget,window)
                } else
                    existingEvent
                updateHandlerAndModifiedMethods(event, data,widget,window,autautMF)
            }
        }

        private fun updateHandlerAndModifiedMethods(event: Input, data: List<String>, widget: StaticWidget?, window: Window,autMF: AutAutMF) {
            val eventHandlers = splitCSVLineToField(data[4])
            eventHandlers.filter{it.isNotBlank()}. forEach { handler ->
                val methodId = autMF.statementMF!!.getMethodId(handler)
                if (!event.eventHandlers.contains(methodId)) {
                    event.eventHandlers.add(methodId)
                }
            }
            val modifiedMethods = splitCSVLineToField(data[5])
            modifiedMethods.filter { it.isNotBlank() }. forEach { method ->
                val methodId = autMF.statementMF!!.getMethodId(method)
                if (!event.modifiedMethods.contains(methodId)) {
                    event.modifiedMethods.put(methodId,false)
                    val updatedStatements = autMF.statementMF!!.getMethodStatements(methodId)
                    updatedStatements.forEach {
                        event.modifiedMethodStatement.put(it,false)
                    }

                }
            }
        }

        private fun createNewEvent(data: List<String>, widget: StaticWidget?, window: Window): Input {
            val eventType = EventType.values().find { it.name == data[0] }
            if (eventType == null) {
                throw Exception("Not supported eventType ${data[0]}")
            }
            val input = Input(eventType = eventType,widget = widget,sourceWindow = window,createdAtRuntime = true,eventHandlers = HashSet())
            return input

        }

        private fun loadWindowStructure(window: Window, ewtgFolderPath: Path) {
            val structureFilePath = ewtgFolderPath.resolve("WindowsWidget").resolve("Widgets_${window.windowId}.csv")
            if (!Files.exists(structureFilePath)) {
                AutAutMF.log.debug("Window $window 'structure does not exist")
                return
            }
            val lines: List<String>
            structureFilePath.toFile().let {file ->
                lines = BufferedReader(FileReader(file)).use {
                    it.lines().skip(1).toList()
                }
            }
            lines.forEach { line ->
                val data = splitCSVLineToField(line)
                val widgetId = data[0]
                val widget = window.widgets.find {it.widgetId == widgetId }
                if (widget == null) {
                    //create new widget
                    val newWidget: StaticWidget = createNewWidget(data, window)
                }
            }

        }

        private fun splitCSVLineToField(line: String) = line.split(";(?=([^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())

        private fun createNewWidget(data: List<String>, window: Window): StaticWidget {
            val widgetId = data[0]
            val resourceIdName = data[1]
            val className = data[2]
            val activity = data[3]
            val createdAtRuntime = data[4].toBoolean()
            val attributeValuationSetId = if (data[5] == "null") {
                emptyUUID
            } else {
                UUID.fromString(data[5])
            }
            val widget = StaticWidget(
                    widgetId = widgetId,
                    activity = activity,
                    createdAtRuntime = createdAtRuntime,
                    resourceIdName = resourceIdName,
                    className = className,
                    wtgNode = window,
                    contentDesc = "",
                    text = "",
                    resourceId = "",
                    attributeValuationSetId = attributeValuationSetId
            )

            return widget
        }

        private fun createNewWindow(data: List<String>): Window {
            val windowId = data[0]
            val windowType = data[1]
            val classType = data[2]
            val activityClass = data[3]
            val createdAtRuntime = data[4].toBoolean()
            val portraitDimension: Rectangle = Helper.parseRectangle(data[5])
            val landscapeDimension: Rectangle = Helper.parseRectangle(data[6])
            val portraitKeyboardDimension: Rectangle = Helper.parseRectangle(data[7])
            val landscapeKeyboardDimension: Rectangle =Helper.parseRectangle(data[8])
            val window = when (windowType) {
                "Activity" -> Activity.getOrCreateNode(nodeId = windowId,
                        classType = classType,
                        fromModel = !createdAtRuntime)
                "Dialog" -> Dialog.getOrCreateNode(nodeId = windowId,
                        classType = classType,
                        fromModel = !createdAtRuntime)
                "OptionsMenu" -> OptionsMenu.getOrCreateNode(nodeId = windowId,
                        classType = classType,
                        fromModel = !createdAtRuntime)
                "ContextMenu" -> ContextMenu.getOrCreateNode(nodeId = windowId,
                        classType = classType,
                        fromModel = !createdAtRuntime)
                "OutOfApp" -> OutOfApp.getOrCreateNode(nodeId = windowId, activity = activityClass)
                else -> throw Exception("Error windowType: $windowType")
            }
            window.activityClass = activityClass
            window.portraitDimension = portraitDimension
            window.landscapeDimension = landscapeDimension
            window.portraitKeyboardDimension = portraitKeyboardDimension
            window.landscapeKeyboardDimension = landscapeKeyboardDimension
            return window
        }

        private fun getEWTGWindowsFilePath(ewtgFolderPath: Path): Path {
            return ewtgFolderPath.resolve("Windows.csv")
        }

        private fun getEWTGFolderPath(modelPath: Path): Path {
            return modelPath.resolve("EWTG")
        }
    }
}