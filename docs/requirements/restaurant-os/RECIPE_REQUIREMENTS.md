# Recipe requirements

| Field | Value |
|---|---|
| Module | RECIPE |
| Status | Approved 2026-10-01 (see [README](README.md)) |
| Sources | ROS §13, §15, §16, §20, §30.4 |
| Proposed owner | inventory-service (new, ROS-OQ-01) |
| Proposed phase | 13C |

## Scope
Which ingredients, in which quantities, make one portion of a product, versioned so that history is never lost. Recipes drive automatic consumption (REQ-INV-004) and food cost (REQ-RECIPE-004).

## Entities (ROS §13)
Recipe, RecipeVersion, RecipeIngredient, RecipeStep, RecipeCost.

Example (ROS §13), Paneer Pizza: Dough 200 g, Paneer 100 g, Cheese 80 g, Sauce 50 ml, Vegetables 100 g.

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-RECIPE-001 | Recipes for products and variants | P0 | REQ-PRODUCT-002, REQ-INV-001 | ROS §13 |
| REQ-RECIPE-002 | Recipe versioning; history is never destroyed | P0 | RECIPE-001 | ROS §13, §30.4 |
| REQ-RECIPE-003 | Recipe steps | P2 | RECIPE-001 | ROS §13 |
| REQ-RECIPE-004 | Recipe cost and margin | P1 | RECIPE-002, REQ-INV-010 | ROS §13, §16 |
| REQ-RECIPE-005 | Add-on and combo consumption (PROPOSED, ROS-OQ-21) | P1 | RECIPE-001, REQ-PRODUCT-003 | ROS §15 (implied) |
| REQ-RECIPE-006 | Sub-recipes and semi-finished items (PROPOSED, ROS-OQ-21) | P1 | RECIPE-001, REQ-OUTLET-005 | ROS §20 (implied) |

### REQ-RECIPE-001 — Recipes
- AC1: A recipe belongs to a master product, or to one of its variants when portions differ (for example Large uses more cheese).
- AC2: Each ingredient line has a stock item (REQ-INV-001), quantity and unit. Units must be convertible to the stock item's base unit (g ↔ kg, ml ↔ l, pcs). Invalid units are rejected.
- AC3: A recipe has a yield (default 1 portion) and an optional wastage percentage per ingredient.
- AC4: HQ owns recipes (ROS §19). Outlets can't change them unless HQ allows outlet variants.

### REQ-RECIPE-002 — Versioning (BR-R4)
- AC1: Any change to a published recipe creates a new version with an effective-from date, author and reason. Published versions are read-only.
- AC2: Consumption uses the version effective when the order was **placed**, and the consumption record stores that version ID.
- AC3: All historical versions stay viewable and comparable (diff of ingredients and quantities).
- AC4: An AI-proposed recipe change is only a proposal until approved (REQ-AI-005).

### REQ-RECIPE-003 — Steps
- AC1: Ordered preparation steps with optional time and image. They can be shown on the KDS item detail.

### REQ-RECIPE-004 — Cost
- AC1: Recipe cost = Σ (ingredient quantity × unit cost) including wastage, with unit cost from the brand's costing method (REQ-INV-010).
- AC2: Food cost % = recipe cost ÷ net selling price per channel. Gross margin = net price − recipe cost.
- AC3: Cost is recalculated when ingredient costs change, and the result is stored with a timestamp (RecipeCost history) for the "why did food cost increase" analysis (REQ-AI-004).

### REQ-RECIPE-005 — Add-ons and combos (PROPOSED)
- AC1: An add-on can have its own recipe (for example Extra cheese = Cheese 40 g), consumed together with the main item.
- AC2: A combo consumes the recipes of its resolved slot items.

### REQ-RECIPE-006 — Sub-recipes (PROPOSED)
- AC1: A semi-finished item (for example Pizza dough) is a stock item with its own recipe, produced in batches (a production entry consumes raw ingredients and adds semi-finished stock).
- AC2: Recipes can use semi-finished items as ingredients. Circular references are rejected.

## Events
`RecipePublished`, `RecipeCostChanged`.

## Test obligations
- Unit: unit conversion; cost including wastage; version effective-date selection; cycle detection in sub-recipes.
- Integration: publishing a new version doesn't change consumption of orders placed before it.
