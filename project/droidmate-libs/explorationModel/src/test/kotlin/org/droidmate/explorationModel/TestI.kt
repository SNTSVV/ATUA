/*
 * Copyright (c) 2019.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.droidmate.explorationModel

import junit.framework.TestCase

interface TestI {
	fun<T> expect(res:T, ref: T){
		val refSplit = ref.toString().split(";")
		val diff = res.toString().split(";").mapIndexed { index, s ->
			if(refSplit.size>index) s.replace(refSplit[index],"#CORRECT#")
			else s
		}
		TestCase.assertTrue("expected \n${ref.toString()} \nbut result was \n${res.toString()}\n DIFF = $diff", res == ref)
	}
}