| CI | Release | Snapshot |
| --- | --- | --- |
| ![Scala CI][Badge-CI] | [![Release Artifacts][badge-releases]][link-releases] | [![Snapshot Artifacts][badge-snapshots]][link-snapshots] |

# Mesmer

Mesmer is an [OpenTelemetry](https://opentelemetry.io/) instrumentation library for [Akka](https://akka.io/)
applications.

## Compatibility

Mesmer has been tested with:

- Scala: 2.13.x
- Akka Actors: 2.6.8, 2.6.9, 2.6.10, 2.6.11, 2.6.12, 2.6.13, 2.6.14
- JVM: 1.8+

## Getting started

Mesmer consists of two parts:

- Akka Extension - that runs in the background and is responsible for exporting the metrics to your chosen backend
- OpenTelemetry Agent Extension - that instruments Akka classes to expose metrics for the Extension

Both parts need to be included in the application for Mesmer to work.

### Akka extension:

Add the following dependency to your `build.sbt` file:

```
libraryDependencies += "io.scalac" %% "mesmer-akka-extension" % "<version>"
```

Add this entry to your `application.conf`:

    akka.actor.typed.extensions= ["io.scalac.mesmer.extension.AkkaMonitoring"]

### OpenTelemetry Extension:

Download the latest OTEL Extension jar from https://github.com/ScalaConsultants/mesmer/releases and add a parameter when
running your JVM:

```
    java -javaagent:opentelemetry-javaagent110.jar \ -- this is the OpenTelemetry Agent
    -Dotel.javaagent.extensions=mesmer-otel-extension.jar -- this is our OTEL Agent Extension
```

### Exporter:

Mesmer itself uses only OpenTelemetry API - it's the user's responsibility to setup the OpenTelemetry SDK.

We highly recommend using
the [OpenTelemetry Sdk Autoconfigure](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure)
artifact. We use it in our example application too. It will set up the OpenTelemetry SDK and an Exporter for you and
will provide you with sensible default settings for it.

## Supported metrics

For a detailed list of supported metrics go to [supported_metrics.md](supported_metrics.md)

# Local testing

`example` subproject contains a test application that uses Akka Cluster sharding with Mesmer Akka extension.
Go [here](example/README.md) for more information.

# Contributor setup

1. Install [pre-commit](https://pre-commit.com/)
2. Run `pre-commit install`
3. If you're using Intelij Idea:
    - Download "google-java-format" plugin and use it
    - Go to "Editor" -> "Code Style" -> "YAML". Uncheck "Indent sequence value" and "Brackets" (in the "Spaces" menu)

[Badge-CI]: https://github.com/ScalaConsultants/mesmer/workflows/Scala%20CI/badge.svg

[badge-releases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/io.scalac/mesmer-akka-extension_2.13 "Sonatype Releases"

[badge-snapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/io.scalac/mesmer-akka-extension_2.13 "Sonatype Snapshots"

[link-releases]: https://oss.sonatype.org/content/repositories/releases/io/scalac/mesmer-akka-extension_2.13/ "Sonatype Releases"

[link-snapshots]: https://oss.sonatype.org/content/repositories/snapshots/io/scalac/mesmer-akka-extension_2.13/ "Sonatype Snapshots"
