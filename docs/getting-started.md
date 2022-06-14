---
sidebar_position: 2
---

# Getting started

Mesmer consists of two parts:

- **Akka Extension** - that runs in the background and is responsible for exporting the metrics to your chosen backend
- **OpenTelemetry Agent Extension** - that instruments Akka classes to expose metrics for the Extension

Both parts need to be included in the application for Mesmer to work.

### Akka extension

Add the following dependency to your `build.sbt` file:

```
libraryDependencies += "io.scalac" %% "mesmer-akka-extension" % "<version>"
```

Add this entry to your `application.conf`:

    akka.actor.typed.extensions= ["io.scalac.mesmer.extension.AkkaMonitoring"]

### OpenTelemetry Extension

Download the latest OTEL Extension fat jar from Maven repository and add a parameter when running your JVM:

```
    java -javaagent:opentelemetry-javaagent110.jar \ -- this is the OpenTelemetry Agent
    -Dotel.javaagent.extensions=mesmer-otel-extension-assembly.jar -- this is our OTEL Agent Extension fat jar
```

### Exporter

Mesmer itself uses only OpenTelemetry API - it's the user's responsibility to setup the OpenTelemetry SDK.

We highly recommend using
the [OpenTelemetry Sdk Autoconfigure](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure)
artifact. We use it in our example application too. It will set up the OpenTelemetry SDK and an Exporter for you and
will provide you with sensible default settings for it.
