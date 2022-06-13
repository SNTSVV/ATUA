@file:Suppress("unused")

package org.droidmate.deviceInterface.communication

import org.droidmate.deviceInterface.exploration.DeactivatableFlag
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI

// REMARK: this data class needs to stay in the communication library as the serializer will otherwise throw class def not found
/** this is only supposed to be used internally in the device communication interface (in device driver and for serializer read) */
data class UiElementProperties(
	override val idHash: Int,
	override val text: String,
	override val contentDesc: String,
	override val resourceId: String,
	override val className: String,
	override val packageName: String,
	override val enabled: Boolean,
	override val isInputField: Boolean,
	override val isPassword: Boolean,
	override val clickable: Boolean,
	override val longClickable: Boolean,
	override val scrollable: Boolean,
	override val checked: DeactivatableFlag,
	override val focused: DeactivatableFlag,
	override val selected: DeactivatableFlag,
	override val boundaries: Rectangle,
	override val definedAsVisible: Boolean,
	override val visibleAreas: List<Rectangle>,
	override val xpath: String,
	override val parentHash: Int,
	override val childHashes: List<Int> = emptyList(),
	override val isKeyboard: Boolean,
	override val metaInfo: List<String>,
	override val hasUncoveredArea: Boolean,
	override val hasClickableDescendant: Boolean,
	override val visibleBounds: Rectangle,
	override val imgId: Int,
//		override val isInBackground: Boolean,
	val allSubAreas: List<Rectangle>,
	override val hintText: String,
	override val inputType: Int
) : UiElementPropertiesI {


	companion object {
		// necessary for TCP communication, otherwise it would be computed by the class hash which may cause de-/serialization errors
		const val serialVersionUID: Long = 5205083142890068067//		@JvmStatic
	}

}


