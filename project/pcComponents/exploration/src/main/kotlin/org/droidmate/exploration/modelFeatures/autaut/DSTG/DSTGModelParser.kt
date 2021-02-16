package org.droidmate.exploration.modelFeatures.autaut.DSTG

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Path
import java.util.*
import kotlin.streams.toList

class DSTGModelParser (val dstgFolderPath: Path,
                       val autAutMF: AutAutMF) {
    fun loadModel(){
        val abstractStateFilePath: Path = getAbstractStateListFilePath()
        if (!abstractStateFilePath.toFile().exists())
            return
        loadAbstractStateListFile(abstractStateFilePath)
        val dstgFilePath: Path  = getDSTGFilePath()
        if (!dstgFilePath.toFile().exists())
            return
        loadDSTGFile(dstgFilePath)

    }

    private fun loadAbstractStateListFile(abstractStateFilePath: Path) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun getAbstractStateListFilePath(): Path {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun loadDSTGFile(dstgFilePath: Path) {
        val lines: List<String>
        dstgFilePath.toFile().let {file ->
            lines = BufferedReader(FileReader(file)).use {
                it.lines().skip(1).toList()
            }
        }

        lines.forEach { abstractTransition ->
            parseAbstractTransition(abstractTransition)
        }
    }

    private fun parseAbstractTransition(abstractTransition: String) {
        val data = abstractTransition.split(";(?=([^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
        val sourceStateID = data[0]
        val resStateID = data[1]
        val actionType = AbstractActionType.values().find{it.toString() == data[2]}
        val interactedAttributePathId = data[3]
        val abstractionTransitionData = data[4]
        val prevWindow = data[5]
        val handlers = data[6].split(';')

        val sourceState = AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == sourceStateID }
        val resState = AbstractStateManager.instance.ABSTRACT_STATES.find { it.abstractStateId == resStateID }
        if (sourceState == null) {
            val sourceStateFilePath: Path = getAbstractStateFilePath(sourceStateID)
            loadAbstractStateFile(sourceStateFilePath)
        }
    }

    val abstractStatesBasicInfo = HashMap<UUID, ArrayList<String>>()

    /**
     * data[0]: abstractStateId
     * data[1]: activity
     * data[2]: window
     * data[3]: rotation
     * data[4]: internetStatus
     * data[5]: isHomeScreen
     * data[6]: isRequestRuntimePermissionDialogBox
     * data[7]: isAppHasStoppedDialog
     * data[8]: isOutOfApplication
     * data[9]: isOpeningKeyboard
     * data[10]: hasOptionsMenu
     * data[11]: guiState
     */
    private fun loadAbstractStatesBasicInfo (abstractStateListFilePath: Path) {
        val lines: List<String>
        abstractStateListFilePath.toFile().let { file ->
            lines = BufferedReader(FileReader(file)).use {
                it.lines().skip(1).toList()
            }
        }
        lines.forEach {
            val data = it.split(";(?=([^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            val uuid = UUID.fromString(data[0])
            abstractStatesBasicInfo.put(uuid,ArrayList(data))
        }

    }
    private fun loadAbstractStateFile(sourceStateFilePath: Path) {
        val lines: List<String>
        sourceStateFilePath.toFile().let {file ->
            lines = BufferedReader(FileReader(file)).use {
                it.lines().skip(1).toList()
            }
        }

        /*lines.forEach { attributeValuationSet ->
            val atttributeValuationSet: AttributeValuationSet = parseAttributeValuationSet(attributeValuationSet)
        }*/
    }

    private fun parseAttributeValuationSet(attributeValuationSet: String): AttributeValuationSet {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun getAbstractStateFilePath(sourceStateID: String): Path {
        return dstgFolderPath.resolve("AbstractStates").resolve("AbstractState_$sourceStateID")
    }

    private fun getDSTGFilePath(): Path {
        return dstgFolderPath.resolve("DSTG.csv")
    }

    companion object{
        @JvmStatic
        val log: Logger by lazy { LoggerFactory.getLogger(DSTGModelParser::class.java) }
    }
}