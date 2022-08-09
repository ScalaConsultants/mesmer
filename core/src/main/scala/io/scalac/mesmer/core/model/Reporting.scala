package io.scalac.mesmer.core.model

sealed trait Reporting

object Reporting {
  case object Group    extends Reporting
  case object Instance extends Reporting
  case object Disabled extends Reporting

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
