# Mesmer Akka Agent

## Supported metrics

In mesmer we support 3 types of metrics:
* gauge - counter that goes up and down
* counter - monotonic counter
* recorder - values are recorded with aggregation. Due to OpenTelemetry limitation currently only supported aggregation is MinMaxAvgSumCount aggregator.

### Akka core

- Running actors - gauge
- Mailbox size - gauge
- Stashed messaged - counter
- Mailbox time - recorder
- Processed messages - counter
- Processing time - recorder
- Sent messages - counter

### Akka Cluster

- Shards per region - gauge
- Reachable nodes - gauge
- Unreachable nodes - gauge
- Entities per region - gauge
- Shard regions on node - gauge
- Entities on node - gauge
- Nodes down - counter

### Akka HTTP

- Connections - gauge
- Requests - counter
- Responses - recorder (for all responses status)
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

- Persisted events - recorder
- Event persistence time - recorder
- Recovery total - counter 
- Recovery time - recorder
- Snapshots - counter

### Akka Streams

- Running streams - gauge
- Running operators per stream - gauge
- Running operators - gauge
- Stream throughput - counter
- Operator throughput - counter
- Operator processing time - counter
