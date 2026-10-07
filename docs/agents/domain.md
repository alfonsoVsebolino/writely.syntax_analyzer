# Domain documentation: Single-context

This project follows a single-context domain documentation layout.

## Locations

- **Glossary**: `GLOSSARY.md` at repo root
- **Architecture Decision Records (ADRs)**: `docs/adr/` at repo root

## Reading rules

- When implementing or specifying features, consult `GLOSSARY.md` for standard terms, types, and invariants.
- Read existing ADRs in `docs/adr/` to understand architectural boundaries and design commitments.
- Never redefine terms established in `GLOSSARY.md`. Propose updates to the glossary when new domain concepts emerge.

## Authoring ADRs

- Place new ADRs in `docs/adr/NNNN-title.md` where `NNNN` is a zero-padded sequential integer.
- Include Status, Context, Decision, and Consequences sections.
