package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.exploration.modelFeatures.atua.EWTG.DescendantLayoutDirection
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.toUUID
import java.io.BufferedWriter
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AttributeValuationMap (val localAttributes: HashMap<AttributeType, String> = HashMap(),
                             var parentAttributeValuationSetId: String = "",
                             val cardinality: Cardinality,
                             val activity: String) {

    var exerciseCount: Int = 0
    val actionCount = HashMap<AbstractAction, Int>()
    var captured = false
    var hashCode: Int = 0
    lateinit var avsId: String

    init {
        hashCode = this.fullAttributeValuationMap().hashCode()
    }

    constructor(attributePath: AttributePath,  cardinality: Cardinality,  activity: String, attributPath_cardinality: HashMap<AttributePath,Cardinality>): this(cardinality = cardinality,activity = activity) {
        if (!ALL_ATTRIBUTE_VALUATION_MAP.containsKey(activity)) {
            ALL_ATTRIBUTE_VALUATION_MAP.put(activity, HashMap())
        }
        localAttributes.putAll(attributePath.localAttributes)
        avsId = "${localAttributes.get(AttributeType.className)}_${localAttributes.get(AttributeType.resourceId)}_${maxId++}"

        if (attributePath.parentAttributePathId == emptyUUID) {
            parentAttributeValuationSetId = ""
        } else {
            val parentAttributePath = AttributePath.getAttributePathById(attributePath.parentAttributePathId,activity)
            if (ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.any { it.value.haveTheSameAttributePath(parentAttributePath) }) {
                parentAttributeValuationSetId = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.map {it.value}.find{ it.haveTheSameAttributePath(parentAttributePath) }!!.avsId
            } else {
                var parentCardinality =  attributPath_cardinality.filterKeys { it.attributePathId == attributePath.parentAttributePathId }.values.firstOrNull()
                if (parentCardinality == null)
                    parentCardinality = Cardinality.ONE
                val newParentAttributeValuationSet = AttributeValuationMap(parentAttributePath,
                        parentCardinality, activity,attributPath_cardinality)
                parentAttributeValuationSetId = newParentAttributeValuationSet.avsId
            }
        }
        //TODO
/*        if (attributePath.childAttributePathIds.isNotEmpty()) {
            attributePath.childAttributePathIds.map { AttributePath.getAttributePathById(it,activity) }.forEach {
                val childAttributeValuationSet = AttributeValuationMap(it,Cardinality.ONE,activity, HashMap())
                childAttributeValuationSetIds.add(childAttributeValuationSet.avsId)
            }
        }*/
        //TODO


        if (avsId == parentAttributeValuationSetId) {
            ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.put(avsId,this)
        } else {
            ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.put(avsId, this)
        }
        hashCode = this.fullAttributeValuationMap().hashCode()

    }


    fun initActions() {
        /*if (!AbstractStateManager.instance.activity_attrValSetsMap.containsKey(activity))
            AbstractStateManager.instance.activity_attrValSetsMap.put(activity, ArrayList())*/
        if (isClickable() ) {
            if (getClassName().equals("android.webkit.WebView")) {
                val itemAbstractAction = AbstractAction(
                        actionType = AbstractActionType.ITEM_CLICK,
                        attributeValuationMap = this
                )
                actionCount.putIfAbsent(itemAbstractAction,0)
                val itemLongClickAbstractAction = AbstractAction(
                        actionType = AbstractActionType.ITEM_LONGCLICK,
                        attributeValuationMap = this
                )
                actionCount.putIfAbsent(itemLongClickAbstractAction,0)

            } else {
                val abstractAction = AbstractAction(
                        actionType = AbstractActionType.CLICK,
                        attributeValuationMap = this
                )
                actionCount.putIfAbsent(abstractAction, 0)
            }
        }
        if (isLongClickable() && !isInputField()) {
            val abstractAction = AbstractAction(
                    actionType = AbstractActionType.LONGCLICK,
                    attributeValuationMap = this
            )
            actionCount.putIfAbsent(abstractAction, 0)
        }
        if (isScrollable()) {
            if (localAttributes[AttributeType.scrollDirection]== DescendantLayoutDirection.HORIZONTAL.toString()) {
                val abstractActionSwipeLeft = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationMap = this,
                        extra = "SwipeLeft"
                )
                val abstractActionSwipeRight = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationMap = this,
                        extra = "SwipeRight"
                )
                actionCount.putIfAbsent(abstractActionSwipeLeft, 0)
                actionCount.putIfAbsent(abstractActionSwipeRight, 0)
            } else if (localAttributes[AttributeType.scrollDirection]== DescendantLayoutDirection.VERTICAL.toString()) {
                val abstractActionSwipeUp = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationMap = this,
                        extra = "SwipeUp"
                )
                val abstractActionSwipeDown = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationMap = this,
                        extra = "SwipeDown"
                )
                actionCount.putIfAbsent(abstractActionSwipeUp, 0)
                actionCount.putIfAbsent(abstractActionSwipeDown, 0)

            } else {
                val abstractActionSwipeUp = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationMap = this,
                        extra = "SwipeUp"
                )
                val abstractActionSwipeDown = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationMap = this,
                        extra = "SwipeDown"
                )
                val abstractActionSwipeLeft = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationMap = this,
                        extra = "SwipeLeft"
                )
                val abstractActionSwipeRight = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        attributeValuationMap = this,
                        extra = "SwipeRight"
                )
                actionCount.putIfAbsent(abstractActionSwipeUp, 0)
                actionCount.putIfAbsent(abstractActionSwipeDown, 0)
                actionCount.putIfAbsent(abstractActionSwipeLeft, 0)
                actionCount.putIfAbsent(abstractActionSwipeRight, 0)
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
                    attributeValuationMap = this
            )
            actionCount.putIfAbsent(abstractAction, 0)
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

    fun havingSameContent(currentAbstractState: AbstractState, comparedAttributeValuationMap: AttributeValuationMap, comparedAbstractState: AbstractState): Boolean {
        val widgetGroupChildren = currentAbstractState.attributeValuationMaps.filter {
            isParent(it)
        }

        val comparedWidgetGroupChildren = comparedAbstractState.attributeValuationMaps.filter {
            comparedAttributeValuationMap.isParent(it)
        }
        widgetGroupChildren.forEach {w1 ->
            if (!comparedWidgetGroupChildren.any { w2 -> w1 == w2 }) {
                return false
            }
        }
        return true
    }

     fun isParent(attributeValuationMap: AttributeValuationMap): Boolean {
        if (attributeValuationMap.parentAttributeValuationSetId== "")
            return false
         val parentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(attributeValuationMap.parentAttributeValuationSetId)!!
        if (haveTheSameAttributePath(parentAttributeValuationSet))
            return true
        return false
    }

    fun haveTheSameAttributePath(attributePath: AttributePath): Boolean {
        if (localAttributes.hashCode() != attributePath.localAttributes.hashCode()) {
            return false
        }
        /*if (childAttributeValuationSetIds.isEmpty() && attributePath.childAttributePathIds.isNotEmpty()) {
            return false
        }
        if (childAttributeValuationSetIds.isNotEmpty() && attributePath.childAttributePathIds.isEmpty())
            return false
        val childAttributePaths = attributePath.childAttributePathIds.map { AttributePath.getAttributePathById(it,activity) }
        val childAttributeValuationSets = childAttributeValuationSetIds.map { ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(it)!! }
        if (!childAttributeValuationSets.all { childAttributeValuationSet -> childAttributePaths.any { childAttributeValuationSet.haveTheSameAttributePath(it) } }) {
            return false
        }*/
        if (parentAttributeValuationSetId != "" && attributePath.parentAttributePathId == emptyUUID)
            return false
        if (parentAttributeValuationSetId == "" && attributePath.parentAttributePathId != emptyUUID)
            return false
        if (parentAttributeValuationSetId != "") {
            if (attributePath.parentAttributePathId == emptyUUID) {
                return false
            }
            val parentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(parentAttributeValuationSetId)!!
            val parentAttributePath = AttributePath.getAttributePathById(attributePath.parentAttributePathId,activity)
            if (!parentAttributeValuationSet.haveTheSameAttributePath(parentAttributePath)) {
                return false
            }
        }
        return true
    }

    fun haveTheSameAttributePath(cmpAttributeValuationMap: AttributeValuationMap): Boolean {
        if (avsId != cmpAttributeValuationMap.avsId)
            return false
        return true
    }

    fun isDerivedFrom(abstractAttributeValuationMap: AttributeValuationMap): Boolean {
        //check local
        abstractAttributeValuationMap.localAttributes.forEach {
            if (!localAttributes.containsKey(it.key))
                return false
            if (localAttributes[it.key] != it.value)
                return false
        }
/*        if (childAttributeValuationSetIds.isEmpty() && !abstractAttributeValuationMap.childAttributeValuationSetIds.isEmpty()) {
            return false
        }
        if (!abstractAttributeValuationMap.childAttributeValuationSetIds.isEmpty()) {
            val childAttributeValuationSet = childAttributeValuationSetIds.map { ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(it)!! }
            abstractAttributeValuationMap.childAttributeValuationSetIds.map { ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(it)!! }. forEach { moreAbstractChildAVS ->
                if (!childAttributeValuationSet.any { it.isDerivedFrom(moreAbstractChildAVS) }) {
                    return false
                }
            }
        }*/
        if (parentAttributeValuationSetId!= "" && abstractAttributeValuationMap.parentAttributeValuationSetId!= "") {
            val parentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(parentAttributeValuationSetId)
            if (parentAttributeValuationSet == null) {
                throw Exception("Cannot find attributeValuationSet $parentAttributeValuationSetId")
                return false
            }
            val abstractParentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(abstractAttributeValuationMap.parentAttributeValuationSetId)
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
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AttributeValuationMap)
            return false
        return this.hashCode==other.hashCode
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
        val s =  listOf<String>(parentAttributeValuationSetId.toString(),localAttributes.toSortedMap().toString(),cardinality.toString(),activity).joinToString("<;>")
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
                "${dumpParentUUID()};\"${localAttributes.get(AttributeType.childrenStructure)}\";\"${localAttributes.get(AttributeType.childrenText)}\";\"${localAttributes.get(AttributeType.siblingsInfo)}\";$cardinality;$captured;" +
                "\"${abstractState.EWTGWidgetMapping[this]?.widgetId}\""
    }

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
            dumpParentUUID()
        else
            ""

        val cardinality = Cardinality.values().find { it.name == cardinality.toString() }!!
        val oldFullAttributeValuationSet = fullAttributeValuationMap()

        val oldUUID = oldFullAttributeValuationSet.toUUID()

    }
    private fun addAttributeIfNotNull(attributeType: AttributeType, attributeValue: String, attributes: HashMap<AttributeType,String>) {
        if (attributeValue!= "null" ) {
            attributes.put(attributeType,attributeValue)
        }
    }
    private fun dumpParentUUID() = if (parentAttributeValuationSetId == "") "null" else parentAttributeValuationSetId

    fun dump(bufferedWriter: BufferedWriter, dumpedAttributeValuationSets: ArrayList<String>, abstractState: AbstractState ) {
        bufferedWriter.write(this.dump(abstractState))

        dumpedAttributeValuationSets.add(this.avsId)
        if (parentAttributeValuationSetId!= "") {
            if (!dumpedAttributeValuationSets.contains(parentAttributeValuationSetId)) {
                bufferedWriter.newLine()
                val parentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(parentAttributeValuationSetId)!!
                parentAttributeValuationSet.dump(bufferedWriter, dumpedAttributeValuationSets,abstractState)
            }
        }
/*        childAttributeValuationSetIds.forEach {
            if (!dumpedAttributeValuationSets.contains(it)) {
                val childAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(it)!!
                bufferedWriter.newLine()
                childAttributeValuationSet.dump(bufferedWriter, dumpedAttributeValuationSets,abstractState)
            }
        }*/
    }


    companion object {
       val ALL_ATTRIBUTE_VALUATION_MAP: HashMap<String, HashMap<String,AttributeValuationMap>> = HashMap()
        val allWidgetAVMHashMap: HashMap<String, HashMap<Widget,AttributeValuationMap>> = HashMap();
        var maxId: Long = 0
        /* fun createOrGetExisitingObject(attributePath: AttributePath, cardinality: Cardinality, activity: String): AttributeValuationSet {

        }*/
    }
}

enum class Cardinality{
    ZERO,
    ONE,
    MANY
}