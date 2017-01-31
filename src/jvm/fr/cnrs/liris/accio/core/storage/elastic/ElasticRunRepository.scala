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

package fr.cnrs.liris.accio.core.storage.elastic

import com.google.inject.Singleton
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.analyzers.KeywordAnalyzer
import com.sksamuel.elastic4s.mappings.MappingDefinition
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import com.twitter.finatra.json.FinatraObjectMapper
import com.typesafe.scalalogging.StrictLogging
import fr.cnrs.liris.accio.core.domain._
import fr.cnrs.liris.accio.core.storage.{LogsQuery, MutableRunRepository, RunList, RunQuery}
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.search.sort.SortOrder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.language.reflectiveCalls
import scala.util.control.NonFatal

/**
 * Run repository persisting data into an Elasticsearch cluster.
 *
 * @param mapper       Finatra object mapper.
 * @param client       Elasticsearch client.
 * @param prefix       Prefix of indices managed by Accio.
 * @param queryTimeout Timeout of queries sent to Elasticsearch.
 */
@Singleton
final class ElasticRunRepository(
  mapper: FinatraObjectMapper,
  client: ElasticClient,
  prefix: String,
  queryTimeout: Duration)
  extends MutableRunRepository with StrictLogging {

  initializeRunsIndex()
  initializeLogsIndex()

  override def find(query: RunQuery): RunList = {
    var q = boolQuery()
    query.owner.foreach { owner =>
      q = q.must(termQuery("owner.name", owner))
    }
    if (query.status.nonEmpty) {
      val qs = query.status.map(v => termQuery("state.status", v.value))
      q = q.should(qs).minimumShouldMatch(1)
    }
    query.workflow.foreach { workflowId =>
      q = q.filter(termQuery("pkg.workflow_id.value", workflowId.value))
    }
    query.name.foreach { name =>
      q = q.must(matchQuery("name", name))
    }
    query.clonedFrom.foreach { clonedFrom =>
      q = q.filter(termQuery("cloned_from.value", clonedFrom.value))
    }
    query.parent match {
      case Some(parent) => q = q.filter(termQuery("parent.value", parent.value))
      case None => q = q.filter(not(existsQuery("parent")))
    }

    val s = search(runsIndex / runsType)
      .query(q)
      .sourceExclude("state.nodes.result")
      .sortBy(fieldSort("created_at").order(SortOrder.DESC))
      .limit(query.limit.getOrElse(10000)) // Max limit defaults to 10000.
      .from(query.offset.getOrElse(0))

    val f = client.execute(s).map { resp =>
      val results = resp.hits.toSeq.map(hit => mapper.parse[Run](hit.sourceAsBytes))
      RunList(results, resp.totalHits.toInt)
    }.recover {
      case _: IndexNotFoundException => RunList(Seq.empty, 0)
      case NonFatal(e) =>
        logger.error("Error while searching runs", e)
        RunList(Seq.empty, 0)
    }
    Await.result(f, queryTimeout)
  }

  override def find(query: LogsQuery): Seq[RunLog] = {
    var q = boolQuery()
      .filter(termQuery("run_id.value", query.runId.value))
      .filter(termQuery("node_name", query.nodeName))
    query.classifier.foreach { classifier =>
      q = q.filter(termQuery("classifier", classifier))
    }
    query.since.foreach { since =>
      q = q.filter(rangeQuery("created_at").from(since.inMillis).includeLower(false))
    }

    val s = search(logsIndex / logsType)
      .query(q)
      .sortBy(fieldSort("created_at"))
      // https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html
      .limit(query.limit.getOrElse(10000))

    val f = client.execute(s).map { resp =>
      resp.hits.toSeq.map(hit => mapper.parse[RunLog](hit.sourceAsBytes))
    }.recover {
      case _: IndexNotFoundException => Seq.empty
      case NonFatal(e) =>
        logger.error("Error while searching logs", e)
        Seq.empty
    }
    Await.result(f, queryTimeout)
  }

  override def save(run: Run): Unit = {
    val json = mapper.writeValueAsString(run)
    val f = client.execute {
      indexInto(runsIndex / runsType).id(run.id.value).source(json)
    }
    f.onSuccess {
      case _ => logger.debug(s"Saved run ${run.id.value}")
    }
    f.onFailure {
      case e: Throwable => logger.error(s"Error while saving run ${run.id.value}", e)
    }
    Await.ready(f, queryTimeout)
  }

  override def save(logs: Seq[RunLog]): Unit = {
    val actions = logs.map { log =>
      val json = mapper.writeValueAsString(log)
      indexInto(logsIndex / logsType).source(json)
    }
    val f = client.execute(bulk(actions))
    f.onSuccess {
      case resp => logger.debug(s"Saved ${resp.successes.size}/${logs.size} logs")
    }
    f.onFailure {
      case e: Throwable => logger.error(s"Error while saving ${logs.size} logs", e)
    }
    Await.ready(f, queryTimeout)
  }

  override def get(id: RunId): Option[Run] = {
    val f = client.execute(ElasticDsl.get(id.value).from(runsIndex / runsType))
      .map { resp =>
        if (resp.isSourceEmpty) {
          None
        } else {
          Some(mapper.parse[Run](resp.sourceAsString))
        }
      }
      .recover {
        case _: IndexNotFoundException => None
        case e: Throwable =>
          logger.error(s"Error while retrieving run ${id.value}", e)
          None
      }
    Await.result(f, queryTimeout)
  }

  override def contains(id: RunId): Boolean = {
    val f = client.execute(ElasticDsl.get(id.value).from(runsIndex / runsType))
      .map(resp => !resp.isSourceEmpty)
      .recover {
        case _: IndexNotFoundException => false
        case e: Throwable =>
          logger.error(s"Error while retrieving run ${id.value}", e)
          false
      }
    Await.result(f, queryTimeout)
  }

  override def remove(id: RunId): Unit = {
    val fs = Future.sequence(Seq(
      client.execute(delete(id.value).from(runsIndex / runsType)),
      client.execute(deleteIn(logsIndex).by(termQuery("id.value", id.value)))))
    fs.onSuccess {
      case _ => logger.debug(s"Removed run ${id.value}")
    }
    Await.ready(fs, queryTimeout)
  }

  override def get(cacheKey: CacheKey): Option[OpResult] = {
    val q = nestedQuery("state.nodes")
      .query(termQuery("state.nodes.cache_key.hash", cacheKey.hash))
      .scoreMode(ScoreMode.None)
    val s = search(runsIndex / runsType).query(q).size(1)
    val f = client.execute(s).map { resp =>
      if (resp.totalHits > 0) {
        val run = mapper.parse[Run](resp.hits.head.sourceAsString)
        run.state.nodes.find(_.cacheKey.contains(cacheKey)).get.result
      } else {
        None
      }
    }.recover {
      case _: IndexNotFoundException => None
      case e: Throwable =>
        logger.error(s"Error while retrieving cached result ${cacheKey.hash}", e)
        None
    }
    Await.result(f, queryTimeout)
  }

  private def runsIndex = s"${prefix}runs"

  private def logsIndex = s"${prefix}logs"

  private def runsType = "default"

  private def logsType = "default"

  private def initializeRunsIndex() = {
    val f = client.execute(indexExists(runsIndex)).flatMap { resp =>
      if (!resp.isExists) {
        // Some fields must absolutely be indexed with the keyword analyzer, which performs no tokenization at all,
        // otherwise they won't be searchable by their exact value (which can be annoying, e.g., for ids).
        val fields = Seq(
          objectField("id").as(textField("value").analyzer(KeywordAnalyzer)),
          objectField("pkg").as(
            objectField("workflow_id").as(textField("value").analyzer(KeywordAnalyzer)),
            textField("version").analyzer(KeywordAnalyzer)
          ),
          objectField("parent").as(textField("value").analyzer(KeywordAnalyzer)),
          objectField("cloned_from").as(textField("value").analyzer(KeywordAnalyzer)),
          longField("created_at"),
          objectField("params").enabled(false),
          objectField("state").as(
            nestedField("nodes").as(
              textField("name").analyzer(KeywordAnalyzer),
              objectField("cache_key").as(textField("hash").analyzer(KeywordAnalyzer)),
              objectField("result").enabled(false)
            )
          ))
        logger.info(s"Creating $runsIndex/$runsType index")
        client.execute(createIndex(runsIndex).mappings(new MappingDefinition(runsType) as (fields: _*)))
      } else {
        Future.successful(true)
      }
    }
    f.onFailure { case NonFatal(e) => logger.error("Failed to initialize runs index", e) }
    Await.ready(f, queryTimeout)
  }

  private def initializeLogsIndex() = {
    val f = client.execute(indexExists(logsIndex)).flatMap { resp =>
      if (!resp.isExists) {
        // Some fields must absolutely be indexed with the keyword analyzer, which performs no tokenization at all,
        // otherwise they won't be searchable by their exact value (which can be annoying, e.g., for ids).
        val fields = Seq(
          objectField("run_id").as(textField("value").analyzer(KeywordAnalyzer)),
          textField("node_name").analyzer(KeywordAnalyzer),
          longField("created_at"),
          textField("classifier").analyzer(KeywordAnalyzer))
        logger.info(s"Creating $logsIndex/$logsType index")
        client.execute(createIndex(logsIndex).mappings(new MappingDefinition(logsType) as (fields: _*)))
      } else {
        Future.successful(true)
      }
    }
    f.onFailure { case NonFatal(e) => logger.error("Failed to initialize logs index", e) }
    Await.ready(f, queryTimeout)
  }
}