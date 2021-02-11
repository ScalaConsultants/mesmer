package io.scalac.core.model

object SupportedModules {
  def apply(module: Module, supportedVersion: SupportedVersion): SupportedModules =
    new SupportedModules(Map(module -> supportedVersion))

  def empty: SupportedModules = new SupportedModules(Map.empty)
}

final case class SupportedModules private (private val _modules: Map[Module, SupportedVersion]) {
  def ++(other: SupportedModules): SupportedModules = {
    val resolved = _modules.keySet
      .intersect(_modules.keySet)
      .flatMap { module =>
        for {
          thisSupported  <- _modules.get(module)
          otherSupported <- other._modules.get(module)
        } yield module -> (thisSupported && otherSupported)
      }
      .toMap
    SupportedModules(_modules ++ other._modules ++ resolved)
  }

  def modules: Set[Module] = _modules.keySet

  def supportedVersion(module: Module): SupportedVersion = _modules.getOrElse(module, SupportedVersion.any)

  def ++(module: Module, supportedVersion: SupportedVersion): SupportedModules = {
    val resolvedVersion = _modules
      .get(module)
      .map(_ && supportedVersion)
      .getOrElse(supportedVersion)
    SupportedModules(_modules + (module -> resolvedVersion))
  }
}
