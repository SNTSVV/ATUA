package org.droidmate.exploration.modelFeatures.autaut.staticModel

import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.WidgetGroup
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class Helper {
    companion object
    {
        fun mergeOptionsMenusWithActivities(optionsMenuNodes: ArrayList<WTGNode>, newState: State<*>, activityNodes: ArrayList<WTGNode>,
                                            transitionGraph: TransitionGraph, regressionTestingMF: RegressionTestingMF) {
            var shouldMerge = false
            //FIXTHIS
            optionsMenuNodes.forEach { n ->
                val activityNode = activityNodes.find { transitionGraph.getOptionsMenu(it)?.equals(n) ?: false }
                mergeOptionsMenuWithActivity(newState,n,activityNode!!,transitionGraph,regressionTestingMF)
                }

        }
        fun mergeOptionsMenuWithActivity(newState: State<*>, optionsMenuNode: WTGNode, activityNode: WTGNode, transitionGraph: TransitionGraph, regressionTestingMF: RegressionTestingMF): Boolean {

            var shouldMerge = false
            var containsOptionMenuWidgets = false
            var containsActivityWidgets = false
            newState.widgets.iterator().also {
                while (it.hasNext()) {
                    val widget = it.next()
                     containsOptionMenuWidgets = matchWidget(widget,newState,optionsMenuNode,false,regressionTestingMF)?.let{true }?:false
                    if (containsOptionMenuWidgets)
                        break

                }
            }
            newState.widgets.iterator().also {
                while (it.hasNext())
                {
                    val widget = it.next()
                    containsActivityWidgets = matchWidget(widget,newState,activityNode,false,regressionTestingMF)?.let { true }?:false
                    if (containsActivityWidgets)
                        break
                }
            }
            if (containsActivityWidgets && containsOptionMenuWidgets) {
                shouldMerge = true
            }
            if (shouldMerge) {
                RegressionTestingMF.log.info("Merge $optionsMenuNode to $activityNode")
                transitionGraph.mergeNode(optionsMenuNode, activityNode)
                transitionGraph.removeVertex(optionsMenuNode)
                regressionTestingMF.staticEventWindowCorrelation.filter { it.value.containsKey(optionsMenuNode) }.forEach { event, correlation ->
                    correlation.remove(optionsMenuNode)
                }


                return true
            }
            return false
        }

        fun calculateMatchScoreForEachNode(newState: State<*>, allPossibleNodes: List<WTGNode>, appName: String,
                                           regressionTestingMF: RegressionTestingMF): HashMap<WTGNode, Double> {
            val matchWidgets = HashMap<WTGNode, Int>()
            val missWidgets = HashMap<WTGNode,Int>()
            val propertyChangedWidgets = HashMap<WTGNode, Int>()
            val visibleWidgets = ArrayList<Widget>()
            visibleWidgets.addAll(getVisibleWidgets(newState))
            if (visibleWidgets.isEmpty())
            {
                visibleWidgets.addAll(newState.widgets.filterNot{it.isKeyboard })
            }
            allPossibleNodes.forEach {
                matchWidgets[it]=0
                missWidgets[it]=0
                propertyChangedWidgets[it]=0
            }
            visibleWidgets.iterator().also {
                while (it.hasNext()) {
                    val widget = it.next()
                    allPossibleNodes.forEach {
                        val matchingWidget = matchWidget(widget,newState,it,false,regressionTestingMF)
                        if (matchingWidget!= null)
                        {
                            if (matchWidgets.contains(it)) {
                                matchWidgets[it] = matchWidgets[it]!! + 1
                            } else {
                                matchWidgets[it] = 1
                            }
                            //TODO Fix
                            if (it is WTGAppStateNode && matchingWidget!!.appStateTextProperty[it]!=widget.text)
                            {
                                if (propertyChangedWidgets.contains(it)) {
                                    propertyChangedWidgets[it] = propertyChangedWidgets[it]!! + 1
                                } else {
                                    propertyChangedWidgets[it] = 1
                                }
                            }
                        }
                        else
                        {
                            if (missWidgets.contains(it)) {
                                missWidgets[it] = missWidgets[it]!! + 1
                            } else {
                                missWidgets[it] = 1
                            }
                        }
                    }
                }
            }
            val scores = HashMap<WTGNode,Double>()
            allPossibleNodes.forEach {
                val totalWidgets = visibleWidgets.size
                val score = ((matchWidgets[it]!!)*1.0-(missWidgets[it]!!)*1.0-(propertyChangedWidgets[it]!!)*0.5)/totalWidgets
                scores.put(it,score)
            }
            return scores
        }
        fun getVisibleInteractableWidgets(newState: State<*>) =
                getVisibleWidgets(newState).filter {
                    isInteractiveWidget(it)
                            || (
                            hasParentWithType(it,newState,"ListView")
                                    && it.className.contentEquals("LinearLayout"))
                }

        fun getVisibleWidgets(state: State<*>) =
                state.widgets.filter {it.enabled &&  it.isVisible && !it.isKeyboard && it.visibleAreas.isNotEmpty()}

        fun getInputFields(state: State<*>)=
                state.widgets.filter { it.isInputField || it.checked != null }
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

        fun matchWidget(originalWidget: Widget, state: State<*>, wtgNode: WTGNode,updateModel: Boolean,
                        regressionTestingMF: RegressionTestingMF): StaticWidget? {
            var matchedStaticWidget: StaticWidget? = null
            val appName = regressionTestingMF.getAppName()
            var widget = originalWidget
            if (widget.resourceId.isBlank() && !widget.className.contains("Button") && widget.contentDesc.isBlank()
                    && widget.text.isBlank() && !hasParentWithType(widget,state,"ListView")) {
                widget = tryGetParentHavingResourceId(widget, state)
            }
            if (widget.resourceId.isBlank() || hasParentWithType(widget,state,"ListView"))
            {
                //In this case, we try to use the xpath, then text and finally contentDesc to identify
                matchedStaticWidget = wtgNode.widgets.find {
                    it.xpath.isNotBlank() &&
                            it.xpath == originalWidget.xpath
                            && (it.mappedRuntimeWidgets.find { w->w.second.text == originalWidget.text } !=null
                            || it.contentDesc == originalWidget.contentDesc)
                }
            }
            if (matchedStaticWidget == null)
            {
                val unqualifiedResourceId = getUnqualifiedResourceId(widget)
                //Process firstly for menu item or some kind of list items created by Framework
                if ((widget.resourceId.startsWith("android:id/title")
                                || widget.resourceId.startsWith("$appName:id/title")
                                || widget.resourceId.contains("menu_item"))
                        && !widget.isInputField && state.widgets.filter { it.resourceId==widget.resourceId }.size>1){
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
                }
                else
                {
                    matchedStaticWidget = wtgNode.widgets.find {
                        it.resourceIdName == unqualifiedResourceId
                    }
                    if (matchedStaticWidget == null)
                    {
                        if (widget.contentDesc.isNotBlank()) {
                            matchedStaticWidget = wtgNode.widgets.find { w ->
                                widget.contentDesc == w.contentDesc ||
                                        w.possibleTexts.contains(widget.text)
                            }
                        }
                        else if (widget.text.isNotBlank())
                        {
                            matchedStaticWidget = wtgNode.widgets.find { w ->
                                w.possibleTexts.contains(widget.text)
                            }
                        }
                    }

                }
            }
            if (updateModel)
            {
                if (matchedStaticWidget != null) {
                    matchedStaticWidget.mappedRuntimeWidgets.add(Pair(state,originalWidget))
                    //addRuntimeWigetInfo(matchedStaticWidget, originalWidget, state)
                    matchedStaticWidget.xpath = originalWidget.xpath
                    matchedStaticWidget.isInputField = originalWidget.isInputField
                    updateInteractiveWidget(originalWidget, state, matchedStaticWidget, wtgNode,false,regressionTestingMF)
                    if (originalWidget.isInputField && originalWidget.text.isNotBlank())
                    {
                        matchedStaticWidget.textInputHistory.add(originalWidget.text)
                    }
                    if (wtgNode is WTGAppStateNode && !originalWidget.isInputField && originalWidget.text != matchedStaticWidget.appStateTextProperty[wtgNode])
                    {
                        matchedStaticWidget.appStateTextProperty[wtgNode] = originalWidget.text
                    }
                }
                else
                {
                    //Create new StaticWidget
                    if (shouldCreateNewWidget(originalWidget))
                    {
                        val unqualifiedResourceId = getUnqualifiedResourceId(widget)
                        val staticWidget = StaticWidget.getOrCreateStaticWidget(StaticWidget.getWidgetId(),unqualifiedResourceId,"",originalWidget.className, wtgNode.classType ,wtgNode)
                        matchedStaticWidget = staticWidget
                        staticWidget.contentDesc = originalWidget.contentDesc
                        staticWidget.xpath = originalWidget.xpath
                        staticWidget.className == originalWidget.className
                        if (!originalWidget.isInputField)
                        {
                            staticWidget.text = originalWidget.nlpText
                            if (!staticWidget.possibleTexts.contains(originalWidget.text))
                                staticWidget.possibleTexts.add(originalWidget.text)
                            if (wtgNode is WTGAppStateNode && originalWidget.text != matchedStaticWidget.appStateTextProperty[wtgNode])
                            {
                                matchedStaticWidget.appStateTextProperty[wtgNode] = originalWidget.text
                            }
                        }
                        //add mappedRuntime
                        staticWidget.mappedRuntimeWidgets.add(Pair(state,originalWidget))
                        //addRuntimeWigetInfo(staticWidget, originalWidget, state)
                       updateInteractiveWidget(originalWidget,state,matchedStaticWidget,wtgNode,true,regressionTestingMF)
                    }

                }
            }
            return matchedStaticWidget
        }

         fun getStaticWidgets(originalWidget: Widget, widgetGroup: WidgetGroup, state: State<*>, wtgNode: WTGNode, updateModel: Boolean,
                              regressionTestingMF: RegressionTestingMF): List<StaticWidget> {
            var matchedStaticWidgets: ArrayList<StaticWidget> = ArrayList()
            val appName = regressionTestingMF.getAppName()
            var widget = originalWidget
            if (widget.resourceId.isNotBlank()) {
                val unqualifiedResourceId = getUnqualifiedResourceId(widget)

                matchedStaticWidgets.addAll(wtgNode.widgets.filter {
                    it.resourceIdName == unqualifiedResourceId
                })
            }
            if (matchedStaticWidgets.isEmpty() && widget.contentDesc.isNotBlank())
            {
                matchedStaticWidgets.addAll(wtgNode.widgets.filter { w ->
                    widget.contentDesc == w.contentDesc
                })
            }
            if (matchedStaticWidgets.isEmpty() && !widget.isInputField && widget.text.isNotBlank())
            {
                matchedStaticWidgets.addAll( wtgNode.widgets.filter { w ->
                    w.possibleTexts.contains(widget.text)
                })
            }
            if (matchedStaticWidgets.isEmpty() &&
                    (hasParentWithType(widget,state,"ListView") || hasParentWithType(widget,state,"RecycleView")))
            {
                //this is an item of ListView or RecycleView

            }
            if (updateModel)
            {
                matchedStaticWidgets.forEach {
                    if (originalWidget.isInputField && originalWidget.text.isNotBlank())
                    {
                        it.textInputHistory.add(originalWidget.text)
                    }
                }
            }
            return matchedStaticWidgets
        }

        fun updateInteractiveWidget(widget: Widget, state: State<*>, staticWidget: StaticWidget, wtgNode: WTGNode
                                    , isNewCreatedWidget: Boolean, regressionTestingMF: RegressionTestingMF) {
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
                    if (widget.containGUIWidget(guiWidget))
                    {
                        mappedGUIWidgets.put(guiWidget,widget)
                        break
                    }
                }
            }
            return mappedGUIWidgets
        }

        fun copyStaticWidgetAndItsEvents(staticWidget: StaticWidget, newNode: WTGNode, sourceNode: WTGNode
                                         , transitionGraph: TransitionGraph) {
            if (!newNode.widgets.contains(staticWidget))
                newNode.widgets.add(staticWidget)
            val relatedEdges = transitionGraph.edges(sourceNode).filter {
                it.label.widget == staticWidget
            }
            relatedEdges.forEach {
                val relatedEvent = it.label
                if (it.destination?.data == sourceNode)
                    transitionGraph.add(newNode,newNode,relatedEvent)
                else
                    transitionGraph.add(newNode, it.destination?.data, relatedEvent)
            }
        }

        private fun moveStaticWidgetAndItsEvents(staticWidget: StaticWidget, newNode: WTGNode, sourceNode: WTGNode
                                                 , transitionGraph: TransitionGraph) {
            if (!newNode.widgets.contains(staticWidget))
                newNode.widgets.add(staticWidget)
            sourceNode.widgets.remove(staticWidget)
            val relatedEdges = transitionGraph.edges(sourceNode).filter{
                it.label.widget == staticWidget
            }
            relatedEdges.forEach {
                transitionGraph.add(newNode, it.destination?.data, it.label)
                transitionGraph.update(sourceNode, it.destination?.data, WTGFakeNode(), it.label, it.label).also {
                    if (it != null && transitionGraph.edgeProved.containsKey(it))
                        transitionGraph.edgeProved.remove(it)

                }
            }

        }

        internal var changeRatioCriteria: Double = 0.05
        fun checkShouldCreateNewNode(newState: State<*>, bestMatchedNode: WTGNode,currentRotation:Int, appName: String,
                                     regressionTestingMF: RegressionTestingMF):Boolean {
            if (bestMatchedNode.rotation!=currentRotation)
                return true
            var newWidgets = ArrayList<Widget>()
            var changeWidgets = HashMap<StaticWidget,String>()
            val visibleWidgets = getVisibleWidgets(newState)
            val matchedWidgets = HashMap<StaticWidget, Boolean>()
            bestMatchedNode.widgets.filter{it.mappedRuntimeWidgets.isNotEmpty()}.forEach {
                matchedWidgets.put(it,false)
            }
            val mappedWidgets =  getMappedGUIWidgets(visibleWidgets, bestMatchedNode)
            mappedWidgets.forEach { w, sw->
                matchedWidgets.put(sw,true)
                if (!w.isInputField)
                {
                    if (sw.mappedRuntimeWidgets.find {sw.possibleTexts.contains(it.second.text) } == null)
                        changeWidgets.put(sw,w.text)
                }
            }
            val unmappedWidgets = visibleWidgets.filter { !mappedWidgets.containsKey(it) }
            unmappedWidgets.forEach {
                var matchedStaticWidget: StaticWidget?
                matchedStaticWidget = matchWidget(it, newState, bestMatchedNode,false,regressionTestingMF)
                if (matchedStaticWidget != null) {
                    //new widget found
                    matchedWidgets[matchedStaticWidget] = true
                    //This is really a new widget
                    if (matchedStaticWidget.mappedRuntimeWidgets.isEmpty())
                        newWidgets.add(it)
                }
                else
                {
                    var widget = it
                    if (shouldCreateNewWidget(widget))
                        newWidgets.add(it)
                }

            }
            if (newWidgets.size/matchedWidgets.size.toDouble() > 0.05)
            {
                return true
            }
            //check lost identified widget
            var lostWidgets= matchedWidgets.filter { it.value == false }

            if (lostWidgets.size/matchedWidgets.size.toDouble()>0.05)
            {
//            if (lostWidget.find { it.className!="android.widget.Image" } != null)
//                return true
                return true
            }
            return false
            if (changeWidgets.size/matchedWidgets.size.toDouble()>0.1)
            {
                return true
            }
            return false
        }
        fun transferStaticWidgetsAndEvents(sourceNode: WTGNode, newNode: WTGNode, newState: State<*>, appName: String
                                           , regressionTestingMF: RegressionTestingMF) {
            regressionTestingMF.transitionGraph.edges(sourceNode).filter { it.label.widget==null}.forEach {
                if (it.destination?.data == sourceNode)
                    regressionTestingMF.transitionGraph.add(newNode,newNode,it.label)
                else
                    regressionTestingMF.transitionGraph.add(newNode,it.destination?.data,it.label)
            }
            //Copy mapped widget
            val visibleWidgets = getVisibleWidgets(newState)
            val mappedWidgets = getMappedGUIWidgets(visibleWidgets, sourceNode)
            mappedWidgets.forEach { w,sw ->
                if (!sw.possibleTexts.contains(w.text) && sw.possibleTexts.isNotEmpty() && !w.isInputField) // it should be an similar widget
                    copyStaticWidgetAndItsEvents(sw, newNode, sourceNode,regressionTestingMF.transitionGraph)
                else
                    copyStaticWidgetAndItsEvents(sw, newNode, sourceNode,regressionTestingMF.transitionGraph)
            }

            //process unmappedWidget
            val unmappedWidgets = visibleWidgets.filter { !mappedWidgets.containsKey(it) }
            unmappedWidgets.forEach {
                var matchedStaticWidget: StaticWidget?=matchWidget(it,newState,sourceNode,false,regressionTestingMF)

                if (matchedStaticWidget != null) {
                    if (matchedStaticWidget.mappedRuntimeWidgets.isEmpty())
                    {
                        //move if this static widget never mapped before
                        //change owner wtgNode
                        copyStaticWidgetAndItsEvents(matchedStaticWidget, newNode, sourceNode,regressionTestingMF.transitionGraph)
                        //update event

                    }
                    else
                    {
                        //copy else
                        if (!matchedStaticWidget.possibleTexts.contains(it.text))
                            copyStaticWidgetAndItsEvents(matchedStaticWidget,newNode, sourceNode,regressionTestingMF.transitionGraph)
                        else
                            copyStaticWidgetAndItsEvents(matchedStaticWidget,newNode, sourceNode,regressionTestingMF.transitionGraph)
                    }
                }
            }
        }
        fun isInteractiveWidget(widget: Widget): Boolean =
                widget.enabled && widget.isVisible && ( widget.isInputField || widget.clickable || widget.checked != null || widget.longClickable || widget.scrollable )
                        && !widget.visibleAreas.isEmpty()

        fun hasParentWithType(it: Widget, state: State<*>, parentType: String): Boolean {
            var widget: Widget = it
            while (widget.hasParent)
            {
                val parent = state.widgets.find {  w -> w.id == widget.parentId }
                if (parent!=null)
                {
                    if(parent.className.contains(parentType))
                    {
                        return true
                    }
                    widget = parent
                }
                else
                {
                    return false
                }
            }
            return false
        }

        fun hasParentWithResourceId(widget: Widget, state: State<*>, parentIdPatterns: List<String>):Boolean{
            var w: Widget = widget
            while (w.hasParent)
            {
                val parent = state.widgets.find {  it -> it.id == w.parentId }
                if (parent!=null)
                {
                    if(parentIdPatterns.find { w.resourceId.contains(it)}!=null)
                    {
                        return true
                    }
                    w = parent
                }
                else
                {
                    return false
                }
            }
            return false
        }

        fun tryGetParentHavingResourceId(widget: Widget, currentState: State<*>): Widget {
            var parentWidget: Widget? = widget
            while (parentWidget!=null) {
                if (parentWidget.resourceId.isNotBlank())
                    return parentWidget
                parentWidget = currentState.widgets.find {
                    it.idHash == parentWidget!!.parentHash }

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

        fun getAllInteractiveChild(allWidgets: List<Widget>, parent: Widget): List<Widget>
        {
            val interactiveWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null)
                {
                    if(Helper.isInteractiveWidget(childWidget))
                    {
                        interactiveWidgets.add(childWidget)
                    }
                    interactiveWidgets.addAll(getAllInteractiveChild(allWidgets,childWidget))
                }

            }
            return interactiveWidgets
        }

        fun getAllInteractiveChild2(allWidgets: List<Widget>, parent: Widget): List<Widget>
        {
            val interactiveWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null)
                {
                    if(childWidget.canInteractWith)
                    {
                        interactiveWidgets.add(childWidget)
                    }
                    interactiveWidgets.addAll(getAllInteractiveChild2(allWidgets,childWidget))
                }

            }
            return interactiveWidgets
        }

        fun getAllChild(allWidgets: List<Widget>, parent: Widget): List<Widget>
        {
            val visibleWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it }
                if (childWidget != null)
                {
                    if(childWidget.isVisible)
                    {
                        visibleWidgets.add(childWidget)
                    }
                    visibleWidgets.addAll(getAllChild(allWidgets,childWidget))
                }

            }
            return visibleWidgets
        }
    }
}