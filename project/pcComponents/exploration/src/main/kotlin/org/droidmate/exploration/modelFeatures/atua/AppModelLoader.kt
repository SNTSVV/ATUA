package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.atua.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.DecisionNode
import org.droidmate.exploration.modelFeatures.atua.EWTG.EventType
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.exploration.modelFeatures.atua.EWTG.Input
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager
import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowTransition
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Activity
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.ContextMenu
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.FakeWindow
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.modelReuse.ModelVersion
import org.droidmate.explorationModel.emptyUUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.streams.toList

class AppModelLoader {
    companion object {
        @JvmStatic
        val log: Logger by lazy { LoggerFactory.getLogger(this::class.java) }

        fun loadModel(modelPath: Path, autAutMF: ATUAMF) {
            if (!Files.exists(modelPath)) {
                log.debug("Base model does not exist")
                return
            }
            val ewtgFolderPath: Path = getEWTGFolderPath(modelPath)
            log.info("Loading EWTG")
            loadEWTG(ewtgFolderPath,autAutMF)
            WindowManager.instance.baseModelWindows.forEach {
                if (!AbstractStateManager.instance.ABSTRACT_STATES.any { it is VirtualAbstractState
                                && it.window == it }) {
                    val virtualAbstractState = VirtualAbstractState(it.classType, it, it is Launcher)
                    AbstractStateManager.instance.ABSTRACT_STATES.add(virtualAbstractState)
                }
            }
            val dstgFolderPath: Path = getDSTGFolderPath(modelPath)
            log.info("Loading DSTG")
            loadDSTG(dstgFolderPath,autAutMF)
        }

        private fun loadDSTG(dstgFolderPath: Path,autAutMF: ATUAMF) {
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
            //loadAbandonedAttributeValuationSet(abstractionFunctionFolderPath)
        }

        private fun loadAbandonedAttributeValuationSet(abstractionFunctionFolderPath: Path) {
            val abandonedAVSFile = abstractionFunctionFolderPath.resolve("abandonedAttributeValuationSet.csv")
            val lines: List<String>
            lines = readAllLines(abandonedAVSFile)
            lines.forEach {line->
              parseAbandonedAttributeValuationSet(line)
            }
        }

        private fun parseAbandonedAttributeValuationSet(line: String): AttributeValuationMap {
            val data = splitCSVLineToField(line)
            val activity = data[0]
            val avsId = data[1]
            val avs = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.get(activity)!!.get(avsId)!!
            //AbstractionFunction.INSTANCE.abandonedAbstractTransitions.add(Triple(activity,avs,actionType))
            return avs
        }

        private fun loadDecisionNodes(abstractionFunctionFolderPath: Path) {
            var currentDecisionNode: DecisionNode?=null
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
            val parentAttributePaths = HashSet<Pair<UUID,String>>()
            lines.forEach {
                parseDecisionNode(it,currentDecisionNode,parentAttributePaths)
                //currentDecisionNode.attributePaths.add(Pair(attributePath,attributePath.activity))
            }
            parentAttributePaths.forEach {
                val attributePath = AttributePath.getAttributePathById(it.first,it.second)
                if (attributePath == null)
                    throw Exception()
            }
        }

        private fun parseDecisionNode(line: String, currentDecisionNode: DecisionNode, parentAttributePaths: HashSet<Pair<UUID,String>>): AttributePath {
            val data = splitCSVLineToField(line)
            val activity = data[0]
            val attributPathUid = UUID.fromString(data[1])
            val parentId = if (data[2] == "null") {
                emptyUUID
            } else {
                UUID.fromString(data[2])
            }
            val attributes = HashMap<AttributeType,String>()
            var index = 3
            AttributeType.values().toSortedSet().forEach { attributeType ->
                val value = data[index]!!
                addAttributeIfNotNull(attributeType,value,attributes)
                index++
            }
            if (parentId != emptyUUID) {
                parentAttributePaths.add(Pair(parentId,activity))
            }
            val attributePath = AttributePath(
                   localAttributes = attributes,
                    parentAttributePathId = parentId,
                    activity = activity
            )
            //val child
            val captured = if (data[index]!!.isNotBlank()) {
                data[index].toBoolean()
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

        private fun loadDSTGFile(dstgFilePath: Path,autAutMF: ATUAMF) {
            val lines: List<String>
            lines = readAllLines(dstgFilePath)
            var i =0
            while (i<lines.size) {
                parseAbstractTransition(lines[i],autAutMF)
                i+=1
            }
        }

        private fun parseAbstractTransition(line: String, autAutMF: ATUAMF) {
            val data = splitCSVLineToField(line)
            if (data.size<11)
                return
            val sourceStateId = data[0]

            val sourceState = if(updatedAbstractStateId.containsKey(sourceStateId)) {
                val newUUID = updatedAbstractStateId.get(sourceStateId)
                AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == newUUID }
            } else {
                AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == sourceStateId }
            }
            if (sourceState == null)
                return
            val destStateId = data[1]
            val destState =if(updatedAbstractStateId.containsKey(destStateId)) {
                val newUUID = updatedAbstractStateId.get(destStateId)
                AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == newUUID }
            } else {
                AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == destStateId }
            }
            if (destState == null)
                return
            val actionType = AbstractActionType.values().first { it.name == data[2] }
            if (actionType == AbstractActionType.LAUNCH_APP)
                return
            val interactedAVSId = if (data[3] == "null") {
                ""
            } else {
                data[3]
            }
            val actionData = if (data[4] == "null") {
                null
            } else {
                data[4]
            }
            val interactionData = if (data[5] == "null") {
                null
            } else {
                data[5]
            }

            val abstractAction = createAbstractAction(actionType,interactedAVSId,actionData,sourceState)

            /*val prevWindowId = data[6]
            val prevWindow = WindowManager.instance.baseModelWindows.firstOrNull(){it.windowId == prevWindowId}?:WindowManager.instance.updatedModelWindows.firstOrNull(){it.windowId == prevWindowId}*/
            // val prevWindowAbstractStateId = data[6]
            // val prevWindowAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == prevWindowAbstractStateId }
            val guiTransitionIds = data[10]
            if (guiTransitionIds.isBlank()) {
                return
            }
            val dependentAbstractStateIds = splitCSVLineToField(data[11])
            val dependentAbstractStates = ArrayList<AbstractState>()
            dependentAbstractStateIds.forEach { dependentAbstractStateId->
                var dependentAbstractState: AbstractState? = null
                if (dependentAbstractStateId != "null") {
                    dependentAbstractState = if(updatedAbstractStateId.containsKey(dependentAbstractStateId)) {
                        val newUUID = updatedAbstractStateId.get(dependentAbstractStateId)
                        AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == newUUID }
                    } else {
                        AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == dependentAbstractStateId }
                    }
                }
                if (dependentAbstractState!=null)
                    dependentAbstractStates.add(dependentAbstractState)
            }

            val abstractTransition = sourceState.abstractTransitions.find {
                it.abstractAction == abstractAction
                        && it.isImplicit == false
                        && it.source == sourceState
                        && it.dest == destState
                        && it.dependentAbstractStates.containsAll(dependentAbstractStates)
            }
            if (abstractTransition == null) {
                val newAbstractTransition = AbstractTransition(
                        source = sourceState,
                        dest = destState,
                        fromWTG = false,
                        isImplicit = false,
                        abstractAction = abstractAction,
                        modelVersion = ModelVersion.BASE
                )

                autAutMF.dstg.add(sourceState,destState,newAbstractTransition)
                createWindowTransitionFromAbstractInteraction(newAbstractTransition,autAutMF)
                AbstractStateManager.instance.addImplicitAbstractInteraction(
                        abstractTransition = newAbstractTransition,
                        currentState = null
                )
            }

        }

        private fun createAbstractAction(actionType: AbstractActionType, interactedAVSId: String, actionData: String?, abstractState: AbstractState): AbstractAction {
            if (interactedAVSId == "") {
                val abstractAction = AbstractAction(
                        actionType = actionType,
                        attributeValuationMap = null,
                        extra = actionData
                )
                return abstractAction
            }
            val avs = abstractState.attributeValuationMaps.firstOrNull() { it.avmId == interactedAVSId }
            val abstractAction = AbstractAction(
                    actionType = actionType,
                    attributeValuationMap = avs,
                    extra = actionData
            )
            return abstractAction
        }

        private fun createWindowTransitionFromAbstractInteraction(abstractTransition: AbstractTransition, atuaMF: ATUAMF) {
            val eventType = Input.getEventTypeFromActionName(abstractTransition.abstractAction.actionType)
            if (eventType == EventType.fake_action || eventType == EventType.resetApp || eventType == EventType.implicit_launch_event)
                return
            val inputs = if (abstractTransition.source.inputMappings.containsKey(abstractTransition.abstractAction))
                abstractTransition.source.inputMappings.get(abstractTransition.abstractAction)!!
            else
                createNewInput(abstractTransition,atuaMF)
            val prevWindows = abstractTransition.dependentAbstractStates.map { it.window }
            inputs.forEach { input ->
                if (prevWindows.isEmpty())
                    atuaMF.wtg.add(abstractTransition.source.window,abstractTransition.dest.window, WindowTransition(
                            abstractTransition.source.window,
                            abstractTransition.dest.window,
                            input,
                            null))
                else
                    prevWindows.forEach { prevWindow ->
                        atuaMF.wtg.add(abstractTransition.source.window,abstractTransition.dest.window, WindowTransition(
                                abstractTransition.source.window,
                                abstractTransition.dest.window,
                                input,
                                prevWindow))

                    }
            }

        }

        private fun createNewInput(abstractTransition: AbstractTransition, atuaMF: ATUAMF): HashSet<Input> {
            val result = HashSet<Input>()
            val eventType = Input.getEventTypeFromActionName(abstractTransition.abstractAction.actionType)
            val sourceAbstractState = abstractTransition.source
            val destAbstractState = abstractTransition.dest
            if (abstractTransition.abstractAction.attributeValuationMap == null)
            {
                var newInput = Input(
                        eventType = eventType,
                        widget = null,
                        sourceWindow = sourceAbstractState.window,
                        eventHandlers = HashSet(),
                        createdAtRuntime = true
                )
                result.add(newInput)
                newInput.data = abstractTransition.abstractAction.extra
                newInput.eventHandlers.addAll(abstractTransition.handlers.map { it.key })
                if (newInput.eventHandlers.intersect(atuaMF.allTargetHandlers).isNotEmpty()) {
                    atuaMF.allTargetInputs.add(newInput)
                }
                sourceAbstractState.inputMappings.putIfAbsent(abstractTransition.abstractAction, hashSetOf())
                sourceAbstractState.inputMappings.get(abstractTransition.abstractAction)!!.add(newInput)
                AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it == sourceAbstractState }. filter { it.window == sourceAbstractState.window }.forEach {
                    val similarAbstractAction = it.getAvailableActions().find { it == abstractTransition.abstractAction }
                    if (similarAbstractAction != null) {
                        it.inputMappings.put(similarAbstractAction, hashSetOf(newInput!!))
                    }
                }
            }
            else
            {
                val attributeValuationSet = abstractTransition.abstractAction.attributeValuationMap
                if (!sourceAbstractState.EWTGWidgetMapping.containsKey(attributeValuationSet)){
                    val attributeValuationSetId = if (attributeValuationSet.getResourceId().isBlank())
                        ""
                    else
                        attributeValuationSet.avmId
                    // create new static widget and add to the abstract state
                    val ewtgWidget = EWTGWidget(
                            widgetId = attributeValuationSet.avmId.toString(),
                            resourceIdName = attributeValuationSet.getResourceId(),
                            window = sourceAbstractState.window,
                            className = attributeValuationSet.getClassName(),
                            contentDesc = attributeValuationSet.getContentDesc(),
                            text = attributeValuationSet.getText(),
                            createdAtRuntime = true,
                            structure = attributeValuationSetId
                    )
                    ewtgWidget.modelVersion = ModelVersion.BASE
                    sourceAbstractState.EWTGWidgetMapping.put(attributeValuationSet, ewtgWidget)
                    AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it == sourceAbstractState }. filter { it.window == sourceAbstractState.window }.forEach {
                        val similarWidget = it.attributeValuationMaps.find { it == attributeValuationSet }
                        if (similarWidget != null ) {
                            it.EWTGWidgetMapping.put(similarWidget,ewtgWidget)
                        }
                    }
                }
                if (sourceAbstractState.EWTGWidgetMapping.contains(attributeValuationSet))
                {
                    val staticWidget = sourceAbstractState.EWTGWidgetMapping[attributeValuationSet]!!
                    atuaMF.allTargetStaticWidgets.add(staticWidget)
                    val newInput = Input(
                            eventType = eventType,
                            widget = staticWidget,
                            sourceWindow = sourceAbstractState.window,
                            eventHandlers = HashSet(),
                            createdAtRuntime = true
                    )
                    result.add(newInput)
                    newInput.data = abstractTransition.abstractAction.extra
                    newInput.eventHandlers.addAll(abstractTransition.handlers.map { it.key })
                    if (!sourceAbstractState.inputMappings.containsKey(abstractTransition.abstractAction)) {
                        sourceAbstractState.inputMappings.put(abstractTransition.abstractAction, hashSetOf())
                    }
                    sourceAbstractState.inputMappings.get(abstractTransition.abstractAction)!!.add(newInput)
                    AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it == sourceAbstractState }. filter { it.window == sourceAbstractState.window }.forEach {
                        val similarAbstractAction = it.getAvailableActions().find { it == abstractTransition.abstractAction }
                        if (similarAbstractAction != null) {
                            it.inputMappings.put(similarAbstractAction, hashSetOf(newInput))
                        }
                    }
                }
            }
            return result
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

        val updatedAbstractStateId = HashMap<String, String>()
        private fun loadAbstractState(line: String, dstgFolderPath: Path) {
            val data = splitCSVLineToField(line)
            val uuid = data[0]
            val activity = data[1]
            val windowId = data[2]
            val window = WindowManager.instance.baseModelWindows.find { it.windowId == windowId }
            if (window == null) {
               log.debug("Cannot find window $windowId")
                return
            }
            val rotation = Rotation.values().find { it.name == data[3] }!!
            val isMenuOpen = data[4].toBoolean()
            val isHomeScreen = data[5].toBoolean()
            val isRequestRuntimePermissionDialogBox = data[6].toBoolean()
            val isAppHasStoppedDialogBox = data[7].toBoolean()
            val isOutOfApp = data[8].toBoolean()
            val isOpenningKeyboard = data[9].toBoolean()
            val hasOptionsMenu = data[10].toBoolean()
            val guiStates = data[11]
            val hashcode = data[12].toInt()
            val isInitalState = data[13].toBoolean()
            val widgetIdMapping: HashMap<AttributeValuationMap,String> = HashMap()
            val attributeValuationSets = loadAttributeValuationSets(uuid, dstgFolderPath,widgetIdMapping, window.classType)
            val widgetMapping = HashMap<AttributeValuationMap,EWTGWidget>()
            widgetIdMapping.forEach { avs, widgetId ->
                val widget = window.widgets.find { it.widgetId == widgetId }
                if (widget!=null) {
                    widgetMapping.put(avs,widget)
                } else {
                    ATUAMF.log.debug("Cannot find WidgetId $widgetId in $window")
                }
            }
            val abstractState = AbstractState(
                    activity = activity,
                    isOutOfApplication = isOutOfApp,
                    isHomeScreen = isHomeScreen,
                    rotation = rotation,
                    isOpeningKeyboard = isOpenningKeyboard,
                    isRequestRuntimePermissionDialogBox = isRequestRuntimePermissionDialogBox,
                    isAppHasStoppedDialogBox = isAppHasStoppedDialogBox,
                    attributeValuationMaps = ArrayList(attributeValuationSets),
                    EWTGWidgetMapping = widgetMapping,
                    isOpeningMenus = isMenuOpen,
                    window = window,
                    loadedFromModel = true,
                    modelVersion = ModelVersion.BASE
            )
            abstractState.hasOptionsMenu = hasOptionsMenu
            abstractState.updateHashCode()
            assert(abstractState.hashCode == hashcode)
            if (abstractState.abstractStateId != uuid) {
                updatedAbstractStateId.put(uuid,abstractState.abstractStateId)
            }
            AbstractStateManager.instance.ABSTRACT_STATES.add(abstractState)
            AbstractStateManager.instance.initAbstractInteractions(abstractState,null)
            if (isInitalState) {
                abstractState.isInitalState = true
            }
        }


        private fun loadAttributeValuationSets(uuid: String, dstgFolderPath: Path, widgetMapping: HashMap<AttributeValuationMap, String>, activity: String): List<AttributeValuationMap> {
            val attributeValuationSets = ArrayList<AttributeValuationMap>()
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
            val attributeValuationSetRawData = HashMap<String, List<String>>()
            lines.forEach { line ->
                val rawData = splitCSVLineToField(line)
                val avsId = rawData[0]
                attributeValuationSetRawData.put(avsId,rawData)
            }
            for (attributeValuationSetRecord in attributeValuationSetRawData) {
                val avmuuid = attributeValuationSetRecord.key
                if (attributeValuationSets.any { it.avmId == avmuuid }) {
                    continue
                }
                val attributeValuationSet = createAttributeValuationSet(attributeValuationSetRecord.value,attributeValuationSets,activity, widgetMapping)
                assert(avmuuid == attributeValuationSet.avmId )
            }
            return attributeValuationSets
        }
        private fun createAttributeValuationSet(attributeValuationSetRawRecord: List<String>,
                                                attributeValuationMaps: ArrayList<AttributeValuationMap>,
                                                windowClassType: String,
                                                widgetMapping: HashMap<AttributeValuationMap, String>):AttributeValuationMap {
            //TODO("Not implemented")
            val parentAVSId = if (attributeValuationSetRawRecord[1] !="null")
                attributeValuationSetRawRecord[1]
            else
                ""
            var index = 2
            val attributes = HashMap<AttributeType,String>()
            AttributeType.values().toSortedSet().forEach { attributeType ->
                val value = attributeValuationSetRawRecord[index]!!
                addAttributeIfNotNull(attributeType,value,attributes)
                index++
            }
            val cardinality = Cardinality.values().find { it.name == attributeValuationSetRawRecord[index] }!!
            val captured = attributeValuationSetRawRecord[index+1]
            val ewtgWidgetIds = splitCSVLineToField(attributeValuationSetRawRecord[index+2])
            val attributeValuationSet = AttributeValuationMap(
                    avmId = attributeValuationSetRawRecord[0],
                    localAttributes = attributes,
                    parentAVMId = parentAVSId,
                    cardinality = cardinality,
                    windowClassType = windowClassType)
            if (!AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.containsKey(windowClassType)) {
                AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.put(windowClassType, HashMap())
            }
            AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[windowClassType]!!.put(attributeValuationSet.avmId,attributeValuationSet)

            if (captured.toBoolean()) {
                attributeValuationMaps.add(attributeValuationSet)
            }
            if (!(ewtgWidgetIds.size == 1 &&
                            (ewtgWidgetIds.single().isBlank() || ewtgWidgetIds.single() == "null" ))) {
                widgetMapping.put(attributeValuationSet, ewtgWidgetIds.first())
            }
            val hashcode =  (attributeValuationSetRawRecord[index+3]).toInt()
            assert(hashcode == attributeValuationSet.hashCode)
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
            val childrenStructure = attributeValuationSetRawDatum[14]
            val childrenText = attributeValuationSetRawDatum[15]
            val siblingInfo = attributeValuationSetRawDatum[16]

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
            addAttributeIfNotNull(AttributeType.childrenStructure, childrenStructure,attributes)
            addAttributeIfNotNull(AttributeType.childrenText,childrenText,attributes)
            addAttributeIfNotNull(AttributeType.siblingsInfo,siblingInfo,attributes)

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


        private fun loadEWTG(ewtgFolderPath: Path, autAutMF: ATUAMF) {
            val ewtgWindowsFilePath: Path = getEWTGWindowsFilePath(ewtgFolderPath)
            if (!Files.exists(ewtgFolderPath))
                return
            parseEWTGListFile(ewtgWindowsFilePath,autAutMF)
        }

        private fun parseEWTGListFile(ewtgWindowsFilePath: Path,autAutMF: ATUAMF) {
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

        private fun loadWindow(line: String, ewtgFolderPath: Path,autAutMF: ATUAMF) {
            val data = splitCSVLineToField(line)
            val windowId = data[0]
            val createdAtRuntime = data[4].toBoolean()
            if (createdAtRuntime) {
                createNewWindow(data)
            }
            var window = WindowManager.instance.baseModelWindows.find { it.windowId == windowId }
            if (window == null) {
                window  = createNewWindow(data)

            }
            loadWindowStructure(window, ewtgFolderPath)
            loadWindowEvents(window, ewtgFolderPath,autAutMF)
        }

        private fun loadWindowEvents(window: Window, ewtgFolderPath: Path, autautMF: ATUAMF) {
            val eventFilePath = ewtgFolderPath.resolve("WindowsEvents").resolve("Events_${window.windowId}.csv")
            if (!Files.exists(eventFilePath)) {
                ATUAMF.log.debug("Window $window 'events does not exist")
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
                val createdAtRuntime = data[3].toBoolean()
                val widget = if (widgetId != "null") {
                    window.widgets.find { it.widgetId == widgetId}
                } else
                    null
                if (widgetId != "null" && widget == null)
                    log.warn("Cannot find widget $widgetId in the window $window ")
                if (widgetId == "null" || widget != null) {
                    val existingEvent = if (widgetId == "null")
                        window.inputs.find { it.eventType.toString() == eventType }
                    else
                    {
                        window.inputs.find { it.eventType.toString() == eventType && it.widget == widget }
                    }
                    val event = if (existingEvent == null) {
                        createNewEvent(data,widget,window,createdAtRuntime)
                    } else
                        existingEvent
                    updateHandlerAndModifiedMethods(event, data,window,autautMF)
                }
            }
        }

        private fun updateHandlerAndModifiedMethods(event: Input, data: List<String>, window: Window, autMF: ATUAMF) {
            val eventHandlers = splitCSVLineToField(data[4])
            eventHandlers.filter{it.isNotBlank()}. forEach { handler ->
                val methodId = autMF.statementMF!!.getMethodId(handler)
                if (methodId.isNotBlank()) {
                    if (!event.eventHandlers.contains(methodId)) {
                        autMF.modifiedMethodTopCallersMap.filter { it.value.contains(methodId) }.forEach { updatedMethod, callers ->
                            if (!event.modifiedMethods.containsKey(updatedMethod)) {
                                event.modifiedMethods.putIfAbsent(updatedMethod, false)
                                val updatedStatements = autMF.statementMF!!.getMethodStatements(methodId)
                                updatedStatements.forEach {
                                    event.modifiedMethodStatement.put(it, false)
                                }
                            }

                        }
                        event.eventHandlers.add(methodId)
                    }
                }
            }
/*            val modifiedMethods = splitCSVLineToField(data[5])
            modifiedMethods.filter { it.isNotBlank() }. forEach { method ->
                val methodId = autMF.statementMF!!.getMethodId(method)
                if (!event.modifiedMethods.contains(methodId)) {
                    event.modifiedMethods.put(methodId,false)
                    val updatedStatements = autMF.statementMF!!.getMethodStatements(methodId)
                    updatedStatements.forEach {
                        event.modifiedMethodStatement.put(it,false)
                    }

                }
            }*/
        }

        private fun createNewEvent(data: List<String>, widget: EWTGWidget?, window: Window,createdAtRuntime: Boolean): Input {
            val eventType = EventType.values().find { it.name == data[0] }
            if (eventType == null) {
                throw Exception("Not supported eventType ${data[0]}")
            }
            val input = Input(eventType = eventType,widget = widget,sourceWindow = window,createdAtRuntime = createdAtRuntime,eventHandlers = HashSet())
            return input

        }

        private fun loadWindowStructure(window: Window, ewtgFolderPath: Path) {
            val structureFilePath = ewtgFolderPath.resolve("WindowsWidget").resolve("Widgets_${window.windowId}.csv")
            if (!Files.exists(structureFilePath)) {
                ATUAMF.log.debug("Window $window 'structure does not exist")
                return
            }
            val lines: List<String>
            structureFilePath.toFile().let {file ->
                lines = BufferedReader(FileReader(file)).use {
                    it.lines().skip(1).toList()
                }
            }
            val widgetParentIdMap = HashMap<EWTGWidget,String>()
            lines.forEach { line ->
                val data = splitCSVLineToField(line)
                val widgetId = data[0]
                val widget = window.widgets.find {it.widgetId == widgetId }
                if (widget == null) {
                    //create new widget
                    createNewWidget(data, window,widgetParentIdMap)
                }
            }
            window.widgets.forEach {
                val parentId = widgetParentIdMap[it]
                if (parentId!=null) {
                    val parentWidget = window.widgets.find { it.widgetId == parentId }
                    if (parentWidget!=null)
                        it.parent = parentWidget
                }
            }

        }

        fun splitCSVLineToField(line: String): List<String> {
            // TODO too slow, reimplement without regex
            val data = ArrayList(line.split(";"))
            val result = ArrayList<String>()
            var startQuote = false
            var temp = ""
            for (s in data) {
                if (!startQuote) {
                    if (!s.startsWith("\"")) {
                        result.add(s)
                    } else {
                        if (s.length>1 && s.endsWith("\"")) {
                            result.add(s.trim('"'))
                        }else {
                            startQuote = true
                            temp = s
                        }
                    }
                } else {
                    temp = temp + ";"+s
                    if (s.endsWith("\"")) {
                        startQuote = false
                        // remove quote
                        temp = temp.substring(1,temp.length-1)
                        result.add(temp)
                        temp = ""
                    }
                }
            }
            return result
        }

        private fun createNewWidget(data: List<String>, window: Window, widgetParentIdMap: HashMap<EWTGWidget,String>): EWTGWidget {
            val widgetId = data[0]
            val resourceIdName = data[1]
            val className = data[2]
            val parentId = data[3]
            val activity = data[4]
            val createdAtRuntime = data[5].toBoolean()
            val structure = if (data[6] == "null") {
                ""
            } else {
                data[6]
            }
            val widget = EWTGWidget(
                    widgetId = widgetId,
                    createdAtRuntime = createdAtRuntime,
                    resourceIdName = resourceIdName,
                    className = className,
                    window = window,
                    contentDesc = "",
                    text = "",
                    structure = ""
            )
            widget.modelVersion = ModelVersion.BASE
            if (parentId!="null") {
                widgetParentIdMap.put(widget,parentId)
            }
            return widget
        }

        private fun createNewWindow(data: List<String>): Window {
            val windowId = data[0]
            val windowType = data[1]
            val classType = data[2]
            val createdAtRuntime = data[3].toBoolean()
            val portraitDimension: Rectangle = Helper.parseRectangle(data[4])
            val landscapeDimension: Rectangle = Helper.parseRectangle(data[5])
            val portraitKeyboardDimension: Rectangle = Helper.parseRectangle(data[6])
            val landscapeKeyboardDimension: Rectangle =Helper.parseRectangle(data[7])
            val window = when (windowType) {
                "Activity" -> Activity.getOrCreateNode(nodeId = windowId,
                        classType = classType,
                        runtimeCreated = createdAtRuntime,
                        isBaseMode = true)
                "Dialog" -> Dialog.getOrCreateNode(nodeId = windowId,
                        classType = classType,
                        runtimeCreated = createdAtRuntime,
                        allocMethod = "",
                        isBaseModel = true)
                "OptionsMenu" -> OptionsMenu.getOrCreateNode(nodeId = windowId,
                        classType = classType,
                        runtimeCreated = createdAtRuntime,
                        isBaseModel = true)
                "ContextMenu" -> ContextMenu.getOrCreateNode(nodeId = windowId,
                        classType = classType,
                        runtimeCreated = createdAtRuntime,
                        isBaseModel = true)
                "OutOfApp" -> OutOfApp.getOrCreateNode(nodeId = windowId, activity = classType,isBaseModel = true)
                "FakeWindow" -> FakeWindow.getOrCreateNode(nodeId = windowId,isBaseModel = true)
                "Launcher" -> Launcher.getOrCreateNode()
                else -> throw Exception("Error windowType: $windowType")
            }
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