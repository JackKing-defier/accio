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

package fr.cnrs.liris.locapriv.ops

import fr.cnrs.liris.accio.sdk.{Dataset, _}
import fr.cnrs.liris.common.geo.Distance
import fr.cnrs.liris.locapriv.clustering.{DTClusterer, PoisClusterer}
import fr.cnrs.liris.locapriv.model.{Poi, PoiSet, Trace}

@Op(
  category = "transform",
  help = "Compute POIs retrieval difference between two datasets of traces",
  cpu = 4,
  ram = "3G")
class PoisExtractionOp extends Operator[PoisExtractionIn, PoisExtractionOut] with SparkleOperator {

  override def execute(in: PoisExtractionIn, ctx: OpContext): PoisExtractionOut = {
    val input = read[Trace](in.data)
    val output =  if (in.minPoints == 0) {
      val clusterer = new DTClusterer(in.duration, in.diameter)
      input.map { trace =>
        val pois = clusterer.cluster(trace).map(cluster => Poi(cluster.events))
        PoiSet(trace.id, pois)
      }
    } else {
      val clusterer = new PoisClusterer(in.duration, in.diameter, in.minPoints)
      input.map { trace =>
        val pois = clusterer.cluster(trace.events)
        PoiSet(trace.id, pois)
      }
    }
    PoisExtractionOut(write(output, ctx))
  }
}

case class PoisExtractionIn(
  @Arg(help = "Clustering maximum diameter") diameter: Distance,
  @Arg(help = "Clustering minimum duration") duration: org.joda.time.Duration,
  @Arg(help = "Minimum number of times a cluster should appear to consider it") minPoints: Int = 0,
  @Arg(help = "Input traces dataset") data: Dataset)

case class PoisExtractionOut(
  @Arg(help = "Output POIs dataset") data: Dataset)