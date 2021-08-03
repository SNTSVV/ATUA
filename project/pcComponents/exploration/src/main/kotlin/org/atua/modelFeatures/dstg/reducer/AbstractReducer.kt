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

import org.droidmate.deviceInterface.exploration.Rectangle
import org.atua.modelFeatures.dstg.AttributePath
import org.atua.modelFeatures.dstg.reducer.localReducer.AbstractLocalReducer
import org.atua.modelFeatures.ewtg.window.Window
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget


abstract class AbstractReducer(
        val localReducer: AbstractLocalReducer
) {

    abstract fun reduce(guiWidget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle:Rectangle, window: Window, rotation: org.atua.modelFeatures.Rotation, atuaMF: org.atua.modelFeatures.ATUAMF
                        , tempWidgetReduceMap: HashMap<Widget,AttributePath>
                        , tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath




}