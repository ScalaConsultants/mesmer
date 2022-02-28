package io.scalac.mesmer.extension

package object metric {

  /* Disclaimer
     Each following type has its companion object that defines the Attribute and BoundMonitor.
     To define the type alias here help us to reference monitors in the code instead of to invent a non-conflicting name for them inside namespaces.
     TODO In Scala 3 we'll have top-level to help us do that.
   */

  type DispatcherStaticMetricsMonitor = EmptyBind[DispatcherStaticMetricsMonitor.BoundMonitor]
  type ActorMetricsMonitor = EmptyBind[ActorMetricsMonitor.BoundMonitor]
  type HttpMetricsMonitor  = Bindable[HttpMetricsMonitor.Attributes, HttpMetricsMonitor.BoundMonitor]
  type HttpConnectionMetricsMonitor =
    Bindable[HttpConnectionMetricsMonitor.Attributes, HttpConnectionMetricsMonitor.BoundMonitor]
  type PersistenceMetricsMonitor =
    Bindable[PersistenceMetricsMonitor.Attributes, PersistenceMetricsMonitor.BoundMonitor]
  type ClusterMetricsMonitor = Bindable[ClusterMetricsMonitor.Attributes, ClusterMetricsMonitor.BoundMonitor]
  type StreamMetricsMonitor  = Bindable[StreamMetricsMonitor.EagerAttributes, StreamMetricsMonitor.BoundMonitor]
  type StreamOperatorMetricsMonitor =
    EmptyBind[StreamOperatorMetricsMonitor.BoundMonitor]
  type ActorSystemMonitor = Bindable[ActorSystemMonitor.Attributes, ActorSystemMonitor.BoundMonitor]

}
