import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Regenerates Prometheus' file_sd targets from Eureka.
 *
 * Prometheus has no native Eureka service discovery, so the registry is
 * bridged the standard way: a small generator writes the target file and
 * Prometheus hot-reloads it. The registry is the source of truth (app name +
 * port of every UP instance), so a new service only needs to register.
 *
 * This is the development bridge (run it with `make refresh-targets`); a real
 * deployment would replace it with a controller that watches the registry, or
 * drop it entirely in favor of the environment's native SD (k8s) — see
 * ADR-0031. The committed snapshot keeps `docker compose up` working without
 * running this script.
 */
const eurekaUrl =
  process.env.EUREKA_URL ?? `http://localhost:${process.env.EUREKA_PORT ?? '8761'}/eureka/apps`;
const outFile = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../observability/targets/zynema.json',
);

let response;
try {
  response = await fetch(eurekaUrl, { headers: { Accept: 'application/json' } });
} catch (error) {
  const cause = error.cause?.code ?? error.message;
  console.error(`Could not reach ${eurekaUrl} (${cause}). Is Eureka running?`);
  process.exit(1);
}
if (!response.ok) {
  console.error(`GET ${eurekaUrl} answered ${response.status}. Is Eureka running?`);
  process.exit(1);
}

const registry = await response.json();
const applications = [].concat(registry.applications?.application ?? []);
const groups = [];

for (const app of applications) {
  const job = String(app.name ?? '').toLowerCase();
  const instances = [].concat(app.instance ?? []).filter((instance) => instance.status === 'UP');
  const targets = instances
    .map((instance) => `${instance.ipAddr}:${instance.port?.$ ?? instance.port}`)
    .filter((target) => !target.includes('undefined'));

  if (job && targets.length > 0) {
    groups.push({ targets, labels: { job, namespace: 'zynema' } });
  }
}

groups.sort((a, b) => a.labels.job.localeCompare(b.labels.job));
await mkdir(dirname(outFile), { recursive: true });
await writeFile(outFile, `${JSON.stringify(groups, null, 2)}\n`);

const total = groups.reduce((count, group) => count + group.targets.length, 0);
console.log(`targets/zynema.json refreshed from ${eurekaUrl} (${groups.length} jobs, ${total} instances)`);
