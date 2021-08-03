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

package org.atua.modelFeatures.ewtg

import org.droidmate.exploration.modelFeatures.graph.*
import org.atua.modelFeatures.*
import org.atua.modelFeatures.ewtg.window.Activity
import org.atua.modelFeatures.ewtg.window.ContextMenu
import org.atua.modelFeatures.ewtg.window.Dialog
import org.atua.modelFeatures.ewtg.window.FakeWindow
import org.atua.modelFeatures.ewtg.window.Launcher
import org.atua.modelFeatures.ewtg.window.OptionsMenu
import org.atua.modelFeatures.ewtg.window.Window
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class EWTG(private val graph: IGraph<Window, WindowTransition> =
                                    Graph(
                                        FakeWindow(nodeId = "Root", isBaseModel = false) as Window,
                                            stateComparison = { a, b -> a == b },
                                            labelComparison = { a, b ->
                                                a == b
                                            })) : IGraph<Window, WindowTransition> by graph {
    init {
        Launcher.getOrCreateNode()
    }

    fun dump(bufferedWriter: BufferedWriter) {

    }

    fun constructFromJson(jObj: JSONObject) {
        var jMap = jObj.getJSONObject("allActivityNodes")
        jMap.keys().asSequence().forEach { key ->
            val windowInfo = StaticAnalysisJSONParser.windowParser(jMap[key]!! as String)
            //val sourceNode = getOrCreateWTGNode(windowInfo)
        }

        jMap = jObj.getJSONObject("allTransitions")
        //for each Activity transition
        jMap.keys().asSequence().forEach { key ->
            val source = key as String
            log.debug("source: $source")
            val windowInfo = StaticAnalysisJSONParser.windowParser(source)
            val sourceNode = getOrCreateWTGNode(windowInfo)
            if (sourceNode is Launcher) {
                val event = LaunchAppEvent(sourceNode)
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
                    // log.debug("action: $action")
                    // log.debug("target: $target")
                    val targetInfo = StaticAnalysisJSONParser.windowParser(target)
                    val targetNode = getOrCreateWTGNode(targetInfo)
                    if (targetNode is OptionsMenu || sourceNode is Launcher) {
                        ignoreWidget = true
                    }
                    val ewtgWidget: EWTGWidget?

                    if (ignoreWidget == false) {
                        val targetView = transition["widget"] as String
                        // log.info("parsing widget: $targetView")
                        val widgetInfo = StaticAnalysisJSONParser.widgetParser(targetView)
                        if (widgetInfo.containsKey("resourceId") && widgetInfo.containsKey("resourceIdName")) {
                            ewtgWidget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                    resourceId = widgetInfo["resourceId"]!!,
                                    resourceIdName = widgetInfo["resourceIdName"]!!,
                                    className = widgetInfo["className"]!!,
                                    wtgNode = sourceNode)
                        } else if (widgetInfo.containsKey("className") && widgetInfo.containsKey("id")) {
                            ewtgWidget = EWTGWidget.getOrCreateStaticWidget(widgetId = widgetInfo["id"]!!,
                                    className = widgetInfo["className"]!!,
                                    wtgNode = sourceNode)
                        } else {
                            ewtgWidget = null
                        }
                    } else {
                        ewtgWidget = null
                    }
                    if (Input.isNoWidgetEvent(action) || (!Input.isNoWidgetEvent(action) && ewtgWidget != null)) {
                        val event = Input.getOrCreateInput(
                                eventTypeString = action,
                                eventHandlers = emptySet(),
                                widget = ewtgWidget,
                                sourceWindow = sourceNode

                        )
                        //event = StaticEvent(EventType.valueOf(action), arrayListOf(), staticWidget, sourceNode.classType, sourceNode)
                        val windowTransition = WindowTransition(
                                source = sourceNode,
                                destination = targetNode,
                                input = event,
                                prevWindow = null
                        )
                        this.add(sourceNode,targetNode,windowTransition)
                        /*if (ewtgWidget != null && ewtgWidget!!.className.contains("Layout")) {
                            var createItemClick = false
                            var createItemLongClick = false
                            *//*when (action) {
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
                            }*//*
                            if (createItemClick) {
                                //create item click and long click
                                val itemClick = Input.getOrCreateEvent(
                                        eventHandlers = emptySet(),
                                        eventTypeString = "item_click",
                                        widget = ewtgWidget,
                                        sourceWindow = sourceNode)

                                this.add(sourceNode, targetNode, WindowTransition(sourceNode,targetNode,itemClick,null))
                            }
                            if (createItemLongClick) {
                                //create item click and long click
                                val itemLongClick = Input.getOrCreateEvent(
                                        eventHandlers = emptySet(),
                                        eventTypeString = "item_long_click",
                                        widget = ewtgWidget,
                                        sourceWindow = sourceNode)
                                this.add(sourceNode, targetNode, WindowTransition(sourceNode,targetNode,itemLongClick,null))
                            }
                        }*/
                    }
                }

                //construct graph
            }
        }
        WindowManager.instance.updatedModelWindows.filter { it is OptionsMenu }.forEach { o ->
            val owner = WindowManager.instance.updatedModelWindows.find { a -> a is Activity && a.classType == o.classType }
            if (owner != null) {
                val edges = this.edges(owner, o)

                if (edges.isEmpty()) {
                    val input = Input(eventType = EventType.implicit_menu,
                            eventHandlers = HashSet(),
                            widget = null,
                            sourceWindow = o)
                    this.add(owner, o, WindowTransition(owner,o,input,null))
                }
            }

        }

    }

    fun getOrCreateWTGNode(windowInfo: HashMap<String, String>): Window {
        val wtgNode = when (windowInfo["NodeType"]) {
            "ACT" -> Activity.getOrCreateNode(windowInfo["id"]!!, windowInfo["className"]!!, false, false)
            "DIALOG" -> Dialog.getOrCreateNode(windowInfo["id"]!!, windowInfo["className"]!!, windowInfo["allocMethod"]!!, false, false)
            "OptionsMenu" -> OptionsMenu.getOrCreateNode(windowInfo["id"]!!, windowInfo["className"]!!, false, false)
            "ContextMenu" -> ContextMenu.getOrCreateNode(windowInfo["id"]!!, windowInfo["className"]!!, false, false)
            "LAUNCHER_NODE" -> Launcher.getOrCreateNode()
            else -> throw Exception("Not supported windowType")
        }
        if (wtgNode is OptionsMenu) {
            val activityNode = WindowManager.instance.updatedModelWindows.find { it.classType == wtgNode.classType && it is Activity }
            if (activityNode != null) {
                if (this.edges(activityNode, wtgNode).isEmpty()) {
                    val input = Input(EventType.press_menu, HashSet(), null, sourceWindow = activityNode)
                    this.add(activityNode, wtgNode, WindowTransition(activityNode,wtgNode,input,null))
                }
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
        if (outEdges.find { it.destination != null && it.destination!!.data is OptionsMenu && it.label.input.eventType != EventType.implicit_back_event } != null)
            return true
        if (outEdges.find { it.destination != null && it.label.input.eventType == EventType.implicit_menu } != null)
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
                    && it.label.input.eventType != EventType.implicit_back_event
        }
        return edges.map { it.destination!!.data }.firstOrNull()
    }

    fun getContextMenus(wtgNode: Window): List<ContextMenu> {
        val edges = this.edges(wtgNode).filter { it.destination != null }
                .filter { it.destination!!.data is ContextMenu }
        val windows = edges.map { it.destination!!.data as ContextMenu }.toMutableList()
        val originalContextMenus = windows.filter { it.classType != wtgNode.classType }
        val activityContextMenus = windows.filterNot { it.classType != wtgNode.classType }
        if (activityContextMenus.isNotEmpty()) {
            return activityContextMenus
        }
        return originalContextMenus
    }

    fun getDialogs(wtgNode: Window): List<Dialog> {
        val edges = this.edges(wtgNode).filter { it.destination != null }
                .filter {
                    it.destination!!.data is Dialog
                }
        val dialogs = edges.map { it.destination!!.data as Dialog }.toHashSet()
        dialogs.addAll(WindowManager.instance.updatedModelWindows.filter { it is Dialog && it.ownerActivitys.contains(wtgNode)} as List<Dialog>)
        return dialogs.toList()
    }

    fun getWindowBackward(wtgNode: Window): List<Edge<*, *>> {
        val pressBackEvents = arrayListOf<Edge<*, *>>()
        this.edges(wtgNode).filter { it.label.input.eventType == EventType.implicit_back_event }.forEach {
            pressBackEvents.add(it)
        }
        return pressBackEvents
    }

    fun containsGraph(childWTG: EWTG): Boolean {
        val childGraphRoot = childWTG.getNextRoot()
        if (childGraphRoot == null)
            return false
        var node: Window? = childGraphRoot
        val stack: Stack<Edge<Window, WindowTransition>> = Stack()
        val traversedEdge = arrayListOf<Edge<Window, WindowTransition>>()
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
                it.window = dest
            }
        }
        source.widgets.clear()

        val edges = this.edges(source).toMutableList()
        edges.forEach {
            val newTransition = WindowTransition(
                    source = dest,
                    destination = it.destination!!.data,
                    input = it.label.input,
                    prevWindow = it.label.prevWindow
            )
            newTransition.input.sourceWindow = dest
            this.add(newTransition.source, newTransition.destination, newTransition)
        }

        this.getVertices().forEach { v ->
            val outEdges = this.edges(v)
            outEdges.filter { it.destination != null && it.destination!!.data == source }.forEach { e ->
                val newTransition = WindowTransition(
                        source = v.data,
                        destination = dest,
                        input = e.label.input,
                        prevWindow = e.label.prevWindow
                )
                newTransition.input.sourceWindow = v.data
                this.add(newTransition.source, newTransition.destination, newTransition)
            }
        }
    }

    override fun add(source: Window, destination: Window?, label: WindowTransition, updateIfExists: Boolean, weight: Double): Edge<Window, WindowTransition> {
        val edge = graph.add(source, destination, label, updateIfExists, weight)
        return edge
    }

    companion object {

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(EWTG::class.java) }


    }
}