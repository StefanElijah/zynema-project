# K8s base manifests

These manifests are **documented, not deployed**. They exist as a reference
for the production target and as a starting point for a real cluster.

## What exists

```
infra/k8s/
├── base/
│   ├── eureka.yaml        # the registry as a Deployment + Service
│   └── README.md          # this file
└── overlays/              # empty: environment patches belong here when they exist
```

Only the registry has a manifest so far. The CI validates whatever is in
`base/` against upstream schemas with **kubeconform** — no cluster required.

## Why only one manifest

Local development runs on Docker Compose, and the Compose file is the real
topology (profiles for core / auth / storage / observability). Translating
every service to K8s today would produce manifests nobody applies: they would
rot silently, which is worse than not having them.

The manifests that do exist are the ones whose K8s shape differs from
Compose in an instructive way (a StatefulSet-less Deployment with a stable
Service for peer discovery).

## What the full deployment would look like

- One Deployment + Service per Spring service; Flyway runs on startup as it
  does today; Kafka and PostgreSQL as managed services or operators.
- **Service discovery**: Eureka is replaced by K8s Services; Spring Cloud
  Kubernetes (or plain `kube-proxy`) takes over. ADR-0031 notes the
  consequence for Prometheus: `file_sd` disappears in favor of
  `kubernetes_sd_configs` / the Prometheus Operator.
- **Config**: the Config Server can stay (mounted ConfigMap) or give way to
  ConfigMaps + profiles.
- **Video**: the worker becomes a Job; MinIO becomes any S3-compatible
  endpoint (the code already talks S3, not MinIO).

## Apply locally (minikube/kind)

```bash
kubectl create namespace zynema
kubectl apply -f infra/k8s/base/ -n zynema
kubectl get pods -n zynema
```

See ADR-0001 (monorepo with Nx) and ADR-0002 (Keycloak day-1) for the
context of this decision.
