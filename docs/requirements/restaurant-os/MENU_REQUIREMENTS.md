# Menu requirements

| Field | Value |
|---|---|
| Module | MENU (continues the approved REQ-MENU-001..004) |
| Status | Approved 2026-10-01 (see [README](README.md)) |
| Sources | ROS §2, §3, §19, §30.9 |
| Proposed owner | catalog-service (today's menu-service) |
| Proposed phase | 6 |
| Affects | REQ-MENU-001..004, REQ-CART-002, REQ-SEARCH-003 (IMPACT-0002) |

## Scope
How master products are sold at each outlet: on which channel, at what price, when, with which tax, preparation time and kitchen station.

## Entities (ROS §3)
Menu, MenuCategory, MenuItem, MenuItemPrice, MenuItemAvailability, MenuSchedule.

## Channels (ROS §3)
`DINE_IN`, `TAKEAWAY`, `DELIVERY`, `QR`, `WEBSITE`, `MOBILE`, `AGGREGATOR`.

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-MENU-005 | Menu items: outlet-specific listing of master products | P0 | REQ-PRODUCT-001, REQ-OUTLET-001 | ROS §2 |
| REQ-MENU-006 | Channel menus | P0 | MENU-005 | ROS §3 |
| REQ-MENU-007 | Scheduled menus | P1 | MENU-006 | ROS §3 |
| REQ-MENU-008 | HQ menu publishing with outlet overrides | P1 | MENU-006, REQ-OUTLET-002 | ROS §19 |
| REQ-MENU-009 | Inventory-driven availability | P1 | MENU-005, REQ-INV-011, REQ-RECIPE-001 | ROS §30.9 |
| REQ-MENU-010 | Published menu versions | P0 | MENU-006 | ROS §2, §30.2 |

### REQ-MENU-005 — Menu items
- AC1: A menu item links one master product to one outlet and holds the outlet-specific fields: price, availability, tax override, preparation time (minutes), kitchen station and channel availability (ROS §2).
- AC2: A menu item can't exist without an active master product. Archiving the product hides its menu items.
- AC3: The outlet manager can toggle availability in one action (keeps UJ-06 from the baseline). The toggle is audited.
- AC4: Menu item changes don't change the master product, and master product changes don't overwrite outlet overrides.

### REQ-MENU-006 — Channel menus
- AC1: An outlet has one or more menus. Each menu is enabled for one or more channels.
- AC2: A menu item can be enabled or disabled per channel and can have a channel price (REQ-PRODUCT-007).
- AC3: Every price check (cart quote, POS, QR, captain) passes the channel. The response only contains items enabled for that channel.
- AC4: Customer web and mobile use `WEBSITE` and `MOBILE`. Delivery uses `DELIVERY` (mapping confirmed in design).

### REQ-MENU-007 — Scheduled menus
- AC1: A menu schedule has a name (Breakfast, Lunch, Dinner, Late night or custom), days of the week and a time window in the outlet's time zone. Windows may cross midnight.
- AC2: Outside its window a scheduled menu's items are not orderable on any channel. The menu shows the next available time.
- AC3: Overlapping schedules are allowed. An item is orderable if any active schedule includes it.

### REQ-MENU-008 — HQ publishing
- AC1: Brand users (HQ) define brand menus and publish them to selected outlets.
- AC2: Outlet managers can change only the fields HQ marks overridable (for example availability, and price within a band). Everything else is read-only at the outlet.
- AC3: A publish shows a diff (added, removed, changed items) before confirmation and is audited.

### REQ-MENU-009 — Inventory-driven availability (BR-R9)
- AC1: Per menu item, the outlet chooses: `MANUAL` (default) or `INVENTORY_LINKED`.
- AC2: An inventory-linked item becomes unavailable automatically when any recipe ingredient at that outlet is out of stock or below the quantity for one portion, and available again when stock returns.
- AC3: The change publishes `MenuItemAvailabilityChanged` within the NFR target (ROS-OQ-19) and invalidates the menu cache.
- AC4: A manual "unavailable" always wins over inventory status.

### REQ-MENU-010 — Published menu versions
- AC1: Menu edits are drafts until published. Publishing creates an immutable menu version.
- AC2: Orders store the menu version they were priced against.
- AC3: The cache key includes outlet, channel and menu version (extends REQ-MENU-004).

## Events
`MenuUpdated` (existing; now carries outlet, channel list and version), `MenuPublished`, `MenuItemAvailabilityChanged` (existing).

## Test obligations
- Unit: schedule windows, including across midnight and time zones; channel filtering; override permissions.
- Integration: inventory-linked availability flips on `LowStockDetected` / out-of-stock and back.
- Regression: the existing customer menu API (REQ-MENU-001..004) keeps working for the DELIVERY channel.
