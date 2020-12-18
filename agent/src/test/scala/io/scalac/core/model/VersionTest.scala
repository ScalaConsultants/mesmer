package io.scalac.core.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VersionTest extends AnyFlatSpec with Matchers {

  "Version" should "split string to major minor and path" in {
    val major      = "2"
    val minor      = "10"
    val patch      = "12"
    val versionRaw = s"${major}.${minor}.${patch}"

    Version(versionRaw) shouldEqual Some(Version(major, minor, patch))
  }

  it should "product none for invalid string" in {
    Version("aaabbcc") shouldEqual None
  }

}
