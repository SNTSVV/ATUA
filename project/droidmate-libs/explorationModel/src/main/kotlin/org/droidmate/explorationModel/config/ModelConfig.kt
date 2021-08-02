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

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.dump.stateFileExtension
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.dump.traceFileExtension
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.dump.traceFilePrefix
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.cleanDirs
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.cleanImgs
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.defaultBaseDir
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.imagesSubDir
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.statesSubDir
import org.droidmate.explorationModel.config.ConfigProperties.Output.outputDir
import org.droidmate.explorationModel.configFromResource
import org.droidmate.explorationModel.debugOutput
import org.droidmate.explorationModel.measurePerformance
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ModelConfig private constructor(path: Path,
                                      val appName: String,
                                      private val config: Configuration,
                                      @Suppress("MemberVisibilityCanBePrivate") // used by some model features to restore/load their state
                                      val isLoadC: Boolean = false) : Configuration by config {
	/** @path path-string locationg the base directory where all model data is supposed to be dumped */

	constructor(path: Path, appName: String, isLoadC: Boolean = false): this(path.toAbsolutePath(), appName, resourceConfig, isLoadC)

	val baseDir: Path = path.resolve(appName)  // directory path where the model file(s) should be stored
	val stateDst: Path = baseDir.resolve(config[statesSubDir].path)       // each state gets an own file named according to UUID in this directory
	val imgDst: Path = baseDir.resolve(config[imagesSubDir].path)  // the images for the app widgets are stored in this directory (for report/debugging purpose only)

	init {  // initialize directories (clear them if cleanDirs is enabled)
		if (!isLoadC){
			if (config[cleanImgs]) imgDst.toFile().deleteRecursively()
			if (config[cleanDirs]) (baseDir).toFile().deleteRecursively()
			Files.createDirectories((baseDir))
			Files.createDirectories((stateDst))
			Files.createDirectories((imgDst))
		}
		// disable debug output if debugMode is disabled
		debugOutput = config[ConfigProperties.Output.debugMode]
		measurePerformance = config[ConfigProperties.Output.debugMode]
	}

	private val idPath: (Path, String, String, String) -> String = { baseDir, id, postfix, fileExtension -> baseDir.toString() + "${File.separator}$id$postfix$fileExtension" }

	val stateFile: (ConcreteId, Boolean) -> String = { id, isHomeScreen 	->
		statePath(id, postfix = (if(isHomeScreen) "_HS" else "") ) }
	fun statePath(id: ConcreteId, postfix: String = "", fileExtension: String = config[stateFileExtension]): String {
		return idPath(stateDst, id.toString(), postfix, fileExtension)
	}

	val traceFile = { traceId: String -> "$baseDir${File.separator}${config[traceFilePrefix]}$traceId${config[traceFileExtension]}" }

	companion object {

		private val resourceConfig: ConfigurationProperties by lazy {
			ConfigurationProperties.configFromResource("/defaultModelConfig.properties")
		}

		@JvmOverloads operator fun invoke(appName: String, isLoadC: Boolean = false, cfg: Configuration? = null): ModelConfig {
			val (config, path) = if (cfg != null)
				Pair(cfg overriding resourceConfig, Paths.get(cfg[outputDir].toString()).resolve("model"))
			else
				Pair(resourceConfig, Paths.get(resourceConfig[defaultBaseDir].toString()))

			return ModelConfig(path, appName, config, isLoadC)
		}

	} /** end COMPANION **/
}
