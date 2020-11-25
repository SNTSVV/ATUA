package org.droidmate.exploration.modelFeatures.autaut.DSTG

import com.sun.xml.internal.bind.v2.schemagen.xmlschema.LocalAttribute
import org.droidmate.exploration.modelFeatures.autaut.WTG.DescendantLayoutDirection
import org.droidmate.exploration.modelFeatures.autaut.WTG.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.toUUID
import java.io.BufferedWriter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class AttributeValuationSet (attributePath: AttributePath, var cardinality: Cardinality, val activity: String,
                             listOfConstructedAVS: ArrayList<AttributeValuationSet>) {
    val localAttributes: HashMap<AttributeType, String> = HashMap()
    val parentAttributeValuationSet: AttributeValuationSet?
    val childAttributeValuationSets: HashSet<AttributeValuationSet>?
    var exerciseCount: Int = 0
    val actionCount = HashMap<AbstractAction, Int>()
    var captured = false
    val avsId: UUID by lazy { lazyIds.value }

    protected open val lazyIds: Lazy<UUID> =
            lazy {
                this.fullAttributeValuationMap().toUUID()
            }
    init {
        localAttributes.putAll(attributePath.localAttributes)
        if (attributePath.parentAttributePath == null) {
            parentAttributeValuationSet = null
        } else {
            if (listOfConstructedAVS.any { it.haveTheSameAttributePath(attributePath.parentAttributePath) }) {
                parentAttributeValuationSet = listOfConstructedAVS.find { it.haveTheSameAttributePath(attributePath.parentAttributePath) }!!
            } else {
                val newParentAttributeValuationSet = AttributeValuationSet(attributePath.parentAttributePath,
                        Cardinality.ONE, activity, listOfConstructedAVS)
                listOfConstructedAVS.add(newParentAttributeValuationSet)
                parentAttributeValuationSet = newParentAttributeValuationSet
            }
        }
        //TODO
        if (attributePath.childAttributePaths==null) {
            childAttributeValuationSets = null
        } else {
            childAttributeValuationSets = HashSet()
            attributePath.childAttributePaths.forEach {
                childAttributeValuationSets.add(AttributeValuationSet(it,Cardinality.ONE,activity,listOfConstructedAVS))
            }
        }
        /*if (!AbstractStateManager.instance.activity_attrValSetsMap.containsKey(activity))
            AbstractStateManager.instance.activity_attrValSetsMap.put(activity, ArrayList())*/


        if (isClickable() ) {
            if (getClassName().equals("android.webkit.WebView")) {
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
        if (isLongClickable() && !isInputField()) {
            val abstractAction = AbstractAction(
                    actionType = AbstractActionType.LONGCLICK,
                    attributeValuationSet = this
            )
            actionCount.put(abstractAction, 0)
        }
        if (isScrollable()) {
            if (localAttributes[AttributeType.scrollDirection]== DescendantLayoutDirection.HORIZONTAL.toString()) {
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
                actionCount.put(abstractActionSwipeLeft, 0)
                actionCount.put(abstractActionSwipeRight, 0)
            } else if (localAttributes[AttributeType.scrollDirection]== DescendantLayoutDirection.VERTICAL.toString()) {
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
                actionCount.put(abstractActionSwipeUp, 0)
                actionCount.put(abstractActionSwipeDown, 0)

            } else {
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
            }

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
        if (isInputField()) {
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
        allAttributeValuationSet.put(avsId,this)
    }
    
    fun getClassName(): String {
        if (localAttributes.containsKey(AttributeType.className))
        {
            return localAttributes[AttributeType.className]!!
        }
        return ""
    }
    fun getResourceId(): String {
        if (localAttributes.containsKey(AttributeType.resourceId))
        {
            return localAttributes[AttributeType.resourceId]!!
        }
        return ""
    }

    fun getContentDesc(): String {
        if (localAttributes.containsKey(AttributeType.contentDesc))
        {
            return localAttributes[AttributeType.contentDesc]!!
        }
        return ""
    }

    fun getText(): String {
        if (localAttributes.containsKey(AttributeType.text))
        {
            return localAttributes[AttributeType.text]!!
        }
        return ""
    }

    fun isClickable(): Boolean{
        if (localAttributes.containsKey(AttributeType.clickable))
        {
            return localAttributes[AttributeType.clickable]!!.toBoolean()
        }
        return false
    }

    fun isLongClickable(): Boolean{
        if (localAttributes.containsKey(AttributeType.longClickable))
        {
            return localAttributes[AttributeType.longClickable]!!.toBoolean()
        }
        return false
    }

    fun isScrollable(): Boolean{
        if (localAttributes.containsKey(AttributeType.scrollable))
        {
            return localAttributes[AttributeType.scrollable]!!.toBoolean()
        }
        return false
    }

    fun isInputField(): Boolean{
        if (localAttributes.containsKey(AttributeType.isInputField))
        {
            return localAttributes[AttributeType.isInputField]!!.toBoolean()
        }
        return false
    }

    fun isCheckable(): Boolean{
        if (localAttributes[AttributeType.checkable]!!.equals("true"))
        {
            return true
        }
        return false
    }

    fun isChecked(): Boolean {
        if (localAttributes.containsKey(AttributeType.checked))
        {
            return localAttributes[AttributeType.checked]!!.toBoolean()
        }
        return false
    }

    fun isEnable(): Boolean {
        if (localAttributes[AttributeType.enabled]!!.equals("true"))
        {
            return true
        }
        return false
    }
    fun isInteractive(): Boolean{
        return isClickable() || isLongClickable() || isScrollable() || isCheckable()
    }
    fun isSelected(): Boolean {
        if (localAttributes[AttributeType.selected]!!.equals("true"))
        {
            return true
        }
        return false
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
        if (this.avsId == derivedAttributeValuationSet.avsId)
        {
            return true
        }
        return false
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

     fun isParent(attributeValuationSet: AttributeValuationSet): Boolean {
        if (attributeValuationSet.parentAttributeValuationSet==null)
            return false
        if (haveTheSameAttributePath(attributeValuationSet.parentAttributeValuationSet))
            return true
        return false
    }

    fun haveTheSameAttributePath(attributePath: AttributePath): Boolean {
        if (parentAttributeValuationSet != null && attributePath.parentAttributePath == null)
            return false
        if (parentAttributeValuationSet == null && attributePath.parentAttributePath != null)
            return false
        if (parentAttributeValuationSet != null) {
            if (attributePath.parentAttributePath == null) {
                return false
            }
            if (!parentAttributeValuationSet.haveTheSameAttributePath(attributePath.parentAttributePath)) {
                return false
            }
        }
        if (!localAttributes.equals(attributePath.localAttributes)) {
            return false
        }
        if (childAttributeValuationSets == null) {
            if (attributePath.childAttributePaths == null)
                return true
            return false
        }
        if (attributePath.childAttributePaths == null)
            return false
        if (childAttributeValuationSets.any { childAttributeValuationSet -> attributePath.childAttributePaths.all { !childAttributeValuationSet.haveTheSameAttributePath(it) } }) {
            return false
        }
        return true
    }

    fun haveTheSameAttributePath(attributeValuationSet: AttributeValuationSet): Boolean {
        if (parentAttributeValuationSet != null && attributeValuationSet.parentAttributeValuationSet == null)
            return false
        if (parentAttributeValuationSet == null && attributeValuationSet.parentAttributeValuationSet != null)
            return false
        if (parentAttributeValuationSet != null) {
            if (!parentAttributeValuationSet.haveTheSameAttributePath(attributeValuationSet.parentAttributeValuationSet!!)) {
                return false
            }
        }
        if (!localAttributes.equals(attributeValuationSet.localAttributes)) {
            return false
        }
        if (childAttributeValuationSets == null) {
            if (attributeValuationSet.childAttributeValuationSets == null)
                return true
            return false
        }
        if (attributeValuationSet.childAttributeValuationSets == null)
            return false
        if (childAttributeValuationSets.any { childAttributeValuationSet -> attributeValuationSet.childAttributeValuationSets.all { !childAttributeValuationSet.haveTheSameAttributePath(it) } }) {
            return false
        }
        return true
    }

    fun isDerivedFrom(moreAbstractAttributeValuationSet: AttributeValuationSet): Boolean {
        //check parent first
        if (parentAttributeValuationSet!=null && moreAbstractAttributeValuationSet.parentAttributeValuationSet!=null) {
            if (!parentAttributeValuationSet.isDerivedFrom(moreAbstractAttributeValuationSet.parentAttributeValuationSet)) {
                return false
            }
        }
        //check local
        moreAbstractAttributeValuationSet.localAttributes.forEach {
            if (!localAttributes.containsKey(it.key))
                return false
            if (localAttributes[it.key] != it.value)
                return false
        }
        if (childAttributeValuationSets == null) {
            if (moreAbstractAttributeValuationSet.childAttributeValuationSets == null) {
                return true
            } else {
                return false
            }
        }
        if (moreAbstractAttributeValuationSet.childAttributeValuationSets == null)
            return true
        if (!moreAbstractAttributeValuationSet.childAttributeValuationSets.isEmpty()) {
            moreAbstractAttributeValuationSet.childAttributeValuationSets.forEach { moreAbstractChildAVS ->
                if (!childAttributeValuationSets.any { it.isDerivedFrom(moreAbstractChildAVS) }) {
                    return false
                }
            }
        }

        return true
    }

    override fun hashCode(): Int {
        return avsId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AttributeValuationSet)
            return false
        return this.avsId==other.avsId
    }

    override fun toString(): String {
        return "WidgetGroup[${getClassName()}]" +
                "[${getResourceId()}]" +
                "[${getContentDesc()}]" +
                "[${getText()}]" +
                "[clickable=${isClickable()}]" +
                "[longClickable=${isLongClickable()}]" +
                "[scrollable=${isScrollable()}]" +
                "[checkable=${isCheckable()}]"
    }

    fun fullAttributeValuationMap(): String {
        return parentAttributeValuationSet.toString() + localAttributes.toString() + childAttributeValuationSets.toString() + cardinality.toString()
    }
    /**
     * Write in csv
     */
    fun dump(abstractState: AbstractState): String {
        return "${avsId};${getClassName()};${getResourceId()};" +
                "\"${localAttributes.get(AttributeType.contentDesc)}\";\"${localAttributes.get(AttributeType.text)}\";${isEnable()};" +
                "${isSelected()};${isCheckable()};${isInputField()};" +
                "${isClickable()};${isLongClickable()};${isScrollable()};" +
                "${localAttributes.get(AttributeType.checked)};${localAttributes.get(AttributeType.isLeaf)};" +
                "${parentAttributeValuationSet?.avsId};${childAttributeValuationSets?.map { it.avsId }};$cardinality;$captured;" +
                "\"${abstractState.staticWidgetMapping[this]?.map{it.widgetId}?.joinToString(";")}\""
    }

    fun dump(bufferedWriter: BufferedWriter, dumpedAttributeValuationSets: ArrayList<UUID>, abstractState: AbstractState ) {
        bufferedWriter.write(this.dump(abstractState))

        dumpedAttributeValuationSets.add(this.avsId)
        if (parentAttributeValuationSet!=null) {
            if (!dumpedAttributeValuationSets.contains(parentAttributeValuationSet.avsId)) {
                bufferedWriter.newLine()
                parentAttributeValuationSet.dump(bufferedWriter, dumpedAttributeValuationSets,abstractState)
            }
        }
        childAttributeValuationSets?.forEach {
            if (!dumpedAttributeValuationSets.contains(it.avsId)) {
                bufferedWriter.newLine()
                it.dump(bufferedWriter, dumpedAttributeValuationSets,abstractState)
            }
        }
    }

    companion object {
       val allAttributeValuationSet: HashMap<UUID,AttributeValuationSet> = HashMap()
        /* fun createOrGetExisitingObject(attributePath: AttributePath, cardinality: Cardinality, activity: String): AttributeValuationSet {

        }*/
    }
}

enum class Cardinality{
    ZERO,
    ONE,
    MANY
}