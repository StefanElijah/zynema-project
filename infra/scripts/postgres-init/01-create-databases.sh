#!/bin/bash
# Creates one database per microservice on first postgres startup.
# Triggered by /docker-entrypoint-initdb.d in the postgres image.

set -e

# Read DB names from POSTGRES_MULTIPLE_DATABASES env var (space- or comma-separated)
if [ -z "$POSTGRES_MULTIPLE_DATABASES" ]; then
  echo "POSTGRES_MULTIPLE_DATABASES is not set. Skipping multi-DB init."
  exit 0
fi

for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE "$db";
EOSQL
done

echo "All databases created."
