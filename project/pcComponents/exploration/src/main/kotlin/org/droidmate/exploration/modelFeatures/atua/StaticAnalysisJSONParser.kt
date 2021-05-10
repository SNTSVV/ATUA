package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.inputRepo.intent.IntentData
import org.droidmate.exploration.modelFeatures.atua.inputRepo.intent.IntentFilter
import org.droidmate.exploration.modelFeatures.atua.EWTG.*
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Activity
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.inputRepo.deviceEnvironment.DeviceEnvironmentConfigurationFileHelper
import org.droidmate.exploration.modelFeatures.atua.inputRepo.textInput.InputConfigurationFileHelper
import org.droidmate.exploration.modelFeatures.atua.inputRepo.textInput.TextInput
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class StaticAnalysisJSONParser() {
    companion object {
        fun readAppModel(appModelFile: Path,
                         atuaMF: ATUAMF,
                         manualIntent: Boolean,
                         manualInput: Boolean) {
            if (appModelFile != null) {
                //val activityEventList = List<ActivityEvent>()
                val jsonData = String(Files.readAllBytes(appModelFile))
                val jObj = JSONObject(jsonData)
                ATUAMF.log.debug("Reading Window Transition Graph")
                atuaMF.wtg.constructFromJson(jObj)
                readActivityAlias(jObj,atuaMF.activityAlias)
                readWindowWidgets(jObj,atuaMF.allEventHandlers,
                        atuaMF.wtg, atuaMF.statementMF!!)
                WindowManager.instance.updatedModelWindows.forEach { w ->
                    if (w is OptionsMenu) {
                        val ownerActivity = WindowManager.instance.updatedModelWindows.find { it is Activity && it.activityClass == w.activityClass }
                        w.ownerActivity = ownerActivity
                    }
/*                    if (w is Dialog) {
                        val ownerActivity = WindowManager.instance.allWindows.find { it is Activity && it.activityClass == w.activityClass }
                        w.ownerActivity = ownerActivity
                    }*/
                }
                readMenuItemTexts(jObj)
                readActivityDialogs(jObj, atuaMF.allDialogOwners)
                readWindowHandlers(jObj,atuaMF.windowHandlersHashMap,atuaMF.wtg,atuaMF.statementMF!!)
                ATUAMF.log.debug("Reading modified method invocation")
                readModifiedMethodTopCallers(jObj, atuaMF.modifiedMethodTopCallersMap,atuaMF.statementMF!!,
                        atuaMF.allTargetHandlers,atuaMF.windowHandlersHashMap,atuaMF.allTargetWindow_ModifiedMethods,atuaMF.untriggeredTargetHandlers)
                readModifiedMethodInvocation(jObj, atuaMF.wtg, atuaMF.allTargetInputs,atuaMF.allTargetStaticWidgets,
                        atuaMF.statementMF!!,atuaMF.allTargetWindow_ModifiedMethods,atuaMF.targetItemEvents)
                readUnreachableModfiedMethods(jObj, atuaMF.unreachableModifiedMethods )
                //log.debug("Reading all strings")
                //readAllStrings(jObj)
                readEventCorrelation(jObj,atuaMF.inputWindowCorrelation, atuaMF.wtg)
                readMethodDependency(jObj, atuaMF.methodTermsHashMap, atuaMF.statementMF!!)
                readWindowDependency(jObj, atuaMF.windowTermsHashMap, atuaMF.wtg )
                if (manualIntent) {
                    val intentModelFile = atuaMF.getIntentModelFile()
                    if (intentModelFile!=null) {
                        readIntentModel(atuaMF.intentFilters, atuaMF.getAppName(),
                                atuaMF.wtg, atuaMF.allTargetInputs, atuaMF.targetIntFilters,intentModelFile)
                    }
                }
                if (manualInput) {
                    val textInputFile = atuaMF.getTextInputFile()
                    if (textInputFile != null) {
                        atuaMF.inputConfiguration = InputConfigurationFileHelper.readInputConfigurationFile(textInputFile)
                        TextInput.inputConfiguration = atuaMF.inputConfiguration

                    }
                }
                val deviceConfigurationFile = atuaMF.getDeviceConfigurationFile()
                if (deviceConfigurationFile != null) {
                    atuaMF.deviceEnvironmentConfiguration = DeviceEnvironmentConfigurationFileHelper.readInputConfigurationFile(deviceConfigurationFile)
                }
            }

        }

        private fun readActivityAlias (jObj: JSONObject, activityAlias: HashMap<String, String>) {
            val jsonActivityAlias = jObj.getJSONObject("activityAlias")
            if (jsonActivityAlias != null) {
                jsonActivityAlias.keys().asSequence().forEach { alias ->
                    val activity = jsonActivityAlias.get(alias).toString()
                    activityAlias.put(alias,activity)
                }
            }
        }

        private fun readWindowDependency(jObj: JSONObject,
                                         windowTermsHashMap: HashMap<Window, HashMap<String,Long>>,
                                         wtg: WindowTransitionGraph) {
            val jsonWindowTerm = jObj.getJSONObject("windowsDependency")
            if (jsonWindowTerm != null)
            {
                windowTermsHashMap.putAll(readWindowTerms(jsonWindowTerm, wtg))
            }
        }

        private fun readWindowHandlers (jObj: JSONObject, windowHandlersHashMap: HashMap<Window, Set<String>>,
                                        wtg: WindowTransitionGraph, statementCoverageMF: StatementCoverageMF) {
            val jsonWindowHandlers = jObj.getJSONObject("windowHandlers")
            if (jsonWindowHandlers != null) {
                windowHandlersHashMap.putAll(readWindowHandlers(jsonWindowHandlers,wtg, statementCoverageMF))
            }
        }

        private fun readMethodDependency(jObj: JSONObject,
                                         methodTermsHashMap: HashMap<String, HashMap<String, Long>>,
                                         statementMF: StatementCoverageMF) {
            var jsonMethodDepedency = jObj.getJSONObject("methodDependency")
            if (jsonMethodDepedency != null)
            {
                methodTermsHashMap.putAll(StaticAnalysisJSONParser.readMethodTerms(jsonMethodDepedency,statementMF))
            }
        }
        private fun readEventCorrelation(jObj: JSONObject,
                                         inputWindowCorrelation:  HashMap<Input, HashMap<Window,Double>>,
                                         wtg: WindowTransitionGraph) {
            var eventCorrelationJson = jObj.getJSONObject("event_window_Correlation")
            if (eventCorrelationJson!=null)
            {
                inputWindowCorrelation.putAll(readEventWindowCorrelation(eventCorrelationJson,wtg))
            }
        }

        private fun readIntentModel(intentFilters: HashMap<String, ArrayList<IntentFilter>>, appName:String,
                                    wtg: WindowTransitionGraph,
                                    allTargetInputs: HashSet<Input>,
                                    targetIntFilters: HashMap<IntentFilter,Int>,
                                    intentModelFile: Path) {
            if (intentModelFile != null) {
                val jsonData = String(Files.readAllBytes(intentModelFile))
                val jObj = JSONObject(jsonData)
                val activitiesJson = jObj.getJSONArray("activities")
                activitiesJson.forEach {
                    StaticAnalysisJSONParser.readActivityIntentFilter(it as JSONObject, intentFilters, appName)
                }
                intentFilters.forEach { t, u ->
                    val activityName = t
                    val qualifiedActivityName = activityName
                    var intentActivityNode = WindowManager.instance.updatedModelWindows.find { it.classType == qualifiedActivityName }
                    if (intentActivityNode == null) {
                        intentActivityNode = Activity.getOrCreateNode(
                                Activity.getNodeId(), qualifiedActivityName,false,false)
                    }
                    WindowManager.instance.intentFilter.put(intentActivityNode, u)
                    /*u.forEach {
                        for (meaningNode in WindowManager.instance.allMeaningWindows) {
                            val intentEvent = Input(eventType = EventType.callIntent,
                                    eventHandlers = HashSet(), widget = null,sourceWindow = meaningNode)
                            intentEvent.data = it
                            wtg.add(meaningNode, intentActivityNode!!, intentEvent)
                        }

                    }*/

                    if (allTargetInputs.filter {
                                it.sourceWindow.activityClass.contains(activityName)
                                        && (it.eventType == EventType.implicit_rotate_event
                                        || it.eventType == EventType.implicit_lifecycle_event
                                        || it.eventType == EventType.implicit_power_event)
                            }.isNotEmpty()) {
                        u.forEach {
                            targetIntFilters.put(it, 0)
                        }
                    }
                }
            }
        }



        private fun readMenuItemTexts(jObj: JSONObject) {
            var jMap = jObj.getJSONObject("menuItemTexts")
            readMenuItemText(jMap, WindowManager.instance.updatedModelWindows.filter { it is OptionsMenu } as List<OptionsMenu>)
        }

/*    private fun readAllStrings(jObj: JSONObject) {
        var jMap = jObj.getJSONArray("allStrings")
        StaticAnalysisJSONFileHelper.readAllStrings(jMap,generalDictionary)
    }*/

        private fun readUnreachableModfiedMethods(jObj: JSONObject, unreachableModifiedMethods:  ArrayList<String> ) {
            var jMap = jObj.getJSONArray("unreachableModifiedMethods")
            readUnreachableModifiedMethods(jsonArray = jMap, methodList = unreachableModifiedMethods)
        }

        private fun readWindowWidgets(jObj: JSONObject, allEventHandlers: HashSet<String>,
                                      wtg: WindowTransitionGraph, statementCoverageMF: StatementCoverageMF) {
            var jMap1 = jObj.getJSONObject("allWindow_Widgets")
            StaticAnalysisJSONParser.readWindowWidgets(jMap1,wtg)
            var jMap2 = jObj.getJSONObject("allWidgetEvent")
            StaticAnalysisJSONParser.readAllWidgetEvents(jMap2, wtg, allEventHandlers, statementCoverageMF)

        }

        private fun readModifiedMethodTopCallers(jObj: JSONObject,
                                                 modifiedMethodTopCallersMap:HashMap<String, Set<String>>,
                                                 statementCoverageMF: StatementCoverageMF,
                                                 allTargetHandlers: HashSet<String>,
                                                 windowHandlersHashMap: HashMap<Window, Set<String>>,
                                                 allTargetWindow_ModifiedMethods: HashMap<Window,HashSet<String>>,
                                                 untriggeredTargetHandlers: HashSet<String>){
            var jMap = jObj.getJSONObject("modiMethodTopCaller")
            readModifiedMethodTopCallers(jMap,modifiedMethodTopCallersMap,statementCoverageMF)

            //Add windows containing top caller to allTargetWindows list
            modifiedMethodTopCallersMap.forEach { modMethod, topCallers ->
                allTargetHandlers.addAll(topCallers)
                topCallers.forEach { caller ->
                    val windows = windowHandlersHashMap.filter { it.value.contains(caller) }.map { it.key }
                    if (windows.isNotEmpty()) {
                        windows.forEach {w ->
                            if (!allTargetWindow_ModifiedMethods.contains(w)) {
                                allTargetWindow_ModifiedMethods.put(w, hashSetOf())
                            }
                            allTargetWindow_ModifiedMethods[w]!!.add(modMethod)
                        }
                    }
                }
            }

            untriggeredTargetHandlers.addAll(allTargetHandlers)
        }

        private fun readModifiedMethodInvocation(jObj: JSONObject,
                                                 wtg: WindowTransitionGraph,
                                                 allTargetInputs: HashSet<Input>,
                                                 allTargetStaticWidgets: HashSet<EWTGWidget>,
                                                 statementCoverageMF: StatementCoverageMF,
                                                 allTargetWindow_ModifiedMethods: HashMap<Window, HashSet<String>>,
                                                 targetItemEvents: HashMap<Input, HashMap<String,Int>>) {
            var jMap = jObj.getJSONObject("modiMethodInvocation")
            readModifiedMethodInvocation(jsonObj = jMap,
                    wtg = wtg,
                    allTargetInputs = allTargetInputs,
                    allTargetEWTGWidgets = allTargetStaticWidgets,
                    statementCoverageMF =statementCoverageMF)
            allTargetInputs.forEach {
                val sourceWindow = it.sourceWindow
                if (!allTargetWindow_ModifiedMethods.contains(sourceWindow) && sourceWindow !is OutOfApp) {
                    allTargetWindow_ModifiedMethods.put(sourceWindow, hashSetOf())
                }
                allTargetWindow_ModifiedMethods[sourceWindow]!!.addAll(it.modifiedMethods.keys)
            }

            allTargetInputs.filter { listOf<EventType>(EventType.item_click, EventType.item_long_click,
                    EventType.item_selected).contains(it.eventType)}.forEach {
                val eventInfo = HashMap<String,Int>()
                targetItemEvents.put(it,eventInfo)
                eventInfo["max"] = 3
                eventInfo["count"] = 0
            }

        }


        fun readActivityDialogs(jObj: JSONObject, allDialogOwners: HashMap<String, ArrayList<String>>) {
            val jMap = jObj.getJSONObject("allActivityDialogs")
            //for each Activity transition
            jMap.keys().asSequence().forEach { key ->
                val activity = key as String
                if (!allDialogOwners.containsKey(activity)) {
                    allDialogOwners[activity] = ArrayList()
                }
                val dialogs = jMap[key] as JSONArray
                dialogs.forEach {
                    allDialogOwners[activity]!!.add(it as String)
                }
            }
        }

        fun readActivityOptionMenuItem(jObj: JSONObject,
                                       allActivityOptionMenuItems: HashMap<String, ArrayList<String>>){
            val jMap = jObj.getJSONObject("allActivityOptionMenuItems")
            //for each Activity transition
            jMap.keys().asSequence().forEach { key ->
                val activity = key as String
                if (!allActivityOptionMenuItems.contains(activity))
                {
                    allActivityOptionMenuItems[activity] = ArrayList()
                }
//            val menuItemsJson = jMap[key] as JSONArray
//            menuItemsJson.forEach {
//                val widgetInfo = StaticAnalysisJSONFileHelper.widgetParser(it as String)
//                val menuItemWidget = StaticWidget.getOrCreateStaticWidget(widgetInfo["id"]!!,"android.view.menu",false)
//                allActivityOptionMenuItems[activity]!!.add(menuItemWidget)
//            }

            }
        }


        fun widgetParser(widgetInfo: String): HashMap<String, String> {
            //widgetInfo = "INFL[android.widget.ListView,WID[2131427361|PlaylistsListView]464,7251]7252"
            try {
                val result = HashMap<String, String>()
                var temp: String = widgetInfo
                //get node type
                val nodetypeIdx = widgetInfo.indexOf("[")
                val nodeType = widgetInfo.substring(0, nodetypeIdx)
                temp = temp.substring(nodetypeIdx + 1)
                //get class type
                val classtypeIdx = temp.indexOf(",")
                if (classtypeIdx<0)
                    return result
                val classType = temp.substring(0, classtypeIdx)
                result["className"] = classType
                temp = temp.substring(classtypeIdx + 1)
                //get idNode
                val idnodeIdx = temp.indexOf("WID[")
                if (idnodeIdx != -1) {
                    temp = temp.substring(idnodeIdx + 1)
                    //get resourceid
                    val resourceidIdx = temp.indexOf("|")
                    val resourceId = temp.substring(0, resourceidIdx)
                    result["resourceId"] = resourceId
                    temp = temp.substring(resourceidIdx + 1)
                    //get resourceidName
                    val resourceidnameIdx = temp.indexOf("]")
                    val resourceIdName = temp.substring(0, resourceidnameIdx)
                    result["resourceIdName"] = resourceIdName
                    temp = temp.substring(resourceidnameIdx + 1)
                }
                //get sootandroidId
                val endNodeIdIdx = temp.lastIndexOf("]")
                val sootandroidId = temp.substring(endNodeIdIdx + 1)
                result["id"] = sootandroidId
                return result
            } catch (e: Exception) {
                throw e
            }
        }

        fun windowParser(windowInfo: String): HashMap<String, String> {
            //"DIALOG[com.teleca.jamendo.dialog.LyricsDialog]2686, alloc: <com.teleca.jamendo.activity.PlayerActivity: void lyricsOnClick(android.view.View)>"
            //"ACT[com.teleca.jamendo.activity.PlayerActivity]823"
            val result = HashMap<String, String>()
            var temp: String = windowInfo
            //result["id"] = temp.substring(temp.lastIndexOf("]")+1);
            //get node type
            val nodetypeIdx = windowInfo.indexOf("[")
            val nodeType = windowInfo.substring(0, nodetypeIdx)
            result["NodeType"] = nodeType
            temp = temp.substring(nodetypeIdx + 1)
            //get class type
            val classtypeEndIdx = temp.lastIndexOf("]")
            val classType = temp.substring(0, classtypeEndIdx)
            result["className"] = classType
            temp = temp.substring(classtypeEndIdx + 1)
            //get sootandroidId
            val allocIndex = temp.indexOf(", alloc: ")
            if (allocIndex == -1) {
                val sootandroidId = temp
                result["id"] = sootandroidId
            } else {
                val sootandroidId = temp.substring(0, allocIndex)
                result["id"] = sootandroidId
                temp = temp.substring(allocIndex+", alloc: ".length)
                result["allocMethod"] = temp
            }
            return result
        }

        fun readWindowWidgets(jsonObj: JSONObject,
                              wtg: WindowTransitionGraph) {
            jsonObj.keys().asSequence().forEach { key ->
                val windowJson = key as String
                val windowInfo = windowParser(windowJson)
                val wtgNode = wtg.getOrCreateWTGNode(windowInfo)
                val widgetListJson = jsonObj[key] as JSONObject
                widgetListJson.keys().asSequence().forEach {
                    if (widgetListJson[it]!! is JSONObject) {
                        val jsonObjectRoot = widgetListJson[it] as JSONObject
                        val widgetInfoJson = jsonObjectRoot["widget"].toString()
                        val widgetInfo = widgetParser(widgetInfoJson)
                        val parent = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                resourceId = widgetInfo["resourceId"]?:"",
                                resourceIdName = widgetInfo["resourceIdName"]?:"",
                                className = widgetInfo["className"]!!,
                                wtgNode = wtgNode,
                                activity = wtgNode.classType)
                        val jsonChildren = jsonObjectRoot["children"] as JSONObject
                        parseWindowWigetsChildren(jsonChildren, parent)

                    } else {
                        val widgetInfoJson = widgetListJson[it].toString()
                        val widgetInfo = widgetParser(widgetInfoJson)
                        if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                            EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                    resourceId = widgetInfo["resourceId"]!!,
                                    resourceIdName = widgetInfo["resourceIdName"]!!,
                                    className = widgetInfo["className"]!!,
                                    wtgNode = wtgNode,
                                    activity = wtgNode.classType)
                        }

                    }
                }
            }
        }

        private fun parseWindowWigetsChildren(jsonChildren: JSONObject, parent: EWTGWidget ) {
            jsonChildren.keys().asSequence().forEach {
                if (jsonChildren[it]!! is JSONObject ) {
                    val jsonObjectTree = jsonChildren[it] as JSONObject
                    val widgetInfoJson = jsonObjectTree["widget"].toString()
                    val widgetInfo = widgetParser(widgetInfoJson)
                    val widget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                            resourceId = widgetInfo["resourceId"]?:"",
                            resourceIdName = widgetInfo["resourceIdName"]?:"",
                            className = widgetInfo["className"]!!,
                            wtgNode = parent.window,
                            activity = parent.window.classType)
                    widget.parent = parent
                    val jsonChildren = jsonObjectTree["children"] as JSONObject
                    parseWindowWigetsChildren(jsonChildren, widget)
                } else {
                    val widgetInfoJson = jsonChildren[it].toString()
                    val widgetInfo = widgetParser(widgetInfoJson)
                    if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                        val widget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                resourceId = widgetInfo["resourceId"]!!,
                                resourceIdName = widgetInfo["resourceIdName"]!!,
                                className = widgetInfo["className"]!!,
                                wtgNode = parent.window,
                                activity = parent.window.classType)
                        widget.parent = parent
                    }

                }
            }
        }

        fun readAllWidgetEvents(jsonObj: JSONObject
                                , wtg: WindowTransitionGraph
                                , allEventHandlers: HashSet<String>
                                , statementCoverageMF: StatementCoverageMF) {
            jsonObj.keys().asSequence().forEach { key ->
                val windowJson = key as String
                val windowInfo = windowParser(windowJson)
                val wtgNode = wtg.getOrCreateWTGNode(windowInfo)
                val widgetListJson = jsonObj[key] as JSONObject
                widgetListJson.keys().asSequence().forEach {
                    val widgetInfoJson = it
                    var ewtgWidget: EWTGWidget? = null
                    val eventListJsons = widgetListJson[it]!! as JSONArray
                    eventListJsons.forEach { eventJson ->
                        val eventJsonObject = eventJson as JSONObject
                        val jsonEventHandlers = eventJsonObject["handler"] as JSONArray
                        val jsonEventType = eventJsonObject["action"] as String
                        if (!Input.isIgnoreEvent(jsonEventType) && jsonEventType != EventType.implicit_back_event.name) {
                            if (Input.isNoWidgetEvent(jsonEventType)) {
                                ewtgWidget = null
                            } else {
                                try {
                                    val widgetInfo = widgetParser(widgetInfoJson)
                                    if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                                        ewtgWidget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                                resourceId = widgetInfo["resourceId"]!!,
                                                resourceIdName = widgetInfo["resourceIdName"]!!,
                                                className = widgetInfo["className"]!!,
                                                wtgNode = wtgNode,
                                                activity = wtgNode.classType)
                                    } else {
                                        ewtgWidget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                                className = widgetInfo["className"]!!,
                                                wtgNode = wtgNode,
                                                activity = wtgNode.classType)
                                    }

                                } catch (e: Exception) {
                                    ewtgWidget = null
                                }

                            }
                            if (Input.isNoWidgetEvent(jsonEventType) ||
                                    (!Input.isNoWidgetEvent(jsonEventType) &&
                                            ewtgWidget != null)) {
                                val event = getOrCreateTargetEvent(
                                        eventHandlers = jsonEventHandlers.map { statementCoverageMF.getMethodId(it as String) }.toSet(),
                                        eventTypeString = jsonEventType,
                                        widget = ewtgWidget,
                                        activity = wtgNode.classType,
                                        sourceWindow = wtgNode,
                                        allTargetInputs = HashSet())
                                allEventHandlers.addAll(event.eventHandlers)

                                if (ewtgWidget!=null && ewtgWidget!!.className.contains("Layout")) {
                                    var createItemClick = false
                                    var createItemLongClick = false
                                    /*when (jsonEventType) {
                                       "touch" -> {
                                            createItemClick=true
                                        createItemLongClick=true
                                        }
                                        "click" -> {
                                            createItemClick=true
                                        }
                                        "long_click" -> {
                                            createItemLongClick=true
                                        }
                                    }*/
                                    //create item click and long click
                                    if (createItemClick) {
                                        val itemClick = getOrCreateTargetEvent(
                                                eventHandlers = jsonEventHandlers.map { statementCoverageMF.getMethodId(it as String) }.toSet(),
                                                eventTypeString = "item_click",
                                                widget = ewtgWidget,
                                                activity = wtgNode.classType,
                                                sourceWindow = wtgNode,
                                                allTargetInputs = HashSet())
                                        allEventHandlers.addAll(itemClick.eventHandlers)
                                    }

                                    if (createItemLongClick) {
                                        //create item click and long click
                                        val itemLongClick = getOrCreateTargetEvent(
                                                eventHandlers = jsonEventHandlers.map { statementCoverageMF.getMethodId(it as String) }.toSet(),
                                                eventTypeString = "item_long_click",
                                                widget = ewtgWidget,
                                                activity = wtgNode.classType,
                                                sourceWindow = wtgNode,
                                                allTargetInputs = HashSet())
                                        allEventHandlers.addAll(itemLongClick.eventHandlers)
                                    }
                                }
                            }

                        }

                    }
                }
            }
        }

        fun readModifiedMethodInvocation(jsonObj: JSONObject,
                                         wtg: WindowTransitionGraph,
                                         allTargetEWTGWidgets: HashSet<EWTGWidget>,
                                         allTargetInputs: HashSet<Input>,
                                         statementCoverageMF: StatementCoverageMF
        ) {

            jsonObj.keys().asSequence().forEach { key ->
                val source = key as String
                val sourceInfo = windowParser(source)
                //val sourceId = sourceInfo["id"]!!
                val sourceNode = wtg.getOrCreateWTGNode(sourceInfo)
                val methodInvocations = jsonObj[key] as JSONObject
                methodInvocations.keys().asSequence().forEach { w ->
                    val widgetInvocation_json = methodInvocations[w] as JSONArray
                    //val widget_methodInvocations = Widget_MethodInvocations(staticWidget, ArrayList())

                    //widgets_modMethodInvocation[staticWidget.widgetId] = widget_methodInvocations
                    widgetInvocation_json.forEach {
                        val jsonEvent = it as JSONObject
                        val jsonEventType: String
                        if ((jsonEvent["eventType"] as String) == "touch")
                            jsonEventType = "click"
                        else
                            jsonEventType = jsonEvent["eventType"] as String
                        var ewtgWidget: EWTGWidget?
                        if (!Input.isIgnoreEvent(jsonEventType) && jsonEventType != EventType.implicit_back_event.name) {
                            if (Input.isNoWidgetEvent(jsonEventType)
                            ) {
                                ewtgWidget = null
                            } else {
                                try {
                                    val widgetInfo = widgetParser(w)
                                    if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                                        ewtgWidget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                                resourceId = widgetInfo["resourceId"]!!,
                                                resourceIdName = widgetInfo["resourceIdName"]!!,
                                                className = widgetInfo["className"]!!,
                                                wtgNode = sourceNode,
                                                activity = sourceNode.classType)

                                    } else {
                                        ewtgWidget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                                className = widgetInfo["className"]!!,
                                                wtgNode = sourceNode,
                                                activity = sourceNode.classType)
                                    }
                                    if (!allTargetEWTGWidgets.contains(ewtgWidget)) {
                                        allTargetEWTGWidgets.add(ewtgWidget)
                                    }
                                } catch (e: Exception) {
                                    ewtgWidget = null
                                }

                            }
                            if (Input.isNoWidgetEvent(jsonEventType) ||
                                    (!Input.isNoWidgetEvent(jsonEventType) &&
                                            ewtgWidget != null)) {
                                val jsonEventHandler = jsonEvent["eventHandlers"] as JSONArray
                                val event = getOrCreateTargetEvent(
                                        eventHandlers = jsonEventHandler.map { statementCoverageMF.getMethodId(it as String) }.toSet(),
                                        eventTypeString = jsonEventType,
                                        widget = ewtgWidget,
                                        activity = sourceNode.classType,
                                        sourceWindow = sourceNode,
                                        allTargetInputs = allTargetInputs)
                                val methods = jsonEvent["modMethods"] as JSONArray
                                methods.forEach {
                                    val methodId = statementCoverageMF.getMethodId(it as String)
                                    val statements = statementCoverageMF.getMethodStatements(methodId)
                                    event.modifiedMethods.put(methodId, false)
                                    statements.forEach {
                                        event.modifiedMethodStatement.put(it, false)
                                    }
                                }
                            }

                        }

                    }

                }
            }
        }

        fun readModifiedMethodTopCallers(jsonObj: JSONObject,
                                         methodTopCallersMap: HashMap<String, Set<String>>,
                                         statementCoverageMF: StatementCoverageMF)
        {
            jsonObj.keys().asSequence().forEach {
                val methodId = statementCoverageMF.getMethodId(it)
                val methodTopCallers = HashSet<String>()
                val methodTopCallersJsonArray = jsonObj[it] as JSONArray
                methodTopCallersJsonArray.asSequence().forEach {
                    val callId = statementCoverageMF.getMethodId(it.toString())
                    if (callId.isNotBlank())
                        methodTopCallers.add(callId)
                }
                methodTopCallersMap.put(methodId,methodTopCallers)
            }


        }

        fun getOrCreateTargetEvent(eventHandlers: Set<String>,
                                   eventTypeString: String,
                                   widget: EWTGWidget?,
                                   @Suppress activity: String,
                                   sourceWindow: Window,
                                   allTargetInputs: HashSet<Input>): Input {
            var event = Input.allStaticEvents.firstOrNull { it.eventType.equals(EventType.valueOf(eventTypeString)) && it.widget == widget && it.sourceWindow == sourceWindow }
            //var event = allTargetStaticEvents.firstOrNull {it.eventTypeString.equals(eventTypeString) && (it.widget!!.equals(widget)) }
            if (event != null) {
                event.eventHandlers.addAll(eventHandlers)
                if (!allTargetInputs.contains(event)) {

                    allTargetInputs.add(event)
                }
                return event
            }
            event = Input(eventHandlers = HashSet(eventHandlers)
                    , eventType = EventType.valueOf(eventTypeString)
                    , widget = widget, sourceWindow = sourceWindow)
            allTargetInputs.add(event)
            return event
        }

        fun readUnreachableModifiedMethods(jsonArray: JSONArray?, methodList: ArrayList<String>) {
            if (jsonArray == null) {
                throw Exception("No never used modified methods object")
            }
            jsonArray.forEach { key ->
                val methodSig = key
                methodList.add(methodSig.toString())
            }
        }

        fun readMenuItemText(jsonObj: JSONObject, optionsMenuList: List<OptionsMenu>) {
            jsonObj.keys().asSequence().forEach { key ->
                val widgetInfo = widgetParser(key)
                optionsMenuList.forEach {
                    val menuItem = it.widgets.find { it.widgetId == widgetInfo["id"]!! }
                    if (menuItem != null) {
                        menuItem.possibleTexts = ArrayList((jsonObj[key] as JSONArray).map { it.toString() })
                    }
                }
            }
        }

        fun readAllStrings(jsonArray: JSONArray, dictionary: ArrayList<String>) {
            jsonArray.forEach {
                val s = it.toString()
                if (!dictionary.contains(s)) {
                    dictionary.add(s)
                }
            }
        }

        //Read intent-filter
        fun readActivityIntentFilter(jsonObj: JSONObject, activityIntentFilters: HashMap<String, ArrayList<IntentFilter>>, appPackageName: String) {
            val intentActivity = jsonObj.getString("name")
            val activityName = if (intentActivity.startsWith(".")) {
                appPackageName + intentActivity
            } else {
                intentActivity
            }
            if (!activityIntentFilters.containsKey(activityName)) {
                activityIntentFilters.put(activityName, ArrayList())
            }
            (jsonObj.get("intent-filters") as JSONArray).forEach {
                val intentFilter = readIntentFilter(activityName, it as JSONObject)
                activityIntentFilters[activityName]!!.add(intentFilter)
            }
        }

        fun readIntentFilter(activityNaME: String, intentJson: JSONObject): IntentFilter {
            val intentFilter = IntentFilter(activityNaME)
            intentJson.keys().asSequence().forEach { key ->
                if (key == "actions") {
                    readIntentFilterActions(intentJson[key] as JSONArray).forEach {
                        intentFilter.addAction(it)
                    }
                }
                if (key == "categories") {
                    readIntentFilterCategories(intentJson[key] as JSONArray).forEach {
                        intentFilter.addCategory(it)
                    }
                }
                if (key == "data") {
                    readIntentFilterDataList(intentJson[key] as JSONArray).forEach {
                        intentFilter.addData(it)
                    }
                }
            }
            return intentFilter
        }

        fun readIntentFilterActions(actionsJson: JSONArray): List<String> {
            val actions = ArrayList<String>()
            actionsJson.forEach {
                actions.add(it.toString())
            }
            return actions
        }

        fun readIntentFilterCategories(categoriesJson: JSONArray): List<String> {
            val categories = ArrayList<String>()
            categoriesJson.forEach {
                categories.add(it.toString())
            }
            return categories
        }

        fun readIntentFilterDataList(dataListJson: JSONArray): List<IntentData> {
            val intentDataList = ArrayList<IntentData>()
            dataListJson.forEach {
                val intentData = readIntentFilterData(it as JSONObject)
                intentDataList.add(intentData)
            }
            return intentDataList
        }

        fun readIntentFilterData(dataJson: JSONObject): IntentData {
            var scheme = ""
            var host = ""
            var pathPrefix = ""
            val testData = ArrayList<String>()
            var mimeType = ""
            dataJson.keys().asSequence().forEach { key ->
                if (key == "scheme") {
                    scheme = dataJson[key].toString()
                }
                if (key == "host") {
                    host = dataJson[key].toString()
                }
                if (key == "pathPrefix") {
                    pathPrefix = dataJson[key].toString()
                }
                if (key == "mimeType") {
                    mimeType = dataJson[key].toString()
                }
                if (key == "testData") {
                    testData.addAll(readIntentFilterDataTestInstance(dataJson[key] as JSONArray))
                }
            }
            return IntentData(scheme = scheme, host = host, path = pathPrefix, mimeType = mimeType, testData = testData)
        }

        private fun readIntentFilterDataTestInstance(testDataJson: JSONArray): ArrayList<String> {
            val testData = ArrayList<String>()
            testDataJson.forEach {
                testData.add(it.toString())
            }
            return testData
        }

        fun readEventWindowCorrelation(jsonObj: JSONObject, wtg: WindowTransitionGraph): HashMap<Input, HashMap<Window, Double>> {
            val result: HashMap<Input, HashMap<Window, Double>> = HashMap()
            jsonObj.keys().asSequence().forEach { key ->
                val source = key as String
                val sourceInfo = windowParser(source)
                //val sourceId = sourceInfo["id"]!!
                val sourceNode = wtg.getOrCreateWTGNode(sourceInfo)
                val eventCorrelations = jsonObj[key] as JSONArray
                eventCorrelations.forEach { item ->
                    val jsonEventCorrelation = item as JSONObject
                    var eventType: String = jsonEventCorrelation["eventType"] as String
                    if (eventType == "touch")
                        eventType = "click"
                    var ewtgWidget: EWTGWidget? = null
                    if (!Input.isIgnoreEvent(eventType) && eventType != EventType.implicit_back_event.name) {
                        if (Input.isNoWidgetEvent(eventType)) {
                            ewtgWidget = null
                        } else {
                            try {
                                val jsonTargetWidget = jsonEventCorrelation["targetWidget"] as String
                                val widgetInfo = widgetParser(jsonTargetWidget)
                                ewtgWidget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                        resourceId = widgetInfo["resourceId"]!!,
                                        resourceIdName = widgetInfo["resourceIdName"]!!,
                                        className = widgetInfo["className"]!!,
                                        wtgNode = sourceNode,
                                        activity = sourceNode.classType)
                            } catch (e: Exception) {
                                ewtgWidget = null
                            }
                        }
                        var event: Input? = Input.allStaticEvents.filter { e -> wtg.edges(sourceNode).any { it.label.input == e } }.find { it.widget == ewtgWidget && it.eventType.name == eventType }
                        if (event == null) {
                            event = Input(
                                    eventType = EventType.valueOf(eventType),
                                    eventHandlers = HashSet(),
                                    widget = ewtgWidget,
                                    sourceWindow = sourceNode
                            )
                        }
                        val correlation = HashMap<Window, Double>()
                        val jsonCorrelaions = jsonEventCorrelation["correlations"] as JSONArray
                        jsonCorrelaions.forEach { item ->
                            val jsonItem = item as JSONObject
                            val score = (jsonItem["second"] as String).toDouble()
                            val jsonWindow = jsonItem["first"] as String
                            val windowInfo = windowParser(jsonWindow)
                            val window = wtg.getOrCreateWTGNode(windowInfo)
                            correlation.put(window, score)
                        }
                        if (correlation.isNotEmpty())
                            result.put(event, correlation)
                    }

                }

            }
            return result
        }

        fun readMethodTerms(jsonObj: JSONObject, statementCoverageMF: StatementCoverageMF):Map<String, HashMap<String, Long>>{
            val methodTerms = HashMap<String, HashMap<String,Long>>()
            jsonObj.keys().asSequence().forEach { methodSig ->
                val methodId = statementCoverageMF.getMethodId(methodSig)
                val terms = HashMap<String, Long>()
                methodTerms.put(methodId,terms)
                val jsonTerms = jsonObj[methodSig] as JSONObject
                jsonTerms.keys().asSequence().forEach {term ->
                    val count = jsonTerms[term] as Int
                    terms.put(term,count.toLong())
                }
            }
            return methodTerms
        }

        fun readWindowTerms(jsonObj: JSONObject, wtg: WindowTransitionGraph):Map<Window, HashMap<String, Long>>{
            val windowTerms = HashMap<Window, HashMap<String,Long>>()
            jsonObj.keys().asSequence().forEach { windowName ->
                val windowInfo = windowParser(windowName)
                val window = wtg.getOrCreateWTGNode(windowInfo)
                if (window != null)
                {
                    val terms = HashMap<String, Long>()
                    windowTerms.put(window,terms)
                    val jsonTerms = jsonObj[windowName] as JSONObject
                    jsonTerms.keys().asSequence().forEach {term ->
                        val count = jsonTerms[term] as Int
                        terms.put(term,count.toLong())
                    }
                }
            }
            return windowTerms
        }

        fun readWindowHandlers(jsonObj: JSONObject, wtg: WindowTransitionGraph, statementCoverageMF: StatementCoverageMF): Map<Window, Set<String>> {
            val windowHandlers = HashMap<Window, HashSet<String>>()
            jsonObj.keys().asSequence().forEach { windowName ->
                val windowInfo = windowParser(windowName)
                val window = wtg.getOrCreateWTGNode(windowInfo)
                if (window != null)
                {
                    val handlers = HashSet<String>()
                    windowHandlers.put(window,handlers)
                    val jsonHandlers = jsonObj[windowName] as JSONArray
                    jsonHandlers.forEach {
                        val methodId = statementCoverageMF.getMethodId(it.toString())
                        handlers.add(methodId)
                    }
                }
            }
            return windowHandlers
        }
    }
}