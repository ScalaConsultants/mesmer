![Scala CI](https://github.com/ScalaConsultants/mesmer-akka-agent/workflows/Scala%20CI/badge.svg)

# Mesmer Akka Agent

Mesmer Akka Agent is an [OpenTelemetry](https://opentelemetry.io/) instrumentation library for [Akka](https://akka.io/) applications. 

## Compatibility

Mesmer has been tested with:
- Scala: 2.13.x
- Akka Actors: 2.6.8, 2.6.9, 2.6.10, 2.6.11, 2.6.12
- Akka HTTP: 10.1.8, 10.2.0, 10.2.1, 10.2.2
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

    akka.actor.typed.extensions= ["io.scalac.mesmer.extension.Mesmer"] 

### JVM agent:

Download the latest agent jar from https://github.com/ScalaConsultants/mesmer-akka-agent/releases and add a parameter when running your JVM:

    java -javaagent {PATH_TO_JAR}/mesmer-akka-agent.jar

where `PATH_TO_JAR` is your **absolute** path to the Mesmer agent jar.

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

`example` subproject contains a test application that uses Akka Cluster sharding with Mesmer Akka Agent extension. Go [here](example/README.md) for more information.
