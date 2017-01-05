/*
 * Accio is a program whose purpose is to study location privacy.
 * Copyright (C) 2016 Vincent Primault <vincent.primault@liris.cnrs.fr>
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

package fr.cnrs.liris.accio.core.framework.local

import com.google.inject.AbstractModule
import fr.cnrs.liris.accio.core.framework.{ArtifactRepository, RunRepository, WorkflowRepository}
import net.codingwell.scalaguice.ScalaModule

/**
 * Guice module provisionning local storage for repositories.
 */
object LocalStorageModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[RunRepository].to[LocalRunRepository]
    bind[WorkflowRepository].to[LocalWorkflowRepository]
    bind[ArtifactRepository].to[LocalArtifactRepository]
  }
}
