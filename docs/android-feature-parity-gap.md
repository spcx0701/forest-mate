# Android Native Feature Parity Gap

Checked from `main` after the Kotlin native phone and Wear OS conversion.

## Web App Surface

- Home: hiking index, local/cloud fallback, recommendations, mountain search, nearby mountains.
- Course detail: mountain hero, elevation sparkline, hazards, start point map, external directions.
- Hike: Leaflet map, course marker, route/trail overlay, GPS tracking, progress, hazard alerts, server track sync, end hike.
- Watch: pair code, watch polling, heart-rate/GPS updates into the active hike.
- SOS: hold-to-confirm, grid number, GPS, rescue station, server SOS handoff.
- AI companion: `/chat` with course and progress context.
- My: account login/register, cloud summary, hike log, calendar, badges.

## Native Android Before This Change

- Present: tabs, hiking index call, course list call, device registration, start hike, SOS, chat, basic summary, Wear OS module.
- Missing: native map engine, route/trail overlay, start/summit/hazard/rescue markers, visible course detail, GPS track rendering, GPS lat/lon upload, watch pair button in phone UI, account login/register, badge/history detail, local course fallback with map-ready metadata.

## Native Android After This Change

- OSMDroid renders OpenStreetMap tiles, route polyline, GPS track, and start/summit/hazard/rescue/current-position markers.
- Course parser preserves web-app detail fields: difficulty, peak, grid number, GPS, rescue point, fire station, elevation, and hazard notes.
- Home restores local fallback course recommendations and loads cloud `/courses` when available.
- Hike restores course detail, native map, GPS permission flow, server hike start/end, GPS demo movement, lat/lon track upload, and watch pairing.
- SOS restores selected-course grid number, GPS, rescue station, and server `/sos` handoff.
- AI companion keeps course/progress context in `/chat`.
- My restores email register/login, account-linked device token, summary, hike log, and badge progress display.

## Remaining Non-Blocking Differences

- Web has richer visual polish, mountain search modal, external Kakao/Google direction buttons, and a hold-to-confirm SOS animation.
- Native has functional parity for the core app flows but does not yet clone every web animation or marketing-style detail.
- `android-v1.2.0` is already published, so this feature recovery should ship as `android-v1.3.0` with `versionCode 8`.
