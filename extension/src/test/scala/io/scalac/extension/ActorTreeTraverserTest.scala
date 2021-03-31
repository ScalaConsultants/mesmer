package io.scalac.extension

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.core.util.ActorPathOps
import io.scalac.extension.ActorEventsMonitorActor.ReflectiveActorTreeTraverser
import io.scalac.extension.util.TestConfig
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class ActorTreeTraverserTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors {

  private val traverser = ReflectiveActorTreeTraverser

  s"ActorTreeRunner instance (${traverser.getClass.getName})" should "getRoot properly" in {
    val root = traverser.getRootGuardian(system.classicSystem)
    ActorPathOps.getPathString(root) should be("/")
  }

  it should "getChildren properly" in {
    val root     = traverser.getRootGuardian(system.classicSystem)
    val children = traverser.getChildren(root)
    children.map(ActorPathOps.getPathString) should contain theSameElementsAs (Set(
      "/system",
      "/user"
    ))
  }

  it should "getChildren properly from nested actor" in {
    spawn[Nothing](Behaviors.ignore, "actorA")
    spawn[Nothing](Behaviors.ignore, "actorB")
    val root             = traverser.getRootGuardian(system.classicSystem)
    val children         = traverser.getChildren(root)
    val guardian         = children.find(c => ActorPathOps.getPathString(c) == "/user").get
    val guardianChildren = traverser.getChildren(guardian)
    guardianChildren.map(ActorPathOps.getPathString) should contain theSameElementsAs Set(
      "/user/actorA",
      "/user/actorB"
    )
  }
}
