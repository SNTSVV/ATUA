package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.WidgetReducer
import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.toUUID
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class AttributeValuationSet (val attributePath: AttributePath, val cardinality: Cardinality, val count: Int =1) {

    var exerciseCount: Int = 0
    var guiWidgets = HashSet<Widget>()
    val actionCount = HashMap<AbstractAction, Int>()

    init {
        if (attributePath.isClickable() ) {
            if (attributePath.getClassName().equals("android.webkit.WebView")) {
                val itemAbstractAction = AbstractAction(
                        actionType = AbstractActionType.ITEM_CLICK,
                        attributeValuationSet = this
                )
                actionCount.put(itemAbstractAction,0)
                val itemLongClickAbstractAction = AbstractAction(
                        actionType = AbstractActionType.ITEM_LONGCLICK,
                        attributeValuationSet = this
                )
                actionCount.put(itemLongClickAbstractAction,0)
            } else {
                val abstractAction = AbstractAction(
                        actionType = AbstractActionType.CLICK,
                        attributeValuationSet = this
                )
                actionCount.put(abstractAction, 0)
            }
        }
        if (attributePath.isLongClickable() && !attributePath.isInputField()) {
            val abstractAction = AbstractAction(
                    actionType = AbstractActionType.LONGCLICK,
                    attributeValuationSet = this
            )
            actionCount.put(abstractAction, 0)
        }
        if (attributePath.isScrollable()) {
            val abstractActionSwipeUp = AbstractAction(
                    actionType = AbstractActionType.SWIPE,
                    attributeValuationSet = this,
                    extra = "SwipeUp"
            )
            val abstractActionSwipeDown = AbstractAction(
                    actionType = AbstractActionType.SWIPE,
                    attributeValuationSet = this,
                    extra = "SwipeDown"
            )
            val abstractActionSwipeLeft = AbstractAction(
                    actionType = AbstractActionType.SWIPE,
                    attributeValuationSet = this,
                    extra = "SwipeLeft"
            )
            val abstractActionSwipeRight = AbstractAction(
                    actionType = AbstractActionType.SWIPE,
                    attributeValuationSet = this,
                    extra = "SwipeRight"
            )
            actionCount.put(abstractActionSwipeUp, 0)
            actionCount.put(abstractActionSwipeDown, 0)
            actionCount.put(abstractActionSwipeLeft, 0)
            actionCount.put(abstractActionSwipeRight, 0)
            /*if (attributePath.getClassName().contains("RecyclerView")
                    || attributePath.getClassName().contains("ListView")
                    || attributePath.getClassName().equals("android.webkit.WebView") ) {
                val abstractActionSwipeTillEnd = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationSet = this,
                        extra = "SwipeTillEnd"
                )
                actionCount.put(abstractActionSwipeTillEnd,0)
            }*/
        }
        if (attributePath.isInputField()) {
            val abstractAction = AbstractAction(
                    actionType = AbstractActionType.TEXT_INSERT,
                    attributeValuationSet = this
            )
            actionCount.put(abstractAction, 0)
        }
        //Item-containing Widget
       /* if (attributePath.getClassName().equals("android.webkit.WebView")) {
            val abstractAction = AbstractAction(
                    actionName = AbstractActionType.CLICK.actionName,
                    widgetGroup = this,
                    extra = "RandomMultiple"
            )
            actionCount.put(abstractAction, 0)
            *//*val longclickAbstractAction = AbstractAction(
                    actionName = AbstractActionType.LONGCLICK.actionName,
                    widgetGroup = this,
                    extra = "RandomMultiple"
            )
            actionCount.put(longclickAbstractAction, 0)*//*
        }*/
    }

    val avsId: UUID by lazy { lazyIds.value }

    protected open val lazyIds: Lazy<UUID> =
            lazy {
                    listOf<Any>(attributePath.toString(),cardinality.toString()).joinToString(separator = "<;>").toUUID()
                }

    fun getGUIWidgets (guiState: State<*>): List<Widget>{
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
        val abstractState = AbstractStateManager.instance.getAbstractState(guiState)
        if (abstractState == null)
            return false
        val activity = abstractState.activity
        if (!AbstractStateManager.instance.activity_widget_AttributeValuationSetHashMap.containsKey(activity))
            return false
        val widget_AttributeValuationSet = AbstractStateManager.instance.activity_widget_AttributeValuationSetHashMap[activity]!!
        if (!widget_AttributeValuationSet.containsKey(widget))
            return false
        val derivedAttributeValuationSet = widget_AttributeValuationSet.get(widget)!!
        if (derivedAttributeValuationSet.attributePath.equals(attributePath))
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

    fun havingSameContent(currentAbstractState: AbstractState, comparedAttributeValuationSet: AttributeValuationSet, comparedAbstractState: AbstractState): Boolean {
        val widgetGroupChildren = currentAbstractState.attributeValuationSets.filter {
            isParent(it)
        }

        val comparedWidgetGroupChildren = comparedAbstractState.attributeValuationSets.filter {
            comparedAttributeValuationSet.isParent(it)
        }
        widgetGroupChildren.forEach {w1 ->
            if (!comparedWidgetGroupChildren.any { w2 -> w1 == w2 }) {
                return false
            }
        }
        return true
    }

    private fun isParent(attributeValuationSet: AttributeValuationSet): Boolean {
        var parentAttributePath: AttributePath? = attributeValuationSet.attributePath.parentAttributePath
        while(parentAttributePath != null) {
            if (parentAttributePath == this.attributePath) {
                return true
            }
            parentAttributePath = parentAttributePath.parentAttributePath
        }
        return false
    }


    override fun hashCode(): Int {
        return attributePath.hashCode()+cardinality.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AttributeValuationSet)
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

    /**
     * Write in csv
     */
    fun dump(): String {
        return "${avsId.toString()};${attributePath.getClassName()};${attributePath.getResourceId()};" +
                "${attributePath.getContentDesc()};${attributePath.getText()};${attributePath.isEnable()};" +
                "${attributePath.isSelected()};${attributePath.isCheckable()};${attributePath.isInputField()};" +
                "${attributePath.isClickable()};${attributePath.isLongClickable()};${attributePath.isScrollable()};" +
                "${attributePath.isChecked()};$attributePath."
    }


}

enum class Cardinality{
    ZERO,
    ONE,
    MANY
}