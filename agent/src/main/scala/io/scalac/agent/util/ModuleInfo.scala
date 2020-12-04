package io.scalac.agent.util
import java.util.jar.{ Attributes, Manifest }

import io.scalac.agent.model.{ Module, Version }

import scala.jdk.CollectionConverters._

object ModuleInfo {

  type Modules = Map[Module, Version]

  def extractModulesInformation(classLoader: ClassLoader): Map[Module, Version] =
    classLoader
      .getResources("META-INF/MANIFEST.MF")
      .asScala
      .flatMap { resource =>
        val manifest   = new Manifest(resource.openStream())
        val attributes = manifest.getMainAttributes
        val moduleId   = attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE)
        val version    = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
        if (moduleId != null && version != null)
          Version(version).map(Module(moduleId) -> _)
        else
          None
      }
      .toMap
}
