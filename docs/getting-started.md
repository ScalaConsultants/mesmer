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

3. Run your application with OT Agent `-javaagent` and Mesmer extension `-Dotel.javaagent.extensions` attached.
   ```sh
   java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=path/to/mesmer-otel-extension.jar \
     -jar your-app.jar
   ```

   If you starting your application using `sbt run` you also can update your `build.sbt` with the following settings:
   ```scala
   run / fork := true
   run / javaOptions ++= Seq(
     "-javaagent:path/to/opentelemetry-javaagent.jar",
     "-Dotel.javaagent.extensions=path/to/mesmer-otel-extension.jar",
   )
   ```

4. By default the agent configured to use OTLP exporter which pushes metrics to the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/) running at `http://localhost:4317`. See the collector documentation for the configuration options.

   Alternatively you can configure the agent to expose the metrics with Prometheus exporter `-Dotel.metrics.exporter=prometheus`. This will make metrics available for scrapping over HTTP at default port 9464.

   ```sh
   curl -i http://localhost:9464
   ```

**Important for v0.8.0.RC1**

At this moment (v0.8.0.RC1) Mesmer is closely bound with Akka. This is something we're working on, but until then there's a specific step that needs to be made in non-Akka applications. The application needs to be run with these additional parameters (that will turn off Akka instrumentation):
```sh
-Dotel.instrumentation.mesmer-akka-actor.enabled=false
-Dotel.instrumentation.mesmer-akka-http.enabled=false
-Dotel.instrumentation.mesmer-akka-persistence.enabled=false
-Dotel.instrumentation.mesmer-akka-stream.enabled=false
```
