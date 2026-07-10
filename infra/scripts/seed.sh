#!/bin/bash
# seed.sh - Populate database with demo catalog data
# Run after services are up: ./infra/scripts/seed.sh

set -euo pipefail

CATALOG_URL="${CATALOG_URL:-http://localhost:8083}"
USER_URL="${USER_URL:-http://localhost:8081}"

echo "Seeding catalog with demo content..."

# Movies
for movie in "The Batman" "Dune" "Interstellar" "Superman 2025" "Predator Badlands"; do
  echo "  Adding movie: $movie"
  curl -sf -X POST "$CATALOG_URL/api/v1/catalog/movies" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$movie\",\"type\":\"MOVIE\"}" > /dev/null || echo "    (skipped: $movie)"
done

# Series
for series in "Arcane" "Invincible" "The Walking Dead" "Foundation" "Fallout"; do
  echo "  Adding series: $series"
  curl -sf -X POST "$CATALOG_URL/api/v1/catalog/series" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$series\",\"type\":\"SERIES\"}" > /dev/null || echo "    (skipped: $series)"
done

echo "Done. Verify at $CATALOG_URL/api/v1/catalog"
