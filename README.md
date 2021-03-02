# Mesmer Akka Agent

# Architecture 

See [overview](https://github.com/ScalaConsultants/mesmer-akka-agent/blob/main/extension_overview.png).

Mesmer Akka Agent is an Akka extension allowing to monitor Akka ecosystem telemetry data and events.

# OpenTelemetry

Mesmer Akka Agent uses [OpenTelemetry](https://opentelemetry.io/) to allow end user choose where data will be stored. This means that application using this extension should include OpenTelemetry SDK and configure appropriate exporter. If no exporter is configured, default NOOP exporter is in use.

# Local testing

There is subproject test_app that contains simple application that uses Akka cluster sharding and uses Akka Agent extension. It requires postgres instance running with database akka created (schema will be created automatically)

## New Relic agent

This is not required to run this with NR agent, as this tool functionality should be orthogonal to NR agent. Nonetheless, `test_app` is tested under New Relic Java agent version `6.0.0`.
