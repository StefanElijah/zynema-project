# K8s base manifests

These manifests are **documented**, not deployed. They serve as:

1. Reference for the production target architecture.
2. A starting point if/when this project is deployed to a real cluster.

## Structure

```
infra/k8s/
├── base/                  # Cluster-agnostic manifests
│   ├── eureka.yaml
│   ├── config-server.yaml (TODO: Fase 1)
│   ├── api-gateway.yaml   (TODO: Fase 1)
│   └── ...                (one per service)
└── overlays/              # Environment-specific patches (TODO: Fase 10)
    ├── dev/
    ├── staging/
    └── prod/
```

## Apply locally (minikube/kind)

```bash
# Create namespace
kubectl create namespace zynema

# Apply base
kubectl apply -f infra/k8s/base/ -n zynema

# Verify
kubectl get pods -n zynema
```

## Why not deployed yet

- Local Docker Compose is sufficient for development.
- K8s adds operational complexity not justified at this stage.
- See ADR-0001 (Monorepo Nx) and ADR-0002 (Keycloak day-1) for context.
