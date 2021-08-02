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

package org.droidmate.legacy

import java.nio.file.Files
import java.nio.file.Path

class FileSystemsOperations {
    companion object {
        @JvmStatic
        private fun copyPath(it: Path, src: Path, dest: Path): Path {
            assert(Files.isDirectory(src))
            assert(Files.isDirectory(dest))

            val itInDest = mapToDestination(it, src, dest)

            if (itInDest != dest) {
                assert(!Files.exists(itInDest))

                when {
                    Files.isDirectory(it) -> Files.createDirectory(itInDest)
                    Files.isRegularFile(it) -> Files.copy(it, itInDest)
                    else -> assert(false)
                }
            }

            return itInDest
        }

        @JvmStatic
        private fun mapToDestination(path: Path, srcDir: Path, destDir: Path): Path {
            return destDir.resolve(
                srcDir.relativize(path).toString().replace(
                    srcDir.fileSystem.separator,
                    destDir.fileSystem.separator
                )
            )
        }

        @JvmStatic
        fun copyDirRecursivelyToDirInDifferentFileSystem(dir: Path, dest: Path) {
            assert(Files.isDirectory(dir))
            assert(Files.isDirectory(dest))
            assert(dir.fileSystem != dest.fileSystem)
            assert(dir.parent != null)

            Files.walk(dir)
                .forEach { copyPath(it, dir.parent, dest) }
        }

        @JvmStatic
        fun copyDirContentsRecursivelyToDirInDifferentFileSystem(dir: Path, dest: Path) {
            assert(Files.isDirectory(dir))
            assert(Files.isDirectory(dest))
            assert(dir.fileSystem != dest.fileSystem)

            Files.walk(dir)
                .forEach { copyPath(it, dir, dest) }
        }

        @JvmStatic
        fun copyFilesToDirInDifferentFileSystem(files: List<Path>, dest: Path) {
            assert(Files.isDirectory(dest))
            files.forEach {
                assert(Files.isRegularFile(it))
                assert(it.parent != null)
                assert(Files.isDirectory(it.parent))
                assert(it.fileSystem != dest.fileSystem)
            }

            files.forEach {
                copyPath(it, it.parent, dest)
            }
        }

        @JvmStatic
        fun copyDirContentsRecursivelyToDirInSameFileSystem(dir: Path, dest: Path) {
            assert(Files.isDirectory(dir))
            assert(Files.isDirectory(dest))
            assert(dir.fileSystem == dest.fileSystem)

            Files.walk(dir)
                .forEach { copyPath(it, dir, dest) }
        }
    }
}
