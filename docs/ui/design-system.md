# Design System

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Requirements | REQ-UX-001, NFR-A11Y-001 (WCAG 2.1 AA), NFR-COMPAT-001, REQ-MOBILE-001/002 |
| Implementation | `web/packages/ui` (tokens as CSS variables + Tailwind preset + React components); mobile themes Ionic with the same tokens (ADR-006) |

Tokens are defined **once** in `web/packages/ui/tokens/tokens.json` (W3C Design Tokens format). A build step generates CSS variables, the Tailwind preset and the Ionic theme variables (REQ-UX-001 AC1).

---

## 1. Brand and colour

### 1.1 Palette
| Token | Hex | Use |
|---|---|---|
| `color.brand.500` "Saffron" | `#FF5A1F` | Brand accents, illustrations, large headings (≥ 24 px), icons, focus ring, progress |
| `color.brand.600` | `#E8480C` | Hover for brand accents (non-text) |
| `color.brand.700` | `#C2410C` | **Primary button background**, links, selected states (text-safe) |
| `color.brand.50` | `#FFF1EB` | Tinted backgrounds (offer banners, selected chips) |
| `color.neutral.900` | `#1A1A1A` | Primary text |
| `color.neutral.600` | `#5C5C5C` | Secondary text |
| `color.neutral.500` | `#767676` | Placeholder and disabled text (minimum AA) |
| `color.neutral.200` | `#E5E5E5` | Borders, dividers |
| `color.neutral.50` | `#FAFAFA` | App background |
| `color.white` | `#FFFFFF` | Surfaces |
| `color.success.700` | `#0B7A33` | Success text, **veg text label** |
| `color.veg.icon` | `#0F8A3B` | Veg marker (square with dot) |
| `color.nonveg.icon` | `#B3261E` | Non-veg marker (triangle) |
| `color.egg.icon` | `#B45309` | Egg marker (circle) |
| `color.error.700` | `#B3261E` | Errors, destructive buttons |
| `color.warning.700` | `#B45309` | Warnings, "closing soon" |
| `color.info.700` | `#1D4ED8` | Info, links in admin |

### 1.2 Measured contrast (WCAG 2.1 relative luminance, computed 2026-10-01)
| Pair | Ratio | AA normal text (4.5) | AA large / UI (3.0) |
|---|---|---|---|
| brand.500 on white | 3.12 | ✗ | ✓ (large text, icons, borders only) |
| neutral.900 on brand.500 | 5.58 | ✓ | ✓ (dark text on saffron chips/badges) |
| white on brand.700 | 5.18 | ✓ | ✓ (primary buttons) |
| brand.700 on white | 5.18 | ✓ | ✓ (links) |
| neutral.900 on white | 17.40 | ✓ | ✓ |
| neutral.600 on white / neutral.50 | 6.69 / 6.41 | ✓ | ✓ |
| neutral.500 on white | 4.54 | ✓ | ✓ |
| success.700 on white | 5.46 | ✓ | ✓ |
| veg.icon on white | 4.45 | ✗ (icon only) | ✓ |
| error.700 / warning.700 / info.700 on white | 6.54 / 5.02 / 6.70 | ✓ | ✓ |

**Rule:** `brand.500` is **never** used for body text or as a background behind white text. Automated axe checks in Playwright (Phase 14) verify contrast on real screens. These calculations are a design-time check only.

### 1.3 Dark mode
Optional for v1 customer and partner apps (night-time delivery work benefits from it). Dark tokens: background `#121212`, surface `#1E1E1E`, text `#E5E5E5` (14.87 on `#121212`), secondary text `#A3A3A3` (7.43), brand.500 is usable as text on dark (6.01). Delivered behind a feature flag. It is not a v1 acceptance criterion.

### 1.4 Food-type markers
These follow the Indian FSSAI convention: **veg** = green square outline with a filled circle; **non-veg** = red/brown square outline with a filled triangle; **egg** = amber square outline with a filled circle. They are always paired with an accessible label (`aria-label="Vegetarian"`) and never rely on colour alone (shape differs).

## 2. Typography
| Token | Value |
|---|---|
| Font family | `Inter`, fallback `system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans", sans-serif`. Noto Sans Devanagari is reserved for future Hindi (NFR-I18N-001). |
| Scale (px / line-height) | `xs` 12/16, `sm` 14/20, `base` 16/24, `lg` 18/28, `xl` 20/28, `2xl` 24/32, `3xl` 30/36, `4xl` 36/40 |
| Weights | 400 regular, 500 medium, 600 semibold, 700 bold |
| Numbers | `font-variant-numeric: tabular-nums` for prices, timers, tables |
| Money format | `₹1,234.50` via `Intl.NumberFormat('en-IN', {style:'currency', currency:'INR'})` in `packages/domain` |
| Minimum | Body ≥ 16 px on mobile; ≥ 14 px for dense admin tables |

## 3. Spacing, radius, elevation, motion
| Token group | Values |
|---|---|
| Spacing (4 px base) | `0, 1=4, 2=8, 3=12, 4=16, 5=20, 6=24, 8=32, 10=40, 12=48, 16=64` |
| Radius | `sm 6`, `md 10` (inputs, buttons), `lg 16` (cards), `xl 24` (bottom sheets), `full` (chips, avatars) |
| Elevation | `e0` none; `e1` `0 1px 2px rgba(0,0,0,.06), 0 1px 3px rgba(0,0,0,.10)` (cards); `e2` `0 4px 12px rgba(0,0,0,.12)` (dropdowns, sticky cart bar); `e3` `0 12px 32px rgba(0,0,0,.18)` (modals, sheets) |
| Motion | Durations 120 ms (micro), 200 ms (standard), 300 ms (sheets/pages); easing `cubic-bezier(.2,.0,.0,1)`; respects `prefers-reduced-motion` |
| Breakpoints | `sm 360`, `md 768`, `lg 1024`, `xl 1280`, `2xl 1536` (REQ-WEB-001 AC2: 360 px minimum) |
| Touch targets | ≥ 44×44 px (mobile and touch web) |
| Z-index | `base 0`, `sticky 100`, `dropdown 200`, `overlay 300`, `modal 400`, `toast 500` |

## 4. Components (REQ-UX-001 AC2)

Each component has documented variants and states (default, hover, focus-visible, active, disabled, loading, error) and a Storybook story with a visual regression baseline (AC4).

| Component | Variants / notes |
|---|---|
| Button | `primary` (brand.700), `secondary` (outline brand.700), `tertiary` (text), `destructive` (error.700), `icon`; sizes `sm 36`, `md 44`, `lg 52`; loading spinner keeps width |
| Input / TextArea | label always visible (no placeholder-only), helper, error text linked with `aria-describedby`, prefix/suffix (₹, icons) |
| Select / Combobox | Native select on mobile; searchable combobox (keyboard-navigable) on web |
| Checkbox / Radio / Switch | Add-on selection groups show min/max ("Choose up to 2") |
| Quantity stepper | − n +; min 0/1, max configurable; announces changes (`aria-live`) |
| Card | `restaurant-card` (image, name, rating, ETA, price for two, offer badge, closed overlay), `dish-card` (veg marker, name, price, description clamp, add button / stepper), `order-card` |
| Chip / Filter chip | Selected uses brand.50 bg + brand.700 text + check icon |
| Badge | rating (green ≥ 4.0, amber 3.0–3.9, red < 3.0, always with a number), offer, status |
| Modal / Dialog | Focus trap, ESC to close, return focus; becomes a **bottom sheet** on mobile |
| Bottom sheet | Mobile: item customisation, filters, cart summary; drag handle; safe-area padding |
| Toast / Snackbar | Info, success, error; auto-dismiss 4 s (errors persist until dismissed); `role="status"`/`alert` |
| Table (admin) | Sortable headers, sticky header, row selection, pagination (cursor), column visibility, empty and loading rows |
| Navigation | Web top bar + side nav (dashboards); mobile bottom tabs (Ionic `IonTabs`); breadcrumb (admin) |
| Tabs / Segmented control | Order list filters, menu categories (scroll-spy on restaurant page) |
| Stepper / Timeline | Order tracking timeline with timestamps |
| Map | MapLibre GL / Leaflet with OSM tiles (ADR-015): restaurant pin, customer pin, partner marker with heading, route polyline (straight-line until routing provider) |
| Countdown | Delivery offer 30 s ring timer (partner app) with haptic feedback |
| Skeleton loaders | Per card type |
| Empty state | Illustration + message + primary action (e.g. "No restaurants deliver here yet. Change location.") |
| Error state | Friendly message + retry + correlation ID (small text, copyable) for support |
| Offline banner | "You're offline. Showing saved data." Persistent on mobile when offline |
| Price breakdown | Item total, fees (with info tooltips), taxes, discount (green), total |
| Rating input | 5 stars with labels; keyboard accessible |
| File upload | For documents: client-side type/size checks; progress; pre-signed upload |

## 5. Accessibility (NFR-A11Y-001, REQ-UX-001 AC3)
- WCAG 2.1 AA: contrast (§1.2), keyboard operability (all actions reachable, visible focus ring `2px brand.500` + `2px white` offset), logical focus order, skip-to-content link.
- Semantic HTML first; ARIA only when needed; form errors announced; live regions for order status updates and cart changes.
- Images have alt text (dish photos: dish name); decorative images `alt=""`.
- Supports 200% zoom and text resize without loss of content; no horizontal scroll at 320 CSS px for core flows.
- Motion is reduced on preference; no flashing content.
- Mobile: dynamic type support (Ionic), VoiceOver/TalkBack labels, haptics never the only signal.
- Testing: axe-core in Playwright (no serious/critical violations gate), manual screen-reader pass for the ordering and delivery flows before v1.0.0.

## 6. Platform variants (mobile)
| Pattern | Web | Mobile (Ionic + Capacitor) |
|---|---|---|
| Navigation | Top bar + side nav | Bottom tabs, stack navigation with native transitions, swipe-back on iOS |
| Dialogs | Centered modal | Bottom sheet / action sheet |
| Lists | Pagination / "load more" | Infinite scroll + pull to refresh |
| Feedback | Toast | Toast + haptics (Capacitor Haptics) |
| Safe areas | — | `env(safe-area-inset-*)` handled by Ionic |
| Offline | Banner | Banner + cached data (TanStack Query persistence) + queued actions where safe (partner location, delivery status updates with server-side idempotency) |
| Permissions | Browser prompts on use | Pre-permission explainer screens before OS prompts (location, notifications, camera); background-location disclosure per store policies (REQ-MOBILE-002 AC2) |

## 7. Content and voice
Friendly, concise, active voice. Times are shown as relative ("in 25 min") with absolute times on tap. Errors explain what happened and what to do next. Users never see technical jargon or raw error codes, except the copyable correlation ID on error screens.

## 8. Governance
- Changes to tokens or components go through a PR that updates Storybook. Visual baselines are re-approved by review.
- Apps may not hardcode colours or spacing (lint rule: Tailwind config restricted to tokens; stylelint for raw hex values).
- UI specs derived from reference images (REQ-TESTAGENT-003) live in `docs/ui/specs/`.
