# Product catalogue requirements

| Field | Value |
|---|---|
| Module | PRODUCT |
| Status | DRAFT (see [README](README.md)) |
| Sources | ROS §1, §2, §4, §5, §19, §27, §30 |
| Proposed owner | catalog-service (today's menu-service, ROS-OQ-01) |
| Proposed phase | 6 (folded into the existing catalogue phase, ROS-OQ-02) |
| Affects | REQ-MENU-001, REQ-MENU-002 (IMPACT-0002) |

## Scope
A brand-level **master catalogue**. It holds what a product *is*: name, category, variants, add-ons, combos, tax class, nutrition, allergens, images and units. Where and how it is sold (outlet price, availability, station) belongs to the menu ([MENU_REQUIREMENTS.md](MENU_REQUIREMENTS.md)).

## Entities (names from ROS §1; the schema comes in the design stage)
Product, Category, SubCategory, ProductVariant, ProductAddon, AddonGroup, Combo, ComboItem, ProductImage, ProductTax, ProductNutrition, ProductAllergen, ProductAvailability, ProductPrice.

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-PRODUCT-001 | Master products with categories, sub-categories, images and units | P0 | REQ-RESTAURANT-002, REQ-MEDIA-001 | ROS §1, §2 |
| REQ-PRODUCT-002 | Product variants | P0 | PRODUCT-001 | ROS §1 |
| REQ-PRODUCT-003 | Add-on groups with selection rules | P0 | PRODUCT-001 | ROS §4 |
| REQ-PRODUCT-004 | Combos | P1 | PRODUCT-002, PRODUCT-003 | ROS §5 |
| REQ-PRODUCT-005 | Tax classes | P0 | PRODUCT-001 | ROS §1, §22 |
| REQ-PRODUCT-006 | Nutrition and allergens | P2 | PRODUCT-001 | ROS §1 |
| REQ-PRODUCT-007 | Layered pricing: base, outlet and channel | P0 | PRODUCT-001, REQ-MENU-006 | ROS §1, §2 |
| REQ-PRODUCT-008 | Dynamic pricing rules | P2 | PRODUCT-007 | ROS §1 |
| REQ-PRODUCT-009 | Product lifecycle, history and events | P0 | PRODUCT-001, REQ-AUDIT-001 | ROS §27, §30 |

### REQ-PRODUCT-001 — Master products
- AC1: A brand user with `CATALOG_MANAGE` creates a product with name, description, category, optional sub-category, food type (veg / non-veg / egg), unit of sale, SKU and up to N images (media IDs; images live in object storage, not PostgreSQL).
- AC2: Categories nest at most two levels (category → sub-category). Display order is configurable.
- AC3: A product belongs to exactly one brand. Users of other brands can't read or change it (BR-R11).
- AC4: The SKU is unique within the brand.
- AC5: A product referenced by any order, bill or recipe version can't be hard-deleted. It can only be archived (BR-R2, BR-R4).

### REQ-PRODUCT-002 — Variants
- AC1: A product has zero or more variants (for example Regular / Medium / Large), each with its own name, SKU and price delta or absolute price.
- AC2: If a product has variants, an order must select exactly one.
- AC3: Variants can be archived individually. Archived variants stay visible in historical orders.

### REQ-PRODUCT-003 — Add-on groups (ROS §4)
- AC1: An add-on group (for example "Extra toppings") has a name, a minimum and maximum selection, and a required flag. Required means minimum ≥ 1.
- AC2: Each add-on has a name and an additional price (for example Cheese +40, Paneer +60, Olives +30). A price can be 0.
- AC3: A group can be attached to a product or to specific variants.
- AC4: Order validation rejects selections outside [min, max], add-ons from groups not attached to the item, and archived add-ons. The error names the group.
- AC5: Add-on prices can be overridden per outlet and channel by the same layering as REQ-PRODUCT-007.

### REQ-PRODUCT-004 — Combos (ROS §5)
- AC1: A combo (for example Burger Combo = Burger + Fries + Drink) has slots. Each slot has a default item and an optional list of allowed replacements with price deltas.
- AC2: A combo has its own price. The discount against the sum of item prices is calculated and shown.
- AC3: Variant and add-on selection is allowed per slot item, using that item's rules.
- AC4: Order lines keep the combo and its resolved slot items, so that kitchen routing (REQ-KOT-001) and recipe consumption (REQ-INV-004) work per item.
- AC5: Combo revenue is apportioned to slot items by a documented rule for product sales reports (design stage).

### REQ-PRODUCT-005 — Tax classes
- AC1: Each product has a tax class: rate(s), HSN/SAC code, and inclusive or exclusive pricing.
- AC2: Rates are configuration and are never hard-coded. A rate change applies from an effective date and never changes historical bills (BR-R3).
- AC3: An outlet can override the tax class of a menu item (ROS §2).

### REQ-PRODUCT-006 — Nutrition and allergens
- AC1: Optional nutrition values per serving (energy, protein, carbohydrate, fat) and allergen tags from a configurable list.
- AC2: Allergens are shown on the customer menu and the QR menu where present.

### REQ-PRODUCT-007 — Layered pricing
- AC1: Effective price = channel price for (outlet, channel), if set; otherwise outlet price; otherwise brand base price.
- AC2: Every price change is stored with an effective-from timestamp and the user who made it. Price history is never overwritten.
- AC3: Prices are `BigDecimal`, INR, two decimal places, HALF_UP rounding, and calculated server-side only.
- AC4: An order stores the resolved price of every line (snapshot). Later price changes don't affect it.

### REQ-PRODUCT-008 — Dynamic pricing
- AC1: Time-window rules (for example happy hour −20% on beverages, Mon–Fri 16:00–19:00, outlet time zone) adjust the effective price.
- AC2: Rules can't overlap on the same product and channel. Overlaps are rejected when saved.
- AC3: An AI-recommended price change is only a proposal until approved (REQ-AI-005).

### REQ-PRODUCT-009 — Lifecycle and events
- AC1: Create, update, archive and price changes publish `ProductCreated` and `ProductUpdated` through the outbox (REQ-PLAT-006) with brand ID and correlation ID.
- AC2: Every change is audited with before and after values (REQ-AUDIT-001, BR-R12).
- AC3: Events are versioned (`catalog.events.v1`). A breaking payload change needs a new version.

## Events
`ProductCreated`, `ProductUpdated`, `ProductArchived`, `ProductPriceChanged`, `ComboUpdated`.

## Test obligations
- Unit: price layering; add-on min/max validation; combo price and discount; tax inclusive and exclusive calculation.
- Integration: archive is blocked when an order or recipe references the product; outbox events are published.
- Security: cross-brand read and write are denied.
- API: contract tests for catalogue endpoints.
