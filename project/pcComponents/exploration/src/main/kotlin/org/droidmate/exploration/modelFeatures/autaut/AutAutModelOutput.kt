package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager
import org.droidmate.explorationModel.config.ModelConfig
import java.io.File
import java.nio.file.Files

class AutAutModelOutput {
    companion object {
        fun dumpModel(config: ModelConfig, autautMF: AutAutMF) {
            AutAutMF.log.info("Dumping WTG...")
            WindowManager.instance.dump(config,autautMF)
            dumpDSTG(config, autautMF)
            produceWindowTransition(config ,autautMF)
        }

        private fun dumpDSTG(config: ModelConfig, autautMF: AutAutMF) {
            val dstgFolder = config.baseDir.resolve("DSTG")
            Files.createDirectory(dstgFolder)
            AutAutMF.log.info("Dumping abstraction function...")
            AbstractionFunction.INSTANCE.dump(dstgFolder)
            AutAutMF.log.info("Dumping abstract states...")
            AbstractStateManager.instance.dump(dstgFolder)
            AutAutMF.log.info("Dumping abstract states transition graph...")
            File(dstgFolder.resolve("DSTG.csv").toUri()).bufferedWriter().use { all ->
                autautMF.abstractTransitionGraph.dump(autautMF.statementMF!!, all)
            }
        }

        private fun produceWindowTransition(config:ModelConfig, autautMF: AutAutMF) {
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