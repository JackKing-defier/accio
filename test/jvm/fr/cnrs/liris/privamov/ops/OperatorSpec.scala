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

package fr.cnrs.liris.privamov.ops

import java.nio.file.Files

import fr.cnrs.liris.accio.core.api.OpContext
import fr.cnrs.liris.accio.testing.WithSparkleEnv
import fr.cnrs.liris.dal.core.api.Dataset
import fr.cnrs.liris.dal.core.io.{CsvSink, CsvSource}
import fr.cnrs.liris.privamov.core.io.{CsvEventCodec, CsvPoiSetCodec, TraceCodec}
import fr.cnrs.liris.privamov.core.model.{PoiSet, Trace}

/**
 * Trait facilitating testing operators.
 */
private[ops] trait OperatorSpec extends WithSparkleEnv { FlatSpec =>
  private[this] val traceCodec = new TraceCodec(new CsvEventCodec)
  private[this] val poiSetCodec = new CsvPoiSetCodec

  protected def ctx: OpContext = {
    val workDir = Files.createTempDirectory(getClass.getSimpleName + "-")
    workDir.toFile.deleteOnExit()
    // This seed makes tests of unstable operators to pass for now. Be careful is you modify it!!
    new OpContext(Some(-7590331047132310476L), workDir, env, Set(traceCodec, poiSetCodec), Set(traceCodec, poiSetCodec))
  }

  protected def writeTraces(data: Trace*): Dataset = {
    val uri = Files.createTempDirectory(getClass.getSimpleName + "-").toAbsolutePath.toString
    env.parallelize(data: _*)(_.id).write(new CsvSink(uri, traceCodec))
    Dataset(uri)
  }

  protected def writePois(data: PoiSet*): Dataset = {
    val uri = Files.createTempDirectory(getClass.getSimpleName + "-").toAbsolutePath.toString
    env.parallelize(data: _*)(_.id).write(new CsvSink(uri, poiSetCodec))
    Dataset(uri)
  }

  protected def readTraces(ds: Dataset): Seq[Trace] = {
    env.read(new CsvSource(ds.uri, traceCodec)).toArray.toSeq
  }
}