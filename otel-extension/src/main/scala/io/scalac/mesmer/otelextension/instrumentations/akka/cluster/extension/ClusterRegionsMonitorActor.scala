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
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableDoubleGauge
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.JavaDurationOps

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

  private val entityPerRegion = meter.gaugeBuilder("mesmer_akka_cluster_entities_per_region")

  private val shardPerRegions = meter.gaugeBuilder("mesmer_akka_cluster_shards_per_region")

  private val entitiesOnNode = meter.gaugeBuilder("mesmer_akka_cluster_entities_on_node")

  private val shardRegionsOnNode = meter.gaugeBuilder("mesmer_akka_cluster_shard_regions_on_node")

  override def apply(): Behavior[Command] =
    OnClusterStartup { selfMember =>
      Behaviors.setup { ctx =>
        val system = ctx.system
        import system.executionContext

        val instruments                          = mutable.HashSet.empty[ObservableDoubleGauge]
        val node                                 = selfMember.uniqueAddress.toNode
        val attributesBuilder: AttributesBuilder = Attributes.builder().put("node", node)
        val regions = new Regions(
          system,
          onCreateEntry = (region, entry) => {

            instruments + entityPerRegion.buildWithCallback { measurement =>
              entry.get.foreach { regionStats =>
                val entities   = regionStats.values.sum
                val attributes = attributesBuilder.put("region", region).build()
                measurement.record(entities, attributes)
              }
            }

            instruments + shardPerRegions.buildWithCallback { measurement =>
              entry.get.foreach { regionStats =>
                val shards     = regionStats.size
                val attributes = attributesBuilder.put("region", region).build()
                measurement.record(shards, attributes)
              }
            }
          }
        )

        instruments + entitiesOnNode.buildWithCallback { measurement =>
          regions.regionStats.map { regionsStats =>
            val entities = regionsStats.view.values.flatMap(_.values).sum
            measurement.record(entities, attributesBuilder.build())
          }
        }

        instruments + shardRegionsOnNode.buildWithCallback { measurement =>
          measurement.record(regions.size, attributesBuilder.build())
        }

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

    def regionStats: Future[RegionStatsMap] = {
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
        .getOrElse(2.second)

  }

}
