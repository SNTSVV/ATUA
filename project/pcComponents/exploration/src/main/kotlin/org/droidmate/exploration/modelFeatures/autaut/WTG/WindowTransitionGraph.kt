package org.droidmate.exploration.modelFeatures.autaut.WTG

import org.droidmate.exploration.modelFeatures.graph.*
import org.droidmate.exploration.modelFeatures.autaut.*
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Activity
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.ContextMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.FakeWindow
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class WindowTransitionGraph(private val graph: IGraph<Window, Input> =
                              Graph(FakeWindow(nodeId = "Root") as Window,
                                      stateComparison = { a, b -> a == b },
                                      labelComparison = { a, b ->
                                          a == b
                                      })) : IGraph<Window, Input> by graph {
    val edgeConditions: HashMap<Edge<*, *>, HashMap<StaticWidget, String>> = HashMap()
    val edgeProved: HashMap<Edge<*, *>, Int> = HashMap()
    val statementCoverageInfo: HashMap<Edge<*, *>, ArrayList<String>> = HashMap()
    val methodCoverageInfo: HashMap<Edge<*, *>, ArrayList<String>> = HashMap()

    init {
        Launcher.getOrCreateNode()
    }
    fun constructFromJson(jObj: JSONObject) {
        var jMap = jObj.getJSONObject("allActivityNodes")
        jMap.keys().asSequence().forEach { key ->
            val windowInfo = StaticAnalysisJSONFileHelper.windowParser(jMap[key]!! as String)
            //val sourceNode = getOrCreateWTGNode(windowInfo)
        }

        jMap = jObj.getJSONObject("allTransitions")
        //for each Activity transition
        jMap.keys().asSequence().forEach { key ->
            val source = key as String
            log.debug("source: $source")
            val windowInfo = StaticAnalysisJSONFileHelper.windowParser(source)
            val sourceNode = getOrCreateWTGNode(windowInfo)
            if (sourceNode is Launcher) {
                this.add(root.data, sourceNode, LaunchAppEvent(sourceNode))
            }
            //for each possbile transition to another window
            val transitions = jMap[key] as JSONArray
            transitions.forEach { it ->
                val transition = it as JSONObject
                var ignoreWidget = false
                val action = transition["action"] as String
                if (!Input.isIgnoreEvent(action)) {
                    if (Input.isNoWidgetEvent(action)
                    ) {
                        ignoreWidget = true
                    }

                    val target = transition["target"] as String
                    log.debug("action: $action")
                    log.debug("target: $target")
                    val targetInfo = StaticAnalysisJSONFileHelper.windowParser(target)
                    val targetNode = getOrCreateWTGNode(targetInfo)
                    if (targetNode is OptionsMenu || sourceNode is Launcher) {
                        ignoreWidget = true
                    }
                    val staticWidget: StaticWidget?

                    if (ignoreWidget == false) {
                        val targetView = transition["widget"] as String
                        log.info("parsing widget: $targetView")
                        val widgetInfo = StaticAnalysisJSONFileHelper.widgetParser(targetView)
                        if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                            staticWidget = StaticWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                    resourceId = widgetInfo["resourceId"]!!,
                                    resourceIdName = widgetInfo["resourceIdName"]!!,
                                    className = widgetInfo["className"]!!,
                                    wtgNode = sourceNode,
                                    activity = sourceNode.classType)
                        } else if (widgetInfo.containsKey("className") && widgetInfo.containsKey("id")) {
                            staticWidget = StaticWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                    className = widgetInfo["className"]!!,
                                    wtgNode = sourceNode,
                                    activity = sourceNode.classType)
                        } else {
                            staticWidget = null
                        }
                    } else {
                        staticWidget = null
                    }
                    if (Input.isNoWidgetEvent(action) || (!Input.isNoWidgetEvent(action) && staticWidget!=null) ) {
                            val event = Input.getOrCreateEvent(
                                    eventTypeString = action,
                                    eventHandlers = emptySet(),
                                    widget = staticWidget,
                                    activity = sourceNode.classType,
                                    sourceWindow = sourceNode

                            )
                            //event = StaticEvent(EventType.valueOf(action), arrayListOf(), staticWidget, sourceNode.classType, sourceNode)
                            edgeConditions.put(this.add(sourceNode, targetNode, event), HashMap())

                            if (staticWidget!=null && staticWidget!!.className.contains("Layout")) {
                                var createItemClick = false
                                var createItemLongClick = false
                                /*when (action) {
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
                                if (createItemClick) {
                                    //create item click and long click
                                    val itemClick = Input.getOrCreateEvent(
                                            eventHandlers = emptySet(),
                                            eventTypeString = "item_click",
                                            widget = staticWidget,
                                            activity = sourceNode.classType,
                                            sourceWindow = sourceNode)

                                    this.add(sourceNode, targetNode, itemClick)
                                }
                                if (createItemLongClick) {
                                    //create item click and long click
                                    val itemLongClick = Input.getOrCreateEvent(
                                            eventHandlers = emptySet(),
                                            eventTypeString = "item_long_click",
                                            widget = staticWidget,
                                            activity = sourceNode.classType,
                                            sourceWindow = sourceNode)
                                    this.add(sourceNode, targetNode, itemLongClick  )
                                }
                            }
                    }
                }

                //construct graph
            }
        }
        OptionsMenu.allNodes.forEach { o ->
            val owner = Activity.allNodes.find { a -> a.classType == o.classType }
            if (owner != null) {
                val edges = this.edges(owner, o)
                if (edges.isEmpty()) {
                    this.add(owner, o, Input(eventType = EventType.implicit_menu,
                            eventHandlers = HashSet(),
                            widget = null,
                            sourceWindow = o))
                }
            }

        }

    }

    fun getOrCreateWTGNode(windowInfo: HashMap<String, String>): Window {
        val wtgNode = when (windowInfo["NodeType"]) {
            "ACT" -> Activity.getOrCreateNode(windowInfo["id"]!!, windowInfo["className"]!!)
            "DIALOG" -> Dialog.getOrCreateNode(windowInfo["id"]!!, windowInfo["className"]!!)
            "OptionsMenu" -> OptionsMenu.getOrCreateNode(windowInfo["id"]!!, windowInfo["className"]!!)
            "ContextMenu" -> ContextMenu.getOrCreateNode(windowInfo["id"]!!, windowInfo["className"]!!)
            "LAUNCHER_NODE" -> Launcher.getOrCreateNode()
            else -> throw Exception("Not supported windowType")
        }
        if (wtgNode is OptionsMenu) {
            val activityNode = Activity.allNodes.find { it.classType == wtgNode.classType }
            if (activityNode != null) {
                if (this.edges(activityNode, wtgNode).isEmpty())
                    this.add(activityNode, wtgNode, Input(EventType.implicit_menu, HashSet( ), null, sourceWindow = activityNode))
            }

        }
        //add pressHome event

        return wtgNode
    }

    fun getNextRoot(): Window? {
        return this.edges(root).firstOrNull()?.destination?.data
    }

    fun haveContextMenu(wtgNode: Window): Boolean {
        val outEdges = this.edges(wtgNode)
        if (outEdges.find { it.destination != null && it.destination!!.data is ContextMenu } != null)
            return true
        return false
    }

    fun haveOptionsMenu(wtgNode: Window): Boolean {
        val outEdges = this.edges(wtgNode)
        if (outEdges.find { it.destination != null && it.destination!!.data is OptionsMenu && it.label.eventType != EventType.implicit_back_event } != null)
            return true
        if (outEdges.find { it.destination != null && it.label.eventType == EventType.implicit_menu } != null)
            return true
        return false
    }

    fun getOptionsMenu(wtgNode: Window): Window? {
        if (wtgNode is OptionsMenu) {
            return null
        }
        val edges = this.edges(wtgNode).filter {
            it.destination != null
                    && it.destination!!.data is OptionsMenu
                    && it.label.eventType != EventType.implicit_back_event
        }
        return edges.map { it.destination!!.data }.firstOrNull()
    }

    fun getContextMenus(wtgNode: Window): List<ContextMenu> {
        val edges = this.edges(wtgNode).filter { it.destination != null }
                .filter { it.destination!!.data is ContextMenu }
        val windows = edges.map { it.destination!!.data as ContextMenu }.toMutableList()
        val originalContextMenus = windows.filter { it.activityClass != wtgNode.activityClass }
        val activityContextMenus = windows.filterNot { it.activityClass != wtgNode.activityClass }
        if (activityContextMenus.isNotEmpty()) {
            return activityContextMenus
        }
        val createdContextMenus = ArrayList<ContextMenu>()
        originalContextMenus.forEach {
            //create new WTGContextMenus
            val newWTGContextMenuNode = ContextMenu.getOrCreateNode(nodeId = ContextMenu.getNodeId(), classType = wtgNode.activityClass)
            newWTGContextMenuNode.activityClass = wtgNode.activityClass
            copyNode(it, newWTGContextMenuNode)
            this.add(wtgNode, newWTGContextMenuNode, FakeEvent(wtgNode))
            AbstractStateManager.instance.createVirtualAbstractState(newWTGContextMenuNode)
            createdContextMenus.add(newWTGContextMenuNode)
        }
        return createdContextMenus
    }

    fun getDialogs(wtgNode: Window): List<Dialog> {
        val edges = this.edges(wtgNode).filter { it.destination != null }
                .filter { it.destination!!.data is Dialog
                        && (it.destination!!.data.activityClass.isBlank()
                        || it.destination!!.data.activityClass == wtgNode.activityClass)}
        val dialogs = edges.map { it.destination!!.data as Dialog }.toHashSet()
        dialogs.addAll(Dialog.allNodes.filter { it.activityClass == wtgNode.activityClass })
        return dialogs.toList()
    }

    fun getWindowBackward(wtgNode: Window): List<Edge<*, *>> {
        val pressBackEvents = arrayListOf<Edge<*, *>>()
        this.edges(wtgNode).filter { it.label.eventType == EventType.implicit_back_event }.forEach {
            pressBackEvents.add(it)
        }
        return pressBackEvents
    }

    fun containsGraph(childWTG: WindowTransitionGraph): Boolean {
        val childGraphRoot = childWTG.getNextRoot()
        if (childGraphRoot == null)
            return false
        var node: Window? = childGraphRoot
        val stack: Stack<Edge<Window, Input>> = Stack()
        val traversedEdge = arrayListOf<Edge<Window, Input>>()
        while (node != null) {
            val edges = childWTG.edges(node)
            val newEdges = edges.filter { !traversedEdge.contains(it) }
            if (newEdges.isEmpty()) {
                //return before
                if (stack.empty())
                    break
                val prevEdge = stack.pop()
                node = prevEdge.source.data
            } else {
                val validEdge = newEdges.first()
                if (this.edge(validEdge.source.data, validEdge.destination?.data, validEdge.label) == null) {
                    return false
                }
                traversedEdge.add(validEdge)
                stack.push(validEdge)
                node = validEdge.destination?.data
            }
        }
        return true
    }

    fun mergeNode(source: Window, dest: Window) {
        source.widgets.forEach {
            //source.widgets.remove(it)
            if (!dest.widgets.contains(it)) {
                dest.addWidget(it)
            }
        }
        source.widgets.clear()

        val edges = this.edges(source).toMutableList()
        edges.forEach {
            it.label.sourceWindow = dest
            this.add(dest, it.destination?.data, it.label)
        }

        this.getVertices().forEach { v ->
            val outEdges = this.edges(v)
            outEdges.filter { it.destination != null && it.destination!!.data == source }.forEach { e ->
                add(v.data, dest, e.label)
            }
        }
    }

    fun copyNode(source: Window, dest: Window) {
        source.widgets.forEach {
            if (!dest.widgets.contains(it)) {
                dest.addWidget(it)
            }
        }

        val edges = this.edges(source).toMutableList()
        edges.forEach {
            it.label.sourceWindow = dest
            this.add(dest, it.destination?.data, it.label)
        }

    }


    override fun add(source: Window, destination: Window?, label: Input, updateIfExists: Boolean, weight: Double): Edge<Window, Input> {
        val edge = graph.add(source, destination, label, updateIfExists, weight)
        edgeProved.put(edge, 0)
        edgeConditions.put(edge, HashMap())
        methodCoverageInfo.put(edge, ArrayList())
        statementCoverageInfo.put(edge, ArrayList())
        return edge
    }

    companion object {

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(WindowTransitionGraph::class.java) }


    }
}