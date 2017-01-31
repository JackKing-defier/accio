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

package fr.cnrs.liris.accio.core.storage.inject

import com.google.inject.{Provides, Singleton}
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.twitter.util.{Duration => TwitterDuration}
import fr.cnrs.liris.accio.core.storage.elastic.{ElasticRunRepository, ElasticWorkflowRepository, ObjectMapperFactory}
import fr.cnrs.liris.accio.core.storage.{MutableRunRepository, MutableWorkflowRepository}
import net.codingwell.scalaguice.ScalaModule
import org.elasticsearch.common.settings.Settings

import scala.concurrent.duration.{Duration => ScalaDuration}

/**
 * Elastic storage configuration.
 *
 * @param addr         Address(es) to Elasticsearch cluster members ("hostname:port[,hostname:port...]").
 * @param prefix       Prefix of indices managed by Accio.
 * @param queryTimeout Timeout of queries sent to Elasticsearch.
 */
case class ElasticStorageConfig(addr: String, prefix: String, queryTimeout: TwitterDuration)

/**
 * Guice module provisioning repositories with storage inside an Elasticsearch cluster.
 *
 * @param config Configuration.
 */
final class ElasticStorageModule(config: ElasticStorageConfig) extends ScalaModule {
  override def configure(): Unit = {}

  @Singleton
  @Provides
  def providesRunRepository(mapperFactory: ObjectMapperFactory): MutableRunRepository = {
    val mapper = mapperFactory.create()
    new ElasticRunRepository(mapper, client, config.prefix, ScalaDuration.fromNanos(config.queryTimeout.inNanoseconds))
  }

  @Singleton
  @Provides
  def providesWorkflowRepository(mapperFactory: ObjectMapperFactory): MutableWorkflowRepository = {
    val mapper = mapperFactory.create()
    new ElasticWorkflowRepository(mapper, client, config.prefix, ScalaDuration.fromNanos(config.queryTimeout.inNanoseconds))
  }

  private lazy val client = {
    val settings = Settings.builder()
      //.put("client.transport.sniff", true)
      .put("cluster.name", "elasticsearch")
      .build()
    val uri = ElasticsearchClientUri(s"elasticsearch://${config.addr}")
    val client = ElasticClient.transport(settings, uri)
    client
  }
}