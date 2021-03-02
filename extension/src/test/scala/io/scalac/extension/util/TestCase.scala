package io.scalac.extension.util

import scala.concurrent.duration._

import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.testkit.TestKit
import akka.util.Timeout

import org.scalatest.Suite

object TestCase {

  trait TestCaseFactory[E, C] {
    protected def startEnv(): E
    protected def stopEnv(implicit env: E): Unit
    protected def createContext(implicit env: E): C
    protected def setUp(context: C): Unit

    // DSL for `import factory._`
    // TODO When we move to Scala 3, let's take advantage of context-functions to build smt as follow:
    // testCase { 
    //    monitor.bind(...)
    //    system.systemActorOf
    // }
    // i.e., without `implicit c => `

    def testCaseWith[T](hackContext: C => C)(tc: C => T): T = {
      val env = startEnv()
      val ctx = hackContext(createContext(env))
      setUp(ctx)
      val result = tc(ctx)
      stopEnv(env)
      result
    }

    def testCase[T](tc: C => T): T =
      testCaseWith(identity)(tc)

    def monitor[M](implicit context: MonitorTestCaseContext[M]): M = context.monitor

    implicit def system(implicit context: MonitorTestCaseContext[_]): ActorSystem[_] = context.system

  }

  // Test Impl

  trait MonitorTestCaseContext[+M] {
    val monitor: M
    implicit val system: ActorSystem[_]
  }
  object MonitorTestCaseContext {
    final case class Basic[+M](monitor: M)(implicit val system: ActorSystem[_]) extends MonitorTestCaseContext[M]
  }

  trait MonitorTestCaseFactory[M, C <: MonitorTestCaseContext[M]]
      extends TestCaseFactory[ActorSystem[_], C]
      with TestOps {
    protected def createMonitor(implicit system: ActorSystem[_]): M
    protected def createContext(monitor: M)(implicit system: ActorSystem[_]): C

    protected final def startEnv(): ActorSystem[_] =
      ActorSystem[Nothing](Behaviors.ignore, createUniqueId, TestConfig.localActorProvider)

    protected final def stopEnv(implicit env: ActorSystem[_]): Unit    = TestKit.shutdownActorSystem(env.classicSystem)
    protected final def createContext(implicit env: ActorSystem[_]): C = createContext(createMonitor(env))(env)
  }

  trait MonitorWithService[M, C <: MonitorTestCaseContext[M]]
      extends MonitorTestCaseFactory[M, C]
      with ReceptionistOps {
    protected def createMonitorBehavior(implicit context: C): Behavior[_]
    protected val serviceKey: ServiceKey[_]
    protected implicit val timeout: Timeout = 1.seconds
    protected def setUp(context: C): Unit = {
      val monitorBehavior = createMonitorBehavior(context)
      val monitorActor    = context.system.systemActorOf(monitorBehavior, createUniqueId)
      onlyRef(monitorActor, serviceKey)(context.system, timeout)
    }
  }

  trait MonitorWithServiceWithBasicContext[M] extends MonitorWithService[M, MonitorTestCaseContext.Basic[M]] {
    final protected def createContext(monitor: M)(implicit system: ActorSystem[_]): MonitorTestCaseContext.Basic[M] =
      MonitorTestCaseContext.Basic(monitor)
  }

}
