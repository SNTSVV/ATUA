package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.autaut.DSTG.InternetStatus
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.DecisionNode
import org.droidmate.exploration.modelFeatures.autaut.WTG.EventType
import org.droidmate.exploration.modelFeatures.autaut.WTG.Helper
import org.droidmate.exploration.modelFeatures.autaut.WTG.Input
import org.droidmate.exploration.modelFeatures.autaut.WTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Activity
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.ContextMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
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
            loadDSTG(dstgFolderPath,autAutMF)
        }

        private fun loadDSTG(dstgFolderPath: Path,autAutMF: AutAutMF) {
            val abstractStateFilePath: Path = getAbstractStateListFilePath(dstgFolderPath)
            if (!abstractStateFilePath.toFile().exists())
                return
            loadAbstractStates(abstractStateFilePath,dstgFolderPath)
            val dstgFilePath: Path  = getDSTGFilePath(dstgFolderPath)
            if (!dstgFilePath.toFile().exists())
                return
            loadDSTGFile(dstgFilePath,autAutMF)
            //Load abstraction functions
            val abstractionFunctionFolderPath: Path = getAbstractionFunctionFolderPath(dstgFolderPath)
            if (!abstractionFunctionFolderPath.toFile().exists())
                throw Exception("Cannot find AbstractionFunction")
            loadAbstractionFunction(abstractionFunctionFolderPath)
        }

        private fun loadAbstractionFunction(abstractionFunctionFolderPath: Path) {
            loadDecisionNodes(abstractionFunctionFolderPath)
            loadAbandonedAttributeValuationSet(abstractionFunctionFolderPath)
        }

        private fun loadAbandonedAttributeValuationSet(abstractionFunctionFolderPath: Path) {
            val abandonedAVSFile = abstractionFunctionFolderPath.resolve("abandonedAttributeValuationSet.csv")
            val lines: List<String>
            lines = readAllLines(abandonedAVSFile)
            lines.forEach {line->
              parseAbandonedAttributeValuationSet(line)
            }
        }

        private fun parseAbandonedAttributeValuationSet(line: String): AttributeValuationSet {
            val data = splitCSVLineToField(line)
            val activity = data[0]
            val avsId = UUID.fromString(data[1])
            val actionType = AbstractActionType.values().find { it.toString() == data[2] }!!
            val avs = AttributeValuationSet.allAttributeValuationSet.get(activity)!!.get(avsId)!!
            AbstractionFunction.INSTANCE.abandonedAttributePaths.add(Triple(activity,avs,actionType))
            return avs
        }

        private fun loadDecisionNodes(abstractionFunctionFolderPath: Path) {
            var currentDecisionNode: DecisionNode?=null
            var attributePath: AttributePath
            var level = 0
            do {
                level++
                if (currentDecisionNode == null)
                    currentDecisionNode = AbstractionFunction.INSTANCE.root
                else
                    currentDecisionNode = currentDecisionNode.nextNode
                val decisionNodeDataFilePath = abstractionFunctionFolderPath.resolve("DecisionNode_LV$level.csv")
                loadDecisionNode(decisionNodeDataFilePath,currentDecisionNode!!)
            }while (currentDecisionNode!=null && currentDecisionNode.nextNode !=null)

        }

        private fun loadDecisionNode(decisionNodeDataFilePath: Path, currentDecisionNode: DecisionNode) {
            val lines: List<String>
            lines = readAllLines(decisionNodeDataFilePath)
            lines.forEach {
                parseDecisionNode(it,currentDecisionNode)
                //currentDecisionNode.attributePaths.add(Pair(attributePath,attributePath.activity))
            }
        }

        private fun parseDecisionNode(line: String, currentDecisionNode: DecisionNode): AttributePath {
            val data = splitCSVLineToField(line)
            val activity = data[0]
            val attributPathUid = UUID.fromString(data[1])
            val className = data[2]
            val resourceId = data[3]
            val contentDesc = data[4]
            val text = data[5]
            val enable = data[6]
            val selected =data[7]
            val checkable = data[8]
            val isInputField = data[9]
            val clickable = data[10]
            val longClickable = data[11]
            val scrollable = data[12]
            val checked = data[13]
            val isLeaf = data[14]

            val attributes = HashMap<AttributeType,String>()
            addAttributeIfNotNull(AttributeType.className,className,attributes)
            addAttributeIfNotNull(AttributeType.resourceId,resourceId,attributes)
            addAttributeIfNotNull(AttributeType.contentDesc,contentDesc,attributes)
            addAttributeIfNotNull(AttributeType.text,text,attributes)
            addAttributeIfNotNull(AttributeType.enabled,enable,attributes)
            addAttributeIfNotNull(AttributeType.selected,selected,attributes)
            addAttributeIfNotNull(AttributeType.checkable,checkable,attributes)
            addAttributeIfNotNull(AttributeType.isInputField,isInputField,attributes)
            addAttributeIfNotNull(AttributeType.clickable,clickable,attributes)
            addAttributeIfNotNull(AttributeType.longClickable,longClickable,attributes)
            addAttributeIfNotNull(AttributeType.scrollable,scrollable,attributes)
            addAttributeIfNotNull(AttributeType.checked,checked,attributes)
            addAttributeIfNotNull(AttributeType.isLeaf,isLeaf,attributes)

            val parentId = if (data[15] == "null") {
                emptyUUID
            } else {
                UUID.fromString(data[15])
            }
            val childIdStrings = splitCSVLineToField(data[16])
            val childIds = if (childIdStrings.isEmpty() ||
                    (childIdStrings.size == 1 && childIdStrings.single().isBlank())) {
                emptyList<UUID>()
            } else {
                childIdStrings.map { UUID.fromString(it) }
            }
            val attributePath = AttributePath(
                   localAttributes = attributes,
                    parentAttributePathId = parentId,
                    childAttributePathIds = childIds.toHashSet(),
                    activity = activity
            )
            //val child
            val captured = if (data[17].isNotBlank()) {
                data[17].toBoolean()
            } else {
                false
            }
            if (captured) {
                if (!currentDecisionNode.attributePaths.containsKey(activity)) {
                    currentDecisionNode.attributePaths.put(activity, arrayListOf())
                }
                currentDecisionNode.attributePaths.get(activity)!!.add(attributePath)
            }
            assert(attributPathUid == attributePath.attributePathId)
            return attributePath
        }

        private fun getAbstractionFunctionFolderPath(dstgFolderPath: Path): Path {
            return dstgFolderPath.resolve("AbstractionFunction")
        }

        private fun loadDSTGFile(dstgFilePath: Path,autAutMF: AutAutMF) {
            val lines: List<String>
            lines = readAllLines(dstgFilePath)
            lines.forEach {
                parseAbstractTransition(it,autAutMF)
            }

        }

        private fun parseAbstractTransition(line: String, autAutMF: AutAutMF) {
            val data = splitCSVLineToField(line)
            val sourceStateId = UUID.fromString(data[0])

            val sourceState = if(updatedAbstractStateId.containsKey(sourceStateId)) {
                val newUUID = updatedAbstractStateId.get(sourceStateId)
                AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == newUUID }!!
            } else {
                AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == sourceStateId }!!
            }

            val destStateId = UUID.fromString(data[1])
            val destState =if(updatedAbstractStateId.containsKey(destStateId)) {
                val newUUID = updatedAbstractStateId.get(destStateId)
                AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == newUUID }!!
            } else {
                AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == destStateId }!!
            }

            val actionType = AbstractActionType.values().first { it.name == data[2] }
            val interactedAVSId = if (data[3] == "null") {
                emptyUUID
            } else {
                UUID.fromString(data[3])
            }
            val actionData = if (data[4] == "null") {
                null
            } else {
                data[4]
            }

            val abstractAction = createAbstractAction(actionType,interactedAVSId,actionData,sourceState)
            val prevWindowId = data[5]
            val prevWindow = WindowManager.instance.allWindows.first{it.windowId == prevWindowId}

            val abstractTransition = sourceState.abstractTransitions.find {
                it.isExplicit() && it.dest == destState && it.abstractAction == abstractAction
                        && it.prevWindow == prevWindow
            }

            if (abstractTransition == null) {
                val newAbstractTransition = AbstractTransition(
                        source = sourceState,
                        dest = destState,
                        fromWTG = false,
                        prevWindow = prevWindow,
                        isImplicit = false,
                        abstractAction = abstractAction
                )
                autAutMF.abstractTransitionGraph.add(sourceState,destState,newAbstractTransition)
               AbstractStateManager.instance.addImplicitAbstractInteraction(
                        abstractTransition = newAbstractTransition,
                        currentState = null,
                        currentAbstractState = destState,
                        prevAbstractState = sourceState,
                        edgeCondition = hashMapOf() ,
                        prevWindow = null
                )
            }

        }

        private fun createAbstractAction(actionType: AbstractActionType, interactedAVSId: UUID, actionData: String?, abstractState: AbstractState): AbstractAction {
            if (interactedAVSId == emptyUUID) {
                val abstractAction = AbstractAction(
                        actionType = actionType,
                        attributeValuationSet = null,
                        extra = actionData
                )
                return abstractAction
            }
            val avs = abstractState.attributeValuationSets.first { it.avsId == interactedAVSId }
            val abstractAction = AbstractAction(
                    actionType = actionType,
                    attributeValuationSet = avs,
                    extra = actionData
            )
            return abstractAction
        }

        private fun readAllLines(dstgFilePath: Path): List<String> {
            val lines : List<String>
            dstgFilePath.toFile().let { file ->
                lines = BufferedReader(FileReader(file)).use {
                    it.lines().skip(1).toList()
                }
            }
            return lines
        }

        private fun loadAbstractStates(abstractStateFilePath: Path,dstgFolderPath: Path) {
            val lines: List<String> = readAllLines(abstractStateFilePath)

            lines.forEach { line ->
                loadAbstractState(line,dstgFolderPath)
            }
        }

        val updatedAbstractStateId = HashMap<UUID, UUID>()
        private fun loadAbstractState(line: String, dstgFolderPath: Path) {
            val data = splitCSVLineToField(line)
            val uuid = UUID.fromString(data[0])
            val activity = data[1]
            val windowId = data[2]
            val window = WindowManager.instance.allWindows.find { it.windowId == windowId }
            if (window == null) {
                throw Exception("Cannot find window $windowId")
            }
            val rotation = Rotation.values().find { it.name == data[3] }!!
            val internetStatus = InternetStatus.values().find { it.name == data[4] }!!
            val isHomeScreen = data[5].toBoolean()
            val isRequestRuntimePermissionDialogBox = data[6].toBoolean()
            val isAppHasStoppedDialogBox = data[7].toBoolean()
            val isOutOfApp = data[8].toBoolean()
            val isOpenningKeyboard = data[9].toBoolean()
            val hasOptionsMenu = data[10].toBoolean()
            val guiStates = data[11]
            val widgetIdMapping: HashMap<AttributeValuationSet,List<String>> = HashMap()
            val attributeValuationSets = loadAttributeValuationSets(uuid, dstgFolderPath,widgetIdMapping, activity)
            val widgetMapping = HashMap<AttributeValuationSet,ArrayList<EWTGWidget>>()
            widgetIdMapping.forEach { avs, widgetIds ->
                val ewtgWidgets = ArrayList<EWTGWidget>()
                widgetIds.forEach { widgetId ->
                    val widget = window.widgets.find { it.widgetId == widgetId }
                    if (widget!=null) {
                        ewtgWidgets.add(widget)
                    } else {
                        AutAutMF.log.debug("Cannot find WidgetId $widgetId in $window")
                    }

                }
                widgetMapping.put(avs,ewtgWidgets)
            }
            val abstractState = AbstractState(
                    activity = activity,
                    isOutOfApplication = isOutOfApp,
                    isHomeScreen = isHomeScreen,
                    internet = internetStatus,
                    rotation = rotation,
                    isOpeningKeyboard = isOpenningKeyboard,
                    isRequestRuntimePermissionDialogBox = isRequestRuntimePermissionDialogBox,
                    isAppHasStoppedDialogBox = isAppHasStoppedDialogBox,
                    attributeValuationSets = ArrayList(attributeValuationSets),
                    EWTGWidgetMapping = widgetMapping,
                    hasOptionsMenu = hasOptionsMenu,
                    window = window,
                    loaded = true
            )
            if (abstractState.abstractStateId != uuid) {
                updatedAbstractStateId.put(uuid,abstractState.abstractStateId)
            }
            AbstractStateManager.instance.ABSTRACT_STATES.add(abstractState)
            AbstractStateManager.instance.initAbstractInteractions(abstractState,null)
        }

        private fun loadAttributeValuationSets(uuid: UUID, dstgFolderPath: Path, widgetMapping: HashMap<AttributeValuationSet, List<String>>, activity: String): List<AttributeValuationSet> {
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
                val attributeValuationSet = createAttributeValuationSet(attributeValuationSetRawDatum.value,attributeValuationSets,activity, widgetMapping)
                assert(uuid == attributeValuationSet.avsId )
            }
            return attributeValuationSets
        }

       private fun createAttributeValuationSet(attributeValuationSetRawDatum: List<String>,
                                               attributeValuationSets: ArrayList<AttributeValuationSet>,
                                               activity: String,
                                               widgetMapping: HashMap<AttributeValuationSet, List<String>>):AttributeValuationSet {
            //TODO("Not implemented")
            val parentAVSId = if (attributeValuationSetRawDatum[14] !="null")
                    UUID.fromString(attributeValuationSetRawDatum[14])
            else
                emptyUUID
           val uuid = UUID.fromString(attributeValuationSetRawDatum[0])
            val attributes: HashMap<AttributeType,String> = parseAttributes(attributeValuationSetRawDatum)
            val childAVSIds = splitCSVLineToField(attributeValuationSetRawDatum[15])
            val childAVSs = HashSet<UUID>()
           if (!(childAVSIds.size==1 && (childAVSIds.single().isBlank() || childAVSIds.single()=="\"\""))) {
               childAVSIds.forEach { avsId ->
                   childAVSs.add(UUID.fromString(avsId))
               }
           }
            val cardinality = Cardinality.values().find { it.name == attributeValuationSetRawDatum[16] }!!
            val attributeValuationSet = AttributeValuationSet(localAttributes = attributes,
                    parentAttributeValuationSetId = parentAVSId,
                    childAttributeValuationSetIds = childAVSs,
                    cardinality = cardinality,
                    activity = activity)
            if (!AttributeValuationSet.allAttributeValuationSet.containsKey(activity)) {
                AttributeValuationSet.allAttributeValuationSet.put(activity, HashMap())
            }
            AttributeValuationSet.allAttributeValuationSet[activity]!!.put(attributeValuationSet.avsId,attributeValuationSet)
            val captured = attributeValuationSetRawDatum[17]
            if (captured.toBoolean()) {
                attributeValuationSets.add(attributeValuationSet)
            }
            val ewtgWidgetIds = splitCSVLineToField(attributeValuationSetRawDatum[18])
            if (!(ewtgWidgetIds.size == 1 &&
                            (ewtgWidgetIds.single().isBlank() || ewtgWidgetIds.single() == "null" ))) {
                widgetMapping.put(attributeValuationSet, ewtgWidgetIds)
            }
           return attributeValuationSet
        }

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
           return dstgFolderPath.resolve("DSTG.csv")
        }

        private fun getAbstractStateListFilePath(dstgFolderPath: Path): Path {
           return dstgFolderPath.resolve("AbstractStateList.csv")
        }

        private fun getDSTGFolderPath(modelPath: Path): Path {
            return modelPath.resolve("DSTG")
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
                        window.inputs.find { it.eventType.toString() == eventType }
                else
                {
                    window.inputs.find { it.eventType.toString() == eventType && it.widget == widget }
                }
                val event = if (existingEvent == null) {
                    createNewEvent(data,widget,window)
                } else
                    existingEvent
                updateHandlerAndModifiedMethods(event, data,widget,window,autautMF)
            }
        }

        private fun updateHandlerAndModifiedMethods(event: Input, data: List<String>, widget: EWTGWidget?, window: Window, autMF: AutAutMF) {
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
            if(event.modifiedMethods.isNotEmpty() && !autMF.allTargetInputs.contains(event)) {
                autMF.allTargetInputs.add(event)
                if (window !is Launcher && window !is OutOfApp) {
                    if (!autMF.allTargetWindow_ModifiedMethods.containsKey(window)) {
                        autMF.allTargetWindow_ModifiedMethods.put(window, hashSetOf())
                    }
                    val window_ModifiedMethods = autMF.allTargetWindow_ModifiedMethods.get(window)!!
                    event.modifiedMethods.forEach { m, _ ->
                        window_ModifiedMethods.add(m)
                    }
                }
            }
        }

        private fun createNewEvent(data: List<String>, widget: EWTGWidget?, window: Window): Input {
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
                    val newWidget: EWTGWidget = createNewWidget(data, window)
                }
            }

        }

        fun splitCSVLineToField(line: String): List<String> {
            val data = ArrayList(line.split(";(?=([^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()))
            val result = ArrayList<String>()
            data.forEach {
                if (it.startsWith("\"") && it.endsWith("\"")) {
                    val newString = it.trim('"')
                    result.add(newString)
                } else {
                    result.add(it)
                }
            }
            return result
        }

        private fun createNewWidget(data: List<String>, window: Window): EWTGWidget {
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
            val widget = EWTGWidget(
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
            return ewtgFolderPath.resolve("EWTG_WindowList.csv")
        }

        private fun getEWTGFolderPath(modelPath: Path): Path {
            return modelPath.resolve("EWTG")
        }
    }
}