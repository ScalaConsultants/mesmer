package io.scalac.agent.akka.cluster
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.ClusterSharding
import io.scalac.extension.event.ClusterEvent.ShardingRegionInstalled
import io.scalac.extension.event.EventBus
import net.bytebuddy.asm.Advice

class ClusterShardingInterceptor
object ClusterShardingInterceptor {
  val fieldExtractor = {
    val extractor = classOf[ClusterSharding].getDeclaredField("system")
    extractor.setAccessible(true)
    extractor
  }

  @Advice.OnMethodEnter
  def enter(@Advice.Argument(0) typeName: String, @Advice.This thiz: Any): Unit = {
    val system = fieldExtractor.get(thiz).asInstanceOf[ActorSystem]
    EventBus(system.toTyped).publishEvent(ShardingRegionInstalled(typeName))
  }

}
