package org.droidmate.exploration.modelFeatures.autaut.DSTG

import org.droidmate.exploration.modelFeatures.autaut.AutAutModelLoader
import org.droidmate.exploration.modelFeatures.autaut.WTG.DescendantLayoutDirection
import org.droidmate.exploration.modelFeatures.autaut.WTG.Helper
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.toUUID
import java.io.BufferedWriter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class AttributeValuationSet (val localAttributes: HashMap<AttributeType, String> = HashMap(),
                             var parentAttributeValuationSetId: UUID = emptyUUID,
                             var childAttributeValuationSetIds: HashSet<UUID> = HashSet(),
                             val cardinality: Cardinality,
                             val activity: String) {

    var exerciseCount: Int = 0
    val actionCount = HashMap<AbstractAction, Int>()
    var captured = false

    constructor(attributePath: AttributePath,  cardinality: Cardinality,  activity: String, attributPath_cardinality: HashMap<UUID,Cardinality>): this(cardinality = cardinality,activity = activity) {
        if (!allAttributeValuationSet.containsKey(activity)) {
            allAttributeValuationSet.put(activity, HashMap())
        }
        localAttributes.putAll(attributePath.localAttributes)
        if (attributePath.parentAttributePathId == emptyUUID) {
            parentAttributeValuationSetId = emptyUUID
        } else {
            val parentAttributePath = AttributePath.getAttributePathById(attributePath.parentAttributePathId,activity)
            if (allAttributeValuationSet[activity]!!.any { it.value.haveTheSameAttributePath(parentAttributePath) }) {
                parentAttributeValuationSetId = allAttributeValuationSet[activity]!!.map {it.value}.find{ it.haveTheSameAttributePath(parentAttributePath) }!!.avsId
            } else {
                val parentCardinality = if (attributPath_cardinality.containsKey(parentAttributeValuationSetId))
                        attributPath_cardinality[attributePath.parentAttributePathId]!!
                else
                    Cardinality.ONE
                val newParentAttributeValuationSet = AttributeValuationSet(parentAttributePath,
                        parentCardinality, activity,attributPath_cardinality)
                parentAttributeValuationSetId = newParentAttributeValuationSet.avsId
            }
        }
        //TODO
        if (attributePath.childAttributePathIds.isNotEmpty()) {
            attributePath.childAttributePathIds.map { AttributePath.getAttributePathById(it,activity) }.forEach {
                val childAttributeValuationSet = AttributeValuationSet(it,Cardinality.ONE,activity, HashMap())
                childAttributeValuationSetIds.add(childAttributeValuationSet.avsId)
            }
        }
        //TODO
        if (avsId == parentAttributeValuationSetId) {
            allAttributeValuationSet[activity]!!.put(avsId,this)
        } else {
            allAttributeValuationSet[activity]!!.put(avsId, this)
        }
    }

    val avsId: UUID by lazy {  this.fullAttributeValuationMap().toUUID()}
    fun initActions() {
        /*if (!AbstractStateManager.instance.activity_attrValSetsMap.containsKey(activity))
            AbstractStateManager.instance.activity_attrValSetsMap.put(activity, ArrayList())*/
        if (isClickable() ) {
            if (getClassName().equals("android.webkit.WebView")) {
                val itemAbstractAction = AbstractAction(
                        actionType = AbstractActionType.ITEM_CLICK,
                        attributeValuationSet = this
                )
                if (!actionCount.containsKey(itemAbstractAction))
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
        return localAttributes[AttributeType.scrollable]!!.toBoolean()
    }
    fun isInputField(): Boolean{
        if (localAttributes.containsKey(AttributeType.isInputField))
        {
            return localAttributes[AttributeType.isInputField]!!.toBoolean()
        }
        return false
    }
    fun isUserLikeInput(): Boolean {
        val className = getClassName()
        return when (className) {
            "android.widget.RadioButton", "android.widget.CheckBox", "android.widget.Switch", "android.widget.ToggleButton" -> true
            else -> isInputField()
        }
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

    fun getGUIWidgets (guiState: State<*>): List<Widget>{
        val selectedGuiWidgets = ArrayList<Widget>()
        Helper.getVisibleWidgetsForAbstraction(guiState).forEach {
            if (isAbstractRepresentationOf(it,guiState,false)) {
                selectedGuiWidgets.add(it)
            }
        }
        if (selectedGuiWidgets.isEmpty()) {
            Helper.getVisibleWidgetsForAbstraction(guiState).forEach {
                if (isAbstractRepresentationOf(it,guiState,true)) {
                    selectedGuiWidgets.add(it)
                }
            }
        }
        return selectedGuiWidgets
    }

    fun isAbstractRepresentationOf(widget: Widget, guiState: State<*>, compareDerived: Boolean): Boolean
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
        if (compareDerived) {
            if (derivedAttributeValuationSet.isDerivedFrom(this)) {
                return true
            }
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
        if (attributeValuationSet.parentAttributeValuationSetId== emptyUUID)
            return false
         val parentAttributeValuationSet = allAttributeValuationSet[activity]!!.get(attributeValuationSet.parentAttributeValuationSetId)!!
        if (haveTheSameAttributePath(parentAttributeValuationSet))
            return true
        return false
    }

    fun haveTheSameAttributePath(attributePath: AttributePath): Boolean {
        if (localAttributes.hashCode() != attributePath.localAttributes.hashCode()) {
            return false
        }
        if (childAttributeValuationSetIds.isEmpty() && attributePath.childAttributePathIds.isNotEmpty()) {
            return false
        }
        if (childAttributeValuationSetIds.isNotEmpty() && attributePath.childAttributePathIds.isEmpty())
            return false
        val childAttributePaths = attributePath.childAttributePathIds.map { AttributePath.getAttributePathById(it,activity) }
        val childAttributeValuationSets = childAttributeValuationSetIds.map { allAttributeValuationSet[activity]!!.get(it)!! }
        if (!childAttributeValuationSets.all { childAttributeValuationSet -> childAttributePaths.any { childAttributeValuationSet.haveTheSameAttributePath(it) } }) {
            return false
        }
        if (parentAttributeValuationSetId != emptyUUID && attributePath.parentAttributePathId == emptyUUID)
            return false
        if (parentAttributeValuationSetId == emptyUUID && attributePath.parentAttributePathId != emptyUUID)
            return false
        if (parentAttributeValuationSetId != emptyUUID) {
            if (attributePath.parentAttributePathId == emptyUUID) {
                return false
            }
            val parentAttributeValuationSet = allAttributeValuationSet[activity]!!.get(parentAttributeValuationSetId)!!
            val parentAttributePath = AttributePath.getAttributePathById(attributePath.parentAttributePathId,activity)
            if (!parentAttributeValuationSet.haveTheSameAttributePath(parentAttributePath)) {
                return false
            }
        }
        return true
    }

    fun haveTheSameAttributePath(cmpAttributeValuationSet: AttributeValuationSet): Boolean {
        if (avsId != cmpAttributeValuationSet.avsId)
            return false
        return true
    }

    fun isDerivedFrom(abstractAttributeValuationSet: AttributeValuationSet): Boolean {
        //check local
        abstractAttributeValuationSet.localAttributes.forEach {
            if (!localAttributes.containsKey(it.key))
                return false
            if (localAttributes[it.key] != it.value)
                return false
        }
        if (childAttributeValuationSetIds.isEmpty() && !abstractAttributeValuationSet.childAttributeValuationSetIds.isEmpty()) {
            return false
        }
        if (!abstractAttributeValuationSet.childAttributeValuationSetIds.isEmpty()) {
            val childAttributeValuationSet = childAttributeValuationSetIds.map { allAttributeValuationSet[activity]!!.get(it)!! }
            abstractAttributeValuationSet.childAttributeValuationSetIds.map { allAttributeValuationSet[activity]!!.get(it)!! }. forEach { moreAbstractChildAVS ->
                if (!childAttributeValuationSet.any { it.isDerivedFrom(moreAbstractChildAVS) }) {
                    return false
                }
            }
        }
        if (parentAttributeValuationSetId!= emptyUUID && abstractAttributeValuationSet.parentAttributeValuationSetId!= emptyUUID) {
            val parentAttributeValuationSet = allAttributeValuationSet[activity]!!.get(parentAttributeValuationSetId)
            if (parentAttributeValuationSet == null) {
                throw Exception("Cannot find attributeValuationSet $parentAttributeValuationSetId")
                return false
            }
            val abstractParentAttributeValuationSet = allAttributeValuationSet[activity]!!.get(abstractAttributeValuationSet.parentAttributeValuationSetId)
            if (abstractParentAttributeValuationSet == null) {
                throw Exception("Cannot find attributeValuationSet $parentAttributeValuationSetId")
                return false
            }
            if (!parentAttributeValuationSet.isDerivedFrom(abstractParentAttributeValuationSet)) {
                return false
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
        val s =  listOf<String>(parentAttributeValuationSetId.toString(),localAttributes.toSortedMap().toString(),childAttributeValuationSetIds.toString(),cardinality.toString(),activity).joinToString("<;>")
        return s
    }
    /**
     * Write in csv
     */
    fun dump(abstractState: AbstractState): String {
        testReloadAttributes()
        return "${avsId};${getClassName()};${getResourceId()};" +
                "\"${localAttributes.get(AttributeType.contentDesc)}\";\"${localAttributes.get(AttributeType.text)}\";${isEnable()};" +
                "${localAttributes.get(AttributeType.selected)};${isCheckable()};${isInputField()};" +
                "${isClickable()};${isLongClickable()};${isScrollable()};" +
                "${localAttributes.get(AttributeType.checked)};${localAttributes.get(AttributeType.isLeaf)};" +
                "${dumpParentUUID()};\"${dumpChildrenAVS()}\";$cardinality;$captured;" +
                "\"${abstractState.EWTGWidgetMapping[this]?.map{it.widgetId}?.joinToString(";")}\""
    }

    private fun dumpChildrenAVS() = childAttributeValuationSetIds?.joinToString(";")

    private fun testReloadAttributes() {
        val attributes = HashMap<AttributeType,String>()

        val className = getClassName()
        val resourceId = getResourceId()
        val contentDesc = localAttributes.get(AttributeType.contentDesc)?:"null"
        val text = localAttributes.get(AttributeType.text)?:"null"
        val enabled = isEnable().toString()
        val selected = localAttributes.get(AttributeType.selected)?:"null".toString()
        val checkable = isCheckable().toString()
        val isInputField = isInputField().toString()
        val clickable = isClickable().toString()
        val longClickable = isLongClickable().toString()
        val scrollable = isScrollable().toString()
        val checked = localAttributes.get(AttributeType.checked)?:"null"
        val isLeaf = localAttributes.get(AttributeType.isLeaf)?:"null"

        addAttributeIfNotNull(AttributeType.className, className, attributes)
        addAttributeIfNotNull(AttributeType.resourceId, resourceId, attributes)
        addAttributeIfNotNull(AttributeType.contentDesc, contentDesc, attributes)
        addAttributeIfNotNull(AttributeType.text, text, attributes)
        addAttributeIfNotNull(AttributeType.enabled, enabled, attributes)
        addAttributeIfNotNull(AttributeType.selected, selected, attributes)
        addAttributeIfNotNull(AttributeType.checkable, checkable, attributes)
        addAttributeIfNotNull(AttributeType.isInputField, isInputField, attributes)
        addAttributeIfNotNull(AttributeType.clickable, clickable, attributes)
        addAttributeIfNotNull(AttributeType.longClickable, longClickable, attributes)
        addAttributeIfNotNull(AttributeType.scrollable, scrollable, attributes)
        addAttributeIfNotNull(AttributeType.checked, checked, attributes)
        addAttributeIfNotNull(AttributeType.isLeaf, isLeaf, attributes)

        val parentId = if (dumpParentUUID() !="null")
            UUID.fromString(dumpParentUUID())
        else
            emptyUUID

        val childDumped = dumpChildrenAVS()
        val childAVSIds = AutAutModelLoader.splitCSVLineToField(childDumped)
        val childAVSs = HashSet<UUID>()
        if (!(childAVSIds.size==1 && (childAVSIds.single().isBlank() || childAVSIds.single()=="\"\""))) {
            childAVSIds.forEach { avsId ->
                childAVSs.add(UUID.fromString(avsId))
            }
        }

        val cardinality = Cardinality.values().find { it.name == cardinality.toString() }!!
        val oldFullAttributeValuationSet = fullAttributeValuationMap()
        val newFullAttributeValuationSet = listOf<String>(parentId.toString(),attributes.toSortedMap().toString(),childAVSs.toString(),cardinality.toString(),activity).joinToString("<;>")
        val oldUUID = oldFullAttributeValuationSet.toUUID()
        val newUUID = newFullAttributeValuationSet.toUUID()

        assert(oldUUID == newUUID)
    }
    private fun addAttributeIfNotNull(attributeType: AttributeType, attributeValue: String, attributes: HashMap<AttributeType,String>) {
        if (attributeValue!= "null" ) {
            attributes.put(attributeType,attributeValue)
        }
    }
    private fun dumpParentUUID() = if (parentAttributeValuationSetId == emptyUUID) "null" else parentAttributeValuationSetId.toString()

    fun dump(bufferedWriter: BufferedWriter, dumpedAttributeValuationSets: ArrayList<UUID>, abstractState: AbstractState ) {
        bufferedWriter.write(this.dump(abstractState))

        dumpedAttributeValuationSets.add(this.avsId)
        if (parentAttributeValuationSetId!= emptyUUID) {
            if (!dumpedAttributeValuationSets.contains(parentAttributeValuationSetId)) {
                bufferedWriter.newLine()
                val parentAttributeValuationSet = allAttributeValuationSet[activity]!!.get(parentAttributeValuationSetId)!!
                parentAttributeValuationSet.dump(bufferedWriter, dumpedAttributeValuationSets,abstractState)
            }
        }
        childAttributeValuationSetIds.forEach {
            if (!dumpedAttributeValuationSets.contains(it)) {
                val childAttributeValuationSet = allAttributeValuationSet[activity]!!.get(it)!!
                bufferedWriter.newLine()
                childAttributeValuationSet.dump(bufferedWriter, dumpedAttributeValuationSets,abstractState)
            }
        }
    }

    companion object {
       val allAttributeValuationSet: HashMap<String, HashMap<UUID,AttributeValuationSet>> = HashMap()
        /* fun createOrGetExisitingObject(attributePath: AttributePath, cardinality: Cardinality, activity: String): AttributeValuationSet {

        }*/
    }
}

enum class Cardinality{
    ZERO,
    ONE,
    MANY
}