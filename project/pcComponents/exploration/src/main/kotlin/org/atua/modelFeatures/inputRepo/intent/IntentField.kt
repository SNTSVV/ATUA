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
package org.atua.modelFeatures.inputRepo.intent

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
