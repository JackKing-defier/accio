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

package fr.cnrs.liris.accio.ops.testing

import com.google.common.io.Resources
import fr.cnrs.liris.accio.ops.io.TraceCodec
import fr.cnrs.liris.accio.ops.model.Trace

trait WithCabspotting {
  // It doesn't work with a val..
  private def decoder = new TraceCodec

  lazy val abboipTrace = cabspottingTrace("abboip")

  def cabspottingTrace(key: String): Trace = {
    val bytes = Resources.toByteArray(Resources.getResource(s"fr/cnrs/liris/accio/ops/testing/$key.csv"))
    decoder.decode(key, bytes).head
  }
}
