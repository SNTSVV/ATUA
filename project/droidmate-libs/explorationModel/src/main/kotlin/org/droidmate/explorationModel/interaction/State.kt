/*
 * Copyright (c) 2019.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package org.droidmate.explorationModel.interaction

import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.dump.sep
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.plus
import org.droidmate.explorationModel.retention.StringCreator
import java.io.File
import java.util.*

/**
 * States have two components, the Id determined by its Widgets image, text and description and the ConfigId defined by the WidgetsProperties.
 ** be aware that the list of widgets is not guaranteed to be sorted in any specific order
 * You should avoid creating instances of State directly and instead use the model to generate states
 * in order to avoid incompatibilities with extension classes of State.
 * */
open class State<out W: Widget> (_widgets: Collection<W>, val isHomeScreen: Boolean = false) {

	val stateId by lazy {
		ConcreteId(uid, configId)
	}

	val uid: UUID by lazy { lazyIds.value.uid }
	val configId: UUID by lazy { lazyIds.value.configId }

	@Suppress("unused")  // used on the exploration layer for strategy decisions
	val hasActionableWidgets by lazy{ actionableWidgets.isNotEmpty() }
	@Suppress("unused")  // used on the exploration layer for strategy decisions
	val hasEdit: Boolean by lazy { widgets.any { it.isInputField } }
	open val widgets by lazy { _widgets.sortedBy { it.id.toString() } 	}

	/**------------------------------- open function default implementations ------------------------------------------**/

	/** Elements we can interact with, however (some of) these may be currently out of screen,
	 * such that we need to navigate (scroll) to them firs.
	 * To know if we can directly interact with any widget right now check its property `canInteractWith`.
	 */
	open val actionableWidgets by lazy { widgets.filter { it.isInteractive } }
	open val visibleTargets by lazy { actionableWidgets.filter { it.canInteractWith	}}

	protected open val lazyIds: Lazy<ConcreteId> =
			lazy {
				widgets.fold(ConcreteId(emptyUUID, emptyUUID)) { (id, configId), widget ->
					// e.g. keyboard elements are ignored for uid computation within [addRelevantId]
					// however different selectable auto-completion proposes are only 'rendered'
					// such that we have to include the img id (part of configId) to ensure different state configuration id's if these are different
					ConcreteId(addRelevantId(id, widget), configId + widget.uid + widget.id.configId)
				}
			}


	/**
	 * We ignore keyboard elements from the unique identifier, they will be only part of this states configuration.
	 * For elements without text nlpText only the image is available which may introduce variance just due to sleigh color differences, therefore
	 * non-text elements are only considered if they can be acted upon or are leaf elements
	 */
	protected open fun isRelevantForId(w: Widget): Boolean = ( !isHomeScreen && !w.isKeyboard
			&& (w.nlpText.isNotBlank() || w.isInteractive || w.isLeaf()	)
			)


	@Suppress("SpellCheckingInspection", "unused")  // used on the exploration layer for strategy decisions
	open val isAppHasStoppedDialogBox: Boolean by lazy {
		(widgets.any { it.resourceId == "android:id/aerr_close" } &&
				widgets.any { it.resourceId == "android:id/aerr_wait" }) ||
				widgets.any {it.resourceId == "android:id/aerr_restart"}
	}

	@Suppress("unused")  // used on the exploration layer for strategy decisions
	open val isRequestRuntimePermissionDialogBox: Boolean	by lazy {
		widgets.any { // identify if we have a permission request
			it.isVisible && 		// permission request are always visible on the current screen
					(it.resourceId.startsWith(resIdRuntimePermissionDialog1)  ||
							it.resourceId.startsWith(resIdRuntimePermissionDialog2) ||
					// handle cases for apps who 'customize' this request and use own resourceIds e.g. Home-Depot
					when(it.text.toUpperCase()) {
						"ALLOW", "DENY", "DON'T ALLOW" -> true
						else -> false
					})
		}
				// check that we have a ok or allow button
				&& widgets.any{it.isVisible && it.text.toUpperCase().let{ wText -> wText.contains("ALLOW") || wText == "OK" } }
	}


	/** write CSV
	 *
	 * [uid] => stateId_[HS,{}] as file name (HS is only present if isHomeScreen is true)
	 */
	open fun dump(config: ModelConfig) {
		File( config.stateFile(stateId,isHomeScreen) ).bufferedWriter().use { all ->
			all.write(StringCreator.widgetHeader(config[sep]))

			widgets.forEach {
				all.newLine()
				all.write( StringCreator.createPropertyString(it, config[sep]) )
			}
		}
	}

	/** used by our tests to verify correct widget-string representations */
	internal fun widgetsDump(sep: String) = widgets.map{ StringCreator.createPropertyString(it, sep) }

	companion object {
		private const val resIdRuntimePermissionDialog1 = "com.android.packageinstaller:id/"
		private const val resIdRuntimePermissionDialog2 = "com.android.permissioncontroller:id/"


		/** dummy element if a state has to be given but no widget data is available */
//		@JvmStatic
//		internal val emptyState: State by lazy { State( emptyList() ) }

	}
	/** this function is used to add any widget.uid if it fulfills specific criteria
	 * (i.e. it can be acted upon, has text nlpText or it is a leaf) */
	protected fun addRelevantId(id: UUID, w: Widget): UUID = if (isRelevantForId(w)){ id + w.uid } else id

	override fun equals(other: Any?): Boolean {
		return when (other) {
			is State<*> -> uid == other.uid && configId == other.configId
			else -> false
		}
	}

	override fun hashCode(): Int {
		return uid.hashCode() + configId.hashCode()
	}

	override fun toString(): String {
		return "State[$stateId, widgets=${widgets.size}]"
	}

}
