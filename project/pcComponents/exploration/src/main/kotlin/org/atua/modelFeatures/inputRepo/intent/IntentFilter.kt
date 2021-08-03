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

import com.google.common.collect.Sets

class IntentFilter(val activity: String) {
    private val mActions: MutableSet<String> = Sets.newHashSet()
    private val mCategories: MutableSet<String> = Sets.newHashSet()
    private val mData: MutableSet<IntentData> = Sets.newHashSet()

    // indicate the data type is partial matched or not
    // e.g., "DIR/*" is partial matched, while "DIR/FILE" is full matched
    private var mHasPartialTypes: Boolean = false

    fun addAction(action: String) {
        this.mActions.add(action)
    }

    fun addCategory(category: String) {
        this.mCategories.add(category)
    }

    fun addData(data: IntentData){
        this.mData.add(data)
    }

    fun getActions(): Set<String> {
        return mActions
    }

    fun getCategories(): Set<String> {
        return mCategories
    }

    fun getDatas(): Set<IntentData>{
        return mData
    }

//  public boolean isLauncherFilter() {
//    return mActions.contains(WTGUtil.v().launcherAction) && mCategories.contains(WTGUtil.v().launcherCategory);
//  }

    class AuthorityEntry(private val mOrigHost: String, port: String?) {
        private val mHost: String
        private val mWild: Boolean
        private val mPort: Int

        init {
            mWild = mOrigHost.length > 0 && mOrigHost[0] == '*'
            mHost = if (mWild) mOrigHost.substring(1).intern() else mOrigHost
            mPort = if (port != null) Integer.parseInt(port) else -1
        }

        override fun toString(): String {
            return "<host: $mHost, port: $mPort>"
        }
    }

}