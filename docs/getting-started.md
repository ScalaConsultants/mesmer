---
sidebar_position: 2
---

# Getting started

Here you will find instructions on how to add OpenTelemetry Agent with Mesmer extension to your application and make it export metrics in a Prometheus format.

In addition to OpenTelemetry components Mesmer consists of two parts:

- **Akka Extension** - that runs in the background and is responsible for exporting the metrics to your chosen backend
- **OpenTelemetry Agent Extension** - that instruments Akka classes to expose metrics for the Extension

Both parts need to be included in the application for Mesmer to work.

## Step by step guide

1. Add Mesmer Akka extension:

   Add the following dependency to your `build.sbt` file:
   ```scala
   libraryDependencies += "io.scalac" %% "mesmer-akka-extension" % "0.7.0"
   ```

   Add this entry to your `application.conf`:
   ```
   akka.actor.typed.extensions = ["io.scalac.mesmer.extension.AkkaMonitoring"]
   ```

2. Download [opentelemetry-javaagent.jar](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.12.0/opentelemetry-javaagent.jar) from `opentelemetry-java` Releases.

3. Download [mesmer-otel-extension.jar](https://github.com/ScalaConsultants/mesmer/releases/download/v0.7.0/mesmer-otel-extension.jar) from `mesmer` Releases.

4. Enable Prometheus exporter by adding the following dependencies to your `build.sbt` file:
   ```scala
   libraryDependencies ++= Seq(
     "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.13.0-alpha",
     "io.opentelemetry" % "opentelemetry-exporters-prometheus"        % "0.9.1"
   )
   ```

5. Run the application with the following options:
   ```sh
   -javaagent:path/to/opentelemetry-javaagent.jar
   -Dotel.javaagent.extensions=mesmer-otel-extension.jar
   ```
   Eg. if you're running the application with `sbt run` add this to `build.sbt` file:
   ```scala
   run / fork := true
   run / javaOptions ++= Seq(
     "-javaagent:path/to/opentelemetry-javaagent.jar",
     "-Dotel.javaagent.extensions=path/to/mesmer-otel-extension.jar"
   )
   ```
   or if you're running your application as a `jar` from command line:
   ```sh
   java \
     -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=path/to/mesmer-otel-extension.jar \
     -jar your-app.jar
   ```

6. Test the Prometheus endpoint:
   ```sh
   curl -i http://localhost:9464
   ```

## How to collect JVM Metrics from ZIO (for Mesmer):

In your ZIO Application, you will need to add the following layers:

- `Runtime.enableRuntimeMetrics`
- `DefaultJvmMetrics.live.unit`

For reference, please follow this ZIO 2.0 SampleApp code:

https://github.com/zio/zio-metrics-connectors/blob/zio/series2.x/core/jvm/src/test/scala/zio/metrics/connectors/SampleApp.scala#L15-L71
