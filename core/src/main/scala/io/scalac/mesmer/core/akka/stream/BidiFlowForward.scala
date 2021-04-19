package io.scalac.mesmer.core.akka.stream

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.BidiFlow
import akka.stream.stage._

object BidiFlowForward {

  def apply[I, O](onPreStart: () => Unit, onPostStop: () => Unit): BidiFlow[I, I, O, O, NotUsed] =
    BidiFlow.fromGraph(
      new GraphStage[BidiShape[I, I, O, O]] {
        // TODO Find a way to simplify this forward of messages

        private val inIn   = Inlet.create[I]("in.in")
        private val inOut  = Outlet.create[I]("in.out")
        private val outIn  = Inlet.create[O]("out.in")
        private val outOut = Outlet.create[O]("out.out")

        val shape: BidiShape[I, I, O, O] =
          BidiShape(inIn, inOut, outIn, outOut)

        def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
          // This following `setHandler`s are just to forward requests and responses.
          // The real value of this flow is `preStart` and postStop` hooks override.
          // The general handlers and its interactions is based on Kamon's akka instrumentation.

          setHandler(
            inIn,
            new InHandler {
              def onPush(): Unit                    = push(inOut, grab(inIn))
              override def onUpstreamFinish(): Unit = complete(inOut)
            }
          )

          setHandler(
            inOut,
            new OutHandler {
              def onPull(): Unit                                      = pull(inIn)
              override def onDownstreamFinish(cause: Throwable): Unit = cancel(inIn)
            }
          )

          setHandler(
            outIn,
            new InHandler {
              def onPush(): Unit                    = push(outOut, grab(outIn))
              override def onUpstreamFinish(): Unit = completeStage()
            }
          )

          setHandler(
            outOut,
            new OutHandler {
              def onPull(): Unit                                      = pull(outIn)
              override def onDownstreamFinish(cause: Throwable): Unit = cancel(outIn)
            }
          )

          override def preStart(): Unit = onPreStart()

          override def postStop(): Unit = onPostStop()

        }
      }
    )

}
