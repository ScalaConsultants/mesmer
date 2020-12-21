package io.scalac.core.model

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class SupportedVersionTest extends AnyFlatSpec with Matchers with Inspectors {

  def randomVersion: Version = {
    val major = Random.nextInt(100).toString
    val minor = Random.nextInt(100).toString
    val patch = Random.nextInt(100).toString
    Version(major, minor, patch)
  }

  "SupportedVersion" should "support any version" in {
    val versions = List.fill(10)(randomVersion)

    val sut = SupportedVersion.any

    forAll(versions.map(sut.supports))(_ shouldBe (true))
  }

  it should "support any specified version" in {
    val (supportedVersions, notSupported) = List
      .tabulate(10)(_ + 1)
      .map(_.toString)
      .map(value => Version(value, value, value))
      .splitAt(5)

    val sut = SupportedVersion(supportedVersions)

    forAll(supportedVersions.map(sut.supports))(_ shouldBe true)
    forAll(notSupported.map(sut.supports))(_ shouldBe false)
  }
  it should "support version with specified majors" in {
    val supportedMajor +: notSupportedMajors = Random.shuffle(Seq.tabulate(10)(_.toString))

    val supportedVersions    = List.fill(10)(randomVersion).map(_.copy(major = supportedMajor))
    val notSupportedVersions = List.fill(10)(randomVersion).map(_.copy(major = Random.shuffle(notSupportedMajors).head))

    val sut = SupportedVersion.majors(supportedMajor)

    forAll(supportedVersions.map(sut.supports))(_ shouldBe (true))
    forAll(notSupportedVersions.map(sut.supports))(_ shouldBe (false))
  }

  it should "support version with many specified majors" in {
    val (supportedMajors, notSupportedMajors) = Random.shuffle(List.tabulate(10)(_ + 1).map(_.toString)).splitAt(5)

    val supportedVersions    = List.fill(10)(randomVersion).map(_.copy(major = Random.shuffle(supportedMajors).head))
    val notSupportedVersions = List.fill(10)(randomVersion).map(_.copy(major = Random.shuffle(notSupportedMajors).head))

    val sut = SupportedVersion.majors(supportedMajors)

    forAll(supportedVersions.map(sut.supports))(_ shouldBe (true))
    forAll(notSupportedVersions.map(sut.supports))(_ shouldBe (false))
  }

  it should "support version with specified minor" in {
    val supportedMinor +: notSupportedMinor = Random.shuffle(Seq.tabulate(10)(_.toString))

    val supportedVersions    = List.fill(10)(randomVersion).map(_.copy(minor = supportedMinor))
    val notSupportedVersions = List.fill(10)(randomVersion).map(_.copy(minor = Random.shuffle(notSupportedMinor).head))

    val sut = SupportedVersion.minors(supportedMinor)

    forAll(supportedVersions.map(sut.supports))(_ shouldBe (true))
    forAll(notSupportedVersions.map(sut.supports))(_ shouldBe (false))
  }

  it should "support version with many specified minors" in {
    val (supportedMinor, notSupportedMinors) = Random.shuffle(List.tabulate(10)(_ + 1).map(_.toString)).splitAt(5)

    val supportedVersions    = List.fill(10)(randomVersion).map(_.copy(minor = Random.shuffle(supportedMinor).head))
    val notSupportedVersions = List.fill(10)(randomVersion).map(_.copy(minor = Random.shuffle(notSupportedMinors).head))

    val sut = SupportedVersion.minors(supportedMinor)

    forAll(supportedVersions.map(sut.supports))(_ shouldBe (true))
    forAll(notSupportedVersions.map(sut.supports))(_ shouldBe (false))
  }

  it should "support no version" in {
    val versions = List.fill(10)(randomVersion)
    val sut      = SupportedVersion.none

    forAll(versions.map(sut.supports))(_ shouldBe false)
  }

  it should "support intersection of versions" in {
    val supportedMajor +: notSupportedMajors = Random.shuffle(Seq.tabulate(10)(_.toString))
    val supportedMinor +: notSupportedMinors = Random.shuffle(Seq.tabulate(10)(_.toString))

    val (intersectedSupportedVersion, otherVersions) = List.fill(10)(randomVersion).splitAt(5) match {
      case (supported, notSupported) => {
        (supported.map(_.copy(major = supportedMajor, minor = supportedMinor)) ->
        notSupported.map(
          _.copy(major = Random.shuffle(notSupportedMajors).head, Random.shuffle(notSupportedMinors).head)
        ))
      }
    }

    val sut = SupportedVersion(intersectedSupportedVersion ++ otherVersions) && SupportedVersion.majors(
      supportedMajor
    ) && SupportedVersion.minors(supportedMinor)

    forAll(intersectedSupportedVersion.map(sut.supports))(_ shouldBe true)
    forAll(otherVersions.map(sut.supports))(_ shouldBe false)
  }

}
