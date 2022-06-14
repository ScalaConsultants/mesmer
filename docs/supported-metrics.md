---
sidebar_position: 3
---

# Supported metrics

In Mesmer we support 3 types of metrics:

* gauge - for sampled values
* counter - monotonic counter
* histograms - for recording value distributions

## Akka core

| Metric             | Type    |
|--------------------|---------|
| Running actors     | gauge   |
| Mailbox size       | gauge   |
| Stashed messaged   | counter |
| Mailbox time       | gauge   |
| Processed messages | counter |
| Processing time    | gauge   |
| Sent messages      | counter |

## Akka Cluster

| Metric                | Type    |
|-----------------------|---------|
| Shards per region     | gauge   |
| Reachable nodes       | gauge   |
| Unreachable nodes     | gauge   |
| Entities per region   | gauge   |
| Shard regions on node | gauge   |
| Entities on node      | gauge   |
| Nodes down            | counter |

## Akka Persistence

| Metric                 | Type      |
|------------------------|-----------|
| Persisted events       | histogram |
| Event persistence time | histogram |
| Recovery total         | counter   |
| Recovery time          | histogram |
| Snapshots              | counter   |

## Akka Streams (experimental)

| Metric                       | Type    |
|------------------------------|---------|
| Running streams              | gauge   |
| Running operators per stream | gauge   |
| Running operators            | gauge   |
| Stream throughput            | counter |
| Operator throughput          | counter |
| Operator processing time     | counter |
