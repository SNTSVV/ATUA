package org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput

import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.DataField
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.InformationInstance

class InformationType (val name: String,
                       val appPackages: ArrayList<String> = ArrayList(),
                       val dataFields: ArrayList<DataField> = ArrayList(),
                       val data: ArrayList<InformationInstance> = ArrayList()) {

}