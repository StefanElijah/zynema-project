import { writeFile } from 'node:fs/promises';

/**
 * Pulls the BFF's OpenAPI document into the committed snapshot.
 *
 * Run with a BFF listening (locally: `java -jar backend/bff-service/target/…`
 * or the docker compose stack) and it overwrites `openapi.json`; then
 * `pnpm api:generate` turns it into the typed client.
 */
const url = process.env.BFF_OPENAPI_URL ?? 'http://localhost:8086/v3/api-docs';

const response = await fetch(url);
if (!response.ok) {
  console.error(`GET ${url} answered ${response.status}. Is the BFF running?`);
  process.exit(1);
}

const spec = await response.json();
const target = new URL('../openapi.json', import.meta.url);
await writeFile(target, `${JSON.stringify(spec, null, 2)}\n`);

const paths = Object.keys(spec.paths ?? {}).length;
console.log(`openapi.json refreshed from ${url} (${paths} paths)`);
