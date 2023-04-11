---
sidebar_position: 1
---

# Supported metrics

## Akka core

| Metric             | OTel Type |
|--------------------|-----------|
| Running actors     | gauge     |
| Mailbox size       | gauge     |
| Stashed messaged   | counter   |
| Mailbox time       | gauge     |
| Processed messages | counter   |
| Processing time    | gauge     |
| Sent messages      | counter   |

## Akka Cluster

| Metric                | OTel Type |
|-----------------------|-----------|
| Shards per region     | gauge     |
| Reachable nodes       | gauge     |
| Unreachable nodes     | gauge     |
| Entities per region   | gauge     |
| Shard regions on node | gauge     |
| Entities on node      | gauge     |
| Nodes down            | counter   |

## Akka Persistence

| Metric                 | OTel Type |
|------------------------|-----------|
| Persisted events       | histogram |
| Event persistence time | histogram |
| Recovery total         | counter   |
| Recovery time          | histogram |
| Snapshots              | counter   |

## Akka Streams (experimental)

| Metric                       | OTel Type |
|------------------------------|-----------|
| Running streams              | gauge     |
| Running operators per stream | gauge     |
| Running operators            | gauge     |
| Stream throughput            | counter   |
| Operator throughput          | counter   |
| Operator processing time     | counter   |
