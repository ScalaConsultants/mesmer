package io.scalac.mesmer.instrumentation.akka.stream

import _root_.io.scalac.mesmer.core.config.MesmerPatienceConfig
import akka.Done
import akka.actor.ActorRef
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.receptionist.Receptionist._
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.stream.scaladsl._
import akka.stream.{ Attributes, BufferOverflowException, OverflowStrategy, QueueOfferResult }
import io.scalac.mesmer.agent.utils.{ OtelAgentTest, SafeLoadSystem }
import io.scalac.mesmer.core.akka.model.PushMetrics
import io.scalac.mesmer.core.util.TestBehaviors.Pass
import io.scalac.mesmer.core.util.TestCase.CommonMonitorTestFactory
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamEvent._
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.{ StreamEvent, StreamService }
import org.scalatest._
import org.scalatest.concurrent.{ Futures, ScalaFutures }
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class AkkaStreamsTest
    extends AnyFlatSpecLike
    with OtelAgentTest
    with Matchers
    with SafeLoadSystem
    with BeforeAndAfterAll
    with BeforeAndAfter
    with LoneElement
    with Inspectors
    with ScalaFutures
    with Futures
    with Inside
    with CommonMonitorTestFactory
    with MesmerPatienceConfig {

  override type Command = StreamEvent

  protected def createMonitorBehavior(implicit context: Context): Behavior[Command] =
    Pass.registerService(StreamService.streamService.serviceKey, monitor.ref)

  protected val serviceKey: ServiceKey[Command] = StreamService.streamService.serviceKey

  type Monitor = TestProbe[StreamEvent]
  protected def createMonitor(implicit system: ActorSystem[_]): Monitor = TestProbe()

  implicit var streamService: ActorStreamRefService = _

  def actors(num: Int)(implicit refService: ActorStreamRefService): Seq[ActorRef] = refService.actors(num)
  def clear(implicit refService: ActorStreamRefService): Unit                     = refService.clear()

  override def beforeAll(): Unit = {
    super.beforeAll() // order is important! actor system must be created
    streamService = new ActorStreamRefService()
    streamService.start()
  }

  after {
    clear
  }

  final class ActorStreamRefService(implicit system: ActorSystem[_]) {
    private val probe = TestProbe[ActorRef]("stream_refs")

    def actors(number: Int): Seq[ActorRef] = {
      val messages = probe.receiveMessages(number, patienceConfig.timeout)
      messages
    }

    sealed trait Command

    private case class Ref(ref: ActorRef) extends Command

    private case object Filter extends Command

    /**
     * Make sure no more actors are created
     */
    def clear(): Unit = {} //probe.expectNoMessage(2.seconds)

    def start(): Unit = system
      .systemActorOf(
        Behaviors.setup[Command] { context =>
          val actorsSoFar = mutable.Set.empty[ActorRef]

          context.system.receptionist ! Register(
            StreamService.streamService.serviceKey,
            context.messageAdapter[StreamEvent] {
              case StreamInterpreterStats(ref, streamName, shellInfo) =>
                println("STATS")
                Ref(ref)
              case LastStreamStats(ref, streamName, shellInfo) =>
                println("LAST")
                Ref(ref)
              case _ => Filter
            }
          )
          Behaviors.receiveMessage { case Ref(ref) =>
            probe.ref ! ref
            println("REF")
            Behaviors.same
          }
        },
        "akka-stream-filter-actor"
      )
  }

  def offerMany[T](input: SourceQueue[T], elements: List[T])(implicit ec: ExecutionContext): Future[Done] = {
    def loop(remaining: List[T]): Future[Done] = remaining match {
      case Nil => Future.successful(Done)
      case head :: tail =>
        input.offer(head).flatMap {
          case QueueOfferResult.Enqueued => loop(tail)
          case _                         => Future.failed(BufferOverflowException(""))
        }
    }
    loop(elements)
  }

  def expectMany[T](output: SinkQueue[T], num: Long)(implicit ec: ExecutionContext): Future[Done] = {
    def loop(remaining: Long): Future[Done] =
      if (remaining <= 0) {
        Future.successful(Done)
      } else
        output
          .pull()
          .flatMap {
            case Some(_) =>
              loop(remaining - 1)
            case None =>
              Future.failed(new RuntimeException("Stream terminated before specified amount of elements was received"))
          }

    loop(num)
  }

  "AkkaStreamAgentTest" should "accurately detect push / demand" in testCase { implicit c =>
    implicit val ec: ExecutionContext = system.executionContext

    val Demand           = 5L
    val ExpectedElements = Demand + 1L // somehow sinkQueue demand 1 element in advance

    val (inputQueue, outputQueue) = Source
      .queue[Int](1024, OverflowStrategy.backpressure, 1)
      .named("InQueue")
      .via(
        Flow[Int]
          .filter(_ % 2 == 0)
          .named("Mod2")
          .map(x => if (x == 0) 0 else x / 2)
          .named("Div2")
      )
      .toMat(Sink.queue[Int]().withAttributes(Attributes.inputBuffer(1, 1)).named("OutQueue"))(Keep.both)
      .run()

    val elements = offerMany(inputQueue, List.tabulate(100)(identity))
      .zipWith(expectMany(outputQueue, Demand))((_, _) => Done)

    whenReady(elements) { _ =>
      val ref = actors(1).loneElement

      ref ! PushMetrics

      val stats = monitor.expectMessageType[StreamInterpreterStats]

      val (stages, connections) = stats.shellInfo.loneElement

      stages should have size 4
      connections should have size 3

      val Array(sinkMap, mapFilter, filterSource) = connections

      sinkMap.push shouldBe ExpectedElements
      sinkMap.pull shouldBe ExpectedElements

      mapFilter.push shouldBe ExpectedElements
      mapFilter.pull shouldBe ExpectedElements

      filterSource.push should be((ExpectedElements * 2) +- 1)
      filterSource.pull should be((ExpectedElements * 2) +- 1)
    }
  }

  it should "find correct amount of actors for async streams" in testCase { implicit c =>
    Source
      .single(())
      .async
      .map(_ => ())
      .async
      .to(Sink.ignore)
      .run()

    actors(3)
  }

  it should "find accurate amount of push / demand for async streams" in testCase { implicit c =>
    implicit val ec: ExecutionContext = system.executionContext

    // seems like all input / output boundaries introduce off by one changes in demand
    val Demand                 = 1L
    val SinkExpectedElements   = Demand + 1L // somehow sinkQueue demand 1 element in advance
    val FlowExpectedElements   = SinkExpectedElements + 1L
    val SourceExpectedElements = (FlowExpectedElements + 1L) * 2

    val (inputQueue, outputQueue) = Source
      .queue[Int](1024, OverflowStrategy.backpressure, 1)
      .withAttributes(Attributes.inputBuffer(1, 1))
      .async
      .filter(_ % 2 == 0)
      .map(x => if (x == 0) 0 else x / 2)
      .withAttributes(Attributes.inputBuffer(1, 1))
      .async
      .toMat(
        Sink
          .queue[Int]()
          .withAttributes(Attributes.inputBuffer(1, 1))
          .named("queueEnd")
      )(Keep.both)
      .run()

    val elements = offerMany(inputQueue, List.tabulate(100)(identity))
      .zipWith(expectMany(outputQueue, Demand))((_, _) => Done)

    whenReady(elements) { _ =>
      // are started in reverse order
      val Seq(sinkRef, flowRef, sourceRef) = actors(3)

      sinkRef ! PushMetrics

      val (sinkStages, sinkConnections) = monitor.expectMessageType[StreamInterpreterStats].shellInfo.loneElement

      sinkStages should have size 2
      sinkConnections should have size 1

      forAll(sinkConnections.toSeq) { connection =>
        connection.push should be(SinkExpectedElements)
        connection.pull should be(SinkExpectedElements)
      }

      flowRef ! PushMetrics

      val (flowStages, flowConnections) = monitor.expectMessageType[StreamInterpreterStats].shellInfo.loneElement

      flowStages should have size 4

      inside(flowConnections) { case Array(filterMap, inputFilter, mapOutput) =>
        filterMap.push should be(FlowExpectedElements)
        filterMap.pull should be(FlowExpectedElements)
        inputFilter.push should be(SourceExpectedElements - 1L)
        inputFilter.pull should be(SourceExpectedElements - 1L)
        mapOutput.push should be(FlowExpectedElements)
        mapOutput.pull should be(FlowExpectedElements)
      }

      sourceRef ! PushMetrics

      val (sourceStages, sourceConnections) = monitor.expectMessageType[StreamInterpreterStats].shellInfo.loneElement

      sourceStages should have size 2

      sourceConnections should have size 1

      forAll(sourceConnections.toSeq) { connection =>
        connection.push should be(SourceExpectedElements)
        connection.pull should be(SourceExpectedElements)
      }
    }
  }

  it should "receive stats of short living streams in" in testCase { implicit c =>
    def runShortStream(): Unit = Source
      .single(())
      .to(Sink.ignore)
      .run()

    val StreamCount = 10

    for {
      _ <- 0 until StreamCount
    } runShortStream()

    actors(StreamCount)

    forAll(monitor.receiveMessages(StreamCount, 10.seconds)) {
      inside(_) { case LastStreamStats(_, _, shellInfo) =>
        val (stages, connectionStats) = shellInfo
        stages should have size 2
        val connection = connectionStats.toSeq.loneElement
        connection.push should be(1L)
        connection.pull should be(1L)
      }
    }
  }

  it should "push information on shells interpreting flatten stream" in testCase { implicit c =>
    implicit val ec: ExecutionContext = system.executionContext

    val Demand = 40L

    val (inputQueue, outputQueue) = Source
      .queue[Int](1024, OverflowStrategy.backpressure, 1)
      .flatMapConcat(element => Source(List.fill(10)(element)).via(Flow[Int].map(_ + 100)))
      .toMat(
        Sink
          .queue[Int]()
          .withAttributes(Attributes.inputBuffer(1, 1))
          .named("queueEnd")
      )(Keep.both)
      .run()

    val elements = offerMany(inputQueue, List.tabulate(100)(identity))
      .zipWith(expectMany(outputQueue, Demand))((_, _) => Done)

    whenReady(elements) { _ =>
      val ref = actors(1).loneElement
      ref ! PushMetrics

      monitor.expectMessageType[StreamInterpreterStats].shellInfo should have size 2
    }
  }

  it should "collect actor count metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_streams_actors")
  }

  it should "collect running streams metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_streams_running_streams")
  }

  it should "collect processed messages metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_stream_processed_messages")
  }

  it should "collect running operators metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_streams_running_operators")
  }

  it should "collect operator demand metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_streams_operator_demand")
  }
}
