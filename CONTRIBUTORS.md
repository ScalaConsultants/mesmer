# How to contribute

Mesmer instruments Metrics using an OpenTelemetry Agent and our custom Opentelemetry Extension. Additionally, for Akka
Metrics we use an extra piece: an Akka Extension.

To learn more:

- [OpenTelemetry Javaagent](https://github.com/open-telemetry/opentelemetry-java-instrumentation#about)
- [OpenTelemetry Extension](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/examples/extension/README.md)
- [Akka Extension](https://doc.akka.io/docs/akka/current/extending-akka.html)

In a bird's eye view, the project works as follows: the OpenTelemetry Agent picks up our OTEL Extension to install all
bytecode modifications that need to be done to auto-instrument an application (ByteBuddy Advice classes + helper code).
The Advice classes send _Events_ to the Akka Extension that is essentially an Event Bus. Then the Akka Extension
processes the events and calls Opentelemetry API to report various metrics for you.

## How to add a new metric

Although adding a new metrics might be a little tricky, there are a couple of steps that are worth following:

* choose a metric that you want to instrument
* check which API of the instrumented library might expose this metric
* start with the tests first - check if you have an application that uses the chosen API. For exampleL if it's an Akka
  library, see if our example app uses that. If not, create a simple example app for testing (consider contributing it
  to the `example` module in Mesmer)
* answer the following question: what is the kind of the metric you wish to add? Is it a gauge, counter or histogram?
* dive into library source code - find fragments of code that you can hook on to extract a metric. This is a tricky part
  and in many cases you might be forced to change previously made decision (e.g. change the metric type). This might get
  overwhelming quickly, so try to deliver a working PoC rather than figuring out all the details upfront.
* write the byte buddy instrumentation code in the OTEL Extension. You will need to add an InstrumentationModule. See
  [MesmerAkkaPersistenceInstrumentationModule](https://github.com/ScalaConsultants/mesmer/blob/main/otel-extension/src/main/java/io/scalac/mesmer/otelextension/MesmerAkkaPersistenceInstrumentationModule.java)
  as an example.
* Adding instrumentation code varies from metric to metric, but there are a couple of rules of thumb:
    * don't use mutable state - currently preferred approach is to fire an event with `EventBus`. You can also use
      a [VirtualField](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation-api/src/main/java/io/opentelemetry/instrumentation/api/field/VirtualField.java)
      to store some state (attributes, context).
    * use
      ByteBuddy's [Advice API](https://javadoc.io/static/net.bytebuddy/byte-buddy/1.10.2/net/bytebuddy/asm/Advice.html)
    * use Java classes as last resort and try to make it as thin as possible - it's usually better to write logic with
      Scala code
* write monitor classes - current approach is not to use `opentelemetry` monitors directly but to use our `wrappers`
  which allowed for better testability. Check `io.scalac.mesmer.extension.metric` package in `extension` for current
  implementations.
* write an event handling class - currently those are solely actors, but this might change in the future. This is the
  part that should take previously defined monitor as a parameter and submit values to it after it's done doing
  calculations.
* remember about testing: for example see
  the [AkkaPersistenceAgentTest](https://github.com/ScalaConsultants/mesmer/blob/main/otel-extension/src/test/scala/io/scalac/mesmer/instrumentation/akka/persistence/AkkaPersistenceAgentTest.scala)
