# Zynema — Architecture (C4 model)

This folder contains the [C4 model](https://c4model.com/) diagrams for the
Zynema platform, in four levels of increasing detail.

## Levels

| File | Level | Audience | Purpose |
|---|---|---|---|
| `01-context.md` | 1 — System Context | Everyone | What is Zynema and who uses it |
| `02-containers.md` | 2 — Containers | Devs, ops | The high-level building blocks (apps, datastores) |
| `03-components.md` | 3 — Components | Devs | What's inside each container |
| `04-code.md` | 4 — Code | Devs | Class-level diagrams (selected hot paths) |
| `diagrams/` | rendered | All | Mermaid source + PlantUML renderings |

## Conventions

- Diagrams are **Mermaid** (text, lives in the repo, no external tool).
- Render with VS Code extension, GitHub Markdown, or
  [mermaid.live](https://mermaid.live).
- Each diagram has a Mermaid block and a short prose explanation.
- When the architecture changes, update the affected diagrams **in the same PR**.

## Status

- [x] C4 Level 1 (context)
- [x] C4 Level 2 (containers)
- [ ] C4 Level 3 (components) — fleshed out per phase
- [ ] C4 Level 4 (code) — selective, for non-obvious classes
