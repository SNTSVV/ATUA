package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.AbstractionFunction2
import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager
import org.droidmate.explorationModel.config.ModelConfig
import java.io.File
import java.nio.file.Files

class AtuaModelOutput {
    companion object {
        fun dumpModel(config: ModelConfig, autautMF: ATUAMF) {
            ATUAMF.log.info("Dumping EWTG...")
            WindowManager.instance.dump(config,autautMF)
            ATUAMF.log.info("Dumping EWTG finished")
            dumpDSTG(config, autautMF)
            produceWindowTransition(config ,autautMF)
        }

        private fun dumpDSTG(config: ModelConfig, autautMF: ATUAMF) {
            val dstgFolder = config.baseDir.resolve("DSTG")
            Files.createDirectory(dstgFolder)
            ATUAMF.log.info("Dumping abstraction function...")
            AbstractionFunction2.INSTANCE.dump(dstgFolder)
            ATUAMF.log.info("Dumping abstraction function finished.")
            ATUAMF.log.info("Dumping abstract states...")
            AbstractStateManager.INSTANCE.dump(dstgFolder)
            ATUAMF.log.info("Dumping abstract states finished.")
            ATUAMF.log.info("Dumping abstract states transition graph...")
            File(dstgFolder.resolve("DSTG.csv").toUri()).bufferedWriter().use { all ->
                autautMF.dstg.dump(autautMF.statementMF!!, all)
            }
            ATUAMF.log.info("Dumping abstract states transition graph finished.")
        }

        private fun produceWindowTransition(config:ModelConfig, autautMF: ATUAMF) {
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