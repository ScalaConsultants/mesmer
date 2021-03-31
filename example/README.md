
## Database setup

The application requires a PostgreSQL instance. The database has to contain the schema for Actor Persistence journal. You can find the relevant sql statements in docker/schema.sql.

If you want to run everything with default value you can just run `docker-compose up` in the `docker` directory.

## Application setup

If you're running the database with the default value you can just do `sbt run`.

Otherwise you might need to override the expected values in the application by setting some or all of the following environment variables:
- `DB_HOST` (default: `localhost`)
- `DB_PORT` (default: `5432`)
- `DB_NAME` (default: `akka`)
- `DB_USER` (default: `postgres`)
- `DB_PASS` (default: `12345`)
