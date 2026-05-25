#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "Stopping db container and removing pgdata volume..."
docker compose down db -v

echo "Starting db (init scripts will run on fresh volume)..."
docker compose up -d db

echo "Waiting for db to be healthy..."
until docker compose ps db --format '{{.Health}}' 2>/dev/null | grep -q 'healthy'; do
    sleep 1
done

echo "Database is ready. Init scripts applied."
