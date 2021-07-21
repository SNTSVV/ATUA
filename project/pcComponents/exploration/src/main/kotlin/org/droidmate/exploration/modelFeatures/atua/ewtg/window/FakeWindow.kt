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
package org.droidmate.exploration.modelFeatures.atua.ewtg.window

import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager

class FakeWindow(nodeId: String= getNodeId(),isBaseModel: Boolean): Window("",nodeId,true,isBaseModel){
    override fun copyToRunningModel(): Window {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getWindowType(): String {
        return "FakeWindow"
    }

    init {
        counter++
    }

    override fun toString(): String {
        return "[Window][FakeWindow]${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "FakeWindow-${counter+1}"
        fun getOrCreateNode(nodeId: String= getNodeId(),isBaseModel: Boolean): FakeWindow {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is FakeWindow}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is FakeWindow}
            }
            if (node != null)
                return node!! as FakeWindow
            else
                return FakeWindow(nodeId = nodeId,isBaseModel = isBaseModel)
        }
    }
}