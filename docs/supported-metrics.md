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

## ZIO (both Default-JVM-Metrics and others)

| Metric                                | Type      |
|---------------------------------------|-----------|
| Executor Capacity                     | gauge     |
| Executor Concurrency                  | gauge     |
| Executor Dequeued Count               | counter   |
| Executor Enqueued Count               | counter   |
| Executor Size                         | gauge     |
| Executor Worker Count                 | counter   |
| JVM Buffer Pool Capacity Bytes        | gauge     |
| JVM Buffer Pool Used Buffers          | gauge     |
| JVM Buffer Pool Used Bytes            | gauge     |
| JVM Classes Loaded                    | gauge     |
| JVM Classes Loaded Total              | gauge     |
| JVM Classes Unloaded Total            | gauge     |
| JVM GC Collection Seconds Count       | histogram |
| JVM GC Collection Seconds Sum         | histogram |
| JVM Info                              | gauge     |
| JVM Memory Bytes Committed            | gauge     |
| JVM Memory Bytes Init                 | gauge     |
| JVM Memory Bytes Max                  | gauge     |
| JVM Memory Bytes Used                 | gauge     |
| JVM Memory Pool Allocated Bytes Total | counter   |
| JVM Memory Pool Bytes Committed       | gauge     |
| JVM Memory Pool Bytes Init            | gauge     |
| JVM Memory Pool Bytes Max             | gauge     |
| JVM Memory Pool Bytes Used            | gauge     |
| JVM Threads Current                   | gauge     |
| JVM Threads Daemon                    | gauge     |
| JVM Threads Deadlocked                | gauge     |
| JVM Threads Deadlocked Monitor        | gauge     |
| JVM Threads Peak                      | gauge     |
| JVM Threads Started Total             | gauge     |
| Null                                  | counter   |
| Process CPU Seconds Total             | gauge     |
| Process Max FDS                       | gauge     |
| Process Open FDS                      | gauge     |
| Process Resident Memory Bytes         | gauge     |
| Process Start Time Seconds            | gauge     |
| Process Virtual Memory Bytes          | gauge     |
| ZIO Fiber Failures                    | counter   |
| ZIO Fiber Started                     | counter   |
| ZIO Fiber Successes                   | counter   |
