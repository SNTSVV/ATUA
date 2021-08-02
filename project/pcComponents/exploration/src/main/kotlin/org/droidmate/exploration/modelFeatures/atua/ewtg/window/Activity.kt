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

class Activity(classType: String,
               nodeId: String = getNodeId(),
               runtimeCreated: Boolean,
               baseModel: Boolean): Window(classType,nodeId,runtimeCreated, baseModel){
    override fun copyToRunningModel(): Window {
        val newActivity = getOrCreateNode(
                nodeId = getNodeId(),
                classType = this.classType,
                runtimeCreated = this.isRuntimeCreated,
                isBaseMode = false
        )
        return newActivity
    }

    override fun getWindowType(): String {
        return "Activity"
    }

    init {
        counter++
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][Activity]${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "Activity-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String, runtimeCreated: Boolean, isBaseMode:Boolean): Activity {
            val node = if (isBaseMode) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is Activity}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is Activity}
            }
            if (node != null)
                return node!! as Activity
            else
                return Activity(nodeId = nodeId, classType = classType, runtimeCreated = runtimeCreated,baseModel = isBaseMode)
        }
    }
}