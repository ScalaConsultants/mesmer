| CI | Release | Snapshot |
| --- | --- | --- |
| ![Scala CI][Badge-CI] | [![Release Artifacts][badge-releases]][link-releases] | [![Snapshot Artifacts][badge-snapshots]][link-snapshots] |

# Mesmer Akka Agent

Mesmer Akka Agent is an [OpenTelemetry](https://opentelemetry.io/) instrumentation library for [Akka](https://akka.io/)
applications.

## Compatibility

Mesmer has been tested with:

- Scala: 2.13.x
- Akka Actors: 2.6.8, 2.6.9, 2.6.10, 2.6.11, 2.6.12, 2.6.13, 2.6.14
- Akka HTTP: 10.1.8, 10.2.0, 10.2.1, 10.2.2, 10.2.3, 10.2.4
- JVM: 1.8+

## Getting started

Mesmer consists of two parts:

- Akka extension - that runs in the background and is responsible for exporting the metrics to your chosen backend
- JVM agent - that instruments Akka classes to expose metrics for the extension

Both parts need to be included in the application for Mesmer to work.

### Akka extension:

Add the following dependency to your `build.sbt` file:

```
libraryDependencies += "io.scalac" %% "mesmer-akka-extension" % "<version>"
```

Add this entry to your `application.conf`:

    akka.actor.typed.extensions= ["io.scalac.mesmer.extension.AkkaMonitoring"]

### JVM agent:

Download the latest agent jar from https://github.com/ScalaConsultants/mesmer-akka-agent/releases and add a parameter
when running your JVM:

    java -javaagent {PATH_TO_JAR}/mesmer-akka-agent.jar

where `PATH_TO_JAR` is your **absolute** path to the Mesmer agent jar.

### Exporter:

Since neither the Mesmer agent nor the Mesmer Akka Extension set up the Open Telemetry SDK, you need to do that in your
app. Only then your application will send your metrics to the Open Telemetry Collector.

For example, you can set up your project with an OTLP Metrics Exporter. To do that you will need to implement an Open
Telemetry Configuration Provider:

1. add the necessary dependencies in your build.sbt file:

```
"io.opentelemetry" % "opentelemetry-exporter-otlp-metrics" % <version>,
"io.opentelemetry" % "opentelemetry-sdk" % <version>
```

2. implement the `OpenTelemetryProvider` interface similarly to what's done in `OpenTelemetrySetup` class (the example
   application),
3. don't forget to make the service provider discoverable - add
   the `META-INF/services/io.scalac.mesmer.extension.opentelemetry.OpenTelemetryProvider` in the application code,
   pointing to your OpenTelemetry Provider class.

## Supported metrics

Mesmer currently supports the following Akka modules:

- Akka (core)
- Akka Cluster
- Akka Persistence
- Akka Streams

For a detailed list of metrics go to [metrics](metrics.md)

# Architecture

See [overview](https://github.com/ScalaConsultants/mesmer-akka-agent/blob/main/extension_overview.png).

//TODO

# Local testing

`example` subproject contains a test application that uses Akka Cluster sharding with Mesmer Akka Agent extension.
Go [here](example/README.md) for more information.

# Contributor setup

1. Install [pre-commit](https://pre-commit.com/)
2. Run `pre-commit install`
3. If you're using Intelij Idea:
    - Download "google-java-format" plugin and use it
    - Go to "Editor" -> "Code Style" -> "YAML". Uncheck "Indent sequence value" and "Brackets" (in the "Spaces" menu)

[Badge-CI]: https://github.com/ScalaConsultants/mesmer-akka-agent/workflows/Scala%20CI/badge.svg

[badge-releases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/io.scalac/mesmer-akka-extension_2.13 "Sonatype Releases"

[badge-snapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/io.scalac/mesmer-akka-extension_2.13 "Sonatype Snapshots"

[link-releases]: https://oss.sonatype.org/content/repositories/releases/io/scalac/mesmer-akka-extension_2.13/ "Sonatype Releases"

[link-snapshots]: https://oss.sonatype.org/content/repositories/snapshots/io/scalac/mesmer-akka-extension_2.13/ "Sonatype Snapshots"
