package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.exploration.modelFeatures.autaut.intent.IntentData
import org.droidmate.exploration.modelFeatures.autaut.intent.IntentFilter
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
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
            val allocIndex = temp.indexOf(" alloc: ")
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
                              transitionGraph: TransitionGraph) {
            jsonObj.keys().asSequence().forEach { key ->
                val windowJson = key as String
                val windowInfo = windowParser(windowJson)
                val wtgNode = transitionGraph.getOrCreateWTGNode(windowInfo)
                val widgetListJson = jsonObj[key] as JSONObject
                widgetListJson.keys().asSequence().forEach {
                    val widgetInfoJson = widgetListJson[it].toString()
                    val widgetInfo = widgetParser(widgetInfoJson)
                    if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                        StaticWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
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
                                , transitionGraph: TransitionGraph
                                , allEventHandlers: HashSet<String>
        , statementCoverageMF: StatementCoverageMF) {
            jsonObj.keys().asSequence().forEach { key ->
                val windowJson = key as String
                val windowInfo = windowParser(windowJson)
                val wtgNode = transitionGraph.getOrCreateWTGNode(windowInfo)
                val widgetListJson = jsonObj[key] as JSONObject
                widgetListJson.keys().asSequence().forEach {
                    val widgetInfoJson = it
                    var staticWidget: StaticWidget? = null
                    val eventListJsons = widgetListJson[it]!! as JSONArray
                    eventListJsons.forEach { eventJson ->
                        val eventJsonObject = eventJson as JSONObject
                        val jsonEventHandlers = eventJsonObject["handler"] as JSONArray
                        val jsonEventType = eventJsonObject["action"] as String
                        if (!StaticEvent.isIgnoreEvent(jsonEventType) && jsonEventType != EventType.implicit_back_event.name) {
                            if (StaticEvent.isNoWidgetEvent(jsonEventType)) {
                                staticWidget = null
                            } else {
                                try {
                                    val widgetInfo = widgetParser(widgetInfoJson)
                                    if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                                        staticWidget = StaticWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                                resourceId = widgetInfo["resourceId"]!!,
                                                resourceIdName = widgetInfo["resourceIdName"]!!,
                                                className = widgetInfo["className"]!!,
                                                wtgNode = wtgNode,
                                                activity = wtgNode.classType)
                                    } else {
                                        staticWidget = null
                                    }

                                } catch (e: Exception) {
                                    staticWidget = null
                                }

                            }
                            if (StaticEvent.isNoWidgetEvent(jsonEventType) ||
                                    (!StaticEvent.isNoWidgetEvent(jsonEventType) &&
                                            staticWidget != null)) {
                                val event = getOrCreateTargetEvent(
                                        eventHandlers = jsonEventHandlers.map { statementCoverageMF.getMethodId(it as String) },
                                        eventTypeString = jsonEventType,
                                        widget = staticWidget,
                                        activity = wtgNode.classType,
                                        sourceWindow = wtgNode,
                                        allTargetStaticEvents = ArrayList())
                                allEventHandlers.addAll(event.eventHandlers)
                            }

                        }

                    }
                }
            }
        }

        fun readModifiedMethodInvocation(jsonObj: JSONObject,
                                         transitionGraph: TransitionGraph,
                                         allTargetStaticWidgets: ArrayList<StaticWidget>,
                                         allTargetStaticEvents: ArrayList<StaticEvent>,
                                         statementCoverageMF: StatementCoverageMF
        ) {
            jsonObj.keys().asSequence().forEach { key ->
                val source = key as String
                val sourceInfo = windowParser(source)
                //val sourceId = sourceInfo["id"]!!
                val sourceNode = transitionGraph.getOrCreateWTGNode(sourceInfo)
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
                        var staticWidget: StaticWidget?
                        if (!StaticEvent.isIgnoreEvent(jsonEventType) && jsonEventType != EventType.implicit_back_event.name) {
                            if (StaticEvent.isNoWidgetEvent(jsonEventType)) {
                                staticWidget = null
                            } else {
                                try {
                                    val widgetInfo = widgetParser(w)
                                    if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                                        staticWidget = StaticWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                                resourceId = widgetInfo["resourceId"]!!,
                                                resourceIdName = widgetInfo["resourceIdName"]!!,
                                                className = widgetInfo["className"]!!,
                                                wtgNode = sourceNode,
                                                activity = sourceNode.classType)
                                        if (!allTargetStaticWidgets.contains(staticWidget)) {
                                            allTargetStaticWidgets.add(staticWidget)
                                        }
                                    } else {
                                        staticWidget = null
                                    }

                                } catch (e: Exception) {
                                    staticWidget = null
                                }

                            }
                            if (StaticEvent.isNoWidgetEvent(jsonEventType) ||
                                    (!StaticEvent.isNoWidgetEvent(jsonEventType) &&
                                            staticWidget != null)) {
                                val jsonEventHandler = jsonEvent["eventHandlers"] as JSONArray
                                val event = getOrCreateTargetEvent(
                                        eventHandlers = jsonEventHandler.map { statementCoverageMF.getMethodId(it as String) },
                                        eventTypeString = jsonEventType,
                                        widget = staticWidget,
                                        activity = sourceNode.classType,
                                        sourceWindow = sourceNode,
                                        allTargetStaticEvents = allTargetStaticEvents)
                                if (transitionGraph.edges(sourceNode).find { it.label == event } == null) {
                                    //this event has not appeared in graph
                                    //let's create new edge
                                    transitionGraph.add(sourceNode, sourceNode, event)
                                }
                                //addWidgetToActivtity_TargetWidget_Map(source, event)
                                val event_methods = Pair<StaticEvent, ArrayList<String>>(event, ArrayList())
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
                    methodTopCallers.add(callId)
                }
                methodTopCallersMap.put(methodId,methodTopCallers)
            }


        }
        private fun getOrCreateTargetEvent(eventHandlers: List<String>,
                                           eventTypeString: String,
                                           widget: StaticWidget?,
                                           activity: String,
                                           sourceWindow: WTGNode,
                                           allTargetStaticEvents: ArrayList<StaticEvent>): StaticEvent {
            var event = StaticEvent.allStaticEvents.firstOrNull { it.eventType.equals(EventType.valueOf(eventTypeString)) && it.widget == widget && it.activity == activity }
            //var event = allTargetStaticEvents.firstOrNull {it.eventTypeString.equals(eventTypeString) && (it.widget!!.equals(widget)) }
            if (event != null) {
                if (!allTargetStaticEvents.contains(event)) {
                    event.eventHandlers.addAll(eventHandlers)
                    allTargetStaticEvents.add(event)
                }
                return event
            }
            event = StaticEvent(eventHandlers = ArrayList(eventHandlers)
                    , eventType = EventType.valueOf(eventTypeString)
                    , widget = widget, activity = activity, sourceWindow = sourceWindow)
            allTargetStaticEvents.add(event)
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

        fun readMenuItemText(jsonObj: JSONObject, optionsMenuList: List<WTGOptionsMenuNode>) {
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
            val activityName = appPackageName + jsonObj.getString("name")
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

        fun readEventWindowCorrelation(jsonObj: JSONObject, transitionGraph: TransitionGraph): HashMap<StaticEvent, HashMap<WTGNode, Double>> {
            val result: HashMap<StaticEvent, HashMap<WTGNode, Double>> = HashMap()
            jsonObj.keys().asSequence().forEach { key ->
                val source = key as String
                val sourceInfo = windowParser(source)
                //val sourceId = sourceInfo["id"]!!
                val sourceNode = transitionGraph.getOrCreateWTGNode(sourceInfo)
                val eventCorrelations = jsonObj[key] as JSONArray
                eventCorrelations.forEach { item ->
                    val jsonEventCorrelation = item as JSONObject
                    var eventType: String = jsonEventCorrelation["eventType"] as String
                    if (eventType == "touch")
                        eventType = "click"
                    var staticWidget: StaticWidget? = null
                    if (!StaticEvent.isIgnoreEvent(eventType) && eventType != EventType.implicit_back_event.name) {
                        if (StaticEvent.isNoWidgetEvent(eventType)) {
                            staticWidget = null
                        } else {
                            try {
                                val jsonTargetWidget = jsonEventCorrelation["targetWidget"] as String
                                val widgetInfo = widgetParser(jsonTargetWidget)
                                staticWidget = StaticWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                        resourceId = widgetInfo["resourceId"]!!,
                                        resourceIdName = widgetInfo["resourceIdName"]!!,
                                        className = widgetInfo["className"]!!,
                                        wtgNode = sourceNode,
                                        activity = sourceNode.classType)
                            } catch (e: Exception) {
                                staticWidget = null
                            }
                        }
                        var event: StaticEvent? = StaticEvent.allStaticEvents.filter { e -> transitionGraph.edges(sourceNode).any { it.label == e } }.find { it.widget == staticWidget && it.eventType.name == eventType }
                        if (event == null) {
                            event = StaticEvent(
                                    eventType = EventType.valueOf(eventType),
                                    eventHandlers = ArrayList(),
                                    activity = sourceNode.classType,
                                    widget = staticWidget,
                                    sourceWindow = sourceNode
                            )
                        }
                        val correlation = HashMap<WTGNode, Double>()
                        val jsonCorrelaions = jsonEventCorrelation["correlations"] as JSONArray
                        jsonCorrelaions.forEach { item ->
                            val jsonItem = item as JSONObject
                            val score = (jsonItem["second"] as String).toDouble()
                            val jsonWindow = jsonItem["first"] as String
                            val windowInfo = windowParser(jsonWindow)
                            val window = transitionGraph.getOrCreateWTGNode(windowInfo)
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

        fun readWindowTerms(jsonObj: JSONObject, transitionGraph: TransitionGraph):Map<WTGNode, HashMap<String, Long>>{
            val windowTerms = HashMap<WTGNode, HashMap<String,Long>>()
            jsonObj.keys().asSequence().forEach { windowName ->
                val windowInfo = windowParser(windowName)
                val window = transitionGraph.getOrCreateWTGNode(windowInfo)
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

        fun readWindowHandlers(jsonObj: JSONObject, transitionGraph: TransitionGraph): Map<WTGNode, Set<String>> {
            val windowHandlers = HashMap<WTGNode, HashSet<String>>()
            jsonObj.keys().asSequence().forEach { windowName ->
                val windowInfo = windowParser(windowName)
                val window = transitionGraph.getOrCreateWTGNode(windowInfo)
                if (window != null)
                {
                    val handlers = HashSet<String>()
                    windowHandlers.put(window,handlers)
                    val jsonHandlers = jsonObj[windowName] as JSONArray
                    jsonHandlers.forEach {
                        handlers.add(it.toString())
                    }
                }
            }
            return windowHandlers
        }
    }
}