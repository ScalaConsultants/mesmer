package io.scalac.mesmer.core.actor

import akka.OptionVal._
import akka.OptionVal.apply
import akka.OptionVal.none

import io.scalac.mesmer.core.util.MetricsToolKit._

final class ActorCellMetrics {
  private var _mailboxTimeAgg: OptionVal[TimeAggregation]    = none
  private var _processingTimeAgg: OptionVal[TimeAggregation] = none
  private var _processingTimer: OptionVal[Timer]             = none
  private var _receivedMessages: OptionVal[Counter]          = none
  private var _unhandledMessages: OptionVal[Counter]         = none
  private var _sentMessages: OptionVal[Counter]              = none
  private var _failedMessages: OptionVal[Counter]            = none
  private var _exceptionHandledMarker: OptionVal[Marker]     = none
  private var _stashedMessages: OptionVal[Counter]           = none
  private var _droppedMessages: OptionVal[Counter]           = none

  def mailboxTimeAgg: OptionVal[TimeAggregation]    = _mailboxTimeAgg
  def processingTimeAgg: OptionVal[TimeAggregation] = _processingTimeAgg
  def processingTimer: OptionVal[Timer]             = _processingTimer
  def receivedMessages: OptionVal[Counter]          = _receivedMessages
  def unhandledMessages: OptionVal[Counter]         = _unhandledMessages
  def sentMessages: OptionVal[Counter]              = _sentMessages
  def failedMessages: OptionVal[Counter]            = _failedMessages
  def exceptionHandledMarker: OptionVal[Marker]     = _exceptionHandledMarker
  def stashedMessages: OptionVal[Counter]           = _stashedMessages
  def droppedMessages: OptionVal[Counter]           = _droppedMessages

  def initMailboxTimeAgg(): Unit         = _mailboxTimeAgg = apply(new TimeAggregation())
  def initProcessingTimeAgg(): Unit      = _processingTimeAgg = apply(new TimeAggregation())
  def initProcessingTimer(): Unit        = _processingTimer = apply(new Timer())
  def initReceivedMessages(): Unit       = _receivedMessages = apply(new Counter())
  def initUnhandledMessages(): Unit      = _unhandledMessages = apply(new Counter())
  def initSentMessages(): Unit           = _sentMessages = apply(new Counter())
  def initFailedMessages(): Unit         = _failedMessages = apply(new Counter())
  def initExceptionHandledMarker(): Unit = _exceptionHandledMarker = apply(new Marker)
  def initStashedMessages(): Unit        = _stashedMessages = apply(new Counter())
  def initDroppedMessages(): Unit        = _droppedMessages = apply(new Counter())

}
