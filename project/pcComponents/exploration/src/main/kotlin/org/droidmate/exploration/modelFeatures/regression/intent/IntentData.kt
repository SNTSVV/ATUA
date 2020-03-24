package org.droidmate.exploration.modelFeatures.regression.intent

data class IntentData (
        val scheme: String="",
        val host: String="",
        val path: String="",
        val mimeType: String="",
        val testData: ArrayList<String> = ArrayList<String>()){
}