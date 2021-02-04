package io.scalac.extension

import java.util.concurrent.ForkJoinPool

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import akka.cluster.sharding.ShardRegion.{ GetShardRegionStats, ShardRegionStats }
import akka.pattern.ask
import akka.util.Timeout

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.model.AkkaNodeOps
import io.scalac.extension.config.ConfigurationUtils.ConfigOps

class ClusterRegionsMonitorActor
object ClusterRegionsMonitorActor {

  private type RegionStats    = Map[ShardRegion.ShardId, Int]
  private type RegionStatsMap = Map[String, RegionStats]

  sealed trait Command

  def apply(clusterMetricsMonitor: ClusterMetricsMonitor): Behavior[Command] =
    OnClusterStartUp { selfMember =>
      Behaviors.setup { ctx =>
        import ctx.system

        implicit val queryRegionStatsTimeout: Timeout = Timeout(getQueryStatsTimeout(system.settings.config))
        implicit val ec: ExecutionContext             = ExecutionContext.fromExecutor(new ForkJoinPool)

        val logger   = LoggerFactory.getLogger(classOf[ClusterRegionsMonitorActor])
        val sharding = ClusterSharding(system.classicSystem)
        val monitor  = clusterMetricsMonitor.bind(selfMember.uniqueAddress.toNode)

        def queryAllRegionsStats(andThen: RegionStatsMap => _): Unit = {
          val regions = sharding.shardTypeNames.toSeq
          Future
            .sequence(
              regions.map(queryOneRegionStats)
            )
            .onComplete {
              case Success(regionStats) =>
                andThen(regions.zip(regionStats).toMap)
              case Failure(ex) =>
                logger.warn("Failed to query region stats", ex)
            }
        }

        def queryOneRegionStats(region: String): Future[RegionStats] =
          (sharding.shardRegion(region) ? GetShardRegionStats)
            .mapTo[ShardRegionStats]
            .flatMap { regionStats =>
              if (regionStats.failed.isEmpty) {
                Future.successful(regionStats.stats)
              } else {
                val shardsFailed = regionStats.failed
                val msg          = s"region $region failed. Shards failed: ${shardsFailed.mkString("(", ",", ")")}"
                Future.failed(new RuntimeException(msg))
              }
            }

        monitor.entitiesOnNode.setUpdater { result =>
          queryAllRegionsStats { regionsStats =>
            val entities = regionsStats.view.values.flatMap(_.values).sum
            result.observe(entities)
            logger.trace("Recorded amount of entities on node {}", entities)
          }
        }

        monitor.shardRegionsOnNode.setUpdater { result =>
          val regions = sharding.shardTypeNames.size
          result.observe(regions)
          logger.trace("Recorded amount of regions on node {}", regions)
        }

        sharding.shardTypeNames.foreach { region =>
          val cachedResult = CachedQueryResult({
            logger.debug(s"running query for region $region")
            queryOneRegionStats(region)
          })

          monitor
            .entityPerRegion(region)
            .setUpdater(result =>
              cachedResult.get.foreach { regionStats =>
                val entities = regionStats.values.sum
                result.observe(entities)
                logger.trace("Recorded amount of entities per region {}", entities)
              }
            )

          monitor
            .shardPerRegions(region)
            .setUpdater(result =>
              cachedResult.get.foreach { regionStats =>
                val shards = regionStats.size
                result.observe(shards)
                logger.trace("Recorded amount of shards per region {}", shards)
              }
            )
        }

        Behaviors.same
      }

    }

  private def getQueryStatsTimeout(config: Config): FiniteDuration =
    config
      .tryValue("io.scalac.scalac.akka-monitoring.timeouts.query-region-stats")(_.getDuration)
      .map(d => FiniteDuration(d.toMillis, MILLISECONDS))
      .getOrElse(2.second)

  // TODO It might be useful for other components in future
  class CachedQueryResult[T] private (q: => T, validBy: FiniteDuration = 1.second) { self =>
    private val validByNanos: Long       = validBy.toNanos
    private var lastUpdate: Option[Long] = None
    private var currentValue: Option[T]  = None

    def get: T = {
      self.synchronized {
        if (needUpdate) {
          lastUpdate = Some(now)
          currentValue = Some(q)
        }
      }
      currentValue.get
    }

    private def needUpdate: Boolean = lastUpdate.fold(true)(lu => now > (lu + validByNanos))
    private def now: Long           = System.nanoTime()
  }
  object CachedQueryResult {
    def apply[T](q: => T): CachedQueryResult[T]                       = new CachedQueryResult(q)
    def by[T](validBy: FiniteDuration)(q: => T): CachedQueryResult[T] = new CachedQueryResult(q, validBy)
  }

}
