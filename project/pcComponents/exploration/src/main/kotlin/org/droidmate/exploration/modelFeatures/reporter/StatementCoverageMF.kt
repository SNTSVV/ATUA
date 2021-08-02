// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.exploration.modelFeatures.reporter

import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.getValue
import com.natpryce.konfig.stringType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.droidmate.coverage.INSTRUMENTATION_FILE_SUFFIX
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.misc.unzip
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.config.ConfigProperties
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.retention.StringCreator
import org.droidmate.legacy.Resource
import org.droidmate.legacy.getExtension
import org.droidmate.misc.deleteDir
import org.json.JSONObject
import org.json.JSONString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.coroutines.CoroutineContext

/**
 * Model feature to monitor the statement coverage by processing an optional instrumentation file and fetching
 * the statement data from the device.
 */
class StatementCoverageMF(private val statementsLogOutputDir: Path,
                          private val readStatements: suspend ()-> List<List<String>>,
                          private val appName: String,
                          private val resourceDir: Path,
                          private val statementFileName: String = "coverage.txt",
                          private val methodFileName: String = "methodCoverage.txt",
                          private val modifiedMethodFileName: String = "updatedMethodCoverage.txt") : ModelFeature() {
    override val coroutineContext: CoroutineContext = CoroutineName("StatementCoverageMF") + Job()

     val executedMethodsMap: ConcurrentHashMap<String, Date> = ConcurrentHashMap() //methodid -> first executed
    val executedStatementsMap: ConcurrentHashMap<String, Date> = ConcurrentHashMap()


    val statementInstrumentationMap= HashMap<String, String>() //statementid -> statement
     val statementMethodInstrumentationMap = HashMap<String, String>() //statementid -> methodid
     val methodInstrumentationMap= HashMap<String, String>() //method id -> method

    val modifiedMethodsList = HashSet<String>()
    val fullyCoveredMethods = HashSet<String>()
    val recentExecutedStatements: ArrayList<String> = ArrayList()
    val recentExecutedMethods: ArrayList<String> = ArrayList()


    val actionCoverageTracking = HashMap<Int,Set<String>>()
    val actionIncreasingCoverageTracking = HashMap<Int,Set<String>>()

    val actionUpdatedCoverageTracking = HashMap<Int,Set<String>>()
    val actionIncreasingUpdatedCoverageTracking = HashMap<Int,Set<String>>()

    var prevUpdateCoverage: Int = 0
    var prevCoverage: Int = 0
    var statementRead:Boolean = false
    //private val instrumentationMap = getInstrumentation(appName)
    val mutex = Mutex()


    private var trace: ExplorationTrace<*,*>? = null

    init {
        assert(statementsLogOutputDir.deleteDir()) { "Could not delete the directory $statementsLogOutputDir" }
        Files.createDirectories(statementsLogOutputDir)
        getInstrumentation(appName)
    }
    val modMethodInstrumentationMap by lazy {
        methodInstrumentationMap.filter { modifiedMethodsList.contains(it.value) }
    } //method id -> method
    val modMethodStatementInstrumentationMap by lazy {
        statementInstrumentationMap.filter { modifiedMethodsList.contains(methodInstrumentationMap.get(statementMethodInstrumentationMap[it.key]!!)!!)}
    } //method id -> method
    val executedModifiedMethodsMap:  Map<String,Date>
        get()= executedMethodsMap.filter { modMethodInstrumentationMap.containsKey(it.key) }
    val executedModifiedMethodStatementsMap: Map<String,Date>
        get() = executedStatementsMap.filter { modMethodStatementInstrumentationMap.containsKey(it.key) }


    override fun onAppExplorationStarted(context: ExplorationContext<*, *, *>) {
        this.trace = context.explorationTrace

    }

    override suspend fun onContextUpdate(context: ExplorationContext<*, *, *>) {
        statementRead = false
        val newExecutedStatements = HashSet<String>()
        val newUpdatedExecutedStatements = HashSet<String>()
        val executedUpdatedStatements = HashSet<String>()
        prevUpdateCoverage = executedModifiedMethodStatementsMap.size
        prevCoverage = executedStatementsMap.size
        mutex.withLock {
            // Fetch the statement data from the device
            recentExecutedMethods.clear()
            recentExecutedStatements.clear()
            val readStatements = readStatements()
            readStatements
                    .forEach { statement ->
                        //goto [?= staticinvoke <org.droidmate.runtime.Runtime: void statementPoint(java.lang.String,java.lang.String,int)>(\"$z0 = interfaceinvoke $r5.<java.util.Iterator: boolean hasNext()>() methodId=03e25164-164b-4836-9ea3-6ea4cb01d87d uuid=20986538-c7d0-4b28-8e3b-959a1affcbf9\", \"/data/local/tmp/coverage_port.tmp\", 0)] methodId=03e25164-164b-4836-9ea3-6ea4cb01d87d uuid=1477a349-7f31-45c7-8597-976aec10e111"
                        //val parts2 = parts[0].split(" methodId=".toRegex())
                        val timestamp = statement[0]
                        val tms = dateFormat.parse(timestamp)
                        //NGO
                        val uuidIndex = statement[1].toString().lastIndexOf(" uuid=")
                        //val parts = statement[1].toString().split(" uuid=".toRegex()).toTypedArray()
                        val statementId = statement[1].toString().substring(uuidIndex+" uuid=".length)
                        val fullStatement = statement[1].toString().substring(0,uuidIndex)
                        // val l = "9946a686-9ef6-494f-b893-ac8b78efb667"
                        val methodIdIndex = fullStatement.lastIndexOf(" methodId=")
                        //val methodId = parts2.last()
                        val methodId = fullStatement.substring(methodIdIndex+" methodId=".length)
                        // Add the statement if it wasn't executed before
                        if (!recentExecutedMethods.contains(methodId))
                            recentExecutedMethods.add(methodId)
                        val methodFound = executedMethodsMap.containsKey(methodId)
                        if (!methodFound)
                        {
                            executedMethodsMap[methodId] = tms
                        }
                        val isUpdatedMethod = isModifiedMethod(methodId)
                        if (!recentExecutedStatements.contains(statementId)) {
                            recentExecutedStatements.add(statementId)
                            if (isUpdatedMethod) {
                                executedUpdatedStatements.add(statementId)
                            }
                        }
                        var found = executedStatementsMap.containsKey(statementId)
                        if (!found /*&& instrumentationMap.containsKey(id)*/) {
                            executedStatementsMap[statementId] = tms
                            newExecutedStatements.add(statementId)
                            if (isUpdatedMethod) {
                                newUpdatedExecutedStatements.add(statementId)
                            }
                        }
                    }

           /* newModifiedMethod.forEach {
                val methodName = getMethodName(it)
                log.info("New modified method: $methodName")
            }*/
            recentExecutedMethods.forEach { m->
                if (!fullyCoveredMethods.contains(m)) {
                    val methodStatements = statementMethodInstrumentationMap.filter { it.value == m }.keys
                    val executedStatements = executedStatementsMap.keys().toList().intersect(methodStatements)
                    if (methodStatements.size == executedStatements.size) {
                        fullyCoveredMethods.add(m)
                    }
                }

            }
            log.info("Current statement coverage: ${"%.2f".format(getCurrentCoverage())}. Encountered statements: ${executedStatementsMap.size}/${statementInstrumentationMap.size}")
            log.info("Current method coverage: ${"%.2f".format(getCurrentMethodCoverage())}. Encountered methods: ${executedMethodsMap.size}/${methodInstrumentationMap.size}")
            log.info("Current modified method coverage: ${"%.2f".format(getCurrentModifiedMethodCoverage())}. Encountered modified methods: ${executedModifiedMethodsMap.size}/${modMethodInstrumentationMap.size}")
            log.info("Current modified method's statement coverage: ${"%.2f".format(getCurrentModifiedMethodStatementCoverage())}. Encountered modified methods: ${executedModifiedMethodStatementsMap.size}/${modMethodStatementInstrumentationMap.size}")

            // Write the received content into a file
            if (readStatements.isNotEmpty()) {
                val lastId = trace?.last()?.actionId ?: 0
                val file = getLogFilename(lastId)
                withContext(Dispatchers.IO){ Files.write(file, readStatements.map { "${it[1]};${it[0]}" }) }
            }

        }
        statementRead = true

        val lastAction = context.getLastAction()
        actionCoverageTracking.put(lastAction.actionId,recentExecutedStatements.toSet())
        actionIncreasingCoverageTracking.put(lastAction.actionId,newExecutedStatements)
        actionUpdatedCoverageTracking.put(lastAction.actionId,executedUpdatedStatements)
        actionIncreasingUpdatedCoverageTracking.put(lastAction.actionId,newUpdatedExecutedStatements)
    }
    /**
     * Fetch the statement data form the device. Afterwards, it parses the data and updates [executedStatementsMap].
     */
    override suspend fun onNewAction(traceId: UUID, interactions: List<Interaction<*>>, prevState: State<*>, newState: State<*>) {
        // This code must be synchronized, otherwise it may read the
        // same statements multiple times

    }

    /**
     * Returns a map which is used for the coverage calculation.
     */
    private fun getInstrumentation(apkName: String){
       if (!Files.exists(resourceDir)) {
            log.warn("Provided Dir does not exist: $resourceDir." +
                "DroidMate will monitor coverage, but won't be able to calculate the coverage.")
        } else {
            val instrumentationFile = getInstrumentationFile(apkName, resourceDir)
            if (instrumentationFile != null)
                readInstrumentationFile(instrumentationFile)
            else {
                log.warn("Provided directory ($resourceDir) does not contain " +
                    "the corresponding instrumentation file. DroidMate will monitor coverage, but won't be able" +
                    "to calculate the coverage.")
            }
            val appModel = getAppModelFile(apkName,resourceDir)
            if (appModel != null) {
                readModifiedMethodList(appModel)
            }
        }
    }

    private fun readModifiedMethodList(appModel: Path) {
        val jsonData = String(Files.readAllBytes(appModel))
        val jObj = JSONObject(jsonData)
        val modifiedMethodsJson = jObj.getJSONArray("modifiedMethods")
        if (modifiedMethodsJson!=null) {
            modifiedMethodsJson.forEach {
                modifiedMethodsList.add(it.toString())
            }
        }
    }

    private fun getAppModelFile(apkName: String, resourceDir: Path): Path? {
        return Files.list(resourceDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-AppModel.json")
                }
                .findFirst()
                .orElse(null)
    }

    /**
     * Returns the the given instrumentation file corresponding to the passed [apkName].
     * Returns null, if the instrumentation file is not present.
     *
     * Example:
     * apkName = a2dp.Vol_137.apk
     * return: instrumentation-a2dp.Vol.json
     */
    private fun getInstrumentationFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
            .filter {
                it.fileName.toString().contains(apkName)
                    && it.fileName.toString().endsWith(".apk$INSTRUMENTATION_FILE_SUFFIX")
            }
            .findFirst()
            .orElse(null)
    }

    @Throws(IOException::class)
    private fun readInstrumentationFile(instrumentationFile: Path?){
//        val jsonData = String(Files.readAllBytes(instrumentationFile))
//        val jObj = JSONObject(jsonData)
//
//        val jMap = jObj.getJSONObject(INSTRUMENTATION_FILE_METHODS_PROP)
//        val statements = mutableMapOf<Long, String>()
//
//        jMap.keys()
//            .asSequence()
//            .forEach { key ->
//                val keyId = key.toLong()
//                val value = jMap[key]
//                statements[keyId] = value.toString()
//            }
//
//        return statements
        val jsonData = String(Files.readAllBytes(instrumentationFile))
        val jObj = JSONObject(jsonData)

        val l = "9946a686-9ef6-494f-b893-ac8b78efb667".length
        var jMap = jObj.getJSONObject("allMethods")
        jMap.keys()
                .asSequence()
                .forEach { key ->
                    val keyId = key.toLong()
                    val method = jMap[key]
                    //check for modified method
                    //example format: "modified=true <com.teleca.jamendo.window.SearchActivity$SearchingDialog: void playlistSearch()> uuid=3b389bcf-70e4-400b-afce-c0e67d682333"
                    val modified = method.toString().contains("modified=true")

                    if (modified) {
                        //get uuid
                        val index = method.toString().indexOf("modified=true")
                        val methodInfo = method.toString().substring(index + "modified=true ".length)
                        val parts = methodInfo.split(" uuid=")
                        val uuid = parts[1]
                        assert(uuid.length == l, { "Invalid UUID $uuid $method" })
                        methodInstrumentationMap[uuid] = parts[0]
                    } else {
                        val parts = method.toString().split(" uuid=")
                        val uuid = parts[1]
                        assert(uuid.length == l, { "Invalid UUID $uuid $method" })
                        methodInstrumentationMap[uuid] = parts[0]
                    }
                }
        log.info("methods : ${methodInstrumentationMap.size}")

        //NGO change
        //val jMap = jObj.getJSONObject(INSTRUMENTATION_FILE_METHODS_PROP)
        jMap = jObj.getJSONObject("allStatements")

        jMap.keys()
                .asSequence()
                .forEach { key ->
                    val keyId = key.toLong()
                    val statement = jMap[key]
                    val parts = statement.toString().split(" uuid=".toRegex()).toTypedArray()
                    val uuid = parts.last()
                    assert(uuid.length == l, { "Invalid UUID $uuid $statement" })
                    statementInstrumentationMap[uuid] = statement.toString()
                    val uuidIndex = statement.toString().lastIndexOf(" uuid=")
                    //val parts = statement[1].toString().split(" uuid=".toRegex()).toTypedArray()
                    val fullStatement = statement.toString().substring(0,uuidIndex)
                    val parts2 = fullStatement.split(" methodId=".toRegex())
                    if (parts2.size > 1)
                    {
                        // val l = "9946a686-9ef6-494f-b893-ac8b78efb667"
                        val methodId = parts2.last()
                        // Add the statement if it wasn't executed before
                        statementMethodInstrumentationMap[uuid] = methodId
                    }
                }
        log.info("statement : ${statementInstrumentationMap.size}")
    }

    /**
     * Returns the current measured coverage.
     * Note: Returns 0 if [instrumentationMap] is empty.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun getCurrentCoverage(): Double {
        return if (statementInstrumentationMap.isEmpty()) {
            0.0
        } else {
            assert(executedStatementsMap.size <= statementInstrumentationMap.size) {
                "Reached statements exceed total numbers of statements in the app"
            }
            executedStatementsMap.size / statementInstrumentationMap.size.toDouble()
        }
    }

    fun getCurrentMethodCoverage(): Double {
        return if (methodInstrumentationMap.isEmpty()) {
            0.0
        } else {
            assert(executedMethodsMap.size <= methodInstrumentationMap.size) {
                "Reached statements exceed total numbers of statements in the app"
            }
            executedMethodsMap.size / methodInstrumentationMap.size.toDouble()
        }
    }

    fun getCurrentModifiedMethodCoverage(): Double {
        return if (modMethodInstrumentationMap.isEmpty()) {
            0.0
        } else {
            assert(executedModifiedMethodsMap.size <= modMethodInstrumentationMap.size) {
                "Reached statements exceed total numbers of statements in the app"
            }

            executedModifiedMethodsMap.size / modMethodInstrumentationMap.size.toDouble()
        }
    }

    fun getCurrentModifiedMethodStatementCoverage(): Double{
        return if (modMethodInstrumentationMap.isEmpty()) {
            0.0
        } else {
            executedModifiedMethodStatementsMap.size / modMethodStatementInstrumentationMap.size.toDouble()
        }
    }
    fun isModifiedMethod(modifiedMethodId: String): Boolean{
        if (modMethodInstrumentationMap.containsKey(modifiedMethodId))
            return true
        return false
    }

    fun isModifiedMethodStatement(statementId: String): Boolean {
        val methodId = statementMethodInstrumentationMap[statementId]
        if (methodId == null)
            return false
        if (isModifiedMethod(methodId))
            return true
        return false
    }
    fun getMethodName(methodId: String): String{
        return methodInstrumentationMap[methodId]?:""
    }

    fun getMethodId(methodName: String): String{
        return methodInstrumentationMap.filterValues { it.equals(methodName) }.entries.firstOrNull()?.key?:""
    }
    //Return List of statment id
    fun getMethodStatements(methodId: String): List<String>{
        return statementMethodInstrumentationMap.filter { it.value.equals(methodId) }.map { it.key }
    }

    /**
     * Return list of modified methods'id
     */
    fun getAllModifiedMethodsId(): List<String>{
        return modMethodInstrumentationMap.map { it.key }
    }

    /**
     * Return list of executed statements
     */

    fun getAllExecutedStatements(): List<String>{
        return executedStatementsMap.map { it.key }
    }
    /**
     * Returns the logfile name depending on the [counter] in which the content is written into.
     */
    private fun getLogFilename(counter: Int): Path {
        return statementsLogOutputDir.resolve("$appName-statements-%04d".format(counter))
    }

    override suspend fun onAppExplorationFinished(context: ExplorationContext<*, *, *>) {
        this.join()
        regressionTestingMF = context.findWatcher { it is ATUAMF } as ATUAMF?
        log.info("Producing coverage reports...")
        produceStatementCoverageOutput(context)
        produceMethodCoverageOutput(context)
        produceModMethodCoverageOutput(context)
        log.info("Producing coverage reports finished")
        log.info("Producing action coverage report...")
        dumpActionTraceWithCoverage(context)
        produceActionCoverageHTMLReport(context)
        log.info("Producing action coverage report finished.")
    }

    fun produceStatementCoverageOutput(context: ExplorationContext<*,*,*>){
        val sb = StringBuilder()
        sb.appendln("Total statements: ${statementInstrumentationMap.size}")
        sb.appendln("Total covered statements: ${executedStatementsMap.size}")
        sb.appendln(statement_header)
        if (executedStatementsMap.isNotEmpty()) {
            val sortedStatements = executedStatementsMap.entries
                    .sortedBy { it.value }
            val initialDate = sortedStatements.first().value

            sortedStatements
                    .forEach {
                        val method = statementMethodInstrumentationMap[it.key]
                        sb.appendln("${it.key};$method;${Duration.between(initialDate.toInstant(), it.value.toInstant()).toMillis() / 1000}")
                    }
        }
        statementInstrumentationMap.filterNot { executedStatementsMap.containsKey(it.key) }.forEach {
            val method = statementMethodInstrumentationMap[it.key]
            sb.appendln("${it.key};$method")
        }
        val outputFile = context.model.config.baseDir.resolve(statementFileName)
        log.info("Prepare writing coverage file: " +
                "\n- File name: ${outputFile.fileName}" +
                "\n- Absolute path: ${outputFile.toAbsolutePath().fileName}")

        Files.write(outputFile, sb.lines())
        log.info("Finished writing coverage in ${outputFile.fileName}")
    }

    fun produceMethodCoverageOutput(context: ExplorationContext<*,*,*>){
        val sb = StringBuilder()
        sb.appendln("Total methods: ${methodInstrumentationMap.size}")
        sb.appendln("Total covered methods: ${executedMethodsMap.size}")
        sb.appendln(method_header)
        if (executedMethodsMap.isNotEmpty()) {
            val sortedMethods = executedMethodsMap.entries
                    .sortedBy { it.value }
            val initialDate = sortedMethods.first().value

            sortedMethods
                    .forEach {
                        val methodName = methodInstrumentationMap[it.key]
                        sb.appendln("${it.key};$methodName;${Duration.between(initialDate.toInstant(), it.value.toInstant()).toMillis() / 1000}")
                    }
        }

        val outputFile = context.model.config.baseDir.resolve(methodFileName)
        log.info("Prepare writing coverage file: " +
                "\n- File name: ${outputFile.fileName}" +
                "\n- Absolute path: ${outputFile.toAbsolutePath().fileName}")

        Files.write(outputFile, sb.lines())
        log.info("Finished writing coverage in ${outputFile.fileName}")
    }
    private var regressionTestingMF: ATUAMF?=null
    fun produceModMethodCoverageOutput(context: ExplorationContext<*,*,*>){
        val sb = StringBuilder()
        sb.appendln(statement_header)
        sb.appendln("Statements;${statementInstrumentationMap.size}")
        sb.appendln("Methods;${methodInstrumentationMap.size}")
        sb.appendln("ModifiedMethods;${modMethodInstrumentationMap.size}")
        sb.appendln("ModifiedMethodsStatements;${
                statementMethodInstrumentationMap.filter { modMethodInstrumentationMap.contains(it.value) }.size
        } ")
        sb.appendln("CoveredStatements;${executedStatementsMap.size}")
        sb.appendln("CoveredMethods;${executedMethodsMap.size}")
        sb.appendln("CoveredModifiedMethods;${executedModifiedMethodsMap.size}")
        val executedModifiedMethodStatement = executedStatementsMap.filter { modMethodInstrumentationMap.contains(statementMethodInstrumentationMap[it.key]) }
        sb.appendln("CoveredModifiedMethodsStatements;${executedModifiedMethodStatement.size}")
        sb.appendln("ListCoveredModifiedMethods")
        if (executedModifiedMethodsMap.isNotEmpty()) {
            val sortedMethods = executedModifiedMethodsMap.entries
                    .sortedBy { it.value }
            val initialDate = sortedMethods.first().value

            sortedMethods
                    .forEach {
                        sb.appendln("${it.key};${modMethodInstrumentationMap[it.key]};${Duration.between(initialDate.toInstant(), it.value.toInstant()).toMillis() / 1000}")
                    }

        }
        sb.appendln("ListUnCoveredModifiedMethods")
        modMethodInstrumentationMap.filter {!executedModifiedMethodsMap.containsKey(it.key) }.forEach{
            sb.appendln("${it.key};${it.value}")
        }
        val outputFile = context.model.config.baseDir.resolve(modifiedMethodFileName)
        log.info("Prepare writing coverage file: " +
                "\n- File name: ${outputFile.fileName}" +
                "\n- Absolute path: ${outputFile.toAbsolutePath().fileName}")

        Files.write(outputFile, sb.lines())
        log.info("Finished writing coverage in ${outputFile.fileName}")
    }

    fun dumpActionTraceWithCoverage(context: ExplorationContext<*, *, *>) {
        runBlocking {
            val outputFile = File(context.model.config.baseDir.resolve("actionCoverage.csv").toAbsolutePath().toString()).bufferedWriter()
            outputFile.write("ActionId;ActionType;SourceState;ResultState;Data;ExecutedStatements;NewExecutedStatements;ExecutedUpdatedStatements;NewUpdatedExecutedStatements")
            outputFile.newLine()
            val actions = context.explorationTrace.P_getActions()
            actions.forEach { action ->
                outputFile.write("${action.actionId.toString()};${action.actionType};${action.prevState};${action.resState};\"${action.data}\"")
                outputFile.write(";${actionCoverageTracking.get(action.actionId)?.size};${actionIncreasingCoverageTracking.get(action.actionId)?.size};${actionUpdatedCoverageTracking.get(action.actionId)?.size};${actionIncreasingUpdatedCoverageTracking.get(action.actionId)?.size};")
                outputFile.newLine()
            }
            outputFile.close()
            (context.explorationTrace as ExplorationTrace<State<*>, Widget>).dumpWithCoverage<State<*>, Widget>(config = context.model.config, actionTraceCoverage = actionCoverageTracking, actionIncreaseCoverage = actionIncreasingCoverageTracking,actionUpdatedStmtCoverage = actionUpdatedCoverageTracking,actionIncreasingUpdatedStmtCoverage = actionIncreasingUpdatedCoverageTracking)

        }
/*        launch(CoroutineName("trace-coverage-dump")) {

            (context.explorationTrace as ExplorationTrace<State<*>, Widget>).dumpWithCoverage<State<*>, Widget>(config = context.model.config, actionTraceCoverage = actionCoverageTracking, actionIncreaseCoverage = actionIncreaseCoverageTracking,actionUpdatedStmtCoverage = actionUpdatedCoverageTracking)
        }*/

    }

    fun produceActionCoverageHTMLReport(context: ExplorationContext<*, *, *>) {
        val actionCoverageHTMLFolderPath = context.model.config.baseDir.resolve("actionCoverageHTMLReport")
        // Files.createDirectory(actionCoverageHTMLFolderPath)
        // Copy the folder with the required resources
        Files.deleteIfExists(actionCoverageHTMLFolderPath)
        val zippedVisDir = Resource("actionCoverageHTML.zip").extractTo(context.model.config.baseDir)
        try {
            zippedVisDir.unzip(actionCoverageHTMLFolderPath)
            Files.delete(zippedVisDir)
        } catch (e: FileSystemException) { // FIXME temporary work-around for windows file still used issue
            log.warn("resource zip could not be unzipped/removed ${e.localizedMessage}")
        }
        val actions = ArrayList<Interaction<*>>()
        runBlocking {
            val temp = context.explorationTrace.P_getActions()
            temp.forEach { action ->

                    actions.add(action)
            }
        }
//        generate data.json
        val dataJson = JSONObject()
        var prevActionId: Int = 0
        actions.forEach {
            if (actionIncreasingUpdatedCoverageTracking.containsKey(it.actionId)
                && actionIncreasingUpdatedCoverageTracking.get(it.actionId)!!.size>0) {
                val actionJSONObject = JSONObject()
                dataJson.put(it.actionId.toString(), actionJSONObject)
                actionJSONObject.put("from", it.prevState.uid)
                actionJSONObject.put("to", it.resState.uid)
                actionJSONObject.put("actionType", it.actionType)
                actionJSONObject.put("actionId", it.actionId)
                actionJSONObject.put("data", it.data)
                if (prevActionId != 0) {
                    val prevActionScreenshotPath = context.model.config.baseDir
                        .resolve("images")
                        .resolve("${prevActionId}.jpg")
                    val newPrevActionScreenshotPath = actionCoverageHTMLFolderPath.resolve("screenshot").resolve("${prevActionId}.jpg")
                    if (!Files.exists(newPrevActionScreenshotPath)) {
                        if (Files.exists(prevActionScreenshotPath)) {
                            Files.copy(prevActionScreenshotPath,newPrevActionScreenshotPath)
                        }
                    }
                    actionJSONObject.put(
                        "prevImage",
                        actionCoverageHTMLFolderPath.relativize(newPrevActionScreenshotPath).toString()
                    )
                } else {
                    actionJSONObject.put(
                        "prevImage",
                        ""
                    )
                }
                val screenshotFile = context.model.config.baseDir
                    .resolve("images")
                    .resolve("${it.actionId}.jpg")
                if (!Files.exists(screenshotFile)) {
                    actionJSONObject.put("image", actionJSONObject.get("prevImage"))
                } else {
                    val newScreenshotFile =
                        actionCoverageHTMLFolderPath.resolve("screenshot").resolve("${it.actionId}.jpg")
                    Files.copy(screenshotFile, newScreenshotFile)
                    actionJSONObject.put("image", actionCoverageHTMLFolderPath.relativize(newScreenshotFile).toString())
                }
                actionJSONObject.put("executedStatements", actionCoverageTracking.get(it.actionId)?.size ?: 0)
                actionJSONObject.put(
                    "newExecutedStatements",
                    actionIncreasingCoverageTracking.get(it.actionId)?.size ?: 0
                )
                actionJSONObject.put(
                    "executedUpdatedStatements",
                    actionUpdatedCoverageTracking.get(it.actionId)?.size ?: 0
                )
                actionJSONObject.put(
                    "newExecutedUpdatedStatements",
                    actionIncreasingUpdatedCoverageTracking.get(it.actionId)?.size ?: 0
                )
                if (it.targetWidget != null) {
                    val widget = it.targetWidget!!
                    val widgetJSONObject = JSONObject()
                    actionJSONObject.put("targetWidget", widgetJSONObject)
                    widgetJSONObject.put("id", widget.id)
                    widgetJSONObject.put("uid", widget.uid)
                    widgetJSONObject.put("configId", widget.configId)
                    widgetJSONObject.put("text", widget.text)
                    widgetJSONObject.put("contentDesc", widget.contentDesc)
                    widgetJSONObject.put("className", widget.className)
                    val visibleBounds = JSONObject()
                    visibleBounds.put("leftX", widget.visibleBounds.leftX)
                    visibleBounds.put("topY", widget.visibleBounds.topY)
                    visibleBounds.put("width", widget.visibleBounds.width)
                    visibleBounds.put("height", widget.visibleBounds.height)
                    widgetJSONObject.put("visibleBounds", visibleBounds)
                }
            }
            prevActionId = it.actionId
        }
        val jsonFile = File(actionCoverageHTMLFolderPath.resolve("data.js").toString())
        val jsonString = dataJson.toString(1)
        jsonFile.writeText("var data = " + jsonString)

//        generate html files
        val templateHTMLFile = actionCoverageHTMLFolderPath.resolve("template.html")
        val file = File(templateHTMLFile.toUri())
        val content = file.readLines()

        actions.forEach {
            if (actionIncreasingUpdatedCoverageTracking.containsKey(it.actionId)
                && actionIncreasingUpdatedCoverageTracking.get(it.actionId)!!.size>0) {
                val newHTMLFilePath = actionCoverageHTMLFolderPath.resolve("${it.actionId}.html")
                Files.createFile(newHTMLFilePath)
                val newHTMLFile = File(newHTMLFilePath.toUri())
                content.forEach { line ->
                    newHTMLFile.appendText(line.replace("##action_id##", it.actionId.toString()))
                }
            }
        }
    }
    companion object {
        private const val statement_header = "Statement(id);Method id;Time(Duration in sec till first occurrence)"
        private const val method_header = "Method(id);Method name;Time(Duration in sec till first occurrence)"

        @JvmStatic
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(StatementCoverageMF::class.java) }

        object StatementCoverage : PropertyGroup() {
            val enableCoverage by booleanType
            val onlyCoverAppPackageName by booleanType
            val coverageDir by stringType
        }

    }

}

private suspend fun <S, W> ExplorationTrace<State<*>,Widget>.dumpWithCoverage(config:ModelConfig, actionTraceCoverage: HashMap<Int, Set<String>>, actionIncreaseCoverage: HashMap<Int, Set<String>>, actionUpdatedStmtCoverage: HashMap<Int,Set<String>>, actionIncreasingUpdatedStmtCoverage: HashMap<Int,Set<String>>) {
    File(config.traceFile2(id.toString())).bufferedWriter().use { out ->
        out.write(StringCreator.actionHeader(config[ConfigProperties.ModelProperties.dump.sep]))
        out.write(";ExecutedStatements;NewExecutedStatements;ExecutedUpdatedStatements;NewUpdatedExecutedStatements")
        out.newLine()
        // ensure that our trace is complete before dumping it by calling blocking getActions
        val actions = ArrayList(P_getActions())
        actions.forEach { action ->
            out.write(StringCreator.createActionString(action, config[ConfigProperties.ModelProperties.dump.sep]))
            out.write(";${actionTraceCoverage.get(action.actionId)?.size};${actionIncreaseCoverage.get(action.actionId)?.size};${actionUpdatedStmtCoverage.get(action.actionId)?.size};${actionIncreasingUpdatedStmtCoverage.get(action.actionId)?.size}")
            out.newLine()
        }
    }
}

private fun ModelConfig.traceFile2(traceId: String): String {
    val oldtraceFile = traceFile(traceId).toString()
    val traceFileExtension = Paths.get(oldtraceFile).getExtension()
    val newTraceFile = oldtraceFile.toString().replace(".$traceFileExtension","")+"_atua.csv"
    return newTraceFile
}
