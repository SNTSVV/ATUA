// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.test_suites

import org.droidmate.test_suite_categories.ExcludedFromFastRegressionTests
import org.junit.experimental.categories.Categories
import org.junit.runner.RunWith

//@RunWith(Categories::class)
//@Categories.ExcludeCategory(ExcludedFromFastRegressionTests::class)
// FIXME dependencies (parent test class should be in commonLib then this test-suit can be put into exploration.report
//@Suite.SuiteClasses(
//		extensions_miscKtTest::class,
//		extensions_time_seriesKtTest::class,
//		functionsKtTest::class,
//		ApkSummaryTest::class,
//		ExplorationOutput2ReportTest::class
//)
class ReporterTestSuite
