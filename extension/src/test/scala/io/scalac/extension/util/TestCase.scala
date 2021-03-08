package io.scalac.extension.util

import akka.actor.PoisonPill
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.testkit.TestKit
import akka.util.Timeout

object TestCase {

  /**
   * This i
   */
  trait TestCaseFactory {
    protected type Env
    protected type Context
    protected type Setup

    protected def startEnv(): Env
    protected def stopEnv(env: Env): Unit
    protected def createContext(env: Env): Context
    protected def setUp(context: Context): Setup
    protected def tearDown(setup: Setup): Unit

    // DSL

    def testCaseWith[T](hackContext: Context => Context)(tc: Context => T): T = {
      val env    = startEnv()
      val ctx    = hackContext(createContext(env))
      val setup  = setUp(ctx)
      val result = tc(ctx)
      tearDown(setup)
      stopEnv(env)
      result
    }

    def testCase[T](tc: Context => T): T =
      testCaseWith(identity)(tc)

  }

  // Test Impl

  trait ActorSystemEnvTestCaseFactory extends TestCaseFactory {
    type Env = ActorSystem[_]
  }

  trait FreshActorSystemTestCaseFactory extends ActorSystemEnvTestCaseFactory with TestOps {

    // overrides
    protected final def startEnv(): ActorSystem[_] =
      ActorSystem[Nothing](Behaviors.ignore, createUniqueId, TestConfig.localActorProvider)
    protected final def stopEnv(env: ActorSystem[_]): Unit = TestKit.shutdownActorSystem(env.classicSystem)

    // DSL
    implicit def system(implicit context: MonitorTestCaseContext[_]): ActorSystem[_] = context.system
  }

  trait ProvidedActorSystemTestCaseFactory extends ActorSystemEnvTestCaseFactory {

    // add-on api
    implicit def system: ActorSystem[_]

    // overrides
    protected final def startEnv(): ActorSystem[_]         = system
    protected final def stopEnv(env: ActorSystem[_]): Unit = {}
  }

  trait MonitorTestCaseContext[+M] {
    val monitor: M
    implicit val system: ActorSystem[_]
  }
  object MonitorTestCaseContext {
    final case class BasicContext[+M](monitor: M, caching: Boolean = false)(implicit val system: ActorSystem[_])
        extends MonitorTestCaseContext[M] {
      def withCaching: BasicContext[M] = copy(caching = true)
    }
  }

  trait AbstractMonitorTestCaseFactory extends ActorSystemEnvTestCaseFactory {
    type Monitor
    type Context <: MonitorTestCaseContext[Monitor]

    // add-on api
    protected def createMonitor(implicit system: ActorSystem[_]): Monitor
    protected def createContextFromMonitor(monitor: Monitor)(implicit system: ActorSystem[_]): Context

    // overrides
    protected final def createContext(env: ActorSystem[_]): Context =
      createContextFromMonitor(createMonitor(env))(env)

    // DSL
    def monitor(implicit context: Context): Monitor = context.monitor
  }

  trait MonitorWithServiceTestCaseFactory extends AbstractMonitorTestCaseFactory with ReceptionistOps {

    type Setup = ActorRef[_]

    // add-on api
    protected def createMonitorBehavior(implicit context: Context): Behavior[_]
    protected val serviceKey: ServiceKey[_]
    implicit def timeout: Timeout

    // overrides
    override final protected def setUp(context: Context): ActorRef[_] = {
      val monitorBehavior = createMonitorBehavior(context)
      val monitorActor    = context.system.systemActorOf(monitorBehavior, createUniqueId)
      onlyRef(monitorActor, serviceKey)(context.system, timeout)
      monitorActor
    }

    override final protected def tearDown(setup: ActorRef[_]): Unit =
      setup.unsafeUpcast[Any] ! PoisonPill
  }

  import MonitorTestCaseContext.BasicContext

  trait MonitorWithBasicContextTestCaseFactory extends AbstractMonitorTestCaseFactory {
    type Context = BasicContext[Monitor]
    // overrides
    final protected def createContextFromMonitor(
      monitor: Monitor
    )(implicit system: ActorSystem[_]): BasicContext[Monitor] =
      BasicContext(monitor)
  }

  trait NoSetupTestCaseFactory extends TestCaseFactory {
    type Setup = Unit
    protected final def tearDown(setup: Setup): Unit  = {}
    protected final def setUp(context: Context): Unit = {}
  }

  // common types as aliases...
  // basic context + service + provided
  trait CommonMonitorTestFactory
      extends MonitorWithBasicContextAndServiceTestCaseFactory
      with ProvidedActorSystemTestCaseFactory

  trait MonitorWithBasicContextAndServiceTestCaseFactory
      extends MonitorWithBasicContextTestCaseFactory
      with MonitorWithServiceTestCaseFactory

}
