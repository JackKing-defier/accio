/*
 * Copyright LIRIS-CNRS (2016)
 * Contributors: Vincent Primault <vincent.primault@liris.cnrs.fr>
 *
 * This software is a computer program whose purpose is to study location privacy.
 *
 * This software is governed by the CeCILL-B license under French law and
 * abiding by the rules of distribution of free software. You can use,
 * modify and/ or redistribute the software under the terms of the CeCILL-B
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty and the software's author, the holder of the
 * economic rights, and the successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading, using, modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean that it is complicated to manipulate, and that also
 * therefore means that it is reserved for developers and experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and, more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-B license and that you accept its terms.
 */

package fr.cnrs.liris.privamov.service.gateway

import javax.inject.{Inject, Singleton}

import com.twitter.finagle.http.Response
import com.twitter.finatra.annotations.Flag
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import fr.cnrs.liris.accio.core.model.GeoJsonConverters._
import fr.cnrs.liris.accio.core.model.{Record, Trace}
import fr.cnrs.liris.accio.core.ops.transform.{GeoIndistinguishability, PromesseOp}
import fr.cnrs.liris.common.geo._
import fr.cnrs.liris.common.util.Distance
import fr.cnrs.liris.privamov.service.gateway.auth.{AccessToken, Firewall, Scope}
import fr.cnrs.liris.privamov.service.gateway.store.{EventStore, StoreRegistry, View}
import org.joda.time.DateTime


case class ListDatasetsRequest(@QueryParam accessToken: Option[String])

case class GetDatasetRequest(
    @RouteParam name: String,
    @QueryParam accessToken: Option[String])

case class ListSourcesRequest(
    @RouteParam name: String,
    @QueryParam accessToken: Option[String],
    @QueryParam startAfter: Option[String],
    @QueryParam limit: Option[Int])

case class GetSourceRequest(
    @RouteParam name: String,
    @RouteParam id: String,
    @QueryParam accessToken: Option[String])

case class ListFeaturesRequest(
    @RouteParam name: String,
    @QueryParam accessToken: Option[String],
    @QueryParam sources: Set[String],
    @QueryParam startAfter: Option[DateTime],
    @QueryParam endBefore: Option[DateTime],
    @QueryParam limit: Option[Int],
    @QueryParam sample: Option[Boolean],
    @QueryParam transform: Option[String])

/**
 * A dataset holding some mobility data.
 *
 * @param name        Dataset name
 * @param storage     Dataset storage type
 * @param description Dataset description
 */
case class Dataset(name: String, storage: String, description: Option[String] = None)

case class PaginatedList[T](next: Option[String], total: Option[Int], data: T)

case class Error(`type`: String, message: Option[String], param: Option[String])

object Error {
  def invalidRequest(message: Option[String] = None, param: Option[String] = None): Error =
    new Error("invalid_request_error", message, param)

  def apiError(message: Option[String] = None): Error = new Error("api_error", message, None)

  def authenticationError(message: Option[String] = None): Error =
    new Error("authentication_error", message, None)
}

/**
 * Controller handling API endpoints.
 *
 * @author Vincent Primault <vincent.primault@liris.cnrs.fr>
 */
@Singleton
class GatewayController @Inject()(
    @Flag("viz.standard_limit") standardLimit: Int,
    @Flag("viz.extended_limit") extendedLimit: Int,
    stores: StoreRegistry,
    firewall: Firewall
) extends Controller {

  get("/api/datasets") { request: ListDatasetsRequest =>
    authenticated(request.accessToken, Scope.Datasets) { token =>
      val datasets = stores
          .filter(token.acl.accessible)
          .map(store => Dataset(store.name, store.getClass.getSimpleName))
      PaginatedList(None, Some(datasets.size), datasets)
    }
  }

  get("/api/datasets/:name") { request: GetDatasetRequest =>
    authenticated(request.accessToken, Scope.Datasets) { token =>
      withStore(request.name, token) { store =>
        Dataset(request.name, store.getClass.getSimpleName)
      }
    }
  }

  get("/api/datasets/:name/sources") { request: ListSourcesRequest =>
    authenticated(request.accessToken, Scope.Datasets) { token =>
      withStore(request.name, token) { store =>
        val accessibleViews = token.acl.resolve(store.name).views
        val limit = request.limit.map(math.min(_, standardLimit)).getOrElse(standardLimit)
        val data = store.sources(accessibleViews, Some(limit), request.startAfter)
        val count = store.countSources(accessibleViews)
        val next = if (count > data.size) Some(data.last.id) else None
        PaginatedList(next, Some(count), data)
      }
    }
  }

  get("/api/datasets/:name/sources/:id") { request: GetSourceRequest =>
    authenticated(request.accessToken, Scope.Datasets) { token =>
      withStore(request.name, token) { store =>
        if (store.contains(request.id)) {
          store.apply(request.id)
        } else {
          response.notFound
        }
      }
    }
  }

  get("/api/datasets/:name/features") { request: ListFeaturesRequest =>
    authenticated(request.accessToken, Scope.Datasets) { token =>
      withStore(request.name, token) { store =>
        val views = if (request.sources.nonEmpty) {
          request.sources.map(source => View(Some(source), request.startAfter, request.endBefore, None))
        } else {
          Set(View(None, request.startAfter, request.endBefore, None))
        }
        val allowedViews = token.acl.resolve(store.name).resolve(views).views
        val limit = request.limit.map(math.min(_, extendedLimit)).getOrElse(extendedLimit)
        val sample = request.sample.getOrElse(false)
        val rawData = store.features(allowedViews, Some(limit), sample)
        val transformedData = request.transform match {
          case Some(spec) => transform(rawData, spec)
          case None => rawData
        }
        val count = store.countFeatures(allowedViews)
        val next = if (!sample && count > transformedData.size) {
          Some(transformedData.last.time.toString)
        } else {
          None
        }
        PaginatedList(next, Some(count), toGeoJson(transformedData))
      }
    }
  }

  private def transform(records: Seq[Record], spec: String) = {
    if (records.isEmpty) {
      records
    } else {
      val parts = spec.split("\\|")
      val res = parts.head match {
        case "geoind" =>
          val epsilon = parts(1).toDouble
          GeoIndistinguishability(epsilon).transform(Trace(records))
        case "smooth" =>
          val epsilon = Distance.parse(parts(1))
          PromesseOp(epsilon).transform(Trace(records))
        case name => throw new IllegalArgumentException(s"Unknown transformation '$name'")
      }
      res.head.records
    }
  }

  private def toGeoJson(records: Seq[Record]) = FeatureCollection(records.map(_.toGeoJson))

  protected def authenticated[T](bearer: Option[String], requiredScopes: Scope*)(fn: AccessToken => T) = {
    bearer.flatMap(firewall.authenticate) match {
      case Some(token) =>
        if (requiredScopes.forall(token.in)) {
          unauthenticated {
            fn(token)
          }
        } else {
          response.unauthorized(
            Error.authenticationError(Some(s"Required scopes ${requiredScopes.mkString(",")}")))
        }
      case None => response.unauthorized(Error.authenticationError(Some("Invalid token")))
    }
  }

  protected def withStore[T](name: String, token: AccessToken)(fn: EventStore => T) = {
    stores.get(name).filter(token.acl.accessible) match {
      case Some(store) => fn(store)
      case None => response.notFound
    }
  }

  protected def unauthenticated(fn: => Any): Any = try {
    fn
  } catch {
    case t: Throwable => errorToResponse(t)
  }

  private def errorToResponse(t: Throwable): Response = t match {
    case ex: IllegalArgumentException =>
      val message = ex.getMessage.stripPrefix("requirement failed:").trim
      response.badRequest(Error.invalidRequest(Some(message)))
    case t: Throwable =>
      Option(t.getCause) match {
        case Some(cause) => errorToResponse(cause)
        case None =>
          logger.error("Error while processing request", t)
          response.internalServerError(Error.apiError(Some(t.getMessage)))
      }
  }
}