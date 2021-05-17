# Mesmer Example Application

Example application using Akka HTTP and Akka Persistence with Mesmer instrumentation.

## Setup

Run `docker-compose up` in the `docker` directory.

This will set up everything needed by the application:
- PostgreSQL database with Akka Persistence Journal
- OpenTelemetry Collector - that will receive metrics from the application
- Prometheus - that will receive the metrics from the collector

## Run the application

To run the application with default settings:
```
sbt "project example" runWithAgent
```

## Call the endpoints

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
