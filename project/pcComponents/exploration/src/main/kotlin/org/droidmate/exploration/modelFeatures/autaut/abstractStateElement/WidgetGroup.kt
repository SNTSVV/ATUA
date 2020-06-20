package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.WidgetReducer
import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class WidgetGroup (val attributePath: AttributePath, val cardinality: Cardinality) {
    var exerciseCount: Int = 0
    var guiWidgets = HashSet<Widget>()
    val actionCount = HashMap<AbstractAction, Int>()

    init {
        if (attributePath.isClickable()) {
            val abstractAction = AbstractAction(
                    actionName = AbstractActionType.CLICK.actionName,
                    widgetGroup = this
            )
            actionCount.put(abstractAction, 0)
        }
        if (attributePath.isLongClickable()) {
            val abstractAction = AbstractAction(
                    actionName = AbstractActionType.LONGCLICK.actionName,
                    widgetGroup = this
            )
            actionCount.put(abstractAction, 0)
        }
        if (attributePath.isScrollable()) {
            val abstractActionSwipeUp = AbstractAction(
                    actionName = AbstractActionType.SWIPE.actionName,
                    widgetGroup = this,
                    extra = "SwipeUp"
            )
            val abstractActionSwipeDown = AbstractAction(
                    actionName = AbstractActionType.SWIPE.actionName,
                    widgetGroup = this,
                    extra = "SwipeDown"
            )
            val abstractActionSwipeLeft = AbstractAction(
                    actionName = AbstractActionType.SWIPE.actionName,
                    widgetGroup = this,
                    extra = "SwipeLeft"
            )
            val abstractActionSwipeRight = AbstractAction(
                    actionName = AbstractActionType.SWIPE.actionName,
                    widgetGroup = this,
                    extra = "SwipeRight"
            )
            actionCount.put(abstractActionSwipeUp, 0)
            actionCount.put(abstractActionSwipeDown, 0)
            actionCount.put(abstractActionSwipeLeft, 0)
            actionCount.put(abstractActionSwipeRight, 0)

        }
        if (attributePath.isInputField()) {
            val abstractAction = AbstractAction(
                    actionName = AbstractActionType.TEXT_INSERT.actionName,
                    widgetGroup = this
            )
            actionCount.put(abstractAction, 0)
        }

        //Item-containing Widget

    }

    fun getGUIWidgets ( guiState: State<*>): List<Widget>{
        val abstractState = AbstractStateManager.instance.getAbstractState(guiState)!!
        val tempFullAttributePaths: HashMap<Widget, AttributePath> = HashMap()
        val tempRelativeAttributePaths: HashMap<Widget, AttributePath> = HashMap()
        val selectedGuiWidgets = ArrayList<Widget>()
        Helper.getVisibleWidgets(guiState).forEach {
            if (isAbstractRepresentationOf(it,guiState)) {
                selectedGuiWidgets.add(it)
            }
        }
        return selectedGuiWidgets
    }

    fun isAbstractRepresentationOf(widget: Widget, guiState: State<*>): Boolean
    {
        val abstractState = AbstractStateManager.instance.getAbstractState(guiState)!!
        val tempFullAttributePaths: HashMap<Widget, AttributePath> = HashMap()
        val tempRelativeAttributePaths: HashMap<Widget, AttributePath> = HashMap()
        val reducedAttributePath = WidgetReducer.reduce(widget,guiState,abstractState.activity,tempFullAttributePaths,tempRelativeAttributePaths)
        if (reducedAttributePath.equals(attributePath))
        {
            return true
        }
        return false
    }

    fun getPossibleActions(): Int {
        var totalActions = 0
        if (this.attributePath.isScrollable()) {
           totalActions += 4
        }
        if (this.attributePath.isClickable()) {
            totalActions += 1
        }
        if (this.attributePath.isLongClickable()) {
            totalActions += 1
        }
        return totalActions
    }

    fun getLocalAttributes(): HashMap<AttributeType, String>{
        return attributePath.localAttributes
    }

    override fun hashCode(): Int {
        return attributePath.hashCode()+cardinality.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is WidgetGroup)
            return false
        return this.hashCode()==other.hashCode()
    }

    override fun toString(): String {
        return "WidgetGroup[${attributePath.getClassName()}]" +
                "[${attributePath.getResourceId()}]" +
                "[${attributePath.getContentDesc()}]" +
                "[${attributePath.getText()}]" +
                "[clickable=${attributePath.isClickable()}]" +
                "[longClickable=${attributePath.isLongClickable()}]" +
                "[scrollable=${attributePath.isScrollable()}]" +
                "[checkable=${attributePath.isCheckable()}]"
    }

}

enum class Cardinality{
    ZERO,
    ONE,
    MANY
}