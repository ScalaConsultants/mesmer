

# How to contribute

Mesmer is a project to extract and export akka specific metrics.

Projects is comprised of 3 major parts:
- java agent (called `agent` from now on) - a piece of technology allowing to modify classes during load.
- akka extension (called `extension` from now on)  - this is where all custom metrics computation happens.
- application that is being instrumented - in repo we have the "example" app, but this in theory this could be any application that uses akka.

The most interesting parts are java agent and akka extension. They have to work in tandem - `agent` creates a foundation on which `extension` can build and calculate metrics.
It's good to use an example to get a better understanding: `agent` allows to trigger an event whenever akka persistence query starts and completes.
We've found places where those events should be triggered by looking at Akka source code and then modified necessary classes during load).
`extension` then captures those events, calculates the duration between the two, and passes this calculated metric (request time) in opentelemetry instrument. There will be more about opentelemetry role in the product later in this document.


## Extension

It's a part that is responsible for calculating and exporting metrics. The fact that it's an Akka extension is an implementation detail, but it's worth knowing that.
Akka extensions are plugins that grant additional functionality to Akka and can be enabled with configuration - this is the approach that we took.
At the start up of the extension we check for configured metrics that were enabled and start only those monitors that are required.
In our example monitors are akka system actors, but this is also an implementation detail.
As an example `io.scalac.mesmer.extension.PersistenceEventsActor$` is a monitor that listens for Akka Persistence events.
For conveyor of messages we use Reception


## Opentelemetry

It's a fusion of opentracing and openCensus. It's an specification and an implementation for creating, aggregating and publishing traces and metrics.
We use this extensively as it's makes publishing metrics to different backends (prometheus, datadog etc) easy and can take care of some aggregations (mostly histograms ATM).
Links to read more:
- https://opentelemetry.io/docs/instrumentation/java/
- https://github.com/open-telemetry/opentelemetry-specification
- https://github.com/open-telemetry/opentelemetry-java


## Agent

This is probably the most complicated part of mesmer development (there is an ongoing [proposal](https://github.com/ScalaConsultants/mesmer-akka-agent/discussions/272) to switch from our custom agent to opentelemetry one which would simplify development and enable tracing).
In some cases it requires writing `.java` files as scala doesn't allow some operations that are needed for class instrumentations.
We use [ByteBuddy](https://bytebuddy.net/#/) as a preferred tool for bytecode generation - it operates on a higher level than ASM and has an `Advice` abstraction - this can be thing as a blueprint that will be injected into loaded class file.

Links to read more:
- https://bytebuddy.net/#/tutorial
- https://www.youtube.com/watch?v=oflzFGONG08 - introduction to bytebuddy and advices by it's creator Rafael Winterhalter

## How to add a new metric

Although adding a new metrics might be a little tricky, there are a couple of steps that are worth following:

* choose a metric that you want to instrument
* check which api might expose this metric
* start with tests first - check if you have an application that use chosen API e.g. if it's an Akka library, see if our example app uses that.
If not, create a simple example app for testing (consider contributing it to the `examples` directory in Mesmer)
* choose what is the kind of metric e.g. is a gauge, counter or histogram?
* dive into library source code - find fragments of code that you can hook on to extract a metric.
This is a tricky part and in many cases you might be forced to change previously made decision (e.g. change the metric type).
This might get overwhelming quickly, so try to deliver a working PoC rather than figuring out all the details upfront.
* write `agent` instrumentation code - this varies from metric to metric, but there are a couple rules of thumb
  * don't use mutable state - currently preferred approach is to fire an event with `EventBus`
  * use `Advice` rather than `Intercept` API
  * use java classes as last resort and try to make it as thin as possible - it's usually better to write logic in scala functions
* write monitor classes - current approach is not to use `opentelemetry` monitors directly but to use our `wrappers` which allowed for better testability.
Check `io.scalac.mesmer.extension.metric` package in `extension` for current implementations.
* write an event handling class - currently those are solely actors, but this might change in the future. This is the part that should take
previously defined monitor as a parameter and submit values to it after it's done doing calculations.
* remember about tests for both `extension` and `agent` part
