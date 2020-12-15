package org.droidmate.exploration.modelFeatures.autaut.DSTG

import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.toUUID
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
        //check parent first
        if (parentAttributePathId!= emptyUUID && containedAttributePath.parentAttributePathId!= emptyUUID) {
            val parentAttributePath = allAttributePaths.get(activity)!!.get(parentAttributePathId)!!
            val containedParentAttributePath = allAttributePaths.get(activity)!!.get(containedAttributePath.parentAttributePathId)!!
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

        return true

    }

    fun dump(activity: String, dumpedAttributeValuationSets: ArrayList<Pair<String, UUID>>, bufferedWriter: BufferedWriter,capturedAttributePaths: List<AttributePath>) {
        dumpedAttributeValuationSets.add(Pair(activity,attributePathId))
        if (parentAttributePathId!= emptyUUID && !dumpedAttributeValuationSets.contains(Pair(activity,parentAttributePathId))) {
            val parentAttributePath = allAttributePaths.get(activity)!!.get(parentAttributePathId)!!
            parentAttributePath.dump(activity, dumpedAttributeValuationSets, bufferedWriter,capturedAttributePaths)
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
        return "${attributePathId};${localAttributes.get(AttributeType.className)};${localAttributes.get(AttributeType.resourceId)};" +
                "${localAttributes.get(AttributeType.contentDesc)};${localAttributes.get(AttributeType.text)};${localAttributes.get(AttributeType.enabled)};" +
                "${localAttributes.get(AttributeType.selected)};${localAttributes.get(AttributeType.checked)!=null};${localAttributes.get(AttributeType.isInputField)};" +
                "${localAttributes.get(AttributeType.clickable)};${localAttributes.get(AttributeType.longClickable)};${localAttributes.get(AttributeType.scrollable)};" +
                "${localAttributes.get(AttributeType.checked)};${localAttributes.get(AttributeType.isLeaf)};" +
                "${if(parentAttributePathId== emptyUUID)"null" else parentAttributePathId};\"${childAttributePathIds.joinToString(";")}\""
    }

    companion object {
        val allAttributePaths = HashMap<String, HashMap<UUID, AttributePath>>()

        fun getAttributePathById(uuid: UUID, activity: String): AttributePath {
            if (!allAttributePaths.get(activity)!!.containsKey(uuid))
                throw Exception("Cannot find attributePath $uuid")
            return allAttributePaths.get(activity)!!.get(uuid)!!
        }
    }
}