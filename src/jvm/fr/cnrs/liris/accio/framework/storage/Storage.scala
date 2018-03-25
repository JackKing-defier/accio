/*
 * Accio is a program whose purpose is to study location privacy.
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

package fr.cnrs.liris.accio.framework.storage

import com.google.common.util.concurrent.{AbstractIdleService, Service, ServiceManager}
import scala.collection.JavaConverters._

/**
 * A storage service is an interface to access repositories. Repositories should never be accessed directly (e.g.,
 * they should not be available through Guice injection), but instead used through their storage. It gives a higher
 * level control over repositories, notably including support for transactions.
 */
trait Storage extends Service {
  def runs: MutableRunRepository

  def logs: MutableLogRepository

  def workflows: MutableWorkflowRepository
}

/**
 * Base class for storage.
 */
private[storage] trait AbstractStorage extends AbstractIdleService with Storage {
  private[this] val serviceManager = new ServiceManager(Set(runs, workflows, logs).asJava)

  override protected def shutDown(): Unit = {
    serviceManager.stopAsync().awaitStopped()
  }

  override protected def startUp(): Unit = {
    serviceManager.startAsync().awaitHealthy()
  }
}