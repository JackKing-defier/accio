/*
 * Accio is a program whose purpose is to study location privacy.
 * Copyright (C) 2016-2017 Vincent Primault <vincent.primault@liris.cnrs.fr>
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

package fr.cnrs.liris.accio.agent.worker

import java.nio.file.{Files, Path}

import com.google.common.io.ByteStreams
import com.google.inject.{Inject, Singleton}
import com.twitter.util._
import com.typesafe.scalalogging.StrictLogging
import fr.cnrs.liris.accio.agent._
import fr.cnrs.liris.accio.agent.config.AgentConfig
import fr.cnrs.liris.accio.core.domain.{InvalidTaskException, Task, TaskId}
import fr.cnrs.liris.accio.core.filesystem.FileSystem
import fr.cnrs.liris.accio.core.util.{ThreadLike, ThreadManager, WorkerPool}
import fr.cnrs.liris.common.util.FileUtils

import scala.collection.mutable

/**
 * @param filesystem   Distributed filesystem.
 * @param config       Agent configuration.
 * @param masterClient Master server client.
 * @param pool         Pool of threads.
 */
@Singleton
final class WorkerTaskExecutor @Inject()(
  filesystem: FileSystem,
  config: AgentConfig,
  masterClient: AgentService$FinagleClient,
  @WorkerPool pool: FuturePool) extends StrictLogging {

  private[this] var status: TaskExecutor.Status = TaskExecutor.Status.Ready
  private[this] val threads = new ThreadManager(pool)
  private[this] lazy val localExecutorPath = downloadExecutor()

  /**
   * Start a task as an external process.
   *
   * @param task Task to start.
   */
  def submit(task: Task): Future[Unit] = synchronized {
    if (status != TaskExecutor.Status.Ready) {
      throw new IllegalStateException(s"Status: $status")
    }
    logger.debug(s"Starting task ${task.id.value}")
    threads.submit(new MonitorThread(task), task.id.value).respond {
      case Throw(e) => logger.error(s"Error in monitoring thread of task ${task.id.value}", e)
      case Return(_) => logger.debug(s"Monitoring thread of task ${task.id.value} completed")
    }
  }

  /**
   * Kill a given task.
   *
   * @param id Task identifier.
   * @throws InvalidTaskException If this task was not running.
   */
  @throws[InvalidTaskException]
  def kill(id: TaskId): Unit = synchronized {
    if (status != TaskExecutor.Status.Ready) {
      throw new IllegalStateException(s"Status: $status")
    }
    if (!threads.kill(id.value)) {
      logger.debug(s"No running task ${id.value}")
      throw new InvalidTaskException
    }
  }

  /**
   * Close this executor by killing all tasks. Any further submission will be rejected.
   */
  def close(): Unit = synchronized {
    if (status != TaskExecutor.Status.Ready) {
      throw new IllegalStateException(s"Status: $status")
    }
    status = TaskExecutor.Status.Terminating
    threads.killAll()
    status = TaskExecutor.Status.Closed
  }

  /**
   * Return the path to the sandbox for a given key.
   *
   * @param id Task identifier.
   */
  private def getSandboxPath(id: TaskId) = config.workDir.resolve(id.value)

  private class MonitorThread(val task: Task) extends ThreadLike with StrictLogging {
    private[this] var killed = false
    private[this] var process: Option[Process] = None

    override def run(): Unit = {
      val maybeProcess = synchronized {
        process = if (killed) None else Some(startProcess(task))
        process
      }
      maybeProcess match {
        case None =>
          logger.debug(s"Skipped task ${task.id.value} (killed)")
          cleanup()
        case Some(p) =>
          try {
            ByteStreams.copy(p.getInputStream, ByteStreams.nullOutputStream)
            p.waitFor()
          } finally {
            cleanup()
          }
      }
    }

    def kill(): Unit = synchronized {
      if (!killed) {
        killed = true
        process.foreach { p =>
          p.destroyForcibly()
          p.waitFor()
        }
        cleanup()
        logger.debug(s"Killed task ${task.id.value}")
      }
    }

    private def cleanup() = {
      process = None
      FileUtils.safeDelete(getSandboxPath(task.id))
    }

    private def startProcess(task: Task): Process = {
      val cmd = createCommandLine(task)
      logger.debug(s"Command-line for task ${task.id.value}: ${cmd.mkString(" ")}")

      val sandboxDir = getSandboxPath(task.id)
      Files.createDirectories(sandboxDir)

      val pb = new ProcessBuilder()
        .command(cmd: _*)
        .directory(sandboxDir.toFile)
        .redirectErrorStream(true)

      pb.start()
    }
  }

  private def createCommandLine(task: Task): Seq[String] = {
    val args = config.worker.get.executorArgs ++ Seq("-addr", config.client.get.masterAddr)
    val javaBinary = config.worker.get.javaHome.orElse(sys.env.get("JAVA_HOME")).map(home => s"$home/bin/java").getOrElse("/usr/bin/java")
    val cmd = mutable.ListBuffer.empty[String]
    cmd += javaBinary
    cmd ++= Seq("-cp", localExecutorPath.toString)
    cmd += s"-Xmx${task.resource.ramMb}M"
    cmd += "fr.cnrs.liris.accio.executor.AccioExecutorMain"
    cmd ++= args
    cmd ++= Seq("-com.twitter.jvm.numProcs", task.resource.cpu.toString)
    cmd += task.id.value
  }

  /**
   * Download the executor locally. It will always be downloaded at startup time, but reused for multiple tasks.
   *
   * @return Local path to the executor
   */
  private def downloadExecutor(): Path = {
    val targetPath = config.workDir.resolve("executor.jar")
    if (targetPath.toFile.exists()) {
      targetPath.toFile.delete()
    }
    logger.info(s"Downloading executor JAR to ${targetPath.toAbsolutePath}")
    filesystem.read(config.worker.get.executorUri, targetPath)
    targetPath.toAbsolutePath
  }
}

private object TaskExecutor {

  sealed trait Status

  object Status {

    case object Ready extends Status

    case object Terminating extends Status

    case object Closed extends Status

  }

}