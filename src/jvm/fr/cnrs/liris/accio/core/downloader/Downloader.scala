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

package fr.cnrs.liris.accio.core.downloader

import java.nio.file.Path

/**
 * Downloaders fetch files or directories from remote locations.
 *
 * The counterpart of a downloader is an uploader. The two inferfaces are split because while executors are usually
 * tied to a specific uploader, a downloader may support multiple ways of fetching data, thus guaranteeing
 * compatibility with multiple uploaders.
 *
 * Note that we do not expect to support another implementation than the standard one that can be found under the
 * core/infra/downloader module. However, this interface is useful for better separation of concerns, and facilitates
 * testing of objects that need a downloader.
 */
trait Downloader {
  def download(src: String, dst: Path): Unit
}