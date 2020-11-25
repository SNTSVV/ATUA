package org.droidmate.exploration.modelFeatures.autaut.DSTG

import org.droidmate.explorationModel.toUUID
import java.io.BufferedWriter
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

data class AttributePath (
        val localAttributes: HashMap<AttributeType, String> = HashMap(),
        val parentAttributePath: AttributePath? = null,
        val childAttributePaths: HashSet<AttributePath>?){

    val attributePathId: UUID by lazy { lazyIds.value }

    protected open val lazyIds: Lazy<UUID> =
            lazy {
                listOf<Any>(this.toString()).joinToString(separator = "<;>").toUUID()
            }
    override fun equals(other: Any?): Boolean {
        if (other !is AttributePath)
            return false
        if (other.hashCode() == this.hashCode())
            return true
        return false
    }

    override fun hashCode(): Int {
        var hashValue: Int=31
        hashValue = hashValue + this.localAttributes.hashCode()
        hashValue = hashValue + this.parentAttributePath.hashCode()
        hashValue = hashValue + this.childAttributePaths.hashCode()

        return hashValue
    }

    fun contains(containedAttributePath: AttributePath): Boolean {
        //check parent first
        if (parentAttributePath!=null && containedAttributePath.parentAttributePath!=null) {
            if (!parentAttributePath.contains(containedAttributePath.parentAttributePath)) {
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
        if (childAttributePaths == null) {
            if (containedAttributePath.childAttributePaths == null) {
                return true
            } else {
                return false
            }
        }
        if (containedAttributePath.childAttributePaths == null)
            return true
        if (!containedAttributePath.childAttributePaths.isEmpty()) {
            containedAttributePath.childAttributePaths.forEach { containedChildAttributePath ->
                if (!childAttributePaths.any { it.contains(containedChildAttributePath) }) {
                    return false
                }
            }
        }

        return true

    }

    fun dump(activity: String, dumpedAttributeValuationSets: ArrayList<Pair<UUID, String>>, bufferedWriter: BufferedWriter) {
        dumpedAttributeValuationSets.add(Pair(attributePathId,activity))
        if (parentAttributePath!=null && !dumpedAttributeValuationSets.contains(Pair(parentAttributePath.attributePathId,activity))) {
            parentAttributePath.dump(activity, dumpedAttributeValuationSets, bufferedWriter)
        }
        bufferedWriter.newLine()
        bufferedWriter.write("$activity;")
        bufferedWriter.write(dump())
        if (childAttributePaths!=null) {
            childAttributePaths.forEach {
                if (!dumpedAttributeValuationSets.contains(Pair(it.attributePathId,activity))) {
                    it.dump(activity,dumpedAttributeValuationSets,bufferedWriter)
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
                "${parentAttributePath?.attributePathId};${childAttributePaths?.map { it.attributePathId }};"
    }

}