---
sidebar_position: 1
---

# Introduction

Mesmer is an [OpenTelemetry](https://opentelemetry.io/) auto-instrumentation library for Scala applications.

Auto-instrumentation means it will automatically detect the libraries you use and will instrument them at the bytecode level to return meaningful metrics to the backend of your chosing.

OpenTelemetry is a set of tools, APIs and SDKs that provide standardized approach to telemetry including: instrumenting, generating, collecting and exporting telemetry data.

Mesmer is designed as an extension for the OpenTelemetry Java agent. This way you will benefit from both the general Java APIs metrics that are still useful in Scala (like JDBC or Netty) and the specific Scala instrumentation provided by Mesmer (like Akka or ZIO).
