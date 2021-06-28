package io.scalac.mesmer.core.actor

import io.scalac.mesmer.core.util.MetricsToolKit._

class ActorCellMetrics {
  val mailboxTimeAgg: TimeAggregation    = new TimeAggregation()
  val processingTimeAgg: TimeAggregation = new TimeAggregation()
  val processingTimer: Timer             = new Timer
  val receivedMessages: Counter          = new Counter
  val processedMessages: Counter         = new Counter
  val unhandledMessages: Counter         = new Counter
  val sentMessages: Counter              = new Counter
  val failedMessages: Counter            = new Counter
  val exceptionHandledMarker: Marker     = new Marker
  val stashSize: UninitializedCounter    = new UninitializedCounter
  def droppedMessages: Option[Counter]   = None
}

/**
 * Mixed in trait for actor cells with bounded mailboxes
 */
trait DroppedMessagesCellMetrics extends ActorCellMetrics {
  val _droppedMessages                        = new Counter
  override def droppedMessages: Some[Counter] = Some(_droppedMessages)
}

//trait CellMetrics {
//  def mailboxTimeAgg:
//  def processingTimeAgg
//  def processingTimer
//  def receivedMessages
//  def processedMessages
//  def unhandledMessages
//  def sentMessages
//  def failedMessages
//  def exceptionHandledMarker
//  def stashSize
//  def droppedMessages
//}
//
//
//final class CellMetricsImpl {
//  val mailboxTimeAgg: TimeAggregation    = new TimeAggregation()
//  val processingTimeAgg: TimeAggregation = new TimeAggregation()
//  val processingTimer: Timer           = new Timer
//  val receivedMessages: Counter        = new Counter
//  val processedMessages: Counter       = new Counter
//  val unhandledMessages: Counter       = new Counter
//  val sentMessages: Counter            = new Counter
//  val failedMessages: Counter          = new Counter
//  val exceptionHandledMarker: Marker   = new Marker
//  val stashSize: UninitializedCounter  = new UninitializedCounter
//}
