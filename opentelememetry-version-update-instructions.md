# OpenTelemetry version update instructions

## Aggregation temporality

Tests in this repo require aggregation temporality to be set to `CUMULATIVE`. However, there was a change made
to the [opentelemetry-java-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation) agent-for-testing.jar
that set the temporality to `DELTA`. For this reason we have to build the .jar with temporality set to `CUMULATIVE` ourselves. Following are the steps:

1. Clone the https://github.com/open-telemetry/opentelemetry-java-instrumentation.
1. Checkout the tag of the version to which the update is being done.
1. Change [OtlpInMemoryMetricExporter.java](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/testing/agent-exporter/src/main/java/io/opentelemetry/javaagent/testing/exporter/OtlpInMemoryMetricExporter.java) 
   `getAggregationTemporality` to return `AggregationTemporality.CUMULATIVE`.
1. Run gradle task to build the jar - `:testing:agent-for-testing:jar`.
1. The jar can be found in `testing/agent-for-testing/build/libs/`.