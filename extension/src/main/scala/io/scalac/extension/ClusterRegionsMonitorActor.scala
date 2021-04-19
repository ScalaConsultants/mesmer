package io.scalac.extension

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
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.DurationConverters.JavaDurationOps

import io.scalac.core.model._
import io.scalac.core.util.CachedQueryResult
import io.scalac.extension.config.ConfigurationUtils._
import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.metric.ClusterMetricsMonitor.Labels

class ClusterRegionsMonitorActor
object ClusterRegionsMonitorActor extends ClusterMonitorActor {

  private type RegionStats    = Map[ShardRegion.ShardId, Int]
  private type RegionStatsMap = Map[String, RegionStats]

  sealed trait Command

  private val logger = LoggerFactory.getLogger(classOf[ClusterRegionsMonitorActor])

  def apply(monitor: ClusterMetricsMonitor): Behavior[Command] =
    OnClusterStartUp { selfMember =>
      Behaviors.setup { ctx =>
        val system = ctx.system
        import system.executionContext

        val node         = selfMember.uniqueAddress.toNode
        val labels       = Labels(node)
        val boundMonitor = monitor.bind(labels)

        val regions = new Regions(
          system,
          onCreateEntry = (region, entry) => {

            boundMonitor.entityPerRegion
              .setUpdater(result =>
                entry.get.foreach { regionStats =>
                  val entities = regionStats.values.sum
                  result.observe(entities, Labels(node, Some(region)))
                  logger.trace("Recorded amount of entities per region {}", entities)
                }
              )

            boundMonitor.shardPerRegions
              .setUpdater(result =>
                entry.get.foreach { regionStats =>
                  val shards = regionStats.size
                  result.observe(shards, Labels(node, Some(region)))
                  logger.trace("Recorded amount of shards per region {}", shards)
                }
              )
          }
        )

        boundMonitor.entitiesOnNode.setUpdater { result =>
          regions.regionStats.map { regionsStats =>
            val entities = regionsStats.view.values.flatMap(_.values).sum
            result.observe(entities, labels)
            logger.trace("Recorded amount of entities on node {}", entities)
          }
        }

        boundMonitor.shardRegionsOnNode.setUpdater { result =>
          result.observe(regions.size, labels)
          logger.trace("Recorded amount of regions on node {}", regions)
        }

        Behaviors.receiveSignal { case (ctx, PreRestart | PostStop) =>
          boundMonitor.unbind()
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
