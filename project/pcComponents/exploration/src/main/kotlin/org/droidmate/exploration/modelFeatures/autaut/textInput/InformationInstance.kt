package org.droidmate.exploration.modelFeatures.autaut.textInput

open class InformationInstance (val data: HashMap<DataField,String> = HashMap(),
                           val informationType: InformationType){
    open fun getValue(dataField: DataField): String{
        if (data.containsKey(dataField))
        {
            return data[dataField]!!
        }
        else
        {
            return ""
        }
    }
}

class GeneratedInformationInstance ( data: HashMap<DataField, String> = HashMap(),
                          informationType: InformationType): InformationInstance(data, informationType){
    override fun getValue(dataField: DataField): String {
        return super.getValue(dataField)
    }
}