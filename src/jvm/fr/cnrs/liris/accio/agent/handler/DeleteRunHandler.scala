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

package fr.cnrs.liris.accio.agent.handler

import com.google.inject.Inject
import com.twitter.util.Future
import fr.cnrs.liris.accio.agent.{DeleteRunRequest, DeleteRunResponse, UnknownRunException}
import fr.cnrs.liris.accio.core.domain.RunId
import fr.cnrs.liris.accio.core.runtime.SchedulerService
import fr.cnrs.liris.accio.core.statemgr.{LockService, StateManager}
import fr.cnrs.liris.accio.core.storage.MutableRunRepository

/**
 * Delete a run, and all its children if it is a parent run.
 *
 * @param runRepository    Run repository.
 * @param lockService      Lock service.
 * @param stateManager     State manager.
 * @param schedulerService Scheduler service.
 */
final class DeleteRunHandler @Inject()(
  runRepository: MutableRunRepository,
  lockService: LockService,
  stateManager: StateManager,
  schedulerService: SchedulerService)
  extends Handler[DeleteRunRequest, DeleteRunResponse] {

  override def handle(req: DeleteRunRequest): Future[DeleteRunResponse] = {
    lockService.withLock(req.id) {
      runRepository.get(req.id) match {
        case None => throw new UnknownRunException()
        case Some(run) =>
          if (run.children.nonEmpty) {
            // It is a parent run, cancel and delete child all runs.
            cancelRuns(run.children)
            run.children.foreach { childId =>
              lockService.withLock(childId) {
                runRepository.remove(childId)
              }
            }
          } else if (run.parent.isDefined) {
            // It is a child run, update or delete parent run.
            lockService.withLock(run.parent.get) {
              runRepository.get(run.parent.get).foreach { parent =>
                if (parent.children.size > 1) {
                  // There are several child runs left, remove current one from the list.
                  runRepository.save(parent.copy(children = parent.children.filterNot(_ == run.id)))
                } else {
                  // It was the last child of this run, delete it as it is now useless.
                  runRepository.remove(parent.id)
                }
              }
            }
          } else {
            // It is a single run, cancel it.
            cancelRuns(Seq(run.id))
          }
          // Finally, delete the run.
          runRepository.remove(run.id)
      }
    }
    Future(DeleteRunResponse())
  }

  private def cancelRuns(ids: Seq[RunId]) = {
    val tasks = stateManager.tasks.filter(task => ids.contains(task.runId))
    tasks.foreach { task =>
      lockService.withLock(task.id) {
        schedulerService.kill(task)
      }
    }
  }
}