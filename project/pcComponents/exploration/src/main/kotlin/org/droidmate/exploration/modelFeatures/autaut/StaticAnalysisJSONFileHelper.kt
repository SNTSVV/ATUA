package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.exploration.modelFeatures.autaut.inputRepo.intent.IntentData
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.intent.IntentFilter
import org.droidmate.exploration.modelFeatures.autaut.WTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class StaticAnalysisJSONFileHelper() {
    companion object {
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
                val endNodeIdIdx = temp.indexOf("]")
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
            //get node type
            val nodetypeIdx = windowInfo.indexOf("[")
            val nodeType = windowInfo.substring(0, nodetypeIdx)
            result["NodeType"] = nodeType
            temp = temp.substring(nodetypeIdx + 1)
            //get class type
            val classtypeIdx = temp.lastIndexOf("]")
            val classType = temp.substring(0, classtypeIdx)
            result["className"] = classType
            temp = temp.substring(classtypeIdx + 1)
            //get sootandroidId
            val allocIndex = temp.indexOf(", alloc: ")
            if (allocIndex == -1) {
                val sootandroidId = temp
                result["id"] = sootandroidId
            } else {
                val sootandroidId = temp.substring(0, allocIndex)
                result["id"] = sootandroidId
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
                                if (wtg.edges(sourceNode).find { it.label == event } == null) {
                                    //this event has not appeared in graph
                                    //let's create new edge
                                    wtg.add(sourceNode, sourceNode, event)
                                }
                                if (ewtgWidget!=null && ewtgWidget!!.className.contains("Layout")) {
                                    var createItemClick = false
                                    var createItemLongClick = false

                                    when (jsonEventType) {
                                        "touch" -> {
                                            createItemClick = true
                                            createItemLongClick = true
                                        }
                                        "click" -> {
                                            createItemClick = true
                                        }
                                        "long_click" -> {
                                            createItemLongClick = true
                                        }
                                    }

                                    //create item click and long click
                                    if (createItemClick) {
                                        val itemClick = getOrCreateTargetEvent(
                                                eventHandlers = jsonEventHandler.map { statementCoverageMF.getMethodId(it as String) }.toSet(),
                                                eventTypeString = "item_click",
                                                widget = ewtgWidget,
                                                activity = sourceNode.classType,
                                                sourceWindow = sourceNode,
                                                allTargetInputs = HashSet())
                                        if (wtg.edges(sourceNode).find { it.label == itemClick } == null) {
                                            //this event has not appeared in graph
                                            //let's create new edge
                                            wtg.add(sourceNode, sourceNode, itemClick)
                                        }
                                    }

                                    if (createItemLongClick) {
                                        //create item click and long click
                                        val itemLongClick = getOrCreateTargetEvent(
                                                eventHandlers = jsonEventHandler.map { statementCoverageMF.getMethodId(it as String) }.toSet(),
                                                eventTypeString = "item_long_click",
                                                widget = ewtgWidget,
                                                activity = sourceNode.classType,
                                                sourceWindow = sourceNode,
                                                allTargetInputs = HashSet())
                                        if (wtg.edges(sourceNode).find { it.label == itemLongClick } == null) {
                                            //this event has not appeared in graph
                                            //let's create new edge
                                            wtg.add(sourceNode, sourceNode, itemLongClick)
                                        }
                                    }
                                }
                                //addWidgetToActivtity_TargetWidget_Map(source, event)
                                val event_methods = Pair<Input, ArrayList<String>>(event, ArrayList())
                                //widget_methodInvocations.methodInvocations.add(event_methods)
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
                        var event: Input? = Input.allStaticEvents.filter { e -> wtg.edges(sourceNode).any { it.label == e } }.find { it.widget == ewtgWidget && it.eventType.name == eventType }
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

        fun readActivityAlias(jsonObj: JSONObject, regressionTestingMF: AutAutMF): HashMap<String, String> {
            val activityAlias = HashMap<String, String>()
            jsonObj.keys().asSequence().forEach { alias ->
                val activity = jsonObj.get(alias).toString()
                activityAlias.put(alias,activity)
            }
            return activityAlias
        }
    }
}