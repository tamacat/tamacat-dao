---
name: sql-parameterization
depth: Standard
keywords: []
description: Migrate a string-concatenating query builder to bound parameters
skeleton: on
---

# sql-parameterization scope

Standard depth for replacing literal-interpolating SQL construction with
bound parameters across a library's query spine. Composed by the adaptive
composer at ARS 53 (Standard band) for a brownfield Java 8 DAO library
whose injection sink is centralized but whose remediation ripples through
the public API.

## Why these stages, why skip those

The shape is driven by one HIGH component — Unresolved Assumptions (0.72)
— against four MED components. The mechanism is decided up front, so
nothing here explores the solution space; what is unresolved is the
*consequences*: whether the public `String get*SQL()` contract may break,
what happens to positions that cannot be bound (table and column
identifiers, sort direction), how existing LIKE-escape machinery survives
parameterization, and how pre-existing placeholders in dialect
implementations avoid colliding with newly added binds.

So the front of the workflow is deliberately thin but not absent:
intent-capture and scope-definition (both cost 1) pin the scope boundary
and the back-compat policy, approval-handoff gates that policy before
design money is spent. Ideation's expensive stages all fold —
market-research has no market to research when the mechanism is given,
feasibility folds into application-design because parameterizing a query
builder is a documented pattern rather than a novel bet, team-formation
is moot for solo work, and rough-mockups has no UI to sketch.

Inception carries the weight. reverse-engineering is the load-bearing
structural stage: it maps the query-construction call graph so design
knows every call site the contract change touches. requirements-analysis
earns its place because several distinct technical contracts need
specification *before* design — the parameter-carrier shape, the
identifier-safety allowlist, the escape-semantics preservation
guarantee, and the deprecation policy. application-design then decides
the one architectural question the whole task hinges on, absorbing the
folded feasibility. practices-discovery folds: on brownfield the
conventions are already embodied in the existing source and test trees,
inferred while mapping and enforced at build. user-stories and
refined-mockups fold for the usual non-UI, single-persona reasons.

units-generation and delivery-planning execute — the distinguishing
choice of this scope against the incremental scopes it otherwise
resembles. A parameterization pass decomposes into ordered units (carrier
core, builder migration, execution path, dialect implementations, test
migration) whose sequence matters: landing the core before the dialects
is what prevents a half-migrated state, which for an injection fix is
worse than no fix because it manufactures false confidence. That same
reasoning is why `skeleton: on` — the first Bolt proves one end-to-end
bound query and is gated before the remaining units follow.

Construction keeps functional-design (the per-unit logic is intricate:
escape-character selection, IN-list arity expansion, two-value range
binds, null handling, and binary-column index reconciliation) and
nfr-requirements (the security acceptance criterion and how it is
verified is the acceptance criterion for the entire intent). nfr-design
folds — there is a single security NFR whose implementation design *is*
the parameterization design already produced upstream.
infrastructure-design skips against zero infrastructure change, and
ci-pipeline skips when CI already exists and is adequate.

The whole Operation phase skips. This scope assumes local, unpushed work
on a library with no deployment surface, no environments, and no running
service to observe. Scopes that must ship a fix to production should not
reach for this one.

## Membership

No keyword triggers — composed scopes are not inferable and resolve only
via `--scope sql-parameterization`. Initialization, intent-capture,
scope-definition, approval-handoff, reverse-engineering,
requirements-analysis, application-design, units-generation,
delivery-planning, functional-design, nfr-requirements, code-generation,
and build-and-test execute; the rest is SKIP.
