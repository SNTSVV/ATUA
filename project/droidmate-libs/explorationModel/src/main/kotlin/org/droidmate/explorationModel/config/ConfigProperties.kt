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

package org.droidmate.explorationModel.config

import com.natpryce.konfig.*

object ConfigProperties {
	object Output : PropertyGroup() {
		val outputDir by uriType
		val debugMode by booleanType
	}

	object ModelProperties : PropertyGroup() {
		object path : PropertyGroup() {
			val defaultBaseDir by uriType
			val statesSubDir by uriType
			val imagesSubDir by uriType
			val cleanDirs by booleanType
			val cleanImgs by booleanType
			val FeatureDir by uriType
		}

		object dump : PropertyGroup() {
			val sep by stringType
			val onEachAction by booleanType

			val stateFileExtension by stringType

			val traceFileExtension by stringType
			val traceFilePrefix by stringType
		}
	}
}