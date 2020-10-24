package org.droidmate.exploration.modelFeatures.autaut.staticModel

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import kotlin.math.abs

class Helper {
    companion object {
        fun mergeOptionsMenusWithActivities(optionsMenuNodes: ArrayList<WTGNode>, newState: State<*>, activityNodes: ArrayList<WTGNode>,
                                            wtg: WindowTransitionGraph, autAutMF: AutAutMF) {
            var shouldMerge = false
            //FIXTHIS
            optionsMenuNodes.forEach { n ->
                val activityNode = activityNodes.find { wtg.getOptionsMenu(it)?.equals(n) ?: false }
                mergeOptionsMenuWithActivity(newState, n, activityNode!!, wtg, autAutMF)
            }

        }

        fun mergeOptionsMenuWithActivity(newState: State<*>, optionsMenuNode: WTGNode, activityNode: WTGNode, wtg: WindowTransitionGraph, autAutMF: AutAutMF): Boolean {

            var shouldMerge = false
            var containsOptionMenuWidgets = false
            var containsActivityWidgets = false
            var optionsMenuWidgets = ArrayList<StaticWidget>()
            var activityWidgets = ArrayList<StaticWidget>()
            newState.widgets.iterator().also {
                while (it.hasNext()) {
                    val widget = it.next()
                    optionsMenuWidgets.addAll(getStaticWidgets(widget, newState, optionsMenuNode, false, autAutMF))
                    if (optionsMenuWidgets.isEmpty()) {
                        containsOptionMenuWidgets = false
                    } else {
                        containsOptionMenuWidgets = true
                    }
                    if (containsOptionMenuWidgets)
                        break
                }
            }
            newState.widgets.iterator().also {
                while (it.hasNext()) {
                    val widget = it.next()
                    activityWidgets.addAll(getStaticWidgets(widget, newState, activityNode, false, autAutMF))
                    if (activityWidgets.isEmpty()) {
                        containsActivityWidgets = false
                    } else {
                        containsActivityWidgets = true
                    }
                    if (containsActivityWidgets)
                        break
                }
            }
            if (containsActivityWidgets && containsOptionMenuWidgets) {
                shouldMerge = true
            }
            if (shouldMerge) {
                AutAutMF.log.info("Merge $optionsMenuNode to $activityNode")
                wtg.mergeNode(optionsMenuNode, activityNode)
                /*transitionGraph.removeVertex(optionsMenuNode)
                regressionTestingMF.staticEventWindowCorrelation.filter { it.value.containsKey(optionsMenuNode) }.forEach { event, correlation ->
                    correlation.remove(optionsMenuNode)
                }*/


                return true
            }
            return false
        }

        fun calculateMatchScoreForEachNode(newState: State<*>, allPossibleNodes: List<WTGNode>, appName: String,
                                           autAutMF: AutAutMF): HashMap<WTGNode, Double> {
            val matchWidgets = HashMap<WTGNode, HashMap<Widget,HashSet<StaticWidget>>>()
            val missWidgets = HashMap<WTGNode, HashSet<Widget>>()
            val propertyChangedWidgets = HashMap<WTGNode, HashSet<Widget>>()
            val visibleWidgets = ArrayList<Widget>()
            visibleWidgets.addAll(getVisibleWidgets(newState))
            if (visibleWidgets.isEmpty()) {
                visibleWidgets.addAll(newState.widgets.filterNot { it.isKeyboard })
            }
            allPossibleNodes.forEach {
                matchWidgets[it] = HashMap()
                missWidgets[it] = HashSet()
                propertyChangedWidgets[it] = HashSet()
            }
            visibleWidgets.iterator().also {
                while (it.hasNext()) {
                    val widget = it.next()
                    allPossibleNodes.forEach {
                        val matchingWidget = getStaticWidgets(widget, newState, it, false, autAutMF)
                        if (matchingWidget.isNotEmpty()) {
                            if (matchWidgets.containsKey(it)) {
                                matchWidgets[it]!!.put(widget, HashSet(matchingWidget))
                            }
                        }
                        else
                        {
                            if (missWidgets.contains(it) && widget.resourceId.isNotBlank()) {
                                missWidgets[it]!!.add(widget)
                            }
                        }
                    }
                }
            }
            val scores = HashMap<WTGNode, Double>()
            allPossibleNodes.forEach {window ->
                val missStaticWidgets = window.widgets.filterNot { staticWidget -> matchWidgets[window]!!.values.any { it.contains(staticWidget) } }
                val totalWidgets = matchWidgets[window]!!.size + missStaticWidgets.size
                if (!window.fromModel && missWidgets.size/window.widgets.size.toDouble() > 0.7) {
                    //need match perfectly at least all widgets with resource id.
                    //Give a bound to be 70%
                    scores.put(window,Double.NEGATIVE_INFINITY)
                } else {
                    val score = if (matchWidgets[window]!!.size > 0 || window.widgets.size == 0) {
                        (matchWidgets[window]!!.size * 1.0 - missWidgets.size * 1.0) / totalWidgets
                    } else {
                        Double.NEGATIVE_INFINITY
                    }
                    scores.put(window, score)
                }
            }
            return scores
        }

        fun getVisibleInteractableWidgets(newState: State<*>) =
                getVisibleWidgets(newState).filter {
                    isInteractiveWidget(it)
                }

        fun getVisibleWidgets(state: State<*>) =
                state.widgets.filter { isVisibleWidget(it) }

        private fun isVisibleWidget(it: Widget) =
                it.enabled && (it.isVisible || it.visibleAreas.isNotEmpty()) && !it.isKeyboard

        fun getVisibleWidgetsForAbstraction(state: State<*>, packageName: String) =
                state.widgets.filter {
                    it.enabled
                            && it.isVisible
                            && it.visibleAreas.isNotEmpty()
                            && !it.isKeyboard
                            //&& !hasParentWithType(it, state, "WebView")
                            && isInteractiveWidget(it)
                            //&& it.packageName == packageName
                }

        fun getInputFields(state: State<*>) =
                Helper.getVisibleWidgets(state).filter { it.isInputField || it.checked.isEnabled() }

        fun getUnmappedWidgets(visibleWidgets: List<Widget>, bestMatchedNode: WTGNode, state: State<*>): List<Widget> {
            return visibleWidgets.filter { w ->
                bestMatchedNode.widgets.map { it.mappedRuntimeWidgets }
                        .find {
                            it.find {
                                it.second.uid == w.uid
                            } != null
                        } == null
            }.filter {
                !hasParentWithType(it, state, "WebView")
                        && it.resourceId != "android:id/floating_toolbar_menu_item_text"
            }
        }

        fun matchWidget(originalWidget: Widget, state: State<*>, wtgNode: WTGNode, updateModel: Boolean,
                        autAutMF: AutAutMF): StaticWidget? {
            var matchedStaticWidget: StaticWidget? = null
            val appName = autAutMF.getAppName()
            var widget = originalWidget
            if (widget.resourceId.isBlank() && !widget.className.contains("Button") && widget.contentDesc.isBlank()
                    && widget.text.isBlank() && !hasParentWithType(widget, state, "ListView")) {
                widget = tryGetParentHavingResourceId(widget, state)
            }
            if (widget.resourceId.isBlank() || hasParentWithType(widget, state, "ListView")) {
                //In this case, we try to use the xpath, then text and finally contentDesc to identify
                matchedStaticWidget = wtgNode.widgets.find {
                    it.xpath.isNotBlank() &&
                            it.xpath == originalWidget.xpath
                            && (it.mappedRuntimeWidgets.find { w -> w.second.text == originalWidget.text } != null
                            || it.contentDesc == originalWidget.contentDesc)
                }
            }
            if (matchedStaticWidget == null) {
                val unqualifiedResourceId = getUnqualifiedResourceId(widget)
                //Process firstly for menu item or some kind of list items created by Framework
                if ((widget.resourceId.startsWith("android:id/title")
                                || widget.resourceId.startsWith("$appName:id/title")
                                || widget.resourceId.contains("menu_item"))
                        && !widget.isInputField && state.widgets.filter { it.resourceId == widget.resourceId }.size > 1) {
                    //use text to map
                    if (widget.text.isNotBlank()) {
                        matchedStaticWidget = wtgNode.widgets.find { w ->
                            w.possibleTexts.contains(widget.text)
                        }
                    } else {
                        if (widget.contentDesc.isNotBlank()) {
                            matchedStaticWidget = wtgNode.widgets.find { w ->
                                widget.contentDesc == w.contentDesc ||
                                        w.possibleTexts.contains(widget.text)
                            }
                        }
                    }
                } else {
                    matchedStaticWidget = wtgNode.widgets.find {
                        it.resourceIdName == unqualifiedResourceId
                    }
                    if (matchedStaticWidget == null) {
                        if (widget.contentDesc.isNotBlank()) {
                            matchedStaticWidget = wtgNode.widgets.find { w ->
                                widget.contentDesc == w.contentDesc ||
                                        w.possibleTexts.contains(widget.text)
                            }
                        } else if (widget.text.isNotBlank()) {
                            matchedStaticWidget = wtgNode.widgets.find { w ->
                                w.possibleTexts.contains(widget.text)
                            }
                        }
                    }

                }
            }
            if (updateModel) {
                if (matchedStaticWidget != null) {
                    matchedStaticWidget.mappedRuntimeWidgets.add(Pair(state, originalWidget))
                    //addRuntimeWigetInfo(matchedStaticWidget, originalWidget, state)
                    matchedStaticWidget.xpath = originalWidget.xpath
                    matchedStaticWidget.isInputField = originalWidget.isInputField
                    updateInteractiveWidget(originalWidget, state, matchedStaticWidget, wtgNode, false, autAutMF)
                    if (originalWidget.isInputField && originalWidget.text.isNotBlank()) {
                        matchedStaticWidget.textInputHistory.add(originalWidget.text)
                    }
                    if (wtgNode is WTGAppStateNode && !originalWidget.isInputField && originalWidget.text != matchedStaticWidget.appStateTextProperty[wtgNode]) {
                        matchedStaticWidget.appStateTextProperty[wtgNode] = originalWidget.text
                    }
                } else {
                    //Create new StaticWidget
                    if (shouldCreateNewWidget(originalWidget)) {
                        val unqualifiedResourceId = getUnqualifiedResourceId(widget)
                        val staticWidget = StaticWidget.getOrCreateStaticWidget(StaticWidget.getWidgetId(), unqualifiedResourceId, "", originalWidget.className, wtgNode.classType, wtgNode)
                        matchedStaticWidget = staticWidget
                        staticWidget.contentDesc = originalWidget.contentDesc
                        staticWidget.xpath = originalWidget.xpath
                        staticWidget.className == originalWidget.className
                        if (!originalWidget.isInputField) {
                            staticWidget.text = originalWidget.nlpText
                            if (!staticWidget.possibleTexts.contains(originalWidget.text))
                                staticWidget.possibleTexts.add(originalWidget.text)
                            if (wtgNode is WTGAppStateNode && originalWidget.text != matchedStaticWidget.appStateTextProperty[wtgNode]) {
                                matchedStaticWidget.appStateTextProperty[wtgNode] = originalWidget.text
                            }
                        }
                        //add mappedRuntime
                        staticWidget.mappedRuntimeWidgets.add(Pair(state, originalWidget))
                        //addRuntimeWigetInfo(staticWidget, originalWidget, state)
                        updateInteractiveWidget(originalWidget, state, matchedStaticWidget, wtgNode, true, autAutMF)
                    }

                }
            }
            return matchedStaticWidget
        }

        fun getStaticWidgets(originalWidget: Widget, state: State<*>, wtgNode: WTGNode, updateModel: Boolean,
                             autAutMF: AutAutMF): List<StaticWidget> {
            var matchedStaticWidgets: ArrayList<StaticWidget> = ArrayList()
            val appName = autAutMF.getAppName()
            var widget = originalWidget
            if (widget.resourceId.isNotBlank()) {
                val unqualifiedResourceId = getUnqualifiedResourceId(widget)

                matchedStaticWidgets.addAll(wtgNode.widgets.filter {
                    if (widget.resourceId == "android:id/title") {
                        it.resourceIdName == unqualifiedResourceId && it.text == widget.text
                    } else {
                        it.resourceIdName == unqualifiedResourceId
                    }
                })
            }
            if (matchedStaticWidgets.isEmpty() && widget.contentDesc.isNotBlank()) {
                matchedStaticWidgets.addAll(wtgNode.widgets.filter { w ->
                    widget.contentDesc == w.contentDesc
                })
            }
            if (matchedStaticWidgets.isEmpty() && !widget.isInputField && widget.text.isNotBlank()) {
                matchedStaticWidgets.addAll(wtgNode.widgets.filter { w ->
                    w.possibleTexts.contains(widget.text)
                })
            }
            if (matchedStaticWidgets.isEmpty()
                    && (widget.className == "android.widget.RelativeLayout" || widget.className.contains("ListView") ||  widget.className.contains("RecycleView" ) ||  widget.className == "android.widget.LinearLayout"))
            {
                matchedStaticWidgets.addAll(wtgNode.widgets.filter { w ->
                    w.className.contains(widget.className) && w.resourceId.isBlank() && w.resourceIdName.isBlank()
                })
            }
            if (matchedStaticWidgets.isEmpty() &&
                    (hasParentWithType(widget, state, "ListView") || hasParentWithType(widget, state, "RecycleView"))) {
                //this is an item of ListView or RecycleView

            }
            if (updateModel) {
                matchedStaticWidgets.forEach {
                    if (originalWidget.isInputField && originalWidget.text.isNotBlank()) {
                        it.textInputHistory.add(originalWidget.text)
                    }

                }
                if (matchedStaticWidgets.isEmpty()) {
                    if (originalWidget.resourceId == "android:id/content") {
                        return matchedStaticWidgets
                    }
                    if (originalWidget.resourceId.isNotBlank()
                            || originalWidget.contentDesc.isNotBlank()) {
                        val newWidget = StaticWidget.getOrCreateStaticWidget(
                                widgetId = StaticWidget.getWidgetId(),
                                resourceIdName = getUnqualifiedResourceId(originalWidget.resourceId),
                                className = originalWidget.className,
                                wtgNode = wtgNode,
                                resourceId = "",
                                activity = wtgNode.activityClass
                        )
                        newWidget.contentDesc = originalWidget.contentDesc
                        if (originalWidget.resourceId == "android:id/title") {
                            newWidget.text = originalWidget.text
                        }
                        wtgNode.addWidget(newWidget)
                        matchedStaticWidgets.add(newWidget)
                    }
                }
            }
            return matchedStaticWidgets
        }

        fun updateInteractiveWidget(widget: Widget, state: State<*>, staticWidget: StaticWidget, wtgNode: WTGNode
                                    , isNewCreatedWidget: Boolean, autAutMF: AutAutMF) {
            val isInteractive: Boolean
            if (!isInteractiveWidget(widget)) {
                if (widget.className == "android.widget.ImageView" && hasParentWithType(widget, state, "Gallery")) {
                    isInteractive = true
                } else {
                    isInteractive = false
                }
            } else {
                isInteractive = true
            }
            wtgNode.widgetState[staticWidget] = isInteractive
            if (isNewCreatedWidget)
                staticWidget.interactive = isInteractive
//            if (!wtgNode.widgetState[staticWidget]!!)
//            {
//                regressionTestingMF.transitionGraph.edges(wtgNode).filter { it.label.widget == staticWidget }.forEach {
//                    regressionTestingMF.addDisablePathFromState(state,it.label,it.destination!!.data)
//                }
//            }
        }

        private fun shouldCreateNewWidget(widget: Widget) =
                (!widget.isKeyboard
                        && (widget.resourceId.isNotBlank() || isInteractiveWidget(widget)))

        fun getMappedGUIWidgets(visibleWidgets: List<Widget>, sourceNode: WTGNode): HashMap<Widget, StaticWidget> {
            val mappedGUIWidgets = HashMap<Widget, StaticWidget>()
            for (guiWidget in visibleWidgets) {
                for (widget in sourceNode.widgets) {
                    if (widget.containGUIWidget(guiWidget)) {
                        mappedGUIWidgets.put(guiWidget, widget)
                        break
                    }
                }
            }
            return mappedGUIWidgets
        }

        fun copyStaticWidgetAndItsEvents(staticWidget: StaticWidget, newNode: WTGNode, sourceNode: WTGNode
                                         , wtg: WindowTransitionGraph) {
            if (!newNode.widgets.contains(staticWidget))
                newNode.widgets.add(staticWidget)
            val relatedEdges = wtg.edges(sourceNode).filter {
                it.label.widget == staticWidget
            }
            relatedEdges.forEach {
                val relatedEvent = it.label
                if (it.destination?.data == sourceNode)
                    wtg.add(newNode, newNode, relatedEvent)
                else
                    wtg.add(newNode, it.destination?.data, relatedEvent)
            }
        }

        private fun moveStaticWidgetAndItsEvents(staticWidget: StaticWidget, newNode: WTGNode, sourceNode: WTGNode
                                                 , wtg: WindowTransitionGraph) {
            if (!newNode.widgets.contains(staticWidget))
                newNode.widgets.add(staticWidget)
            sourceNode.widgets.remove(staticWidget)
            val relatedEdges = wtg.edges(sourceNode).filter {
                it.label.widget == staticWidget
            }
            relatedEdges.forEach {
                wtg.add(newNode, it.destination?.data, it.label)
                wtg.update(sourceNode, it.destination?.data, WTGFakeNode(), it.label, it.label).also {
                    if (it != null && wtg.edgeProved.containsKey(it))
                        wtg.edgeProved.remove(it)

                }
            }

        }

        internal var changeRatioCriteria: Double = 0.05
        fun checkShouldCreateNewNode(newState: State<*>, bestMatchedNode: WTGNode, currentRotation: Int, appName: String,
                                     autAutMF: AutAutMF): Boolean {
            if (bestMatchedNode.rotation != currentRotation)
                return true
            var newWidgets = ArrayList<Widget>()
            var changeWidgets = HashMap<StaticWidget, String>()
            val visibleWidgets = getVisibleWidgets(newState)
            val matchedWidgets = HashMap<StaticWidget, Boolean>()
            bestMatchedNode.widgets.filter { it.mappedRuntimeWidgets.isNotEmpty() }.forEach {
                matchedWidgets.put(it, false)
            }
            val mappedWidgets = getMappedGUIWidgets(visibleWidgets, bestMatchedNode)
            mappedWidgets.forEach { w, sw ->
                matchedWidgets.put(sw, true)
                if (!w.isInputField) {
                    if (sw.mappedRuntimeWidgets.find { sw.possibleTexts.contains(it.second.text) } == null)
                        changeWidgets.put(sw, w.text)
                }
            }
            val unmappedWidgets = visibleWidgets.filter { !mappedWidgets.containsKey(it) }
            unmappedWidgets.forEach {
                var matchedStaticWidget: StaticWidget?
                matchedStaticWidget = matchWidget(it, newState, bestMatchedNode, false, autAutMF)
                if (matchedStaticWidget != null) {
                    //new widget found
                    matchedWidgets[matchedStaticWidget] = true
                    //This is really a new widget
                    if (matchedStaticWidget.mappedRuntimeWidgets.isEmpty())
                        newWidgets.add(it)
                } else {
                    var widget = it
                    if (shouldCreateNewWidget(widget))
                        newWidgets.add(it)
                }

            }
            if (newWidgets.size / matchedWidgets.size.toDouble() > 0.05) {
                return true
            }
            //check lost identified widget
            var lostWidgets = matchedWidgets.filter { it.value == false }

            if (lostWidgets.size / matchedWidgets.size.toDouble() > 0.05) {
//            if (lostWidget.find { it.className!="android.widget.Image" } != null)
//                return true
                return true
            }
            return false
            if (changeWidgets.size / matchedWidgets.size.toDouble() > 0.1) {
                return true
            }
            return false
        }

        fun transferStaticWidgetsAndEvents(sourceNode: WTGNode, newNode: WTGNode, newState: State<*>, appName: String
                                           , autAutMF: AutAutMF) {
            autAutMF.wtg.edges(sourceNode).filter { it.label.widget == null }.forEach {
                if (it.destination?.data == sourceNode)
                    autAutMF.wtg.add(newNode, newNode, it.label)
                else
                    autAutMF.wtg.add(newNode, it.destination?.data, it.label)
            }
            //Copy mapped widget
            val visibleWidgets = getVisibleWidgets(newState)
            val mappedWidgets = getMappedGUIWidgets(visibleWidgets, sourceNode)
            mappedWidgets.forEach { w, sw ->
                if (!sw.possibleTexts.contains(w.text) && sw.possibleTexts.isNotEmpty() && !w.isInputField) // it should be an similar widget
                    copyStaticWidgetAndItsEvents(sw, newNode, sourceNode, autAutMF.wtg)
                else
                    copyStaticWidgetAndItsEvents(sw, newNode, sourceNode, autAutMF.wtg)
            }

            //process unmappedWidget
            val unmappedWidgets = visibleWidgets.filter { !mappedWidgets.containsKey(it) }
            unmappedWidgets.forEach {
                var matchedStaticWidget: StaticWidget? = matchWidget(it, newState, sourceNode, false, autAutMF)

                if (matchedStaticWidget != null) {
                    if (matchedStaticWidget.mappedRuntimeWidgets.isEmpty()) {
                        //move if this static widget never mapped before
                        //change owner wtgNode
                        copyStaticWidgetAndItsEvents(matchedStaticWidget, newNode, sourceNode, autAutMF.wtg)
                        //update event

                    } else {
                        //copy else
                        if (!matchedStaticWidget.possibleTexts.contains(it.text))
                            copyStaticWidgetAndItsEvents(matchedStaticWidget, newNode, sourceNode, autAutMF.wtg)
                        else
                            copyStaticWidgetAndItsEvents(matchedStaticWidget, newNode, sourceNode, autAutMF.wtg)
                    }
                }
            }
        }

        fun isInteractiveWidget(widget: Widget): Boolean =
                widget.enabled && ( widget.isInputField || widget.clickable || widget.checked != null || widget.longClickable || widget.scrollable || (!widget.hasClickableDescendant && widget.selected.isEnabled() && widget.selected == true) )

        fun hasParentWithType(it: Widget, state: State<*>, parentType: String): Boolean {
            var widget: Widget = it
            while (widget.hasParent) {
                val parent = state.widgets.find { w -> w.id == widget.parentId }
                if (parent != null) {
                    if (parent.className.contains(parentType)) {
                        return true
                    }
                    widget = parent
                } else {
                    return false
                }
            }
            return false
        }

        fun hasParentWithResourceId(widget: Widget, state: State<*>, parentIdPatterns: List<String>): Boolean {
            var w: Widget = widget
            while (w.hasParent) {
                val parent = state.widgets.find { it -> it.id == w.parentId }
                if (parent != null) {
                    if (parentIdPatterns.find { w.resourceId.contains(it) } != null) {
                        return true
                    }
                    w = parent
                } else {
                    return false
                }
            }
            return false
        }

        fun tryGetParentHavingResourceId(widget: Widget, currentState: State<*>): Widget {
            var parentWidget: Widget? = widget
            while (parentWidget != null) {
                if (parentWidget.resourceId.isNotBlank())
                    return parentWidget
                parentWidget = currentState.widgets.find {
                    it.idHash == parentWidget!!.parentHash
                }

            }
            return widget
        }

        fun getUnqualifiedResourceId(widget: Widget): String {
            val unqualifiedResourceId = widget.resourceId.substring(widget.resourceId.indexOf("/") + 1)
            return unqualifiedResourceId
        }

        fun getUnqualifiedResourceId(qualifiedResourceId: String): String {
            val unqualifiedResourceId = qualifiedResourceId.substring(qualifiedResourceId.indexOf("/") + 1)
            return unqualifiedResourceId
        }

        fun haveClickableChild(allWidgets: List<Widget>, parent: Widget): Boolean {
            val allChildren = ArrayList<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null) {
                    allChildren.add(childWidget)
                    if (childWidget.clickable) {
                        return true
                    }
                }
            }
            allChildren.forEach {
                if (haveClickableChild(allWidgets,it)) {
                    return true
                }
            }
            return false
        }

        fun haveLongClickableChild(allWidgets: List<Widget>, parent: Widget): Boolean {
            val allChildren = ArrayList<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null) {
                    allChildren.add(childWidget)
                    if (childWidget.longClickable) {
                        return true
                    }
                }
            }
            allChildren.forEach {
                if (haveClickableChild(allWidgets,it)) {
                    return true
                }
            }
            return false
        }

        fun haveScrollableChild(allWidgets: List<Widget>, parent: Widget): Boolean {
            val allChildren = ArrayList<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null) {
                    allChildren.add(childWidget)
                    if (childWidget.scrollable) {
                        return true
                    }
                }
            }
            allChildren.forEach {
                if (haveClickableChild(allWidgets,it)) {
                    return true
                }
            }
            return false
        }

        fun getAllInteractiveChild(allWidgets: List<Widget>, parent: Widget): List<Widget> {
            val interactiveWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null) {
                    if (isInteractiveWidget(childWidget) && isVisibleWidget(childWidget)) {
                        interactiveWidgets.add(childWidget)
                    }
                    interactiveWidgets.addAll(getAllInteractiveChild(allWidgets, childWidget))
                }

            }
            return interactiveWidgets
        }

        fun getAllInteractiveChild2(allWidgets: List<Widget>, parent: Widget): List<Widget> {
            val interactiveWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null) {
                    if (childWidget.canInteractWith) {
                        interactiveWidgets.add(childWidget)
                    }
                    interactiveWidgets.addAll(getAllInteractiveChild2(allWidgets, childWidget))
                }

            }
            return interactiveWidgets
        }

        fun getAllChild(allWidgets: List<Widget>, parent: Widget): List<Widget> {
            val visibleWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null) {
                    if (childWidget.isVisible) {
                        visibleWidgets.add(childWidget)
                    }
                    visibleWidgets.addAll(getAllChild(allWidgets, childWidget))
                }

            }
            return visibleWidgets
        }

        fun computeGuiTreeDimension(guiState: State<*>): Rectangle {
            val outboundViews = guiState.widgets.filter { !it.hasParent && !it.isKeyboard }
            if (outboundViews.isNotEmpty()) {
                val outBound = outboundViews.maxBy { it.boundaries.height+it.boundaries.width }!!.boundaries
                return outBound
            }
            val bound = guiState.widgets.sortedBy { it.boundaries.width + it.boundaries.height }.last().boundaries
            return bound
        }

        fun computeGuiTreeVisibleDimension(guiState: State<*>): Rectangle {
            val outboundViews = guiState.widgets.filter { !it.hasParent && !it.isKeyboard }
            if (outboundViews.isNotEmpty()) {
                val outBound = outboundViews.maxBy { it.visibleBounds.height+it.visibleBounds.width }!!.visibleBounds
                return outBound
            }
            val bound = guiState.widgets.sortedBy { it.visibleBounds.width + it.visibleBounds.height }.last().visibleBounds
            return bound
        }

         fun parseSwipeData(data: String): List<Pair<Int, Int>> {
            val splitData = data.split(" TO ")
            if (splitData.size != 2) {
                return emptyList()
            }
            val first = splitData[0].split(",").let { Pair(first = it[0].toInt(),second = it[1].toInt())}
            val second = splitData[1].split(",").let { Pair(first = it[0].toInt(),second = it[1].toInt())}
            return arrayListOf(first,second)
        }

        fun computeStep(swipeInfo: List<Pair<Int, Int>>): Int {
            val dx = abs(swipeInfo[0].first-swipeInfo[1].first)
            val dy = abs(swipeInfo[0].second-swipeInfo[1].second)
            return (dx+dy)/2
        }

        fun parseCoordinationData(data: String): Pair<Int,Int> {
            val splitData = data.split(",")
            if (splitData.size == 2) {
                return Pair(splitData[0].toInt(),splitData[1].toInt())
            }
            return Pair(0,0)
        }

        fun extractInputFieldAndCheckableWidget(prevState: State<*>): Map<Widget,String> {
            val condition = HashMap<Widget, String>()
            prevState.visibleTargets.filter { it.isInputField }.forEach { widget ->
                condition.put(widget, widget.text)
            }
            prevState.visibleTargets.filter { it.checked.isEnabled() }.forEach { widget ->
                condition.put(widget, widget.checked.toString())
            }
            return condition
        }

        fun extractTextInputWidgetData(sourceNode: WTGNode, prevState: State<*>): HashMap<StaticWidget, String> {
            val inputTextData = HashMap<StaticWidget, String>()
            sourceNode.widgets.filter { it.isInputField }.forEach {
                val textInputWidget = prevState.widgets.find { w ->
                    it.containGUIWidget(w)
                }
                if (textInputWidget != null) {
                    inputTextData.put(it, textInputWidget.text)
                }
            }
            return inputTextData
        }
    }
}