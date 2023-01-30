package io.scalac.mesmer.otelextension.instrumentations.akka.http

import akka.http.javadsl.server.Rejected
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.PathMatcher.Matched
import akka.http.scaladsl.server.PathMatcher.Unmatched
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.util.VirtualField

object OverridingRawPatchMatcherImpl {

  private lazy val matchingVirtualField: VirtualField[AnyRef, String] =
    VirtualField.find(
      Class.forName("akka.http.scaladsl.server.PathMatcher$Matching").asInstanceOf[Class[AnyRef]],
      classOf[String]
    )

  /**
   * This is the new implementation of rawPathPrefix - method that is used internally in akka for writing DSLS. We must
   * mimic what that Directive originally does and add extraction on matched template on top of that. Last step it to
   * revert it back to previous template if route end up being rejected. Check original implementation for more context
   * [[akka.http.scaladsl.server.directives.PathDirectives.rawPathPrefix]]
   */
  def rawPathPrefix[L](pm: PathMatcher[L]): Directive[L] = {
    implicit val LIsTuple = pm.ev
    extract { ctx =>
      val matched = pm(ctx.unmatchedPath)

      (matched, ctx)
    }.flatMap {
      case (matched @ Matched(rest, values), _) =>
        val routeTemplate = Context.current().get(RouteContext.routeKey)

        val matchingTemplateResult = matchingVirtualField.get(matched)

        val previous = for {
          current <- Option(matchingTemplateResult)
          p       <- Option(routeTemplate).map(_.append(current))
        } yield p

        tprovide(values) & mapRequestContext { ctx =>
          val context = ctx.withUnmatchedPath(rest)
          context
        } & mapRouteResult { result =>
          result match {
            case _: Rejected =>
              // bring back previous value
              previous.foreach(routeTemplate.set)

            case _ =>
          }
          result
        }
      case (Unmatched, _) =>
        reject
    }
  }

}
