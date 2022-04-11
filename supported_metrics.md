# Supported metrics

In mesmer we support 3 types of metrics:

* gauge - for sampled values
* counter - monotonic counter
* histograms - for recording value distributions

## Akka core

- Running actors - gauge
- Mailbox size - gauge
- Stashed messaged - counter
- Mailbox time - recorder
- Processed messages - counter
- Processing time - recorder
- Sent messages - counter

## Akka Cluster

- Shards per region - gauge
- Reachable nodes - gauge
- Unreachable nodes - gauge
- Entities per region - gauge
- Shard regions on node - gauge
- Entities on node - gauge
- Nodes down - counter

## Akka Persistence

- Persisted events - recorder
- Event persistence time - recorder
- Recovery total - counter
- Recovery time - recorder
- Snapshots - counter

## Akka Streams (experimental)

- Running streams - gauge
- Running operators per stream - gauge
- Running operators - gauge
- Stream throughput - counter
- Operator throughput - counter
- Operator processing time - counter
