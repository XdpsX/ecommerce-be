---
name: figma-screen-design
description: Design this repository's single-store Backoffice or Storefront screens in a user-provided Figma Design file using the FE briefs and current online UX references. Use for Figma screen design via MCP; not for frontend implementation or diagram-only requests.
---

# Design ecommerce screens in Figma

Create usable, coherent Figma screens for the requested part of this repository. The user may have no design experience: make justified design decisions from the product brief, existing Figma file, and relevant research. Do not require the user to choose colors, grids, or UI patterns before useful work can begin.

## Inputs and scope

- Use the user's Figma Design URL as the target. If no URL was supplied, ask for it while reading the local brief. If the user explicitly wants a new file, load `figma-create-new-file` before creating one. If the MCP connection or edit access is unavailable, report the precise blocker and the prepared design scope.
- Read `CONTEXT.md` and `docs/fe/screen-inventory.md` first. Read `docs/fe/backoffice-screen-brief.md` and/or `docs/fe/storefront-screen-brief.md` for the requested screen IDs. Use `docs/fe/admin-first-api-and-screen-design.md` for data, actions, and status semantics; consult `docs/fe/backend-readiness-before-frontend.md` only when a BE limitation matters. The user's latest scope and preferences take precedence over these documents.
- If the user does not pick screen IDs, use the flow they named. If they request the entire product, cover the inventory in coherent batches until the requested scope is complete. If they simply say “thiết kế Figma” for this repo, start with the inventory's Backoffice-first flow and ask one focused scope question while doing the work that is already clear.
- Use the inventory's viewport matrix. Defaults: Backoffice 1440 CSS px; Storefront 390 and 1440 CSS px. Before drawing optional 768 tablet or 390 mobile Backoffice variants, ask which of those surfaces the user needs. Treat 360, 1280, and 1920 as responsive checks unless a distinct layout is required or requested. Frame widths are design references, not fixed frontend breakpoints.

## Research and design direction

- Browse current, relevant examples for the selected flow before drawing. Prefer evidence about the task (for example Baymard's ecommerce UX research for PLP/checkout, Shopify's merchant UI patterns for Backoffice, and W3C accessibility guidance) plus a small number of visual references. Record direct links and one useful takeaway per reference. Check source dates when advice or products may have changed.
- Use references to learn hierarchy, interaction, and responsive behavior. Compose an original design; do not copy an entire screen, brand identity, illustration, or proprietary asset from a reference. Keep the M1 single-store scope and API-backed actions in the FE brief. No invented KPI, customer management, shipping integration, voucher engine, or automated refund flow.
- If the Figma file already has brand tokens, components, or screens, inspect and reuse them. Otherwise choose one simple visual direction suitable for commerce operations and shopping; explain it briefly and keep it consistent. Use realistic Vietnamese copy, VND, and clearly illustrative sample data. If the file is empty, build only the tokens and reusable components needed for the selected screens, following `figma:figma-generate-library`.

## Figma execution

- Inspect the target file and relevant pages before writing. Respect existing work: add the requested screens in a clearly named page/section or update the specific frames the user identified. Do not overwrite unrelated frames or libraries.
- For composed screens, load and follow `figma:figma-generate-design` and `figma:figma-use` before `use_figma`; for a new reusable component/library, also load `figma:figma-generate-library`. Follow the Figma tool skills' required discovery, component, variable, font, and validation steps rather than duplicating their API instructions here.
- Build the requested user flow, not just a moodboard. Name frames with the inventory ID and viewport (for example `A7 Orders / 1440`). Include the main state and only the alternative states needed to explain meaningful behavior: loading/empty/error for lists, validation/conflict for forms, and distinct order/payment/refund states. Use reusable components where they improve consistency.
- Review each completed flow in screenshots at the requested widths. Fix clipped text, overflow, weak contrast, ambiguous status labels, missing primary actions, and inconsistent spacing before moving on. Validate that screen links and actions correspond to the FE brief. A Figma browser return must not imply payment success; an Order read determines its status.

## Handoff

Return links to the Figma file and finished frames or pages, the screen IDs and widths completed, a short explanation of the design direction with research links, and any missing API/content assumption. State which optional viewports or screens were left for a later pass. If access or MCP capability blocked editing, say exactly what is needed to continue; do not claim that Figma was updated.
