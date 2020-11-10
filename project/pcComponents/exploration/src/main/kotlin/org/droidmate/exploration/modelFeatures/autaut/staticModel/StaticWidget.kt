package org.droidmate.exploration.modelFeatures.autaut.staticModel

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributePath
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import kotlin.collections.ArrayList
import kotlin.random.Random

open class StaticWidget constructor(val widgetId: String,//sootandroid id
                                    val resourceIdName: String,
                                    val resourceId: String,
                                    val className: String,
                                    var contentDesc: String,
                                    var text: String, //usefull for mapping menu item
                                    var activity: String,
                                    var wtgNode: WTGNode
                                    ){
    var possibleTexts= ArrayList<String>()
    val mappedRuntimeWidgets= ArrayList<Pair<State<*>,Widget>>()
    val appStateTextProperty = HashMap<WTGAppStateNode,String>()
    var exercised: Boolean = false
    var exerciseCount: Int = 0
    var textInputHistory: ArrayList<String> = ArrayList()
    var isListItem: Boolean = false
    var xpath: String = ""
    var attributePath: AttributePath? = null
    var index: Int = 0
    var interactive = true
    var isInputField = false
    init {
        wtgNode.widgets.add(this)
        allStaticWidgets.add(this)
    }
    override fun toString(): String {
        return "StaticWidget: $widgetId-resourceId=$resourceIdName-className=$className"
    }

    fun containGUIWidget(widget: Widget, state: State<*>): Boolean{
        if (mappedRuntimeWidgets.find { it.second.uid == widget.uid && it.first.uid == state.uid }!=null)
            return true
        return false
    }

    /**
     * use this function when the state is clear.
     * Don't use this function when we want to figure out which WTGNode should contain the state
     */
    fun containGUIWidget(widget: Widget):Boolean{
        if (mappedRuntimeWidgets.find { it.second.uid == widget.uid  }!=null)
            return true
        return false
    }
    fun clone(newNode: WTGNode): StaticWidget {
        val newStaticWidget = getOrCreateStaticWidget(
                widgetId = this.widgetId,
                resourceId = this.resourceId,
                resourceIdName = this.resourceIdName,
                className = this.className,
                activity = this.activity,
                wtgNode = newNode
        )
        newStaticWidget.isInputField = this.isInputField
        newStaticWidget.possibleTexts = this.possibleTexts
        newStaticWidget.contentDesc = this.contentDesc
        newStaticWidget.text = this.text
        newStaticWidget.exercised = this.exercised
        newStaticWidget.exerciseCount = this.exerciseCount
        newStaticWidget.xpath = this.xpath
        newStaticWidget.index = this.index
        newStaticWidget.interactive = this.interactive
        newStaticWidget.mappedRuntimeWidgets.addAll(this.mappedRuntimeWidgets)
        return newStaticWidget
    }



    companion object{
        val allStaticWidgets= ArrayList<StaticWidget>()
        fun getOrCreateStaticWidget(widgetId: String,
                                    resourceIdName: String = "",
                                    resourceId: String = "",
                                    className: String,
                                    activity: String,
                                    wtgNode: WTGNode): StaticWidget {
            val returnWidget = allStaticWidgets.find{ it.widgetId==widgetId && it.activity == activity}
            if ( returnWidget == null) {
                var staticWidget = StaticWidget(widgetId = widgetId, resourceIdName = resourceIdName,
                        resourceId = resourceId, wtgNode = wtgNode, className = className, text = "", contentDesc = "",
                        activity = activity)
                return staticWidget
            }
            else
            {

                return returnWidget
            }
        }

        fun getWidgetId(): String{
            var widgetId = Random.nextLong().toString()
            while(allStaticWidgets.find { it.widgetId == widgetId }!=null)
            {
                widgetId = Random.nextLong().toString()
            }
            return widgetId
        }
    }
}