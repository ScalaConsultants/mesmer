package io.scalac.mesmer.core.model

object ActorConfiguration {

  sealed trait Reporting {
    import Reporting._

    final def aggregate: Boolean = this match {
      case Group => true
      case _     => false
    }

    final def visible: Boolean = this match {
      case Disabled => false
      case _        => true
    }
  }

  object Reporting {
    private case object Group    extends Reporting
    private case object Instance extends Reporting
    private case object Disabled extends Reporting

    def parse(value: String): Option[Reporting] = value.toLowerCase match {
      case "group"    => Some(Group)
      case "instance" => Some(Instance)
      case "disabled" => Some(Disabled)
      case _          => None
    }

    val group: Reporting    = Group
    val instance: Reporting = Instance
    val disabled: Reporting = Disabled
  }

  val groupingConfig: ActorConfiguration = ActorConfiguration(Reporting.group)
  val instanceConfig: ActorConfiguration = ActorConfiguration(Reporting.instance)
  val disabledConfig: ActorConfiguration = ActorConfiguration(Reporting.disabled)
}

final case class ActorConfiguration(reporting: ActorConfiguration.Reporting)
