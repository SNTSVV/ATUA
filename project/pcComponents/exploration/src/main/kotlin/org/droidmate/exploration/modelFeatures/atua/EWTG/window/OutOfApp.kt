// ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
// Copyright (C) 2019 - 2021 University of Luxembourg
//
// This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.droidmate.exploration.modelFeatures.atua.EWTG.window

import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager

class OutOfApp(nodeId: String= getNodeId(), activity: String,
               isBaseModel: Boolean): Window(activity,nodeId,true,isBaseModel){
    override fun copyToRunningModel(): Window {
        val newWindow = OutOfApp.getOrCreateNode(
                nodeId = getNodeId(),
                activity = this.classType,
                isBaseModel = false
        )
        return newWindow
    }

    override fun getWindowType(): String {
        return "OutOfApp"
    }

    init {
        this.classType = activity
        counter++
    }

    override fun toString(): String {
        return "[Window][OutOfApp]${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "OutOfApp-${counter+1}"
        fun getOrCreateNode(nodeId:String, activity: String, isBaseModel: Boolean): OutOfApp {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is OutOfApp}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is OutOfApp}
            }
            if (node != null)
                return node!! as OutOfApp
            else
                return OutOfApp(nodeId = nodeId, activity = activity,isBaseModel = isBaseModel)
        }
    }
}