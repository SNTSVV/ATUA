// ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
// Copyright (C) 2019 - 2021 University of Luxembourg
//
// This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
//
@file:Suppress("unused", "DEPRECATION", "UNUSED_PARAMETER")

package org.droidmate.exploration.actions

import org.droidmate.deviceInterface.exploration.*
import org.droidmate.explorationModel.ExplorationTrace.Companion.widgetTargets
import org.droidmate.explorationModel.firstCenter
import org.droidmate.explorationModel.firstOrEmpty
import org.droidmate.explorationModel.interaction.Widget
import kotlin.random.Random

/**
 * These are the new interface functions to interact with any widget.
 * The implementation of the actions itself is going to be refactored in the new version and all
 * old ExplorationActions are going to be removed.
 * Instead we are going to have :
 * ExplorationContext and Widgets Actions (via extension function)
 * + a LaunchApp action + ActionQue to handle a set of actions which is executed on the device before fetching a new state
 */

/**
 * issue a click to [this.visibleAreas.firstCenter()] if it exists and to the boundaries center otherwise.
 * The widget has to be clickable and enabled. If it is not definedAsVisible this method will throw an exception
 * (you should use [navigateTo] instead).
 */
@JvmOverloads
fun Widget.click(delay: Long = 0, isVisible: Boolean = false, ignoreClickable: Boolean = false): ExplorationAction {
//	if (!(definedAsVisible || isVisible) || !enabled || !(clickable||selected.isEnabled()||ignoreClickable))
//		throw RuntimeException("ERROR: tried to click non-actionable Widget $this")
	widgetTargets.add(this)
	return clickCoordinate().let { (x, y) -> Click(x, y, true, delay) }
}

fun Widget.clickEvent(delay: Long = 0, ignoreClickable: Boolean = false): ExplorationAction {
//	if (!enabled || !(clickable||selected.isEnabled()||ignoreClickable))
//		throw RuntimeException("ERROR: tried to click non-actionable Widget $this")
	widgetTargets.add(this)
	return if(!clickable)	clickCoordinate().let { (x, y) -> Click(x, y, true, delay) }
	else ClickEvent(this.idHash, true, delay)
}

@JvmOverloads
fun Widget.tick(delay: Long = 0, ignoreVisibility: Boolean = false): ExplorationAction {
//	if (!(definedAsVisible || ignoreVisibility) || !enabled)
//		throw RuntimeException("ERROR: tried to tick non-actionable (checkbox) Widget $this")
	widgetTargets.add(this)
	return clickCoordinate().let { (x, y) -> Tick(idHash,x, y, true, delay) }
}

@JvmOverloads
fun Widget.longClick(delay: Long = 0, isVisible: Boolean = false): ExplorationAction {
//	if (!(definedAsVisible || isVisible) || !enabled || !longClickable)
//		throw RuntimeException("ERROR: tried to long-click non-actionable Widget $this")
	widgetTargets.add(this)
	return clickCoordinate().let { (x, y) -> LongClick(x, y, true, delay) }
}

@JvmOverloads
fun Widget.longClickEvent(delay: Long = 0, ignoreVisibility: Boolean = false): ExplorationAction {
//	if (!(definedAsVisible || ignoreVisibility) || !enabled || !longClickable)
//		throw RuntimeException("ERROR: tried to long-click non-actionable Widget $this")
	widgetTargets.add(this)
	return LongClickEvent(this.idHash, true, delay)
}

@JvmOverloads
fun Widget.setText(newContent: String, ignoreVisibility: Boolean = false,
                   enableValidation: Boolean = true, delay: Long =0, sendEnter: Boolean = true): ExplorationAction {
	if (enableValidation && (!(definedAsVisible || ignoreVisibility) || !enabled || !isInputField))
		throw RuntimeException("ERROR: tried to enter text on non-actionable Widget $this")
	widgetTargets.add(this)
	return TextInsert(this.idHash, newContent, true, delay, sendEnter)
}

fun Widget.dragTo(x: Int, y: Int, stepSize: Int): ExplorationAction = TODO()
//FIXME the center points may be overlayed by other elements, swiping the corners would be safer
fun Widget.swipeUp(stepSize: Int = 35): ExplorationAction = Swipe(Pair(this.visibleBounds.center.first, this.visibleBounds.topY+ this.visibleBounds.height-100) , Pair(this.visibleBounds.center.first, this.visibleBounds.topY), stepSize, true)
fun Widget.swipeDown(stepSize: Int = 35): ExplorationAction = Swipe(Pair(this.visibleBounds.center.first, this.visibleBounds.topY+100), Pair(this.visibleBounds.center.first, this.visibleBounds.topY + this.visibleBounds.height), stepSize, true)
fun Widget.swipeLeft(stepSize: Int = 35): ExplorationAction = Swipe(Pair(this.visibleBounds.leftX + this.visibleBounds.width-100, this.visibleBounds.center.second), Pair(this.visibleBounds.leftX, this.visibleBounds.center.second), stepSize, true)
fun Widget.swipeRight(stepSize: Int = 35): ExplorationAction = Swipe(Pair(this.visibleBounds.leftX+100, this.visibleBounds.center.second), Pair(this.visibleBounds.leftX + this.visibleBounds.width, this.visibleBounds.center.second), stepSize, true)

/**
 * Used by RobustDevice which does not currently create [Widget] objects.
 * This function should not be used anywhere else.
 */
fun UiElementPropertiesI.click(): ExplorationAction = (visibleAreas.firstCenter()
		?: visibleBounds.center).let{ (x,y) -> Click(x,y) }

fun Widget.clickCoordinate(): Pair<Int, Int> {
	/*if (visibleAreas.firstCenter()!=null)
		return visibleAreas.firstCenter()!!*/
	if (visibleBounds.height==0 || visibleBounds.width==0)
		return boundaries.center
	return visibleBounds.center
}


fun Widget.availableActions(delay: Long, useCoordinateClicks:Boolean): List<ExplorationAction> {
	val actionList: MutableList<ExplorationAction> = mutableListOf()

	if (this.longClickable){
		if(useCoordinateClicks) actionList.add(this.longClick(delay))
		else actionList.add(this.longClickEvent(delay))
	}
	if (this.clickable ||this.selected.isEnabled()){
		if(useCoordinateClicks) actionList.add(this.click(delay))
		else actionList.add(this.clickEvent(delay))
	}

	if (this.checked != null)
		actionList.add(this.tick(delay))

	if (this.scrollable) {
		actionList.add(this.swipeUp())
		actionList.add(this.swipeDown())
		actionList.add(this.swipeRight())
		actionList.add(this.swipeLeft())
	}

	widgetTargets.clear() // ensure the target is only once in the list and not multiple times
	widgetTargets.add(this)
	return actionList
}