package io.scalac.agent.util
import java.util.jar.{ Attributes, Manifest }

import io.scalac.agent.model.{ Module, Version }

import scala.jdk.CollectionConverters._

object ModuleInfo {

  type Modules = Map[Module, Version]

  private val ModulePrefix = "module"

  private def matchModules(properties: Map[String, String]): Modules =
    properties.keySet.flatMap { key =>
      for {
        splited @ Array(ModulePrefix, module) <- Option(key.split('.')) if splited.length == 2
        versionRaw                            <- properties.get(key)
        version                               <- Version(versionRaw)
      } yield (Module(module) -> version)
    }.toMap

  private def fromSystemProperties(): Modules =
    matchModules(System.getProperties.asScala.toMap)

  private def fromJarManifest(classLoader: ClassLoader): Modules =
    classLoader
      .getResources("META-INF/MANIFEST.MF")
      .asScala
      .flatMap { resource =>
        val manifest   = new Manifest(resource.openStream())
        val attributes = manifest.getMainAttributes

        for {
          moduleId   <- Option(attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE))
          versionRaw <- Option(attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION))
          version    <- Version(versionRaw)
        } yield Module(moduleId) -> version
      }
      .toMap

  def extractModulesInformation(classLoader: ClassLoader): Modules =
    fromJarManifest(classLoader) ++ fromSystemProperties()

}
