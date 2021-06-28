package io.scalac.mesmer.core.util

import java.util.jar.Attributes
import java.util.jar.Manifest

import scala.jdk.CollectionConverters._

import io.scalac.mesmer.core.model.Version

object LibraryInfo {

  /**
   * Mapping from library implementation name to found version
   */
  type LibraryInfo = Map[String, Version]

  private val ModulePrefix = "module"

  private def matchModules(properties: Map[String, String]): LibraryInfo =
    properties.keySet.flatMap { key =>
      for {
        splitted @ Array(ModulePrefix, libraryName) <- Option(key.split('.')) if splitted.length == 2
        versionRaw                                  <- properties.get(key)
        version                                     <- Version(versionRaw)
      } yield libraryName -> version
    }.toMap

  private def fromSystemProperties(): LibraryInfo =
    matchModules(System.getProperties.asScala.toMap)

  private def fromJarManifest(classLoader: ClassLoader): LibraryInfo =
    classLoader
      .getResources("META-INF/MANIFEST.MF")
      .asScala
      .flatMap { resource =>
        val manifest   = new Manifest(resource.openStream())
        val attributes = manifest.getMainAttributes

        for {
          libraryName <- Option(attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE))
          versionRaw  <- Option(attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION))
          version     <- Version(versionRaw)
        } yield libraryName -> version
      }
      .toMap

  def extractModulesInformation(classLoader: ClassLoader): LibraryInfo =
    fromJarManifest(classLoader) ++ fromSystemProperties()

}
