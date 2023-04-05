---
sidebar_position: 5
---

# ZIO Example

ZIO exposes a lot of information about its own and application internals out-of-the-box - like ZIO Executor metrics and JVM metrics. In addition integrated ZIO Metrics DSL allows us to create custom metrics measuring effects of our choice.

When combined with OpenTelemetry Java Agent we're provided with even more extensive data about different parts our application.

In this example we will:
- create a simple web application
- enable all the available ZIO internal metrics
- add additional custom ZIO Metrics
- run the application with OpenTelemetry Java Agent and Mesmer extension

The end result will be multiple metrics automatically exported to the metrics backend of our choice.

## Basic application

We start with a simple ZIO HTTP application serving a `/healthcheck` endpoint that answers with a `204 No Content` response.

First we need add our dependencies and create the application:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio"      % zioVersion,
  "dev.zio" %% "zio-http" % zioHttpVersion
```

```scala
import zio._
import zio.http._
import zio.http.model.{ Method, Status }

object AppZioHttp extends ZIOAppDefault {

  val routes = Http.collectZIO[Request] {
    case Method.GET -> !! / "healthcheck" =>
      ZIO.succeed(Response.status(Status.NoContent))
  }

  override val run = 
    Server.serve(routes).provide(Server.default)

}
```

## Enable ZIO Runtime and JVM metrics

```scala
import zio._
import zio.http._
import zio.http.model.{ Method, Status }
import zio.metrics.jvm.DefaultJvmMetrics

object AppZioHttp extends ZIOAppDefault {

  val routes = Http.collectZIO[Request] {
    case Method.GET -> !! / "healthcheck" =>
      ZIO.succeed(Response.status(Status.NoContent))
  }

  override val run = 
    Server.serve(routes).provide(
      Server.default,
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit
    )

}
```

## Add ZIO Metrics

Now let's add a simple metric that will count the requests:

```scala
import zio._
import zio.http._
import zio.http.model.{ Method, Status }
import zio.metrics._
import zio.metrics.jvm.DefaultJvmMetrics

object AppZioHttp extends ZIOAppDefault {

  val requestCount = 
  	Metric.counterInt("request_count").fromConst(1)
      .tagged(
        MetricLabel("method", "GET"),
        MetricLabel("handler", "/healthcheck")
      )

  val routes = Http.collectZIO[Request] {
    case Method.GET -> !! / "healthcheck" =>
      ZIO.succeed(Response.status(Status.NoContent)) @@ requestCount
  }

  override val run = 
    Server.serve(routes).provide(
      Server.default,
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit
    )

}
```

From the application code perspective this is all we need to export metrics to an OpenTelemetry collector. The rest is about OpenTelemetry and Mesmer configuration.

## Running the application with Java agent

Right now our application doesn't export any metrics as it doesn't know anything about OpenTelemetry and has no ZIO Metrics exporter declared. And we want it to stay that way.

By running the application with OpenTelemetry Agent and Mesmer extension we achieve 2 things:
- OpenTelemetry Agent will auto-instrument all the supported libraries that it detects (in our case Netty is the significant one because of ZIO HTTP)
- Mesmer extension will auto-instrument all the metrics exposed by ZIO and translate it to OpenTelemetry instruments.

To run the application with Java agents from `sbt` add these lines to the `build.sbt` file:

```scala
run / fork := true
run / javaOptions ++= Seq(
  "-javaagent:path/to/opentelemetry-javaagent.jar",
  "-Dotel.javaagent.extensions=path/to/mesmer-otel-extension.jar",
)
```

You can find more information about downloading these files at the [Getting Started](getting-started.md) page.

## Running the OpenTelemetry Collector

By default Mesmer and OpenTelemetry agent will export metrics using OTLP over gRPC to an OTel Collector running at localhost.

To run OTel Collector using docker-compose add this to your docker-compose.yaml:

```yaml
version: '3.3'

services:
  otel-collector:
    container_name: my_otel_collector
    image: otel/opentelemetry-collector-contrib-dev:latest
    command: [--config=/etc/otel-collector-config.yaml, '']
    volumes:
    - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    ports:
    - 1888:1888     # pprof extension
    - 8888:8888     # Prometheus metrics exposed by the collector
    - 8889:8889     # Prometheus exporter metrics
    - 13133:13133   # health_check extension
    - 55681:55679   # zpages extension
    - 4317:4317     # otlp receiver
```

Docker-compose will expect `otel-collector-config.yaml` file to exist. 

This is an example configuration for a Collector exposing metrics in Prometheus format:

```yaml
receivers:
  otlp:
    protocols:
      grpc:

exporters:
  prometheus:
    endpoint: 0.0.0.0:8889
    namespace: testzioapp
  logging:
    loglevel: info

processors:
  batch:

extensions:
  health_check:
  pprof:
    endpoint: :1888
  zpages:
    endpoint: :55679

service:
  extensions: [pprof, zpages, health_check]
  pipelines:
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
```

More about OTel Collector configuration can found [here](https://opentelemetry.io/docs/collector/configuration/).

## Run the application

Now with all of the above set up we can run the application from `sbt`

```sh
sbt run
```

and make a couple of calls to the `/healthcheck` endpoint

```sh
curl http://localhost:8080/healthcheck
```

## Check the metrics

To get the metrics from OTel Collector we can use the same endpoint as Prometheus would use to acquire them:

```sh
curl http://localhost:8889/metrics
```

In the response body we will receive a list of metrics in the Prometheus format with different name prefixes. Following is the list of all of the metrics broken down by name prefix: 

### mesmer_zio_executor

ZIO Executor metrics exposed by ZIO

```
mesmer_zio_executor_dequeued_count - The number of tasks that have been dequeued, over all time.
mesmer_zio_executor_size           - The number of tasks remaining to be executed.
mesmer_zio_executor_worker_count   - The number of current live worker threads.
mesmer_zio_executor_enqueued_count - The number of tasks that have been enqueued, over all time.
mesmer_zio_executor_concurrency    - The concurrency level of the executor.
mesmer_zio_executor_capacity       - The capacity of the executor.
```

### mesmer_zio_forwarded

This name prefix includes runtime and JVM metrics (enabled by additional `ZLayers`) as well as the custom merics defined using ZIO Metrics DSL.

The custom `request_count` metric (in full Prometheus format):

```
mesmer_zio_forwarded_request_count_total{otel_scope_name="mesmer",handler="/healthcheck",method="GET"} 10.0 1680557471485
```

ZIO Runtime and JVM metrics:

```
mesmer_zio_forwarded_zio_fiber_successes_total 

mesmer_zio_forwarded_process_max_fds 
mesmer_zio_forwarded_process_resident_memory_bytes 
mesmer_zio_forwarded_process_open_fds 
mesmer_zio_forwarded_process_cpu_seconds_total 
mesmer_zio_forwarded_process_virtual_memory_bytes 
mesmer_zio_forwarded_process_start_time_seconds 

mesmer_zio_forwarded_jvm_buffer_pool_capacity_bytes 
mesmer_zio_forwarded_jvm_threads_started_total 
mesmer_zio_forwarded_jvm_memory_pool_bytes_used 
mesmer_zio_forwarded_jvm_threads_daemon 
mesmer_zio_forwarded_jvm_gc_collection_seconds_count 
mesmer_zio_forwarded_jvm_memory_pool_bytes_committed 
mesmer_zio_forwarded_jvm_threads_peak 
mesmer_zio_forwarded_jvm_memory_pool_collection_committed_bytes 
mesmer_zio_forwarded_jvm_memory_bytes_max 
mesmer_zio_forwarded_jvm_memory_pool_bytes_init 
mesmer_zio_forwarded_jvm_buffer_pool_used_buffers 
mesmer_zio_forwarded_jvm_memory_pool_bytes_max 
mesmer_zio_forwarded_jvm_memory_bytes_used 
mesmer_zio_forwarded_jvm_threads_current 
mesmer_zio_forwarded_jvm_classes_loaded 
mesmer_zio_forwarded_jvm_classes_loaded_total 
mesmer_zio_forwarded_jvm_memory_pool_collection_used_bytes 
mesmer_zio_forwarded_jvm_gc_collection_seconds_sum 
mesmer_zio_forwarded_jvm_memory_pool_allocated_bytes_total_total 
mesmer_zio_forwarded_jvm_classes_unloaded_total 
mesmer_zio_forwarded_jvm_memory_pool_collection_max_bytes 
mesmer_zio_forwarded_jvm_memory_pool_collection_init_bytes 
mesmer_zio_forwarded_jvm_threads_deadlocked_monitor 
mesmer_zio_forwarded_jvm_threads_state 
mesmer_zio_forwarded_jvm_threads_deadlocked 
mesmer_zio_forwarded_jvm_memory_objects_pending_finalization 
mesmer_zio_forwarded_jvm_buffer_pool_used_bytes 
mesmer_zio_forwarded_jvm_memory_bytes_committed 
mesmer_zio_forwarded_jvm_info 
mesmer_zio_forwarded_jvm_memory_bytes_init 
```

### http_server

Metrics provided by OpenTelemetry Agent's auto-instrumentation for Netty:

```
http_server_duration        - The duration of the inbound HTTP request
http_server_request_size    - The size of HTTP request messages
http_server_active_requests - The number of concurrent HTTP requests that are currently in-flight
``` 

### process_runtime

JVM metrics provided by OpenTelemetry Agent

```
process_runtime_jvm_threads_count              - Number of executing threads
process_runtime_jvm_classes_current_loaded     - Number of classes currently loaded
process_runtime_jvm_memory_committed           - Measure of memory committed
process_runtime_jvm_buffer_usage               - Memory that the Java virtual machine is using for this buffer pool
process_runtime_jvm_memory_usage               - Measure of memory used
process_runtime_jvm_system_cpu_load_1m         - Average CPU load of the whole system for the last minute
process_runtime_jvm_classes_unloaded_total     - Number of classes unloaded since JVM start
process_runtime_jvm_buffer_count               - The number of buffers in the pool
process_runtime_jvm_gc_duration                - Duration of JVM garbage collection actions
process_runtime_jvm_memory_init                - Measure of initial memory requested
process_runtime_jvm_memory_usage_after_last_gc - Measure of memory used after the most recent garbage collection event on this pool
process_runtime_jvm_system_cpu_utilization     - Recent cpu utilization for the whole system
process_runtime_jvm_buffer_limit               - Total capacity of the buffers in this pool
process_runtime_jvm_classes_loaded_total       - Number of classes loaded since JVM start
process_runtime_jvm_memory_limit               - Measure of max obtainable memory
process_runtime_jvm_cpu_utilization            - Recent cpu utilization for the process
```

## Next steps

TODO Connect Prometheus and Graphana
