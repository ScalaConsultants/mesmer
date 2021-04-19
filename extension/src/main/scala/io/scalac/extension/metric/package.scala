package io.scalac.extension

package object metric {

  /* Disclaimer
     Each following type has its companion object that defines the Label and BoundMonitor.
     To define the type alias here help us to reference monitors in the code instead of to invent a non-conflicting name for them inside namespaces.
     TODO In Scala 3 we'll have top-level to help us do that.
   */
  type ActorMetricsMonitor = EmptyBind[ActorMetricsMonitor.BoundMonitor]
  type HttpMetricsMonitor  = Bindable[HttpMetricsMonitor.Labels, HttpMetricsMonitor.BoundMonitor]
  type HttpConnectionMetricsMonitor =
    Bindable[HttpConnectionMetricsMonitor.Labels, HttpConnectionMetricsMonitor.BoundMonitor]
  type PersistenceMetricsMonitor = Bindable[PersistenceMetricsMonitor.Labels, PersistenceMetricsMonitor.BoundMonitor]
  type ClusterMetricsMonitor     = Bindable[ClusterMetricsMonitor.Labels, ClusterMetricsMonitor.BoundMonitor]
  type StreamMetricsMonitor      = Bindable[StreamMetricsMonitor.EagerLabels, StreamMetricsMonitor.BoundMonitor]
  type StreamOperatorMetricsMonitor =
    EmptyBind[StreamOperatorMetricsMonitor.BoundMonitor]

}
