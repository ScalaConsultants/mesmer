package io.scalac.mesmer.extension.metric

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.extension.config.CachingConfig

class CachingMonitorTest extends AnyFlatSpec with Matchers with Inspectors {

  case class TestAttributes(attribute: String) extends AttributesSerializable {
    def serialize: RawAttributes = Seq("attribute" -> attribute)
  }

  case class TestBound(attributes: TestAttributes) extends Bound {
    private[this] var _unbound = false
    def unbind(): Unit         = _unbound = true
    def unbound: Boolean       = _unbound
  }

  class TestBindable extends Bindable[TestAttributes, TestBound]() {

    private[this] val _binds: ListBuffer[TestAttributes] = ListBuffer.empty

    def bind(attributes: TestAttributes): TestBound = {
      _binds += attributes
      TestBound(attributes)
    }

    def binds: List[TestAttributes] = _binds.toList
  }

  type Fixture = TestBindable

  def test[T](body: Fixture => T): T =
    body(new TestBindable)

  "CachingMonitor" should "proxy to wrapped monitor" in test { testBindable =>
    val sut = CachingMonitor(testBindable, CachingConfig.empty)

    val attributes = List.tabulate(10)(num => s"attribute_$num").map(TestAttributes.apply)

    attributes.foreach(sut.bind)

    testBindable.binds should contain theSameElementsInOrderAs attributes
  }

  it should "return same instance when keys repeat" in test { testBindable =>
    val sut        = CachingMonitor(testBindable, CachingConfig.empty)
    val attribute  = TestAttributes("attribute")
    val attributes = List.fill(10)(attribute)
    val instances  = attributes.map(sut.bind)

    testBindable.binds should have size 1
    testBindable.binds shouldBe List(attribute)

    forAll(instances.tail)(_ should be theSameInstanceAs instances.head)
  }

  it should "evict elements when cache limit is hit" in test { testBindable =>
    val cacheSize  = 5
    val sut        = CachingMonitor(testBindable, CachingConfig(cacheSize))
    val attributes = List.tabulate(cacheSize + 1)(num => s"attribute_$num").map(TestAttributes.apply)

    val instances = attributes.map(sut.bind)
    instances.head.unbound shouldBe true
    forAll(instances.tail)(_.unbound shouldBe false)
    sut.cachedMonitors should have size cacheSize
    sut.cachedMonitors.keys should contain theSameElementsAs attributes.tail
  }

  it should "evict monitors in LRU manner" in test { testBindable =>
    val cacheSize = 5
    val sut       = CachingMonitor(testBindable, CachingConfig(cacheSize))
    val attributes @ firstAttribute :: _ :: attributesTail =
      List.tabulate(cacheSize)(num => s"attribute_$num").map(TestAttributes.apply)
    val additionalAttribute = TestAttributes("evicting_attribute")

    val instances @ firstInstance :: secondInstance :: instancesTail = attributes.map(sut.bind)
    sut.bind(firstAttribute)
    //
    forAll(instances)(_.unbound shouldBe false)
    sut.cachedMonitors.keys should contain theSameElementsAs attributes
    val additionalInstance = sut.bind(additionalAttribute)

    secondInstance.unbound shouldBe true

    forAll(firstInstance :: additionalInstance :: instancesTail)(_.unbound shouldBe false)

    sut.cachedMonitors.keys should contain theSameElementsAs (firstAttribute :: additionalAttribute :: attributesTail)
  }
}
