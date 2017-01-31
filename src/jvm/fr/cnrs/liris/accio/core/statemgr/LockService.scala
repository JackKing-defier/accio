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

package fr.cnrs.liris.accio.core.statemgr

import fr.cnrs.liris.accio.core.domain.{RunId, TaskId}

/**
 * High-level lock service, wrapping a state manager;
 *
 * @param stateManager State manager.
 */
final class LockService(stateManager: StateManager) {
  /**
   * Execute a block of code, with a lock around a run acquired.
   *
   * @param runId Run identifier.
   * @param f     Code block.
   * @tparam T Code block return type.
   * @return Result of the code block.
   */
  def withLock[T](runId: RunId)(f: => T): T = {
    val lock = stateManager.lock(s"run/${runId.value}")
    lock.lock()
    try {
      f
    } finally {
      lock.unlock()
    }
  }

  def withLock[T](maybeRunId: Option[RunId])(f: => T): T = {
    maybeRunId match {
      case Some(runId) => withLock(runId)(f)
      case None => f
    }
  }

  /**
   * Execute a block of code, with a lock around a task acquired.
   *
   * @param taskId Task identifier.
   * @param f      Code block.
   * @tparam T Code block return type.
   * @return Result of the code block.
   */
  def withLock[T](taskId: TaskId)(f: => T): T = {
    val lock = stateManager.lock(s"task/${taskId.value}")
    lock.lock()
    try {
      f
    } finally {
      lock.unlock()
    }
  }
}