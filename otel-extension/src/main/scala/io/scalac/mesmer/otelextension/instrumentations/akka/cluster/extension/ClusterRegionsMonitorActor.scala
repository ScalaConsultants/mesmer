package io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.PreRestart
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.GetShardRegionStats
import akka.cluster.sharding.ShardRegion.ShardRegionStats
import akka.pattern.ask
import akka.util.Timeout
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableDoubleGauge
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS
import scala.jdk.DurationConverters.JavaDurationOps
import scala.util.Try

import io.scalac.mesmer.core.config.ConfigurationUtils.toConfigOps
import io.scalac.mesmer.core.model.AkkaNodeOps
import io.scalac.mesmer.core.model.Region
import io.scalac.mesmer.core.util.CachedQueryResult
import io.scalac.mesmer.otelextension.instrumentations.akka.common.SerializableMessage

object ClusterRegionsMonitorActor extends ClusterMonitorActor {

  private type RegionStats = Map[ShardRegion.ShardId, Int]

  private type RegionStatsMap = Map[String, RegionStats]

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  sealed trait Command extends SerializableMessage

  private val entityPerRegion = meter
    .gaugeBuilder("mesmer_akka_cluster_entities_per_region")
    .setDescription("Amount of entities in a region.")

  private val shardPerRegions = meter
    .gaugeBuilder("mesmer_akka_cluster_shards_per_region")
    .setDescription("Amount of shards in a region.")

  private val entitiesOnNode = meter
    .gaugeBuilder("mesmer_akka_cluster_entities_on_node")
    .setDescription("Amount of entities on a node.")

  private val shardRegionsOnNode = meter
    .gaugeBuilder("mesmer_akka_cluster_shard_regions_on_node")
    .setDescription("Amount of shard regions on a node.")

  override def apply(): Behavior[Command] =
    OnClusterStartup { selfMember =>
      Behaviors.setup { ctx =>
        val system = ctx.system
        import system.executionContext

        val instruments   = mutable.HashSet.empty[ObservableDoubleGauge]
        val node          = selfMember.uniqueAddress.toNode
        val nodeKey       = AttributeKey.stringKey("node")
        val nodeAttribute = Attributes.of(nodeKey, node)

        /**
         * Since measurement.record() uses volatile fields underneath, we can't just .map over future to collect the
         * measurement. We need to Await for the future result and then collect the value. Otherwise the measurement
         * will be dropped by OpenTelemetry SDK.
         */
        def onFutureSuccess(future: Future[Int], timeout: Duration = awaitTimeout())(measurement: Int => Unit) =
          Try(Await.result(future, Duration(1, SECONDS))).map(result => measurement.apply(result))

        def awaitTimeout(): FiniteDuration =
          system.settings.config
            .tryValue("io.scalac.scalac.akka-monitoring.timeouts.await-timeout")(_.getDuration)
            .map(_.toScala)
            .getOrElse(3.seconds)

        val regions = new Regions(
          system,
          onCreateEntry = (region, entry) => {
            val attributes = Attributes.of(AttributeKey.stringKey("region"), region, nodeKey, node)
            instruments.add(entityPerRegion.buildWithCallback { measurement =>
              onFutureSuccess(entry.get.map { regionStats: RegionStats => regionStats.values.sum })(entities =>
                measurement.record(entities, attributes)
              )
            })

            instruments.add(shardPerRegions.buildWithCallback { measurement =>
              onFutureSuccess(entry.get.map(regionStats => regionStats.size))(shards =>
                measurement.record(shards, attributes)
              )
            })
          }
        )

        instruments.add(entitiesOnNode.buildWithCallback { measurement =>
          onFutureSuccess(regions.regionStats().map { regionsStats =>
            regionsStats.view.values.flatMap(_.values).sum
          })(entities => measurement.record(entities, nodeAttribute))
        })

        instruments.add(shardRegionsOnNode.buildWithCallback { measurement =>
          measurement.record(regions.size, nodeAttribute)
        })

        Behaviors.receiveSignal { case (_, PreRestart | PostStop) =>
          instruments.foreach(_.close())
          instruments.clear()
          Behaviors.same
        }
      }
    }

  private[extension] class Regions(
    system: ActorSystem[_],
    onCreateEntry: (Region, CachedQueryResult[Future[RegionStats]]) => Unit
  )(implicit
    ec: ExecutionContext
  ) {

    implicit val queryRegionStatsTimeout: Timeout = Timeout(getQueryStatsTimeout)

    private val sharding = ClusterSharding(system.classicSystem)
    private val logger   = LoggerFactory.getLogger(getClass)
    private val cache    = collection.mutable.HashMap.empty[String, CachedQueryResult[Future[RegionStats]]]

    def size: Int = cache.size

    def regionStats(): Future[RegionStatsMap] = {
      renewEntries()
      val regions = cache.keySet.toSeq
      Future
        .sequence(regions.map(cache(_).get))
        .map(regionStats => regions.zip(regionStats).toMap)
    }

    private def renewEntries(): Unit = {
      val current = sharding.shardTypeNames
      val cached  = cache.keySet
      val coming  = current.diff(cached)
      val leaving = cached.diff(current)
      leaving.foreach(cache.remove)
      coming.foreach(createEntry)
    }

    private def createEntry(region: String): Unit = {
      val entry = CachedQueryResult(runQuery(region))
      cache(region) = entry
      onCreateEntry(region, entry)
    }

    private def runQuery(region: String): Future[RegionStats] = {
      logger.debug("running query for region {}", region)
      (sharding.shardRegion(region) ? GetShardRegionStats)
        .mapTo[ShardRegionStats]
        .flatMap { regionStats =>
          if (regionStats.failed.isEmpty) {
            Future.successful(regionStats.stats)
          } else {
            val shardsFailed = regionStats.failed
            val msg          = s"region $region failed. Shards failed: ${shardsFailed.mkString("(", ",", ")")}"
            logger.warn(msg)
            Future.failed(new RuntimeException(msg))
          }
        }
    }

    private def getQueryStatsTimeout: FiniteDuration =
      system.settings.config
        .tryValue("io.scalac.scalac.akka-monitoring.timeouts.query-region-stats")(_.getDuration)
        .map(_.toScala)
        .getOrElse(2.seconds)
  }
}
