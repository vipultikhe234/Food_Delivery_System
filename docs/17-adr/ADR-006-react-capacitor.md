# ADR-006: React + Capacitor (+ Ionic React) for mobile
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-MOBILE-001, REQ-MOBILE-002, REQ-UX-001

## Context
The brief requires React + TypeScript + Capacitor and a shared codebase where practical. It also says the mobile apps must not be a wrapped website. The partner app needs background location, push and camera.

## Decision
- Use Capacitor native shells with React + TypeScript.
- Share `web/packages/*` (design tokens, API client, auth, realtime, domain hooks).
- Use **Ionic React** for mobile navigation, gestures, safe areas and platform-adaptive components, themed with the shared design tokens.
- Native features come from official Capacitor plugins. Background geolocation uses a maintained community plugin with an Android foreground service and the iOS background location mode.

## Consequences
### Positive
- One language and toolchain for web and mobile, with high logic reuse.
- Native-feeling navigation without writing native UI.

### Negative / risks
- Performance is below fully native for heavy animation. Acceptable for this app.
- Background location depends on a community plugin and store policies (risk tracked in 04 §15).
- Ionic adds a UI dependency on mobile only. Web apps do not use it.

## Alternatives considered
- **React Native:** better native feel, but less sharing with the React web apps and contradicts the brief.
- **Flutter:** a different language.
- **Plain Capacitor without Ionic:** more custom work to achieve platform-correct navigation and gestures.
