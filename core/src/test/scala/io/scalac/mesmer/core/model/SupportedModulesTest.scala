package io.scalac.mesmer.core.model

import io.scalac.mesmer.core.module.Module
import io.scalac.mesmer.core.module.Module.CommonJars
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SupportedModulesTest extends AnyFlatSpec with Matchers with Inspectors {
  type Id[T] = T

  object TestModuleOne extends Module {
    val name: String = "test-module-one"

    case class TestConfig() extends All[Boolean]

    override type All[T] = AnyRef

    def enabled = TestConfig()

    override type Jars[T] = CommonJars[T]

    def jarsFromLibraryInfo(info: LibraryInfo): Option[Jars[Version]] = None

  }

  "SupportedModules" should "combine required versions" in {
    val module = TestModuleOne

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
