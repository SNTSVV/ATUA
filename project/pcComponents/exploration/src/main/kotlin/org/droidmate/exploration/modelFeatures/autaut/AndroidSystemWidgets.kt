package org.droidmate.exploration.modelFeatures.autaut

class AndroidSystemWidgets{
    companion object{
        val systemWidgetResourceIds: List<String>
        get() {
            return ArrayList<String>().also {
                it.add("android:id/floating_toolbar_menu_item_text")
                it.add("android:id/selection_end_handle")
                it.add("android:id/selection_start_handle")
            }
        }
    }
}