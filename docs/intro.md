---
sidebar_position: 1
---

# Introduction

Mesmer is an [OpenTelemetry](https://opentelemetry.io/) auto-instrumentation library for Scala applications.

Auto-instrumentation means it will automatically detect the libraries you use and will instrument them at the bytecode level to return meaningful metrics to the backend of your chosing.

OpenTelemetry is a set of tools, APIs and SDKs that provide standardized approach to telemetry including: instrumenting, generating, collecting and exporting telemetry data.

Mesmer is designed as an extension for the OpenTelemetry Java agent. This way you will benefit from both the general Java APIs metrics that are still useful in Scala (like JDBC or Netty) and the specific Scala instrumentation provided by Mesmer (like Akka or ZIO).

# Links to get started:

## ByteBuddy
This is the tool that we use to instrument java bytecode. It provides a declarative API and allows to register transformation on loaded classes.
main page tutorial, provides a good overview, but we don't use many features from here (and not all features that we use are there described) - https://bytebuddy.net/#/tutorial
https://www.youtube.com/watch?v=OF3YFGZcQkg - talk by the creator. Provides good introduction to Advice - tool that we heavily use in mesmer


## OpenTelemetry
Initiative that's goal is to create a spec for observable systems and provide tools to implement it for different languages.
java SDK - https://github.com/open-telemetry/opentelemetry-java
tool for manual instrumentation
java autoinstrumentation -  https://github.com/open-telemetry/opentelemetry-java-instrumentation
depends on the former
collection of auto instruments that integrates with an application seemlessly
our goal is to make mesmer a plugin for this
I recommend that check out the repository, but it's huge and requires a lot of time to index by IDE
conceptual introduction to OT https://www.youtube.com/watch?v=DbaO0Xxv34c


## Mesmer
https://github.com/ScalaConsultants/mesmer/blob/main/CONTRIBUTORS.md

https://scalac.io/blog/the-opentelemetry-mesmer-duo-state-of-the-mesmer-project/

https://scalac.io/mesmer-opentelemetry-extension/
