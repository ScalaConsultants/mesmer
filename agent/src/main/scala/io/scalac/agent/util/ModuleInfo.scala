package io.scalac.agent.util
import java.util.jar.{ Attributes, Manifest }

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

object ModuleInfo {
  type ModuleId = String
  type Version  = String
  type Modules  = Map[ModuleId, Version]

  def extractModulesInformation(classLoader: ClassLoader): Map[ModuleId, Version] =
    classLoader
      .getResources("META-INF/MANIFEST.MF")
      .asScala
      .flatMap { resource =>
        val manifest   = new Manifest(resource.openStream())
        val attributes = manifest.getMainAttributes
        val moduleId   = attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE)
        val version    = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
        if (moduleId != null && version != null)
          Some(moduleId -> version)
        else
          None
      }
      .toMap
}
