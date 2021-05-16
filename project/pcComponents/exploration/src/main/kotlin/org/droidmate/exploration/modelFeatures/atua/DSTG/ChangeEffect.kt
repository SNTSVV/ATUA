package org.droidmate.exploration.modelFeatures.atua.DSTG

data class ChangeEffect (val affectedElementType: AffectElementType,val changeableElement: ChangeableElement?, val changed: Boolean){

}

enum class AffectElementType {
    Rotation
}

class ChangeableElement (val element: Any, val affectType: AffectType) {

}

enum class AffectType {
    deleted,
    added,
    changed
}
