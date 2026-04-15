Dev Docker shortcuts

Run Redis for local development:

  docker compose -f ../../docker-compose.yml up redis

This will start a redis container bound to host port 6379 so the application (running locally) can connect to it using the defaults in src/main/resources/application.properties.

Notes:
- We intentionally expose Redis on 6379 in docker-compose for convenience.
- CI uses Testcontainers for tests and does not require this file.
