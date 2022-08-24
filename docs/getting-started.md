---
sidebar_position: 2
---

# Getting started

Here you will find instructions on how to add OpenTelemetry Agent with Mesmer extension to your application and make it export metrics to your chosen backend service.

OpenTelemetry supports exporting telemetry data with multiple different protocols. You will find the list of exporters and their configuration options [here](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#exporters).

This guide presents steps for Prometheus and OTEL (native OpenTelemetry protocol) exporters.

## Basic setup

1. Download [opentelemetry-javaagent.jar](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.13.1/opentelemetry-javaagent.jar) from `opentelemetry-java` Releases.

2. Download [mesmer-otel-extension.jar](https://github.com/ScalaConsultants/mesmer/releases/download/v0.8.0.RC1/mesmer-otel-extension.jar) from `mesmer` Releases.

3. Add the dependency on OpenTelemetry autoconfiguration extension to your `build.sbt` file:
   ```scala
   libraryDependencies ++= Seq(
     "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.13.0-alpha"
   )
   ```

4. Add an exporter for your favorite protocol to your `build.sbt` file:
   
   **For OTLP**
   ```scala
   libraryDependencies ++= Seq(
     "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.13.0",
   )
   ```

   **For Prometheus**
   ```scala
   libraryDependencies ++= Seq(
     "io.opentelemetry" % "opentelemetry-exporter-prometheus" % "1.13.0-alpha".
   )
   ```

5. Run the application with the following options:
   ```sh
   -javaagent:path/to/opentelemetry-javaagent.jar
   -Dotel.javaagent.extensions=mesmer-otel-extension.jar
   # add the following option if you're using Prometheus exporter
   -Dotel.metrics.exporter=prometheus
   ```
   Eg. if you're running the application with `sbt run` add this to `build.sbt` file:
   ```scala
   run / fork := true
   run / javaOptions ++= Seq(
     "-javaagent:path/to/opentelemetry-javaagent.jar",
     "-Dotel.javaagent.extensions=path/to/mesmer-otel-extension.jar",
     // add the following option if you're using Prometheus exporter
     "-Dotel.metrics.exporter=prometheus"
   )
   ```
   or if you're running your application as a `jar` from command line:
   ```sh
   java \
     -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=path/to/mesmer-otel-extension.jar \
     # add the following option if you're using Prometheus exporter
     -Dotel.metrics.exporter=prometheus \
     -jar your-app.jar
   ```

6. Test it:
   
   **For OTLP**

   You need to have a running OTLP Collector for the metrics to be automatically streamed to it.

   **For Prometheus**

   Call the metrics endpoint:
   ```sh
   curl -i http://localhost:9464
   ```

## Akka-specific setup

Add Mesmer Akka extension:

   Add the following dependency to your `build.sbt` file:
   ```scala
   libraryDependencies += "io.scalac" %% "mesmer-akka-extension" % "0.8.0.RC1"
   ```

   Add this entry to your `application.conf`:
   ```
   akka.actor.typed.extensions = ["io.scalac.mesmer.extension.AkkaMonitoring"]
   ```

## ZIO-specific setup

Enable all available ZIO metrics by adding the following layers to your program:
- `Runtime.enableRuntimeMetrics`
- `DefaultJvmMetrics.live.unit`

Eg.
```scala
myProgram.provide(
  Runtime.enableRuntimeMetrics,
  DefaultJvmMetrics.live.unit,
)
```

For full reference see this ZIO 2.0 SampleApp code:

https://github.com/zio/zio-metrics-connectors/blob/zio/series2.x/core/jvm/src/test/scala/zio/metrics/connectors/SampleApp.scala#L15-L71

**Important for v0.8.0.RC1**

At this moment (v0.8.0.RC1) Mesmer is closely bound with Akka. This is something we're working on, but until then there's a specific step that needs to be made in non-Akka applications. The application needs to be run with these additional parameters (that will turn off Akka instrumentation):
```sh
-Dotel.instrumentation.mesmer-akka-actor.enabled=false
-Dotel.instrumentation.mesmer-akka-http.enabled=false
-Dotel.instrumentation.mesmer-akka-persistence.enabled=false
-Dotel.instrumentation.mesmer-akka-stream.enabled=false
```
