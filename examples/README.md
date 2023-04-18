# Mesmer Example Applications

## Setup

Run `docker-compose up` in the `docker` directory.

This will set up everything needed by the application:

- PostgresSQL database with Akka Persistence Journal
- OpenTelemetry Collector that will receive metrics from the application
- Prometheus that will receive the metrics from the collector and will allow to display them in the simplest form
- Grafana. It's an extra dashboard that uses metrics gathered in Prometheus to display something more useful than bare
  data (Averages, Requests per second etc). It shows graphs based both on Mesmer-provided (Akka Persistence + Actor
  Metrics) and OpenTelemetry-provided metrics (Akka Http).

## Run the application

```
./gradlew :examples:akka:run
```

or (for Akka Streaming example)

```
./gradlew :examples:akka-stream:run:wq
```

or (for ZIO 2.0.0 example)

```
./gradlew :examples:zio:run
```

## In case you are running the non-streaming example: call the endpoints

You can now interact with the application to generate some traffic and metrics:

1. Get account balance:

```
curl -X GET http://localhost:8080/api/v1/account/{UUID}/balance
```

2. Deposit funds:

```
curl -X POST http://localhost:8080/api/v1/account/{UUID}/deposit/100
```

3. Withdraw funds:

```
curl -X POST http://localhost:8080/api/v1/account/{UUID}/withdraw/100
```

If the account with the given UUID doesn't exist, it will be automatically created.
