package io.scalac.mesmer.otelextension.instrumentations.akka.http

import java.security.ProtectionDomain

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import net.bytebuddy.agent.builder.AgentBuilder.Transformer
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.SuperMethodCall
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.utility.JavaModule

import io.scalac.mesmer.instrumentation.http.impl.OverridingRawPathMatcher

object PathMatching {
  val uuidPathMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$")
      .or(ElementMatchers.named[TypeDescription]("akka.http.scaladsl.server.Directives$"))

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.named[MethodDescription]("akka$http$scaladsl$server$PathMatchers$_setter_$JavaUUID_$eq"),
        "io.scalac.mesmer.instrumentation.http.impl.UuidPathTemplateAdvice"
      )
  }

  val doublePathMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$")
      .or(ElementMatchers.named[TypeDescription]("akka.http.scaladsl.server.Directives$"))

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.named[MethodDescription]("akka$http$scaladsl$server$PathMatchers$_setter_$DoubleNumber_$eq"),
        "io.scalac.mesmer.instrumentation.http.impl.DoubleTemplateAdvice"
      )
  }

  val neutralPathMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$")
      .or(ElementMatchers.named[TypeDescription]("akka.http.scaladsl.server.Directives$"))

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.named[MethodDescription]("akka$http$scaladsl$server$PathMatchers$_setter_$Neutral_$eq"),
        "io.scalac.mesmer.instrumentation.http.impl.NeutralTemplateAdvice"
      )
  }

  val slashPathMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$Slash$")

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.isConstructor[MethodDescription],
        "io.scalac.mesmer.instrumentation.http.impl.SlashPathTemplateAdvice"
      )
  }

  val pathEndMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$PathEnd$")

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.isConstructor[MethodDescription],
        "io.scalac.mesmer.instrumentation.http.impl.EmptyTemplateAdvice"
      )
  }

  val segmentPathMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$Segment$")

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.isConstructor[MethodDescription],
        "io.scalac.mesmer.instrumentation.http.impl.WildcardTemplateAdvice"
      )
  }

  val numberPathMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$IntNumber$")
      .or[TypeDescription](
        ElementMatchers
          .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$LongNumber$")
      )
      .or[TypeDescription](
        ElementMatchers
          .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$HexIntNumber$")
      )
      .or[TypeDescription](
        ElementMatchers
          .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$HexLongNumber$")
      )

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.isConstructor[MethodDescription],
        "io.scalac.mesmer.instrumentation.http.impl.NumberTemplateAdvice"
      )
  }

  val remainingPathMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.PathMatchers$Remaining$")

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.isConstructor[MethodDescription],
        "io.scalac.mesmer.instrumentation.http.impl.WildcardTemplateAdvice"
      )
  }

  val segmentRoute: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.server.ImplicitPathMatcherConstruction")

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers
          .named[MethodDescription]("_segmentStringToPathMatcher")
          .and[MethodDescription](ElementMatchers.not[MethodDescription](ElementMatchers.isStatic[MethodDescription])),
        "io.scalac.mesmer.instrumentation.http.impl.StaticSegmentPathTemplateAdvice"
      )
  }

  val applyPathMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers.hasSuperType[TypeDescription](
      ElementMatchers
        .named[TypeDescription]("akka.http.scaladsl.server.PathMatcher")
    )

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.named[MethodDescription]("apply"),
        "io.scalac.mesmer.instrumentation.http.impl.PathMatcherApplyAdvice"
      )
  }

  val mapMatchedMatching: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers.hasSuperType[TypeDescription](
      ElementMatchers
        .named[TypeDescription]("akka.http.scaladsl.server.PathMatcher$Matched")
    )

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers
          .named[MethodDescription]("map")
          .or[MethodDescription](ElementMatchers.named[MethodDescription]("flatMap")),
        "io.scalac.mesmer.instrumentation.http.impl.MapMatchedMatchingAdvice"
      )
  }

  val andThenMatchedMatching: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers.hasSuperType[TypeDescription](
      ElementMatchers
        .named[TypeDescription]("akka.http.scaladsl.server.PathMatcher$Matched")
    )

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.named[MethodDescription]("andThen"),
        "io.scalac.mesmer.instrumentation.http.impl.AndThenMatchedMatchingAdvice"
      )
  }

  val asyncHandler: TypeInstrumentation = new TypeInstrumentation {
    override def typeMatcher(): ElementMatcher[TypeDescription] =
      ElementMatchers.named[TypeDescription]("akka.http.scaladsl.HttpExt")

    override def transform(transformer: TypeTransformer): Unit = transformer
      .applyAdviceToMethod(
        ElementMatchers
          .named[MethodDescription]("bindAndHandleAsync")
          .and[MethodDescription](
            ElementMatchers
              .takesArgument[MethodDescription](0, ElementMatchers.named[TypeDescription]("scala.Function1"))
          ),
        "io.scalac.mesmer.instrumentation.http.impl.AsyncHandlerAdvice"
      )
  }

  val rawMatcher: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] =
      ElementMatchers
        .hasSuperType[TypeDescription](
          ElementMatchers.named[TypeDescription]("akka.http.scaladsl.server.directives.PathDirectives")
        )
        .and[TypeDescription](ElementMatchers.not[TypeDescription](ElementMatchers.isAbstract[TypeDescription]))

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyTransformer {
        new Transformer {
          override def transform(
            builder: DynamicType.Builder[_],
            typeDescription: TypeDescription,
            classLoader: ClassLoader,
            module: JavaModule,
            protectionDomain: ProtectionDomain
          ): DynamicType.Builder[_] = builder
            .method(ElementMatchers.named[MethodDescription]("rawPathPrefix"))
            .intercept(Advice.to(classOf[OverridingRawPathMatcher]).wrap(SuperMethodCall.INSTANCE))
        }

      }
  }

}
