package io.scalac.mesmer.extension.service

import akka.actor.setup.ActorSystemSetup
import akka.actor.{ BootstrapSetup, ExtendedActorSystem }
import akka.http.scaladsl.model.{ StatusCodes, Uri }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.util.ByteString
import akka.{ actor => classic }
import io.scalac.mesmer.extension.service.ActorInfoService.ActorInfo
import org.scalatest.LoneElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class ActorTreeRoutesProviderTest extends AnyFlatSpec with Matchers with ScalatestRouteTest with LoneElement {

  val ManagementSettings = ManagementRouteProviderSettings(Uri.Empty, false)

  final class MutableActorInfoService() extends ActorInfoService {

    @volatile
    private var _info: Option[NonEmptyTree[ActorInfo]] = None

    def actorInfo(tree: NonEmptyTree[ActorInfo]): Unit = _info = Some(tree)

    def actorTree: Future[Option[NonEmptyTree[ActorInfo]]] = Future.successful(_info)
  }

  override protected def createActorSystem(): classic.ActorSystem = {
    val actorSystemSetup = ActorSystemSetup(BootstrapSetup.create(testConfig))
      .withSetup(new ActorInfoServiceSetup(new MutableActorInfoService()))
    classic.ActorSystem(actorSystemNameFrom(getClass), actorSystemSetup)
  }

  "ActorTreeRoutesProvider" should "publish actor tree when service is available" in {

    val infoService =
      system.settings.setup.get[ActorInfoServiceSetup].get.actorInfoService.asInstanceOf[MutableActorInfoService]

    val sut = new ActorTreeRoutesProvider(system.asInstanceOf[ExtendedActorSystem])

    Get() ~> sut.routes(ManagementSettings) ~> check {
      status shouldBe (StatusCodes.OK)

      entityAs[ByteString].utf8String should be("[]")
    }

  }

  it should "respond with 503 when actor service is unavailable" in {}

  it should "publish actor tree as tree structured json" in {

    val treeInfo = NonEmptyTree.withChildren(ActorInfo("/"))(
      NonEmptyTree
        .withChildren(ActorInfo("system"))(NonEmptyTree(ActorInfo("worker-1")), NonEmptyTree(ActorInfo("worker-2")))
    )
    val expectedPayload = """{"/":{"system":{"worker-1":{},"worker-2":{}}}}"""

    val infoService =
      system.settings.setup.get[ActorInfoServiceSetup].get.actorInfoService.asInstanceOf[MutableActorInfoService]

    infoService.actorInfo(treeInfo)

    val sut = new ActorTreeRoutesProvider(system.asInstanceOf[ExtendedActorSystem])

    Get() ~> sut.routes(ManagementSettings) ~> check {
      status shouldBe (StatusCodes.OK)

      entityAs[ByteString].utf8String should be(expectedPayload)
    }

  }

}
