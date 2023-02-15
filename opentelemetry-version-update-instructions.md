# OpenTelemetry version update instructions

1. Bump all opentelemetry-related version variables in Dependencies.scala file to the desired version
1. Download the opentelemetry-javaagent
   from [here](https://mvnrepository.com/artifact/io.opentelemetry.javaagent/opentelemetry-javaagent). Make sure to
   choose the latest version. Place it in the root dir of the project.
1. Build the agent-for-testing jar file. See the below instructions on how to do that. Place it in the root dir of the
   project.

## Agent for testing, workaround: how to build the agent-for-testing with CUMULATIVE aggregation mode?

Tests in this repo require aggregation temporality to be set to `CUMULATIVE`. However, there was a change made
to the [opentelemetry-java-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
agent-for-testing.jar that set the temporality to `DELTA` ([change reference](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/5923#discussion_r856619839)).
For this reason we have to build the .jar with temporality set to `CUMULATIVE` ourselves. Following are the steps:

1. Clone the https://github.com/open-telemetry/opentelemetry-java-instrumentation.
1. Checkout the tag of the version to which the update is being done.
1. Change [OtlpInMemoryMetricExporter.java](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/testing/agent-exporter/src/main/java/io/opentelemetry/javaagent/testing/exporter/OtlpInMemoryMetricExporter.java)
   `getAggregationTemporality()` to return `AggregationTemporality.CUMULATIVE`.
1. Run gradle task to build the jar - `:testing:agent-for-testing:jar`.
1. The jar can be found in `testing/agent-for-testing/build/libs/`.
