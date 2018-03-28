/*
 * Accio is a platform to launch computer science experiments.
 * Copyright (C) 2016-2018 Vincent Primault <v.primault@ucl.ac.uk>
 *
 * Accio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Accio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Accio.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.cnrs.liris.accio.executor

import java.io.FileOutputStream
import java.nio.file.{Files, Path}

import com.twitter.util.logging.Logging
import fr.cnrs.liris.accio.api.Errors
import fr.cnrs.liris.accio.api.thrift.{OpResult, Task}
import fr.cnrs.liris.accio.service.{OpExecutor, OpExecutorOpts}
import fr.cnrs.liris.common.scrooge.BinaryScroogeSerializer

final class TaskExecutor(opExecutor: OpExecutor) extends Logging {
  def execute(task: Task, outputFile: Path): Unit = {
    val result = execute(task)
    Files.createDirectories(outputFile.getParent)
    val fos = new FileOutputStream(outputFile.toFile)
    try {
      BinaryScroogeSerializer.write(result, fos)
    } finally {
      fos.close()
    }
  }

  private def execute(task: Task): OpResult = {
    logger.info(s"Starting execution of task ${task.id.value}")
    try {
      val opts = OpExecutorOpts(useProfiler = true)
      opExecutor.execute(task.payload, opts)
    } catch {
      case e: Throwable =>
        // Normally, operator executor is supposed to be robust enough to catch all errors. But we still handle
        // and uncaught error here, just in case...
        logger.error(s"Operator raised an unexpected error", e)
        OpResult(-999, Some(Errors.create(e)))
    }
  }
}