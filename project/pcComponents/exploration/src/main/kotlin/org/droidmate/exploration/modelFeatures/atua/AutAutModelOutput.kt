package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.AbstractionFunction2
import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager
import org.droidmate.explorationModel.config.ModelConfig
import java.io.File
import java.nio.file.Files

class AutAutModelOutput {
    companion object {
        fun dumpModel(config: ModelConfig, autautMF: ATUAMF) {
            ATUAMF.log.info("Dumping WTG...")
            WindowManager.instance.dump(config,autautMF)
            dumpDSTG(config, autautMF)
            produceWindowTransition(config ,autautMF)
        }

        private fun dumpDSTG(config: ModelConfig, autautMF: ATUAMF) {
            val dstgFolder = config.baseDir.resolve("DSTG")
            Files.createDirectory(dstgFolder)
            ATUAMF.log.info("Dumping abstraction function...")
            AbstractionFunction2.INSTANCE.dump(dstgFolder)
            ATUAMF.log.info("Dumping abstract states...")
            AbstractStateManager.INSTANCE.dump(dstgFolder)
            ATUAMF.log.info("Dumping abstract states transition graph...")
            File(dstgFolder.resolve("DSTG.csv").toUri()).bufferedWriter().use { all ->
                autautMF.dstg.dump(autautMF.statementMF!!, all)
            }
        }

        private fun produceWindowTransition(config:ModelConfig, autautMF: ATUAMF) {
            val outputFile = config.baseDir.resolve("TargetInputs.txt")
            val sb = StringBuilder()
            autautMF.allTargetInputs.forEach { event ->
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