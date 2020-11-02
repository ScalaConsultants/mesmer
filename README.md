# akka-monitoring

# Architecture 

See [overview](https://github.com/ScalaConsultants/akka-monitoring/blob/main/extension_overview.png).

Akka monitoring is an Akka extension allowing to monitor akka ecosystem telemetry data and events.

# Opentelemtry

Akka extension uses opentelemtry to allow end user choose where data will be stored. This means that application using akka extension should include opentelemetry SDK and configure appropriate exporter. If no exporter is configured, default NOOP exporter is in use.

# Local testing

There is subproject test_app that contains simple application that uses akka cluster sharding and uses akka metrics extension. It requires postgres instance running with database akka created (schema will be created automatically)

## New Relic agent

This is not required to run this with NR agent, as this tool functionality should be orthogonal to NR agent. Nonetheless, `test_app` is tested unded New Relic java agent version `6.0.0`.