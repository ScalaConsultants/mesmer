---
sidebar_position: 1
---

# Supported metrics

## ZIO Metrics API

| ZIO Metric Type                       | OTel Type   |
|---------------------------------------|-------------|
| Counter                               | counter     |
| Frequency                             | counter     |
| Gauge                                 | gauge       |
| Histogram                             | histogram   |
| Summary                               | unsupported |

## ZIO Executor Metrics

| Metric                                | OTel Type |
|---------------------------------------|-----------|
| Executor Capacity                     | gauge     |
| Executor Concurrency                  | gauge     |
| Executor Dequeued Count               | counter   |
| Executor Enqueued Count               | counter   |
| Executor Size                         | gauge     |
| Executor Worker Count                 | counter   |

## ZIO JVM Metrics

| Metric                                | OTel Type |
|---------------------------------------|-----------|
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

## ZIO Runtime Metrics

| Metric                                | OTel Type |
|---------------------------------------|-----------|
| Forwarded Null                        | counter   |
| Process CPU Seconds Total             | gauge     |
| Process Max FDS                       | gauge     |
| Process Open FDS                      | gauge     |
| Process Resident Memory Bytes         | gauge     |
| Process Start Time Seconds            | gauge     |
| Process Virtual Memory Bytes          | gauge     |
| ZIO Fiber Failures                    | counter   |
| ZIO Fiber Started                     | counter   |
| ZIO Fiber Successes                   | counter   |
