package io.scalac.core.model

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SupportedModulesTest extends AnyFlatSpec with Matchers with Inspectors {

  "SupportedModules" should "combine required versions" in {
    val module = Module("akka.http")

    val firstSupportedVersions  = List(Version(2, 6, 8), Version(2, 6, 9))
    val secondSupportedVersions = List(Version(2, 6, 9), Version(2, 6, 10))

    val supportedModules =
      SupportedModules(module, SupportedVersion(firstSupportedVersions)) ++ (module, SupportedVersion(
        secondSupportedVersions
      ))

    val supportedVersion    = firstSupportedVersions.intersect(secondSupportedVersions)
    val notSupportedVersion = firstSupportedVersions ++ secondSupportedVersions filterNot supportedVersion.contains

    forAll(supportedVersion.map(supportedModules.supportedVersion(module).supports))(_ shouldBe true)
    forAll(notSupportedVersion.map(supportedModules.supportedVersion(module).supports))(_ shouldBe false)
  }
}
