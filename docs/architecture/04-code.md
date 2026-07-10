# C4 — Level 4: Code (selected)

Level 4 diagrams show **specific classes and their relationships** for the
non-obvious parts of the codebase. Most classes don't need a diagram — the
code is the diagram. We only add C4 Level 4 for:

- The **Saga** state machine in payment-service.
- The **Outbox** relay loop.
- The **BFF** aggregator (webclient fan-out).
- The **Event Sourcing** aggregate in playback-service.
- The **Keycloak JWT → Spring Security** filter chain.

These are added in the corresponding phases (Fase 5+ for BFF, Fase 7 for
Saga/Outbox/ES).

For now, this folder is empty by design.
