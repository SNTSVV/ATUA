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
package org.atua.modelFeatures

import org.atua.modelFeatures.dstg.AbstractStateManager
import org.atua.modelFeatures.dstg.reducer.AbstractionFunction2
import org.atua.modelFeatures.ewtg.WindowManager
import org.droidmate.explorationModel.config.ModelConfig
import java.io.File
import java.nio.file.Files

class ATUAModelOutput {
    companion object {
        fun dumpModel(config: ModelConfig, autautMF: org.atua.modelFeatures.ATUAMF) {
            org.atua.modelFeatures.ATUAMF.log.info("Dumping WTG...")
            WindowManager.instance.dump(config,autautMF)
            dumpDSTG(config, autautMF)
            produceWindowTransition(config ,autautMF)
        }

        private fun dumpDSTG(config: ModelConfig, autautMF: org.atua.modelFeatures.ATUAMF) {
            val dstgFolder = config.baseDir.resolve("DSTG")
            Files.createDirectory(dstgFolder)
            org.atua.modelFeatures.ATUAMF.log.info("Dumping abstraction function...")
            AbstractionFunction2.INSTANCE.dump(dstgFolder)
            org.atua.modelFeatures.ATUAMF.log.info("Dumping abstract states...")
            AbstractStateManager.INSTANCE.dump(dstgFolder)
            org.atua.modelFeatures.ATUAMF.log.info("Dumping abstract states transition graph...")
            File(dstgFolder.resolve("DSTG.csv").toUri()).bufferedWriter().use { all ->
                autautMF.dstg.dump(autautMF.statementMF!!, all)
            }
        }

        private fun produceWindowTransition(config:ModelConfig, autautMF: org.atua.modelFeatures.ATUAMF) {
            val outputFile = config.baseDir.resolve("TargetInputs.txt")
            val sb = StringBuilder()
            autautMF.notFullyExercisedTargetInputs.forEach { event ->
                sb.appendln("*")
                sb.appendln(event.toString())
                event.coverage.toSortedMap().forEach { tms, c ->
                    sb.appendln("$tms;$c")
                }
            }
            Files.write(outputFile, sb.lines())
        }
    }
}