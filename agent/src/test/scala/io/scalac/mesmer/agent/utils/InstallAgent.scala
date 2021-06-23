package io.scalac.mesmer.agent.utils

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.agent.akka.stream.AkkaStreamAgent
import io.scalac.mesmer.agent.util.i13n.InstrumentModuleFactory
import io.scalac.mesmer.agent.util.i13n.InstrumentModuleFactory._
import io.scalac.mesmer.core.module.Module
import io.scalac.mesmer.core.util.ModuleInfo.{ extractModulesInformation, Modules }
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.scalatest.TestSuite
import org.scalatest.flatspec.AnyFlatSpecLike

import java.net.{ URL, URLClassLoader }
import scala.util.Try

object InstallAgent {
  def allInstrumentations: Agent =
    AkkaActorAgent.defaultAgent ++ AkkaHttpAgent.defaultAgent ++ AkkaPersistenceAgent.defaultAgent ++ AkkaStreamAgent.defaultAgent
}

abstract class InstallAgent extends TestSuite {

  def modules: Modules = extractModulesInformation(Thread.currentThread().getContextClassLoader)

  protected var agent: Option[Agent] = None

  private val builder = new AgentBuilder.Default(
    new ByteBuddy()
      .`with`(TypeValidation.DISABLED)
  )
    .`with`(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
    .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
    .`with`(
      AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly()
    )

  private def installAgent(): Unit = {
    val instrumentation = ByteBuddyAgent.install()

    agent.fold[Unit](throw new AssertionError("Agent must be set")) {
      _.installOn(builder, instrumentation, modules)
        .eagerLoad()
    }
  }

  def withAgent(setup: () => Unit)(test: () => Any): Any = {
    val context = Thread.currentThread().getContextClassLoader.asInstanceOf[URLClassLoader]
    val prefixClassLoader =
      new PrefixChildFirstClassLoader(Vector("akka.http.scaladsl.HttpExt"), context.getURLs, context)
    Thread.currentThread().setContextClassLoader(prefixClassLoader)

    setup()
    installAgent()
    Class.forName("akka.http.scaladsl.HttpExt", true, prefixClassLoader)
    test()
    Thread.currentThread().setContextClassLoader(context)
  }

}

final class PrefixChildFirstClassLoader(prefix: Vector[String], urls: Array[URL], parent: ClassLoader)
    extends URLClassLoader(urls, parent) {
  override def loadClass(name: String, resolve: Boolean): Class[_] = {

    val loaded = Option(findLoadedClass(name)).orElse {
      if (prefix.exists(p => name.startsWith(p))) {
        println(s"Loading class: $name")

        Try(findClass(name)).toOption
      } else None
    }.orElse(Try(super.loadClass(name, resolve)).toOption).getOrElse(throw new ClassNotFoundException(name))

    if (resolve) {
      resolveClass(loaded)
    }
    loaded
  }

}

abstract class InstallModule[M <: Module](moduleFactory: InstrumentModuleFactory[M]) extends InstallAgent {
  this: AnyFlatSpecLike =>

  def withVersion(versions: moduleFactory.module.All[Boolean]*)(name: String)(test: => Any): Any =
    versions.foreach { version =>
      it should s"$name with $version" in withAgent { () =>
        agent = Some(moduleFactory.agent(version))
        println(s"Class loader: ${Thread.currentThread().getContextClassLoader}")
        println(s"Class loader parent: ${Thread.currentThread().getContextClassLoader.getParent}")
        println(s"Class loader 2xparent: ${Thread.currentThread().getContextClassLoader.getParent.getParent}")
      }(() => test)

    }

}
