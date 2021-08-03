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

package org.atua.modelFeatures.dstg

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Path
import java.util.*
import kotlin.streams.toList

class DSTGModelParser (val dstgFolderPath: Path,
                       val atuaMF: org.atua.modelFeatures.ATUAMF
) {
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

        val sourceState = AbstractStateManager.INSTANCE.ABSTRACT_STATES.find { it.abstractStateId == sourceStateID }
        val resState = AbstractStateManager.INSTANCE.ABSTRACT_STATES.find { it.abstractStateId == resStateID }
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

    private fun parseAttributeValuationSet(attributeValuationSet: String): AttributeValuationMap {
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