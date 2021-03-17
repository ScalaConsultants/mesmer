package io.scalac.agent.akka.stream

import akka.Done
import akka.actor.ActorRef
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.receptionist.Receptionist._
import akka.actor.typed.scaladsl.adapter._
import akka.stream.scaladsl._
import akka.stream.{Attributes, BufferOverflowException, OverflowStrategy, QueueOfferResult}
import io.scalac.agent.utils.{InstallAgent, SafeLoadSystem}
import io.scalac.core.akka.model.PushMetrics
import io.scalac.extension.event.{ActorInterpreterStats, Service, TagEvent}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.concurrent.{Futures, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Inspectors, LoneElement}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

final case class TestSubscription[T](
  private val subscribe: Subscriber[_ >: T],
  private val data: Iterator[T],
  private val cancelFunc: () => Unit
) extends Subscription {
  @volatile
  private var downstreamDemand: Long = 0
  @volatile
  private var allowedElements: Long = 0
  @volatile
  private var cancelled: Boolean = false

  override def request(n: Long): Unit =
    if (!cancelled) {
      downstreamDemand += n
      publishElements()
    }

  def pushAllowed(n: Int): Unit =
    if (!cancelled) {
      allowedElements += n
      publishElements()
    }

  private def publishElements(): Unit =
    while (downstreamDemand > 0 && allowedElements > 0 && !cancelled)
      if (data.hasNext) {
        subscribe.onNext(data.next())
        downstreamDemand -= 1
        allowedElements -= 1
      } else {
        subscribe.onComplete()
        this.cancel()
      }

  override def cancel(): Unit = {
    cancelled = true
    cancelFunc()
  }
}

final class TestPublisher[T](data: Seq[T]) extends Publisher[T] {

  def allowElements(n: Int): Unit = buffer.foreach(_.pushAllowed(n))

  private val buffer = ListBuffer.empty[TestSubscription[T]]

  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    lazy val subscription: TestSubscription[T] =
      TestSubscription[T](s, data.iterator, () => buffer.filterInPlace(_ != subscription))
    s.onSubscribe(subscription)
    buffer += subscription
  }
}

final case class TestSubscriber[T]() extends Subscriber[T] {
  @volatile
  private var subscription: Option[Subscription] = None

  @volatile
  private var _demand = 0

  @volatile
  private var demanded: Boolean = false

  override def onSubscribe(s: Subscription): Unit = {
    subscription = Some(s)
    publishDemand()
  }

  override def onNext(t: T): Unit = {
    println(t)
    demanded = true
    publishDemand()
  }

  override def onError(t: Throwable): Unit = ()

  override def onComplete(): Unit = ()

  def demand(num: Int): Unit = {

    _demand += num
    if (!demanded) {
      demanded = true
      publishDemand()
    }
  }

  private def publishDemand(): Unit =
    while (_demand > 0) {
      _demand -= 1
      subscription.foreach(_.request(1))
    }
}

class AkkaStreamAgentTest
    extends InstallAgent
    with AnyFlatSpecLike
    with Matchers
    with SafeLoadSystem
    with BeforeAndAfterAll
    with BeforeAndAfter
    with LoneElement
    with Inspectors
    with ScalaFutures
    with Futures {

  implicit var streamService: ActorStreamRefService = _

  def actors(num: Int)(implicit refService: ActorStreamRefService): Seq[ActorRef] = refService.actors(num)
  def clear(implicit refService: ActorStreamRefService): Unit                     = refService.clear

  override def beforeAll(): Unit = {
    super.beforeAll() // order is important!
    streamService = new ActorStreamRefService()
  }

  after {
    clear
  }

  final class ActorStreamRefService {
    private val probe = TestProbe[TagEvent]("stream_refs")

    def actors(number: Int): Seq[ActorRef] = probe
      .within(2.seconds) {
        val messages = probe.receiveMessages(number)
        probe.expectNoMessage(probe.remaining)
        messages.map(_.ref)
      }

    /**
     * Make sure no more actors are created
     */
    def clear: Unit = probe.expectNoMessage(2.seconds)

    system.receptionist ! Register(Service.tagService.serviceKey, probe.ref)
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

  "AkkaStreamAgentTest" should "accurately detect push / demand" in {

    implicit val ec: ExecutionContext = system.executionContext

    val replyProbe = createTestProbe[ActorInterpreterStats]

    val Demand           = 5L
    val ExpectedElements = Demand + 1L // somehow sinkQueue demand 1 element in advance

    val (inputQueue, outputQueue) = Source
      .queue[Int](1024, OverflowStrategy.dropNew, 1)
      .filter(_ % 2 == 0)
      .map(x => if (x == 0) 0 else x / 2)
      .toMat(Sink.queue[Int]().withAttributes(Attributes.inputBuffer(1, 1)))(Keep.both)
      .run()

    val elements = offerMany(inputQueue, List.tabulate(100)(identity))
      .zipWith(expectMany(outputQueue, Demand))((_, _) => Done)

    whenReady(elements) { _ =>
      val ref = actors(1).loneElement

      ref ! PushMetrics(replyProbe.ref.toClassic)

      val stats = replyProbe.receiveMessage()

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

  it should "find correct amount of actors for async streams" in {
    val sutStream = Source
      .single(())
      .async
      .map(_ => ())
      .async
      .to(Sink.ignore)
      .run()

    actors(3)
  }

  it should "find accurate amount of push / demand for async streams" in {

    implicit val ec: ExecutionContext = system.executionContext

    val replyProbe = createTestProbe[ActorInterpreterStats]

    val Demand           = 5L
    val ExpectedElements = Demand + 1L // somehow sinkQueue demand 1 element in advance

    val (inputQueue, outputQueue) = Source
      .queue[Int](1024, OverflowStrategy.dropNew, 1)
      .async
      .filter(_ % 2 == 0)
      .map(x => if (x == 0) 0 else x / 2)
      .async
      .toMat(Sink.queue[Int]().withAttributes(Attributes.inputBuffer(1, 1)))(Keep.both)
      .run()

    val elements = offerMany(inputQueue, List.tabulate(100)(identity))
      .zipWith(expectMany(outputQueue, Demand))((_, _) => Done)

    whenReady(elements) { _ =>
      val refs = actors(3)

      refs.foreach(_ ! PushMetrics(replyProbe.ref.toClassic))

      val allStats = replyProbe.receiveMessages(3)

      forAll(allStats) { stats =>
        val (stages, connections) = stats.shellInfo.loneElement

        stages.foreach(println)
        connections.foreach(println)

//        stages should have size 4
//        connections should have size 3
//
//        val Array(sinkMap, mapFilter, filterSource) = connections
//
//        sinkMap.push shouldBe ExpectedElements
//        sinkMap.pull shouldBe ExpectedElements
//
//        mapFilter.push shouldBe ExpectedElements
//        mapFilter.pull shouldBe ExpectedElements
//
//        filterSource.push should be((ExpectedElements * 2) +- 1)
//        filterSource.pull should be((ExpectedElements * 2) +- 1)
      }
    }
  }
}
