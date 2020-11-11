package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.explorationModel.toUUID
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

data class AttributePath (
        val localAttributes: HashMap<AttributeType, String> = HashMap(),
        val parentAttributePath: AttributePath? = null,
        val childAttributePaths: HashSet<AttributePath> = HashSet()){

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
        if (localAttributes[AttributeType.checked]!!.equals("true"))
        {
            return true
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

        if (!containedAttributePath.childAttributePaths.isEmpty()) {
            containedAttributePath.childAttributePaths.forEach { containedChildAttributePath ->
                if (!childAttributePaths.any { it.contains(containedChildAttributePath) }) {
                    return false
                }
            }
        }

        return true

    }

    fun hasParentWithClassName(s: String): Boolean {
        var parentAttributePath = this.parentAttributePath
        while (parentAttributePath!=null) {
            if (parentAttributePath.getClassName().contains(s)) {
                return true
            }
            parentAttributePath = parentAttributePath.parentAttributePath
        }
        return false
    }

    fun hasParent(attributePath: AttributePath): Boolean {
        var parentAttributePath = this.parentAttributePath
        while (parentAttributePath!=null) {
            if (parentAttributePath.equals(attributePath)) {
                return true
            }
            parentAttributePath = parentAttributePath.parentAttributePath
        }
        return false
    }

    fun isSelected(): Boolean {
        if (localAttributes[AttributeType.selected]!!.equals("true"))
        {
            return true
        }
        return false
    }
}