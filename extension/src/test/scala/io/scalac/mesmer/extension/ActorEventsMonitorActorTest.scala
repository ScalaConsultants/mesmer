package io.scalac.mesmer.extension

import akka.actor.PoisonPill
import akka.actor.testkit.typed.javadsl.FishingOutcomes
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.util.Timeout
import akka.{ Done, actor => classic }
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.AggMetric.LongValueAggMetric
import io.scalac.mesmer.core.util.TestCase._
import io.scalac.mesmer.core.util.probe.ObserverCollector.ManualCollectorImpl
import io.scalac.mesmer.core.util.{ ActorPathOps, ReceptionistOps, TestConfig, TestOps }
import io.scalac.mesmer.extension.ActorEventsMonitorActor._
import io.scalac.mesmer.extension.actor.{ ActorMetrics, MutableActorMetricStorageFactory }
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor.Labels
import io.scalac.mesmer.extension.service.ActorTreeService
import io.scalac.mesmer.extension.service.ActorTreeService.Command.{ GetActorTree, GetActors }
import io.scalac.mesmer.extension.util.Tree._
import io.scalac.mesmer.extension.util.TreeF
import io.scalac.mesmer.extension.util.probe.ActorMonitorTestProbe
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.{ CounterCommand, MetricObserved, MetricObserverCommand }
import org.scalatest.concurrent.{ PatienceConfiguration, ScaledTimeSpans }
import org.scalatest.{ LoneElement, OptionValues, TestSuite }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NoStackTrace

trait ActorEventMonitorActorTestConfig {
  this: TestSuite with ScaledTimeSpans with ReceptionistOps with PatienceConfiguration =>

  protected val pingOffset: FiniteDuration         = scaled(1.seconds)
  protected val reasonableTime: FiniteDuration     = 3 * pingOffset
  override lazy val patienceConfig: PatienceConfig = PatienceConfig(reasonableTime, scaled(100.millis))
}

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

final class ActorEventsMonitorActorTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with ProvidedActorSystemTestCaseFactory
    with AbstractMonitorTestCaseFactory
    with ScaledTimeSpans
    with LoneElement
    with TestOps
    with ReceptionistOps
    with OptionValues
    with ActorEventMonitorActorTestConfig {

  import ActorEventsMonitorActorTest._

  /**
   * process needed here:
   * - prepare actor refs -
   * - set up actor from context and monitor
   * --
   */

  protected type Setup = ActorEventSetup
  type Monitor         = ActorMonitorTestProbe
  type Context         = TestContext
  final val ActorsPerCase = 10

  val CheckInterval: FiniteDuration = scaled(100.millis)

  private def treeDataReader(tree: Tree[(classic.ActorRef, ActorMetrics)]): ActorMetricsReader = ref => {
    tree.unfix.find(_._1 == ref).map { case (_, data) =>
      data
    }
  }

  private def actorDataReader(actorRef: ActorRef[ReaderCommand])(implicit timeout: Timeout): ActorMetricsReader =
    classicRef => {
      Await.result(actorRef.ask[Option[ActorMetrics]](replyTo => GetOne(classicRef, replyTo)), Duration.Inf)
    }

  private def randomActorMetrics(): ActorMetrics = ActorMetrics(
    Some(Random.nextLong(100)),
    Some(randomAggMetric()),
    Some(Random.nextLong(100)),
    Some(Random.nextLong(100)),
    Some(Random.nextLong(100)),
    Some(randomAggMetric()),
    Some(Random.nextLong(100)),
    Some(Random.nextLong(100)),
    Some(Random.nextLong(100))
  )

  private val addRandomMetrics: Tree[ActorRefDetails] => Tree[(classic.ActorRef, ActorMetrics)] =
    _.unfix.mapValues(details => (details.ref, randomActorMetrics))

  private val constRefsActorServiceTree: Tree[ActorRefDetails] => Behavior[ActorTreeService.Command] = refs =>
    Behaviors.receiveMessagePartial {
      case GetActorTree(reply) =>
        reply ! refs
        Behaviors.same

      case GetActors(Tag.all, reply) =>
        reply ! refs.unfix.toVector.map(_.ref)
        Behaviors.same
      case GetActors(_, reply) =>
        reply ! Seq.empty
        Behaviors.same
    }

  private def countingRefsActorServiceTree(
    monitor: ActorRef[Done]
  ): Tree[ActorRefDetails] => Behavior[ActorTreeService.Command] = refs =>
    Behaviors.receiveMessagePartial { case GetActorTree(reply) =>
      reply ! refs
      monitor ! Done
      Behaviors.same
    }

  private def noReplyActorServiceTree(monitor: ActorRef[Done]): Behavior[ActorTreeService.Command] =
    Behaviors.receiveMessagePartial { case _ =>
      monitor ! Done
      Behaviors.same
    }

  private val ConstActorMetrics: ActorMetrics = ActorMetrics(
    Some(1),
    Some(LongValueAggMetric(1, 10, 2, 20, 10)),
    Some(2),
    Some(3),
    Some(4),
    Some(LongValueAggMetric(10, 100, 20, 200, 10)),
    Some(5),
    Some(6),
    Some(7)
  )

  private def randomAggMetric() = {
    val x     = Random.nextLong(1000)
    val y     = Random.nextLong(1000)
    val sum   = Random.nextLong(1000)
    val count = Random.nextInt(100) + 1
    if (x > y) LongValueAggMetric(y, x, sum / count, sum, count) else LongValueAggMetric(x, y, sum / count, sum, count)
  }

  override implicit val timeout: Timeout = pingOffset

  protected def createMonitor(implicit system: ActorSystem[_]): ActorMonitorTestProbe = ActorMonitorTestProbe(
    new ManualCollectorImpl
  )

  protected def createContextFromMonitor(monitor: ActorMonitorTestProbe)(implicit
    system: ActorSystem[_]
  ): Context = TestContext(
    monitor,
    TestProbe()
  )

  /**
   * Spawns 1 level deep tree
   *
   * @param nodes
   * @return
   */
  private def spawnTree(nodes: Int): Tree[classic.ActorRef] = {

    val spawnRoot  = system.systemActorOf(SpawnProtocol(), createUniqueId)
    val spawnProbe = createTestProbe[ActorRef[_]]()

    for {
      _ <- 0 until nodes
    } spawnRoot ! SpawnProtocol.Spawn(Behaviors.empty, createUniqueId, Props.empty, spawnProbe.ref)

    val children = spawnProbe.receiveMessages(nodes)

    spawnProbe.stop()

    tree(spawnRoot.toClassic, children.map(ch => leaf(ch.toClassic)): _*)
  }

  private def rootGrouping(
    refs: Tree[classic.ActorRef],
    childrenConfiguration: ActorConfiguration = ActorConfiguration.instanceConfig
  ) = refs.unfix.foldRight[Tree[ActorRefDetails]] {
    case TreeF(value, Vector()) => leaf(ActorRefDetails(value, Set.empty, childrenConfiguration))
    case TreeF(value, children) =>
      tree(ActorRefDetails(value, Set.empty, ActorConfiguration.groupingConfig), children: _*)
  }

  private def actorConfiguration(
    refs: Tree[classic.ActorRef],
    configuration: ActorConfiguration
  ) = refs.unfix.foldRight[Tree[ActorRefDetails]] { case TreeF(value, children) =>
    tree(ActorRefDetails(value, Set.empty, configuration), children: _*)
  }

  protected def setUp(c: Context): Setup = {

    val sut = system.systemActorOf(
      Behaviors
        .supervise(
          Behaviors.setup[Command] { context =>
            Behaviors.withTimers[Command] { scheduler =>
              new ActorEventsMonitorActor(
                context,
                monitor(c),
                None,
                pingOffset,
                new MutableActorMetricStorageFactory,
                scheduler,
                c.actorMetricReader
              ).start(c.actorTreeService, loop = false) // false is important here!
            }
          }
        )
        .onFailure(SupervisorStrategy.restart),
      createUniqueId
    )

    ActorEventSetup(c.actorTreeService, sut)
  }

  protected def tearDown(setup: Setup): Unit =
    setup.allRefs.foreach(_.unsafeUpcast[Any] ! PoisonPill)

  def refs(implicit setup: Setup): Seq[classic.ActorRef] = Seq.empty

  def collectData(probe: TestProbe[Done])(implicit setup: Setup): Unit = {
    setup.sut ! StartActorsMeasurement(Some(probe.ref))
    probe.receiveMessage()
  }

  def collectData()(implicit setup: Setup): Unit =
    setup.sut ! StartActorsMeasurement(None)

  def collectAll()(implicit context: Context): Unit = context.monitor.collector.collectAll()

  private def rootLabels(tree: Tree[ActorRefDetails]): Labels =
    Labels(ActorPathOps.getPathString(tree.unfix.value.ref))

  private def nonRootLabels(tree: Tree[ActorRefDetails]): Option[Labels] =
    tree.unfix.foldRight[Option[Labels]] {
      case TreeF(value, Vector()) =>
        Some(Labels(ActorPathOps.getPathString(value.ref)))
      case TreeF(_, children) => Random.shuffle(children.flatten).headOption
    }

  behavior of "ActorEventsMonitor"

  private def expect(
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    labels: Labels,
    value: Long
  )(implicit context: Context): Unit =
    selectProbe(context.monitor)
      .fishForMessage(pingOffset) {
        case MetricObserved(_, `labels`) => FishingOutcomes.complete()
        case _                           => FishingOutcomes.continueAndIgnore()
      }
      .loneElement should be(MetricObserved(value, labels))

  /**
   * Checks aggregation when there were several metrics collection messages but no export in the meantime
   */
  private def collectMany[T](
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    extract: ActorMetrics => T,
    name: String,
    combine: (T, T) => T,
    extractLong: T => Long
  )(metrics: Seq[ActorMetrics]) =
    it should s"record ${name} after many transitions" in {

      val refs         = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val labels       = Labels(ActorPathOps.getPathString(refs.unfix.value.ref))

      val Seq(firstMetrics, metricsTransitions @ _*) = metrics
      val refsWithMetrics = refs.unfix.mapValues { details =>
        details.ref -> firstMetrics
      }
      val metricsService = system.systemActorOf(readerBehavior(refsWithMetrics), createUniqueId)

      val expectedValue = extractLong(metrics.map(extract).reduce(combine))

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = actorDataReader(metricsService))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]

        collectData(ackProbe)

        metricsTransitions.foreach { nextMetrics =>
          metricsService ! ForAll(nextMetrics, ackProbe.ref)
          ackProbe.receiveMessage()
          collectData(ackProbe)
        }
        collectAll()

        expect(selectProbe, labels, expectedValue)
      }
    }

  private def collect[T](
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    extract: ActorMetrics => T,
    name: String,
    combine: (T, T) => T,
    extractLong: T => Long
  ): Unit = {

    it should s"add up ${name} for consecutive collections" in {

      val refs         = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val labels       = Labels(ActorPathOps.getPathString(refs.unfix.value.ref))

      val refsWithMetrics = refs.unfix.mapValues { details =>
        details.ref -> ConstActorMetrics
      }

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]

        collectData(ackProbe)
        collectData(ackProbe)
        collectAll()

        val extracted     = extract(ConstActorMetrics)
        val expectedValue = extractLong(combine(extracted, extracted))

        expect(selectProbe, labels, expectedValue)
      }
    }

    it should s"record ${name}" in {

      val refs         = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val labels       = Labels(ActorPathOps.getPathString(refs.unfix.value.ref))

      val refsWithMetrics = refs.unfix.mapValues { details =>
        details.ref -> ConstActorMetrics
      }

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]

        collectData(ackProbe)
        collectAll()

        expect(selectProbe, labels, extractLong(extract(ConstActorMetrics)))

      }
    }
  }

  private def reportingAggregation[T](
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    extract: ActorMetrics => T,
    name: String,
    combine: (T, T) => T,
    extractLong: T => Long
  )(addMetrics: Tree[ActorRefDetails] => Tree[(classic.ActorRef, ActorMetrics)]): Unit = {

    it should s"aggregate ${name} in root node when child have instance config" in {
      val refs = rootGrouping(spawnTree(3), ActorConfiguration.instanceConfig)

      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val labels       = rootLabels(refs)

      val refsWithMetrics = addMetrics(refs)

      val expectedValue = extractLong(refsWithMetrics.unfix.foldRight[T] { case TreeF((_, metrics), children) =>
        children.fold(extract(metrics))(combine)
      })

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]

        collectData(ackProbe)
        collectAll()

        expect(selectProbe, labels, expectedValue)
      }
    }

    it should s"aggregate ${name} in root node when child have disabled config" in {
      val refs         = rootGrouping(spawnTree(3), ActorConfiguration.disabledConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val labels       = rootLabels(refs)

      val refsWithMetrics = addMetrics(refs)
      val expectedValue = extractLong(refsWithMetrics.unfix.foldRight[T] { case TreeF((_, metrics), children) =>
        children.fold(extract(metrics))(combine)
      })

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]

        collectData(ackProbe)
        collectAll()

        expect(selectProbe, labels, expectedValue)

      }
    }

    it should s"publish ${name} for non-root node when child have instance config" in {
      val refs         = rootGrouping(spawnTree(3), ActorConfiguration.instanceConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val labels       = nonRootLabels(refs).value

      val refsWithMetrics = addMetrics(refs)

      val expectedValue = extractLong(extract(refsWithMetrics.unfix.find { case (ref, _) =>
        ref.path.toStringWithoutAddress == labels.actorPath
      }.map(_._2).value))

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]

        collectData(ackProbe)
        collectAll()

        expect(selectProbe, labels, expectedValue)
      }
    }

    it should s"not publish ${name} for non-root node when child have disabled config" in {
      val refs         = rootGrouping(spawnTree(3), ActorConfiguration.disabledConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val labels       = rootLabels(refs)

      val refsWithMetrics = addMetrics(refs)

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]

        collectData(ackProbe)
        collectAll()

        val probe = selectProbe(context.monitor)

        inside(probe.receiveMessage) { case MetricObserved(_, l) =>
          l should be(labels)
        }
        probe.expectNoMessage()
      }
    }
  }

  def allBehaviorsLong(
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    extract: ActorMetrics => Option[Long],
    name: String
  ): Unit = {
    collect[Option[Long]](selectProbe, extract, name, combine, _.get)
    collectMany[Option[Long]](selectProbe, extract, name, combine, _.get)(Seq.fill(10)(randomActorMetrics()))
    (reportingAggregation[Option[Long]](
      selectProbe,
      extract,
      name,
      combine,
      _.get
    )(addRandomMetrics))

  }

  def allBehaviorsAgg(
    probeAvg: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    probeMin: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    probeMax: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    probeSum: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]],
    extract: ActorMetrics => Option[LongValueAggMetric],
    name: String
  ): Unit = {
    val args
      : List[(String, LongValueAggMetric => Long, ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Labels]])] =
      List(("avg", _.avg, probeAvg), ("min", _.min, probeMin), ("max", _.max, probeMax), ("sum", _.sum, probeSum))
    for {
      (suffix, mapping, probe) <- args
    } {
      collect[Option[LongValueAggMetric]](probe, extract, s"$name $suffix", combineAgg, _.map(mapping).get)
      reportingAggregation[Option[LongValueAggMetric]](
        probe,
        extract,
        s"$name $suffix",
        combineAgg,
        _.map(mapping).get
      )((addRandomMetrics))
      collectMany[Option[LongValueAggMetric]](probe, extract, s"$name $suffix", combineAgg, _.map(mapping).get)(
        Seq.fill(10)(randomActorMetrics())
      )

    }

  }

  private val combine: (Option[Long], Option[Long]) => Option[Long] = (optX, optY) => {
    (for {
      x <- optX
      y <- optY
    } yield (x + y))
  }

  private val combineAgg: (Option[LongValueAggMetric], Option[LongValueAggMetric]) => Option[LongValueAggMetric] =
    (optX, optY) => {
      (for {
        x <- optX
        y <- optY
      } yield x.combine(y))
    }

  it should behave like allBehaviorsLong(_.mailboxSizeProbe, _.mailboxSize, "mailbox size")
  it should behave like allBehaviorsLong(_.receivedMessagesProbe, _.receivedMessages, "received messages")
  it should behave like allBehaviorsLong(_.failedMessagesProbe, _.failedMessages, "failed messages")
  it should behave like allBehaviorsLong(_.sentMessagesProbe, _.sentMessages, "sent messages")
  it should behave like allBehaviorsLong(_.stashSizeProbe, _.stashSize, "stash messages")
  it should behave like allBehaviorsLong(_.droppedMessagesProbe, _.droppedMessages, "dropped messages")
  it should behave like allBehaviorsLong(_.processedMessagesProbe, _.processedMessages, "processed messages")

  it should behave like allBehaviorsAgg(
    _.mailboxTimeAvgProbe,
    _.mailboxTimeMinProbe,
    _.mailboxTimeMaxProbe,
    _.mailboxTimeSumProbe,
    _.mailboxTime,
    "mailbox time"
  )

  it should behave like allBehaviorsAgg(
    _.processingTimeAvgProbe,
    _.processingTimeMinProbe,
    _.processingTimeMaxProbe,
    _.processingTimeSumProbe,
    _.processingTime,
    "processing time"
  )

  it should "unbind monitors on restart" in {
    val failingReader: ActorMetricsReader = _ => throw new RuntimeException("Expected failure") with NoStackTrace
    val refs                              = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
    val actorService                      = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)

    testCaseWithSetupAndContext(_.copy(actorMetricReader = failingReader, actorTreeService = actorService)) {
      implicit setup => implicit context =>
//        val ackProbe = TestProbe[Done]
        collectData()

        eventually {
          monitor.unbinds should be(1)
          monitor.binds should be(2)
      }
    }
  }

  it should "not product any metrics until actroTreeService is availabe" in {
    val refs         = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
    val ackProbe     = TestProbe[Done]
    val actorService = system.systemActorOf(countingRefsActorServiceTree(ackProbe.ref)(refs), createUniqueId)

    testCaseWithSetupAndContext(
      _.copy(actorMetricReader = _ => Some(ConstActorMetrics), actorTreeService = actorService)
    ) { implicit setup => implicit context =>
      collectData()
      ackProbe.receiveMessage()
      collectAll()
      monitor.sentMessagesProbe.expectNoMessage()
    }
  }

  it should "retry to get actor refs" in {
    val ackProbe     = TestProbe[Done]
    val actorService = system.systemActorOf(noReplyActorServiceTree(ackProbe.ref), createUniqueId)

    testCaseWithSetupAndContext(
      _.copy(actorMetricReader = _ => Some(ConstActorMetrics), actorTreeService = actorService)
    ) { implicit setup => implicit context =>
      collectData()
      ackProbe.receiveMessage()
      ackProbe.receiveMessage()
      ackProbe.receiveMessage()
    }
  }

}

object ActorEventsMonitorActorTest {

  final case class ActorEventSetup(
    service: ActorRef[ActorTreeService.Command],
    sut: ActorRef[ActorEventsMonitorActor.Command]
  ) {
    def allRefs: Seq[classic.ActorRef] = Seq(service.toClassic, sut.toClassic)
  }

  private val NoMetricsReader: ActorMetricsReader = _ => None

  final case class TestContext(
    monitor: ActorMonitorTestProbe,
    actorTreeServiceProbe: TestProbe[CounterCommand],
    actorMetricReader: ActorMetricsReader = NoMetricsReader,
    actorTreeService: ActorRef[ActorTreeService.Command] = classic.ActorRef.noSender.toTyped[ActorTreeService.Command]
  )(implicit
    val system: ActorSystem[_]
  ) extends MonitorTestCaseContext[ActorMonitorTestProbe] {

    sealed trait Command

    final case object Open extends Command

    final case object Close extends Command

    final case class Message(text: String) extends Command

    //TODO delete
    object StashActor {
      def apply(capacity: Int): Behavior[Command] =
        Behaviors.withStash(capacity)(buffer => new StashActor(buffer).closed())
    }

    class StashActor(buffer: StashBuffer[Command]) {
      private def closed(): Behavior[Command] =
        Behaviors.receiveMessagePartial {
          case Open =>
            buffer.unstashAll(open())
          case msg =>
            buffer.stash(msg)
            Behaviors.same
        }

      private def open(): Behavior[Command] = Behaviors.receiveMessagePartial {
        case Close =>
          closed()
        case Message(_) =>
          Behaviors.same
      }

    }

  }

  sealed trait ReaderCommand

  final case class GetOne(ref: classic.ActorRef, replyTo: ActorRef[Option[ActorMetrics]]) extends ReaderCommand

  final case class ForAll(metrics: ActorMetrics, replyTo: ActorRef[Done]) extends ReaderCommand

  final case class ForOne(ref: classic.ActorRef, metrics: ActorMetrics, replyTo: ActorRef[Done]) extends ReaderCommand

  def readerBehavior(refs: Tree[(classic.ActorRef, ActorMetrics)]): Behavior[ReaderCommand] = Behaviors.receiveMessage {
    case ForAll(metrics, replyTo) =>
      val nextRefs = refs.unfix.mapValues { case (ref, _) =>
        (ref, metrics)
      }
      replyTo ! Done
      readerBehavior(nextRefs)

    case ForOne(targetRef, metrics, replyTo) =>
      val nextRefs = refs.unfix.mapValues {
        case (ref, _) if ref == targetRef =>
          (ref, metrics)
        case x => x
      }
      replyTo ! Done
      readerBehavior(nextRefs)

    case GetOne(targetRef, replyTo) =>
      replyTo ! refs.unfix.find { case (ref, _) =>
        ref == targetRef
      }.map(_._2)
      Behaviors.same
  }
}
