package org.droidmate.exploration.modelFeatures.regression.staticModel

enum class EventType {
    // "usual" ones
    click,
    long_click,
    // This is for selectable objects - radio button, check box, etc.
    select,
    scroll,

    // Quickly slide through the screen without long impact
    swipe,
    // Swipe through the screen but hold for long enough
    drag,
    // The general multi-touch event
    touch,

    // Not sure if this should be a user event
    focus_change,

    // This does not need to happen for a text box (but it can)
    press_key,
    //This is for press back
    press_back,
    // This is for text boxes
    enter_text,
    // Special editor action performed on a text view - when the enter key is
    // pressed, or when an action supplied to the IME is selected by the user.
    editor_action,

    // For any composite views (ListView, Menu, etc) - the user sees a list, and
    // intends to interact with one of its items. Additional events may be
    // triggered simultaneously on the specific item object.
    item_click,
    item_long_click,
    item_selected,

    zoom_in,
    zoom_out,

    // Dialog events
    dialog_negative_button, // TODO(tony): remove soon
    dialog_neutral_button, // TODO(tony): remove soon
    dialog_cancel,
    dialog_dismiss,
    dialog_press_key,
    dialog_positive_button, // TODO(tony): remove soon

    //intent
    callIntent,

    EXPLICIT_IMPLICIT_SEPARATOR,

    // View
    implicit_create_context_menu,
    implicit_hierarchy_change,
    implicit_time_tick,
    implicit_system_ui_change,

    // Temporarily added for model construction
    // event related with activity create, resume, stop, pause
    implicit_lifecycle_event,
    // event related with onActivityResult
    implicit_on_activity_result,
    // event related with onNewIntent
    implicit_on_activity_newIntent,
    // back event
    implicit_back_event,
    // rotate
    implicit_rotate_event,
    // home
    implicit_home_event,
    // power
    implicit_power_event,
    //pressMenuKey
    implicit_menu,
    // launcher
    implicit_launch_event,
    // asynchronous operations: Activity.runOnUiThread, View.post, View.postDelayed
    implicit_async_event,

    fake_action,

    no_action,

    END_MARKER_NEVER_USE;

    val isImplicit: Boolean
        get() = this.ordinal > EXPLICIT_IMPLICIT_SEPARATOR.ordinal

    val isItemEvent: Boolean
        get() = (this== item_selected)||(this== item_long_click)||(this== item_click)
}