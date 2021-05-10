package io.scalac.mesmer.extension.service

import akka.actor.BootstrapSetup
import akka.actor.ExtendedActorSystem
import akka.actor.setup.ActorSystemSetup
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Props
import akka.actor.typed.SpawnProtocol
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.util.ByteString
import akka.util.Timeout
import akka.{ actor => classic }
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.LoneElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.concurrent.duration._

import io.scalac.mesmer.core.util.ReceptionistOps
import io.scalac.mesmer.core.util.TestCase.NoContextTestCaseFactory
import io.scalac.mesmer.core.util.TestCase.NoSetupTestCaseFactory
import io.scalac.mesmer.core.util.TestCase.ProvidedClassicActorSystemTestCaseFactory
import io.scalac.mesmer.extension.service.ActorInfoService.ActorInfo
import io.scalac.mesmer.extension.service.ActorTreeService.Command.GetActors

trait ActorTreeRoutesProviderTestSettings {
  val ManagementSettings: ManagementRouteProviderSettings = ManagementRouteProviderSettings(Uri.Empty, false)

}

class ActorTreeRoutesProviderTest
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with LoneElement
    with ActorTreeRoutesProviderTestSettings {

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

    Get("/mesmer/actor-tree") ~> sut.routes(ManagementSettings) ~> check {
      status shouldBe (StatusCodes.OK)

      entityAs[ByteString].utf8String should be("null")
    }

  }

  it should "publish actor tree as tree structured json" in {

    val treeInfo = NonEmptyTree.withChildren(ActorInfo("/", "/"))(
      NonEmptyTree
        .withChildren(ActorInfo("/system", "system"))(
          NonEmptyTree(ActorInfo("/system/worker-1", "worker-1")),
          NonEmptyTree(ActorInfo("/system/worker-2", "worker-2"))
        )
    )
    val expectedPayload = """{"/":{"system":{"worker-1":{},"worker-2":{}}}}"""

    val infoService =
      system.settings.setup.get[ActorInfoServiceSetup].get.actorInfoService.asInstanceOf[MutableActorInfoService]

    infoService.actorInfo(treeInfo)

    val sut = new ActorTreeRoutesProvider(system.asInstanceOf[ExtendedActorSystem])

    Get("/mesmer/actor-tree") ~> sut.routes(ManagementSettings) ~> check {
      status shouldBe (StatusCodes.OK)

      entityAs[ByteString].utf8String should be(expectedPayload)
    }

  }
}

class ActorTreeRoutesProviderReceptionistTest
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with LoneElement
    with ActorTreeRoutesProviderTestSettings
    with ReceptionistOps
    with ProvidedClassicActorSystemTestCaseFactory
    with NoSetupTestCaseFactory
    with NoContextTestCaseFactory {

  lazy val config: Config =
    ConfigFactory.parseString("""
                                |io.scalac.akka-monitoring.routes-provider.service-timeout=1 second
                                |""".stripMargin)

  val initialRoutesTimeout: RouteTestTimeout = RouteTestTimeout(2.seconds)

  override def testConfig: Config = config.withFallback(super.testConfig)

  implicit val typedSystem: ActorSystem[_] = system.toTyped
  implicit val akkaTimeout: Timeout        = 2.seconds

  def checkWithTimeout(routes: Route)(implicit routesTimeout: RouteTestTimeout): RouteTestResult =
    Get("/mesmer/actor-tree") ~> routes

  override def setUp(context: Context): Unit = {
    killServices(actorTreeServiceKey)
    noServices(actorTreeServiceKey)
  }

  "ActorTreeRoutesProvider" should "timeout after 1 second" in {
    val sut = new ActorTreeRoutesProvider(system.asInstanceOf[ExtendedActorSystem])

    checkWithTimeout(sut.routes(ManagementSettings))(initialRoutesTimeout) ~> check {
      status shouldBe (StatusCodes.ServiceUnavailable)
    }

  }

  it should "short circuit after initial timeout" in {
    val sut = new ActorTreeRoutesProvider(system.asInstanceOf[ExtendedActorSystem])

    checkWithTimeout(sut.routes(ManagementSettings))(initialRoutesTimeout) ~> check {
      status shouldBe (StatusCodes.ServiceUnavailable)
    }

    val shortTimeout: RouteTestTimeout = RouteTestTimeout(100.millis)

    checkWithTimeout(sut.routes(ManagementSettings))(shortTimeout) ~> check {
      status shouldBe (StatusCodes.ServiceUnavailable)
    }
  }

  it should "publish events receptionist" in {

    val expectedJson = """{"root":{"c":{"cc":{}},"b":{"bb":{}},"a":{"aa":{}}}}"""
    val flatProbe    = TestProbe[ActorRef[SpawnProtocol.Command]]
    val root         = typedSystem.systemActorOf(SpawnProtocol(), "root")

    root ! SpawnProtocol.Spawn(SpawnProtocol(), "a", Props.empty, flatProbe.ref)
    root ! SpawnProtocol.Spawn(SpawnProtocol(), "b", Props.empty, flatProbe.ref)
    root ! SpawnProtocol.Spawn(SpawnProtocol(), "c", Props.empty, flatProbe.ref)

    val firstTier @ Seq(a, b, c) = flatProbe.receiveMessages(3)

    a ! SpawnProtocol.Spawn(SpawnProtocol(), "aa", Props.empty, flatProbe.ref)
    b ! SpawnProtocol.Spawn(SpawnProtocol(), "bb", Props.empty, flatProbe.ref)
    c ! SpawnProtocol.Spawn(SpawnProtocol(), "cc", Props.empty, flatProbe.ref)

    val secondTier = flatProbe.receiveMessages(3)

    val flatRefs: Seq[classic.ActorRef] = (root +: (firstTier ++ secondTier)).map(_.toClassic).sortBy(_.path.name)

    val actorService = typedSystem.systemActorOf(
      Behaviors.receiveMessage[ActorTreeService.Command] { case GetActors(_, reply) =>
        reply ! flatRefs
        Behaviors.same
      },
      createUniqueId
    )

    typedSystem.receptionist ! Receptionist.Register(actorTreeServiceKey, actorService)

    onlyRef(actorService, actorTreeServiceKey)

    val sut = new ActorTreeRoutesProvider(system.asInstanceOf[ExtendedActorSystem])

    checkWithTimeout(sut.routes(ManagementSettings))(initialRoutesTimeout) ~> check {
      status shouldBe (StatusCodes.OK)
      entityAs[ByteString].utf8String should be(expectedJson)
    }
  }
}
