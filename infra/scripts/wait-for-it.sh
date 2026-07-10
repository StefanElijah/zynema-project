#!/bin/bash
# wait-for-it.sh - Wait for a host:port to be available
# Usage: ./wait-for-it.sh host:port [-t timeout] [-- command args]

set -e

HOSTPORT="${1:?Usage: $0 host:port [-t timeout] [-- command args]}"
TIMEOUT="${TIMEOUT:-30}"

HOST="${HOSTPORT%%:*}"
PORT="${HOSTPORT##*:}"

echo "Waiting for $HOST:$PORT (timeout: ${TIMEOUT}s)..."

for i in $(seq 1 $TIMEOUT); do
  if nc -z "$HOST" "$PORT" 2>/dev/null; then
    echo "$HOST:$PORT is ready after ${i}s"
    exec "${@:2}"
  fi
  sleep 1
done

echo "Timeout waiting for $HOST:$PORT" >&2
exit 1
