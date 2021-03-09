package io.scalac.extension

package object metric {

  /* Disclaimer
     Each following type has its companion object that defines the Label and BoundMonitor.
     To define the type alias here help us to reference monitors in the code instead of to invent a non-conflicting name for them inside namespaces.
     TODO In Scala 3 we'll have top-level to help us do that.
   */
  type ActorMetricMonitor       = Bindable[ActorMetricMonitor.Labels, ActorMetricMonitor.BoundMonitor]
  type HttpMetricMonitor        = Bindable[HttpMetricMonitor.Labels, HttpMetricMonitor.BoundMonitor]
  type PersistenceMetricMonitor = Bindable[PersistenceMetricMonitor.Labels, PersistenceMetricMonitor.BoundMonitor]
  type ClusterMetricsMonitor    = Bindable[ClusterMetricsMonitor.Labels, ClusterMetricsMonitor.BoundMonitor]
  type StreamMetricMonitor      = Bindable[StreamMetricMonitor.Labels, StreamMetricMonitor.BoundMonitor]
  type StreamOperatorMetricsMonitor =
    EmptyBind[StreamOperatorMetricsMonitor.BoundMonitor]

}
