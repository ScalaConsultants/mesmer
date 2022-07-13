package io.scalac.mesmer.extension

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.PoisonPill
import akka.actor.testkit.typed.javadsl.FishingOutcomes
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import akka.{ actor => classic }
import org.scalatest.LoneElement
import org.scalatest.OptionValues
import org.scalatest.TestSuite
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScaledTimeSpans

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NoStackTrace

import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.MinMaxSumCountAggregation.LongMinMaxSumCountAggregationImpl
import io.scalac.mesmer.core.util.TestCase._
import io.scalac.mesmer.core.util.TestConfig
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.core.util._
import io.scalac.mesmer.core.util.probe.ObserverCollector.ManualCollectorImpl
import io.scalac.mesmer.extension.ActorEventsMonitorActor._
import io.scalac.mesmer.extension.actor.ActorMetrics
import io.scalac.mesmer.extension.actor.MutableActorMetricStorageFactory
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor.Attributes
import io.scalac.mesmer.extension.service.ActorTreeService
import io.scalac.mesmer.extension.service.ActorTreeService.Command.GetActorTree
import io.scalac.mesmer.extension.service.ActorTreeService.Command.TagSubscribe
import io.scalac.mesmer.extension.util.Tree._
import io.scalac.mesmer.extension.util.TreeF
import io.scalac.mesmer.extension.util.probe.ActorMonitorTestProbe
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.CounterCommand
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.MetricObserved
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.MetricObserverCommand

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

  protected type Setup = ActorEventSetup
  type Monitor         = ActorMonitorTestProbe
  type Context         = TestContext
  final val ActorsPerCase = 10

  val CheckInterval: FiniteDuration = scaled(100.millis)

  private def treeDataReader(tree: Tree[(classic.ActorRef, ActorMetrics)]): ActorMetricsReader = ref =>
    tree.unfix.find(_._1 == ref).map { case (_, data) =>
      data
    }

  private def actorDataReader(actorRef: ActorRef[ReaderCommand])(implicit timeout: Timeout): ActorMetricsReader =
    classicRef => Await.result(actorRef.ask[Option[ActorMetrics]](replyTo => GetOne(classicRef, replyTo)), Duration.Inf)

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
    _.unfix.mapValues(details => (details.ref, randomActorMetrics()))

  private val constRefsActorServiceTree: Tree[ActorRefDetails] => Behavior[ActorTreeService.Command] = refs =>
    Behaviors.receiveMessagePartial {
      case GetActorTree(reply) =>
        reply ! refs
        Behaviors.same
      case TagSubscribe(_, _) =>
        Behaviors.same
    }

  /**
   * Create Actor that given
   */
  private def subscribeAllConstRefsActorServiceTree(
    terminated: Vector[ActorRefDetails]
  ): Tree[ActorRefDetails] => Behavior[ActorTreeService.Command] = refs =>
    Behaviors.receiveMessagePartial {
      case GetActorTree(reply) =>
        reply ! refs
        Behaviors.same
      case TagSubscribe(_, reply) =>
        terminated.foreach(reply.tell)
        Behaviors.same
    }

  private def countingRefsActorServiceTree(
    monitor: ActorRef[Done]
  ): Tree[ActorRefDetails] => Behavior[ActorTreeService.Command] = refs =>
    Behaviors.receiveMessagePartial { case GetActorTree(reply) =>
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
    Some(LongMinMaxSumCountAggregationImpl(1, 10, 20, 10)),
    Some(2),
    Some(3),
    Some(4),
    Some(LongMinMaxSumCountAggregationImpl(10, 100, 200, 10)),
    Some(5),
    Some(6),
    Some(7)
  )

  private def randomAggMetric() = {
    val x     = Random.nextLong(1000)
    val y     = Random.nextLong(1000)
    val sum   = Random.nextLong(1000)
    val count = Random.nextInt(100) + 1
    if (x > y) LongMinMaxSumCountAggregationImpl(y, x, sum, count)
    else LongMinMaxSumCountAggregationImpl(x, y, sum, count)
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

  private def spawnWithChildren(children: Int): (classic.ActorRef, Seq[classic.ActorRef]) = {
    val spawnRoot  = system.systemActorOf(SpawnProtocol(), createUniqueId)
    val spawnProbe = createTestProbe[ActorRef[_]]()

    for {
      _ <- 0 until children
    } spawnRoot ! SpawnProtocol.Spawn(Behaviors.empty, createUniqueId, Props.empty, spawnProbe.ref)

    val childrenRefs = spawnProbe.receiveMessages(children)

    spawnProbe.stop()
    (spawnRoot.toClassic, childrenRefs.map(_.toClassic))
  }

  /**
   * Spawns 1 level deep tree
   *
   * @param nodes
   * @return
   */
  private def spawnTree(children: Int): Tree[classic.ActorRef] = {
    val (root, childrenRefs) = spawnWithChildren(children)

    tree(root, childrenRefs.map(leaf): _*)
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

  def collectActorMetrics(probe: TestProbe[Done])(implicit setup: Setup): Unit = {
    setup.sut ! StartActorsMeasurement(Some(probe.ref))
    probe.receiveMessage()
  }

  def collectActorMetrics()(implicit setup: Setup): Unit =
    setup.sut ! StartActorsMeasurement(None)

  def runUpdaters()(implicit context: Context): Unit = context.monitor.collector.collectAll()

  private def rootLabels(tree: Tree[ActorRefDetails]): Attributes =
    Attributes(ActorPathOps.getPathString(tree.unfix.value.ref))

  private def nonRootLabels(tree: Tree[ActorRefDetails]): Option[Attributes] =
    tree.unfix.foldRight[Option[Attributes]] {
      case TreeF(value, Vector()) =>
        Some(Attributes(ActorPathOps.getPathString(value.ref)))
      case TreeF(_, children) => Random.shuffle(children.flatten).headOption
    }

  behavior of "ActorEventsMonitor"

  private def expect(
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    attributes: Attributes,
    value: Long
  )(implicit context: Context): Unit =
    selectProbe(context.monitor)
      .fishForMessage(pingOffset) {
        case MetricObserved(_, `attributes`) => FishingOutcomes.complete()
        case _                               => FishingOutcomes.continueAndIgnore()
      }
      .loneElement should be(MetricObserved(value, attributes))

  /**
   * Checks aggregation when there were several metrics collection messages but no export in the meantime
   */
  private def collectMany[T](
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    extract: ActorMetrics => T,
    name: String,
    addTo: (T, T) => T,
    extractLong: T => Long
  )(metrics: Seq[ActorMetrics]) =
    it should s"record ${name} after many transitions" in {

      val refs         = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val attributes   = Attributes(ActorPathOps.getPathString(refs.unfix.value.ref))

      val Seq(firstMetrics, metricsTransitions @ _*) = metrics
      val refsWithMetrics = refs.unfix.mapValues { details =>
        details.ref -> firstMetrics
      }
      val metricsService = system.systemActorOf(readerBehavior(refsWithMetrics), createUniqueId)

      val expectedValue = extractLong(metrics.map(extract).reduce(addTo))

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = actorDataReader(metricsService))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]()

        collectActorMetrics(ackProbe)

        metricsTransitions.foreach { nextMetrics =>
          metricsService ! ForAll(nextMetrics, ackProbe.ref)
          ackProbe.receiveMessage()
          collectActorMetrics(ackProbe)
        }
        runUpdaters()

        expect(selectProbe, attributes, expectedValue)
      }
    }

  private def collect[T](
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    extract: ActorMetrics => Long,
    name: String
  ): Unit =
    it should s"record ${name} value" in {

      val refs         = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val attributes   = Attributes(ActorPathOps.getPathString(refs.unfix.value.ref))

      val refsWithMetrics = refs.unfix.mapValues { details =>
        details.ref -> ConstActorMetrics
      }

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]()

        collectActorMetrics(ackProbe)
        runUpdaters()

        expect(selectProbe, attributes, extract(ConstActorMetrics))

      }
    }

  private def reportingAggregation[T](
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    extract: ActorMetrics => T,
    name: String,
    sum: (T, T) => T,
    combineWithExisting: (T, T) => T,
    extractLong: T => Long
  )(addMetrics: Tree[ActorRefDetails] => Tree[(classic.ActorRef, ActorMetrics)]): Unit = {

    it should s"aggregate ${name} in root node when child have instance config" in {
      val refs = rootGrouping(spawnTree(3), ActorConfiguration.instanceConfig)

      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val attributes   = rootLabels(refs)

      val refsWithMetrics = addMetrics(refs)

      val expectedValue = extractLong(refsWithMetrics.unfix.foldRight[T] { case TreeF((_, metrics), children) =>
        children.fold(extract(metrics))(sum)
      })

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]()

        collectActorMetrics(ackProbe)
        runUpdaters()

        expect(selectProbe, attributes, expectedValue)
      }
    }

    it should s"aggregate ${name} in root node when child have disabled config" in {
      val refs         = rootGrouping(spawnTree(3), ActorConfiguration.disabledConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val attributes   = rootLabels(refs)

      val refsWithMetrics = addMetrics(refs)
      val expectedValue = extractLong(refsWithMetrics.unfix.foldRight[T] { case TreeF((_, metrics), children) =>
        children.fold(extract(metrics))(sum)
      })

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]()

        collectActorMetrics(ackProbe)
        runUpdaters()

        expect(selectProbe, attributes, expectedValue)

      }
    }

    it should s"publish ${name} for non-root node when child have instance config" in {
      val refs         = rootGrouping(spawnTree(3), ActorConfiguration.instanceConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val attributes   = nonRootLabels(refs).value

      val refsWithMetrics = addMetrics(refs)

      val expectedValue = extractLong(extract(refsWithMetrics.unfix.find { case (ref, _) =>
        ref.path.toStringWithoutAddress == attributes.actorPath
      }.map(_._2).value))

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]()

        collectActorMetrics(ackProbe)
        runUpdaters()

        expect(selectProbe, attributes, expectedValue)
      }
    }

    it should s"not publish ${name} for non-root node when child have disabled config" in {
      val refs         = rootGrouping(spawnTree(3), ActorConfiguration.disabledConfig)
      val actorService = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)
      val attributes   = rootLabels(refs)

      val refsWithMetrics = addMetrics(refs)

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]()

        collectActorMetrics(ackProbe)
        runUpdaters()

        val probe = selectProbe(context.monitor)

        inside(probe.receiveMessage()) { case MetricObserved(_, l) =>
          l should be(attributes)
        }
        probe.expectNoMessage()
      }
    }

    it should s"add ${name} terminated actors to grouping parent" in {

      val allRefs = rootGrouping(spawnTree(3), ActorConfiguration.disabledConfig)

      val terminatingRefs = allRefs.unfix.inner.flatMap(_.unfix.toVector)
      val rootRef         = leaf(allRefs.unfix.value)

      val actorService =
        system.systemActorOf(subscribeAllConstRefsActorServiceTree(terminatingRefs)(rootRef), createUniqueId)

      val attributes = rootLabels(allRefs)

      val refsWithMetrics = addMetrics(allRefs)

      val expectedValue = extractLong(refsWithMetrics.unfix.foldRight[T] { case TreeF((_, metrics), children) =>
        children.fold(extract(metrics))(sum)
      })

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]()

        collectActorMetrics(ackProbe)
        runUpdaters()

        expect(selectProbe, attributes, expectedValue)
      }
    }

    it should s"add ${name} terminated actors to grouping parent only once" in {

      val allRefs = rootGrouping(spawnTree(3), ActorConfiguration.disabledConfig)

      val terminatingRefs = allRefs.unfix.inner.flatMap(_.unfix.toVector)
      val rootRef         = leaf(allRefs.unfix.value)

      val actorService =
        system.systemActorOf(subscribeAllConstRefsActorServiceTree(terminatingRefs)(rootRef), createUniqueId)

      val attributes = rootLabels(allRefs)

      val refsWithMetrics = addMetrics(allRefs)

      val (_, rootMetrics) = refsWithMetrics.unfix.value

      /**
       * We expect root value to be taken into account twice
       */
      val expectedValue = extractLong(
        combineWithExisting(
          refsWithMetrics.unfix.foldRight[T] { case TreeF((_, metrics), children) =>
            children.fold(extract(metrics))(sum)
          },
          extract(rootMetrics)
        )
      )

      testCaseWithSetupAndContext { ctx =>
        ctx.copy(actorTreeService = actorService, actorMetricReader = treeDataReader(refsWithMetrics))
      } { implicit setup => implicit context =>
        val ackProbe = TestProbe[Done]()

        collectActorMetrics(ackProbe)
        runUpdaters()

        selectProbe(context.monitor).receiveMessage()

        collectActorMetrics(ackProbe)
        runUpdaters()

        expect(selectProbe, attributes, expectedValue)
      }
    }

  }

  def allBehaviorsLong(
    selectProbe: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    extract: ActorMetrics => Option[Long],
    name: String
  ): Unit = {
    collect[Option[Long]](selectProbe, extract andThen (_.get), name)
    collectMany[Option[Long]](selectProbe, extract, name, sumLong, _.get)(Seq.fill(10)(randomActorMetrics()))
    (reportingAggregation[Option[Long]](
      selectProbe,
      extract,
      name,
      sumLong,
      sumLong,
      _.get
    )(addRandomMetrics))

  }

  def allBehaviorsAgg(
    probeMin: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    probeMax: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    probeSum: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    probeCount: ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]],
    extract: ActorMetrics => Option[LongMinMaxSumCountAggregationImpl],
    name: String
  ): Unit = {
    val args: List[
      (
        String,
        LongMinMaxSumCountAggregationImpl => Long,
        ActorMonitorTestProbe => TestProbe[MetricObserverCommand[Attributes]]
      )
    ] =
      List(("min", _.min, probeMin), ("max", _.max, probeMax), ("sum", _.sum, probeSum), ("count", _.count, probeCount))
    for {
      (suffix, mapping, probe) <- args
    } {
      collect[Option[LongMinMaxSumCountAggregationImpl]](probe, extract.andThen(_.map(mapping).get), s"$name $suffix")
      reportingAggregation[Option[LongMinMaxSumCountAggregationImpl]](
        probe,
        extract,
        s"$name $suffix",
        sumAgg,
        addToAgg,
        _.map(mapping).get
      )((addRandomMetrics))
      collectMany[Option[LongMinMaxSumCountAggregationImpl]](
        probe,
        extract,
        s"$name $suffix",
        addToAgg,
        _.map(mapping).get
      )(
        Seq.fill(10)(randomActorMetrics())
      )
    }

  }

  private val sumLong: (Option[Long], Option[Long]) => Option[Long] = (optX, optY) =>
    (for {
      x <- optX
      y <- optY
    } yield (x + y))

  private val sumAgg: (Option[LongMinMaxSumCountAggregationImpl], Option[LongMinMaxSumCountAggregationImpl]) => Option[
    LongMinMaxSumCountAggregationImpl
  ] =
    (optX, optY) =>
      (for {
        x <- optX
        y <- optY
      } yield x.sum(y))

  private val addToAgg
    : (Option[LongMinMaxSumCountAggregationImpl], Option[LongMinMaxSumCountAggregationImpl]) => Option[
      LongMinMaxSumCountAggregationImpl
    ] =
    (optX, optY) =>
      (for {
        x <- optX
        y <- optY
      } yield x.addTo(y))

  it should behave like allBehaviorsLong(_.mailboxSizeProbe, _.mailboxSize, "mailbox size")
  it should behave like allBehaviorsLong(_.receivedMessagesProbe, _.receivedMessages, "received messages")
  it should behave like allBehaviorsLong(_.failedMessagesProbe, _.failedMessages, "failed messages")
  it should behave like allBehaviorsLong(_.sentMessagesProbe, _.sentMessages, "sent messages")
  it should behave like allBehaviorsLong(_.stashedMessagesProbe, _.stashSize, "stash messages")
  it should behave like allBehaviorsLong(_.droppedMessagesProbe, _.droppedMessages, "dropped messages")
  it should behave like allBehaviorsLong(_.processedMessagesProbe, _.processedMessages, "processed messages")

  it should behave like allBehaviorsAgg(
    _.mailboxTimeMinProbe,
    _.mailboxTimeMaxProbe,
    _.mailboxTimeSumProbe,
    _.mailboxTimeCountProbe,
    _.mailboxTime,
    "mailbox time"
  )

  it should behave like allBehaviorsAgg(
    _.processingTimeMinProbe,
    _.processingTimeMaxProbe,
    _.processingTimeSumProbe,
    _.processingTimeCountProbe,
    _.processingTime,
    "processing time"
  )

  it should "unbind monitors on restart" in {
    val failingReader: ActorMetricsReader = _ => throw new RuntimeException("Expected failure") with NoStackTrace
    val refs                              = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
    val actorService                      = system.systemActorOf(constRefsActorServiceTree(refs), createUniqueId)

    testCaseWithSetupAndContext(_.copy(actorMetricReader = failingReader, actorTreeService = actorService)) {
      implicit setup => implicit context =>
        collectActorMetrics()

        eventually {
          monitor.unbinds should be(1)
          monitor.binds should be(2)
        }
    }
  }

  it should "not produce any metrics until actorTreeService is available" in {
    val refs         = actorConfiguration(spawnTree(3), ActorConfiguration.instanceConfig)
    val ackProbe     = TestProbe[Done]()
    val actorService = system.systemActorOf(countingRefsActorServiceTree(ackProbe.ref)(refs), createUniqueId)

    testCaseWithSetupAndContext(
      _.copy(actorMetricReader = _ => Some(ConstActorMetrics), actorTreeService = actorService)
    ) { implicit setup => implicit context =>
      collectActorMetrics(ackProbe)
      ackProbe.receiveMessage()
      runUpdaters()
      monitor.sentMessagesProbe.expectNoMessage()
    }
  }

  it should "retry to get actor refs" in {
    val ackProbe     = TestProbe[Done]()
    val actorService = system.systemActorOf(noReplyActorServiceTree(ackProbe.ref), createUniqueId)

    testCaseWithSetupAndContext(
      _.copy(actorMetricReader = _ => Some(ConstActorMetrics), actorTreeService = actorService)
    ) { implicit setup => implicit context =>
      collectActorMetrics()
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

  final class CountingTimestampFactory() extends (() => Timestamp) {

    private val _count = new AtomicInteger(0)
    override def apply(): Timestamp = {
      _count.incrementAndGet()
      Timestamp.create()
    }

    def count(): Int = _count.get()
  }

  final case class TestContext(
    monitor: ActorMonitorTestProbe,
    actorTreeServiceProbe: TestProbe[CounterCommand],
    actorMetricReader: ActorMetricsReader = NoMetricsReader,
    actorTreeService: ActorRef[ActorTreeService.Command] = classic.ActorRef.noSender.toTyped[ActorTreeService.Command],
    timestampFactory: CountingTimestampFactory = new CountingTimestampFactory()
  )(implicit
    val system: ActorSystem[_]
  ) extends MonitorTestCaseContext[ActorMonitorTestProbe] {

    sealed trait Command

    final case object Open extends Command

    final case object Close extends Command

    final case class Message(text: String) extends Command

    // TODO delete
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
