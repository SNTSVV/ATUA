/*
 * ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
 * Copyright (C) 2019 - 2021 University of Luxembourg
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.atua.modelFeatures.ewtg.window

import org.atua.modelFeatures.ewtg.WindowManager


class OptionsMenu (classType: String,
                   nodeId: String = getNodeId(),
                   runtimeCreated: Boolean,
                   isBaseModel: Boolean): Window(classType,nodeId,runtimeCreated,isBaseModel){
    override fun copyToRunningModel(): Window {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    var ownerActivity: Window? = null
    init {
        counter++
    }
    override fun getWindowType(): String {
        return "OptionsMenu"
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][OptionsMenu]-${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "OptionsMenu-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String, runtimeCreated: Boolean, isBaseModel: Boolean): OptionsMenu {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is OptionsMenu}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is OptionsMenu}
            }
            if (node != null)
                return node!! as OptionsMenu
            return OptionsMenu(nodeId = nodeId, classType = classType, runtimeCreated = runtimeCreated,
                        isBaseModel = isBaseModel)
        }
    }
}