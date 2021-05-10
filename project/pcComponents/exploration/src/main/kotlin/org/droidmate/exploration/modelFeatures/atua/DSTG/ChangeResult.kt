package org.droidmate.exploration.modelFeatures.atua.DSTG

data class ChangeResult (val changeableElement: ChangeableElement, val changed: Boolean){

}

enum class ChangeableElement {
    Rotation
}
