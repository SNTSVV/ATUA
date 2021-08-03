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

package org.atua.modelFeatures.dstg.reducer

import org.atua.modelFeatures.ewtg.EWTGWidget
import java.util.*

class DecisionNode2 (
        val ewtgWidgets: ArrayList<EWTGWidget> = ArrayList(),
        val reducer: AbstractReducer,
        var nextNode: DecisionNode2?=null
) {

    fun needPassToNextLevel(ewtgWidget: EWTGWidget): Boolean {
        return ewtgWidgets.contains(ewtgWidget)
    }
}