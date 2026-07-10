# C4 — Level 1: System Context

Zynema is a streaming platform. It serves two main kinds of users:
**end-users** (who watch content) and **operators** (who manage the catalog).

```mermaid
C4Context
    title System Context: Zynema

    Person(endUser, "End User", "Watches movies and series")
    Person(operator, "Content Operator", "Manages catalog and metadata")

    System(zynema, "Zynema", "Streaming platform — catalog, playback, billing")

    System_Ext(idp, "Identity Provider", "OIDC (Keycloak) — authentication")
    System_Ext(stripe, "Payment Provider", "Card processing (mocked locally)")
    System_Ext(cdn, "CDN", "Video delivery (HLS) — local nginx in dev")
    System_Ext(smtp, "Email Provider", "Transactional email (MailHog in dev)")

    Rel(endUser, zynema, "Browses, plays, manages list", "HTTPS")
    Rel(operator, zynema, "Curates catalog", "HTTPS")
    Rel(zynema, idp, "Authenticates users (OIDC)", "HTTPS")
    Rel(zynema, stripe, "Charges subscriptions", "HTTPS")
    Rel(zynema, cdn, "Streams HLS to clients", "HTTPS")
    Rel(zynema, smtp, "Sends notifications", "SMTP")
```

## Key takeaways

- Zynema is a single **system** in C4 Level 1.
- All external systems (Keycloak, Stripe, MailHog, etc.) are modeled as
  **System_Ext** because they are not part of Zynema itself.
- The operator is included because content management is part of the
  product surface, not just a back-office DB tool.
