package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.exploration.modelFeatures.atua.AutAutModelLoader
import org.droidmate.exploration.modelFeatures.atua.EWTG.DescendantLayoutDirection
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.toUUID
import java.io.BufferedWriter
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AttributeValuationMap {
    var avmId: String
    val localAttributes: HashMap<AttributeType, String> = HashMap()
    var parentAttributeValuationMapId: String = ""
    var cardinality: Cardinality
    var activity: String
    var exerciseCount: Int = 0
    val actionCount = HashMap<AbstractAction, Int>()
    var captured = false
    var hashCode: Int = 0

    constructor(avmId: String, localAttributes: Map<AttributeType,String>, parentAVMId: String, cardinality: Cardinality, activity: String) {
        this.avmId = avmId
        this.localAttributes.putAll(localAttributes)
        this.parentAttributeValuationMapId = parentAVMId
        this.cardinality = cardinality
        this.activity = activity
        hashCode = this.fullAttributeValuationMap().hashCode()
        if (!ALL_ATTRIBUTE_VALUATION_MAP.containsKey(activity)) {
            ALL_ATTRIBUTE_VALUATION_MAP.put(activity, HashMap())
        }
        ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.put(avmId,this)
    }
    constructor(attributePath: AttributePath,  cardinality: Cardinality,  activity: String, attributPath_cardinality: HashMap<AttributePath,Cardinality>) {
        if (!ALL_ATTRIBUTE_VALUATION_MAP.containsKey(activity)) {
            ALL_ATTRIBUTE_VALUATION_MAP.put(activity, HashMap())
        }
        localAttributes.putAll(attributePath.localAttributes)
        avmId = "${localAttributes.get(AttributeType.className)}_${localAttributes.get(AttributeType.resourceId)}_${maxId++}"

        if (attributePath.parentAttributePathId == emptyUUID) {
            parentAttributeValuationMapId = ""
        } else {
            val parentAttributePath = AttributePath.getAttributePathById(attributePath.parentAttributePathId,activity)
            if (ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.any { it.value.haveTheSameAttributePath(parentAttributePath) }) {
                parentAttributeValuationMapId = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.map {it.value}.find{ it.haveTheSameAttributePath(parentAttributePath) }!!.avmId
            } else {
                var parentCardinality =  attributPath_cardinality.filterKeys { it.attributePathId == attributePath.parentAttributePathId }.values.firstOrNull()
                if (parentCardinality == null)
                    parentCardinality = Cardinality.ONE
                val newParentAttributeValuationSet = AttributeValuationMap(parentAttributePath,
                        parentCardinality, activity,attributPath_cardinality)
                parentAttributeValuationMapId = newParentAttributeValuationSet.avmId
            }
        }
        this.cardinality = cardinality
        this.activity = activity
        //TODO
/*        if (attributePath.childAttributePathIds.isNotEmpty()) {
            attributePath.childAttributePathIds.map { AttributePath.getAttributePathById(it,activity) }.forEach {
                val childAttributeValuationSet = AttributeValuationMap(it,Cardinality.ONE,activity, HashMap())
                childAttributeValuationSetIds.add(childAttributeValuationSet.avsId)
            }
        }*/
        //TODO


        if (avmId == parentAttributeValuationMapId) {
            ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.put(avmId,this)
        } else {
            ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.put(avmId, this)
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
        if (this.avmId == derivedAttributeValuationSet.avmId)
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
        if (attributeValuationMap.parentAttributeValuationMapId== "")
            return false
         val parentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(attributeValuationMap.parentAttributeValuationMapId)!!
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
        if (parentAttributeValuationMapId != "" && attributePath.parentAttributePathId == emptyUUID)
            return false
        if (parentAttributeValuationMapId == "" && attributePath.parentAttributePathId != emptyUUID)
            return false
        if (parentAttributeValuationMapId != "") {
            if (attributePath.parentAttributePathId == emptyUUID) {
                return false
            }
            val parentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(parentAttributeValuationMapId)!!
            val parentAttributePath = AttributePath.getAttributePathById(attributePath.parentAttributePathId,activity)
            if (!parentAttributeValuationSet.haveTheSameAttributePath(parentAttributePath)) {
                return false
            }
        }
        return true
    }

    fun haveTheSameAttributePath(cmpAttributeValuationMap: AttributeValuationMap): Boolean {
        if (avmId != cmpAttributeValuationMap.avmId)
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
        if (parentAttributeValuationMapId!= "" && abstractAttributeValuationMap.parentAttributeValuationMapId!= "") {
            val parentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(parentAttributeValuationMapId)
            if (parentAttributeValuationSet == null) {
                throw Exception("Cannot find attributeValuationSet $parentAttributeValuationMapId")
                return false
            }
            val abstractParentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(abstractAttributeValuationMap.parentAttributeValuationMapId)
            if (abstractParentAttributeValuationSet == null) {
                throw Exception("Cannot find attributeValuationSet $parentAttributeValuationMapId")
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
        val s =  listOf<String>(parentAttributeValuationMapId.toString(),localAttributes.toSortedMap().toString(),cardinality.toString(),activity).joinToString("<;>")
        return s
    }
    /**
     * Write in csv
     */
    fun dump(abstractState: AbstractState): String {
        //testReloadAttributes()
        val dumpedString = listOf<String>(avmId,parentAttributeValuationMapId,dumpLocalAttributes()
                ,cardinality.toString(),captured.toString(),
                abstractState.EWTGWidgetMapping[this]?.widgetId?:"",hashCode.toString()).joinToString(";")
        loadDumpedString(dumpedString)
        return dumpedString
        /*return "${avmId};${getClassName()};${getResourceId()};" +
                "\"${localAttributes.get(AttributeType.contentDesc)}\";\"${localAttributes.get(AttributeType.text)}\";${isEnable()};" +
                "${localAttributes.get(AttributeType.selected)};${isCheckable()};${isInputField()};" +
                "${isClickable()};${isLongClickable()};${isScrollable()};" +
                "${localAttributes.get(AttributeType.checked)};${localAttributes.get(AttributeType.isLeaf)};" +
                "\"${localAttributes.get(AttributeType.childrenStructure)}\";\"${localAttributes.get(AttributeType.childrenText)}\";\"${localAttributes.get(AttributeType.siblingsInfo)}\";${dumpParentUUID()};$cardinality;$captured;" +
                "\"${abstractState.EWTGWidgetMapping[this]?.widgetId}\";$hashCode"*/
    }

    fun loadDumpedString(line: String) {
        val splites = AutAutModelLoader.splitCSVLineToField(line)
        val avmId = splites[0]
        val parentAVMId = splites[1]
        var index = 2
        val localAttributes = HashMap<AttributeType,String>()
        AttributeType.values().toSortedSet().forEach { attributeType ->
            val value = splites[index]!!
            addAttributeIfNotNull(attributeType,value,localAttributes)
            index++
        }
        val cardinality = Cardinality.values().find { it.toString() == splites[index]!! }!!
        val captured = splites[index+1]!!
        val hashcode = splites[index+3]!!.toInt()
        val newAttributeValuationMap = AttributeValuationMap(
                avmId = avmId,
                localAttributes = localAttributes,
                parentAVMId = parentAVMId,
                cardinality = cardinality,
                activity = activity
        )
        assert(hashcode==newAttributeValuationMap.hashCode)
    }

    private fun dumpLocalAttributes(): String {
        var result = ""
        var first = true
        AttributeType.values().toSortedSet().forEach { attributeType ->
            val value = localAttributes.get(attributeType)
            if (value==null) {
                result+= "null"
            } else {
                result += dumpAttributeValueToString(attributeType, value)
            }
            result+=";"
        }
        result = result.substring(0,result.length-1)
        return result
    }

    fun dumpAttributeValueToString(attributeType: AttributeType,value: Any?): String {
        return when (attributeType) {
            AttributeType.resourceId -> value as String
            AttributeType.className -> value as String
            AttributeType.contentDesc -> value as String
            AttributeType.text -> value as String
            AttributeType.checkable -> value.toString()
            AttributeType.enabled -> value.toString()
            AttributeType.password -> value.toString()
            AttributeType.selected -> value.toString()
            AttributeType.isInputField -> value.toString()
                //interactive
            AttributeType.clickable -> value.toString()
            AttributeType.longClickable -> value.toString()
            AttributeType.scrollable -> value.toString()
            AttributeType.scrollDirection -> value.toString()
            AttributeType.checked -> value.toString()
            AttributeType.isLeaf -> value.toString()
            AttributeType.childrenStructure -> "\""+value.toString()+"\""
            AttributeType.childrenText -> "\""+value.toString()+"\""
            AttributeType. siblingsInfo -> "\""+value.toString()+"\""
        }
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
    private fun dumpParentUUID() = if (parentAttributeValuationMapId == "") "null" else parentAttributeValuationMapId

    fun dump(bufferedWriter: BufferedWriter, dumpedAttributeValuationSets: ArrayList<String>, abstractState: AbstractState ) {
        bufferedWriter.write(this.dump(abstractState))

        dumpedAttributeValuationSets.add(this.avmId)
        if (parentAttributeValuationMapId!= "") {
            if (!dumpedAttributeValuationSets.contains(parentAttributeValuationMapId)) {
                bufferedWriter.newLine()
                val parentAttributeValuationSet = ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(parentAttributeValuationMapId)!!
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