package org.droidmate.exploration.modelFeatures.regression.intent

/*
 * IntentField.java - part of the GATOR project
 *
 * Copyright (c) 2018 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */

enum class IntentField {
    /////////////////////////////
    // normal section
    NormalStart,
    SrcActivity,
    TgtActivity,
    Action,
    Category,
    MimeType,
    /////////////////////////////
    // uri section
    UriPartStart,
    Scheme,
    Host,
    Port,
    Path,
    /////////////////////////////
    // all sections
    AllStart,
    All,
    ////////////////////////////
    // special key
    UriStart,
    Uri,
    ////////////////////////////
    // implicit target
    ImplicitStart,
    ImplicitTgtActivity,
    END;

    val isNormalField: Boolean
        get() = this.ordinal > NormalStart.ordinal && this.ordinal < UriPartStart.ordinal

    val isPartOfUriField: Boolean
        get() = this.ordinal > UriPartStart.ordinal && AllStart.ordinal > this.ordinal

    val isAllField: Boolean
        get() = this.ordinal > AllStart.ordinal && UriStart.ordinal > this.ordinal

    val isUriField: Boolean
        get() = this.ordinal > UriStart.ordinal && END.ordinal > this.ordinal

    // data field includes everything except srcActivity, tgtActivity, action and category
    val isDataField: Boolean
        get() = this.ordinal > Category.ordinal && ImplicitStart.ordinal > this.ordinal
}
