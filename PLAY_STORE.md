# Publishing Slate on Google Play

Website (GitHub Pages, from `docs/`): https://thrishankkuntimaddi.github.io/slatelauncher/

## Signing (do this once)

- Builds are signed with the key in `~/.android/nolauncher-signing/` (kept outside the repo; back it up somewhere safe — losing it means you can't update the app).
- On Play Console choose **Play App Signing** (default). Google keeps the app-signing key; the key above becomes your **upload key**. Upload the `.aab` signed with it.

## Before every upload

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`. Play rejects a versionCode it has seen.
2. Build: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
3. Upload the `.aab` in Play Console → Testing (internal) first, then Production.

## Store listing — what Play asks for

- App icon 512 × 512 PNG, feature graphic 1024 × 500, at least 2 screenshots (phone/tablet as applicable)
- Short description (80 chars) and full description (4000) — the website text works as a start
- Category: **Personalization**; contact email; **Privacy policy URL**: `https://thrishankkuntimaddi.github.io/slatelauncher/privacy.html`
- Content rating questionnaire (no violence etc. → Everyone), Target audience: 18+ / not for children
- **Data safety**: no data collected, no data shared. Weather: approximate location (city) is sent to Open-Meteo to fetch the forecast — declare as *Location → Approximate, shared, not stored, for app functionality*.

## Sensitive permissions — declarations Play will ask for

- **AccessibilityService** (gestures + reading app names on the Recents screen): needs the Accessibility declaration form and a **prominent in-app disclosure** before it's turned on. Core use: navigation gestures on devices where a third-party launcher loses them. Expect review to ask for a video. Reading Recents titles must be disclosed exactly as in the privacy policy.
- **PACKAGE_USAGE_STATS** (Usage access): allowed for launchers/usage features; declare it in the Permissions declaration form.
- **READ_CALENDAR / READ_CONTACTS**: optional, requested in context (widgets, search).
- Package id is `app.nolauncher.pad`; you can't rename it after the first Play upload — change it now (e.g. `app.slate`) if you want a different id. Changing it means a fresh install on the tablet.
- Tablet app: upload tablet screenshots (7" and 10").
