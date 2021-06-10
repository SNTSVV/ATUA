package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.toUUID
import org.slf4j.Logger
import java.io.BufferedWriter
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

data class AttributePath (
        val localAttributes: HashMap<AttributeType, String> = HashMap(),
        val parentAttributePathId: UUID = emptyUUID,
        val childAttributePathIds: HashSet<UUID> = HashSet(),
        val activity: String
        ){

    val attributePathId: UUID by lazy { lazyIds.value }

    protected open val lazyIds: Lazy<UUID> =
            lazy {
                listOf<Any>(parentAttributePathId,localAttributes.toSortedMap().toString(),childAttributePathIds.sorted().toString()).joinToString(separator = "<;>").toUUID()
            }

    init {
        if (!allAttributePaths.containsKey(activity)) {
            allAttributePaths.put(activity, HashMap())
        }
        if (!allAttributePaths.get(activity)!!.containsKey(attributePathId)) {
            allAttributePaths.get(activity)!!.put(attributePathId, this)
        }
    }


    override fun equals(other: Any?): Boolean {
        if (other !is AttributePath)
            return false
        if (other.hashCode() == this.hashCode())
            return true
        return false
    }

    override fun hashCode(): Int {
        return attributePathId.hashCode()
    }

    fun contains(containedAttributePath: AttributePath, activity: String): Boolean {
        if (this.attributePathId == containedAttributePath.attributePathId)
            return true
        return false
        /*//check parent first
        if (parentAttributePathId!= emptyUUID && containedAttributePath.parentAttributePathId!= emptyUUID) {
            val parentAttributePath = allAttributePaths.get(activity)!!.get(parentAttributePathId)!!
            val containedParentAttributePath = allAttributePaths.get(activity)!!.get(containedAttributePath.parentAttributePathId)
            if (containedParentAttributePath == null) {
                return false
            }
            if (!parentAttributePath.contains(containedParentAttributePath,activity)) {
                return false
            }
        }
        //check local
        containedAttributePath.localAttributes.forEach {
            if (!localAttributes.containsKey(it.key))
                return false
            if (localAttributes[it.key] != it.value)
                return false
        }
        if (childAttributePathIds.isEmpty()) {
            if (containedAttributePath.childAttributePathIds.isEmpty()) {
                return true
            } else {
                return false
            }
        }
        if (containedAttributePath.childAttributePathIds.isEmpty())
            return true
        val childAttributePaths = childAttributePathIds.map {
            allAttributePaths.get(activity)!!.get(it)!!
        }
        val containedChildAttributePaths = containedAttributePath.childAttributePathIds.map {
            allAttributePaths.get(activity)!!.get(it)!!
        }
       containedChildAttributePaths.forEach { containedChildAttributePath ->
            if (!childAttributePaths.any { it.contains(containedChildAttributePath,activity) }) {
                return false
            }
        }

        return true*/

    }

    fun dump(activity: String, dumpedAttributeValuationSets: ArrayList<Pair<String, UUID>>, bufferedWriter: BufferedWriter,capturedAttributePaths: List<AttributePath>) {
        dumpedAttributeValuationSets.add(Pair(activity,attributePathId))
        if (parentAttributePathId!= emptyUUID && !dumpedAttributeValuationSets.contains(Pair(activity,parentAttributePathId))) {
            val parentAttributePath = getAttributePathById(parentAttributePathId,activity)
            if (parentAttributePath==null)
            {
                throw Exception("")
            }
            parentAttributePath?.dump(activity, dumpedAttributeValuationSets, bufferedWriter,capturedAttributePaths)
        }
        bufferedWriter.newLine()
        bufferedWriter.write("$activity;")
        bufferedWriter.write(dump())
        if(capturedAttributePaths.contains(this)) {
            bufferedWriter.write(";TRUE")
        } else {
            bufferedWriter.write(";FALSE")
        }
        if (childAttributePathIds.isNotEmpty()) {
            childAttributePathIds.forEach {
                if (!dumpedAttributeValuationSets.contains(Pair(activity,it))) {
                    val childAttributePath = allAttributePaths.get(activity)!!.get(it)!!
                    childAttributePath.dump(activity,dumpedAttributeValuationSets,bufferedWriter,capturedAttributePaths)
                }
            }
        }
    }

    /**
     * Write in csv
     */
    fun dump(): String {
        val dumpedString = listOf<String>(attributePathId.toString(),dumpParentAttributePathId(),dumpLocalAttributes()
        ).joinToString(";")
        //loadDumpedString(dumpedString)
        return dumpedString
       /* return "${attributePathId};${localAttributes.get(AttributeType.className)};${localAttributes.get(AttributeType.resourceId)};" +
                "${localAttributes.get(AttributeType.contentDesc)};${localAttributes.get(AttributeType.text)};${localAttributes.get(AttributeType.enabled)};" +
                "${localAttributes.get(AttributeType.selected)};${localAttributes.get(AttributeType.checked)!=null};${localAttributes.get(AttributeType.isInputField)};" +
                "${localAttributes.get(AttributeType.clickable)};${localAttributes.get(AttributeType.longClickable)};${localAttributes.get(AttributeType.scrollable)};" +
                "${localAttributes.get(AttributeType.checked)};${localAttributes.get(AttributeType.isLeaf)};" +
                "${if(parentAttributePathId== emptyUUID)"null" else parentAttributePathId};\"${childAttributePathIds.joinToString(";")}\""*/
    }

    private fun dumpParentAttributePathId(): String {
        return if(parentAttributePathId== emptyUUID)
            "null"
            else parentAttributePathId.toString()
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
    companion object {
        val allAttributePaths = HashMap<String, HashMap<UUID, AttributePath>>()

        fun getAttributePathById(uuid: UUID, activity: String): AttributePath? {
            if (!allAttributePaths.get(activity)!!.containsKey(uuid))
                return null
            return allAttributePaths.get(activity)!!.get(uuid)!!
        }
    }
}