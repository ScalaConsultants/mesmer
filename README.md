![Scala CI](https://github.com/ScalaConsultants/mesmer-akka-agent/workflows/Scala%20CI/badge.svg)

# Mesmer Akka Agent

Mesmer Akka Agent is an [OpenTelemetry](https://opentelemetry.io/) instrumentation library for [Akka](https://akka.io/) applications. 

Currently supports the following Akka modules and metrics:

### Akka core

- Running actors
- Mailbox size
- Stash size
- Mailbox time
- Processed messages
- Processing time
- Sent messages

### Akka Cluster

- Shards per region
- Reachable nodes
- Unreachable nodes
- Entities per region
- Shard regions on node
- Entities on node
- Nodes down

### Akka HTTP

- Connections
- Requests
- Responses
- Responses 2xx
- Responses 3xx
- Responses 4xx
- Responses 5xx
- Response time 
- Response time 2xx
- Response time 3xx
- Response time 4xx
- Response time 5xx
- Endpoint responses
- Endpoint responses 2xx 
- Endpoint responses 3xx 
- Endpoint responses 4xx 
- Endpoint responses 5xx 
- Endpoint response time 2xx
- Endpoint response time 3xx
- Endpoint response time 4xx
- Endpoint response time 5xx

### Akka Persistence

- Persisted events
- Event persistence time
- Recovery total
- Recovery time
- Snapshots

### Akka Streams

- Running streams
- Running operators per stream
- Running operators
- Stream throughput
- Operator throughput
- Operator processing time

## Getting started

//TODO

# Architecture 

See [overview](https://github.com/ScalaConsultants/mesmer-akka-agent/blob/main/extension_overview.png).

//TODO

# Local testing

`example` subproject contains a test application that uses Akka Cluster sharding with Mesmer Akka Agent extension. Go [here](example/README.md) for more information.
