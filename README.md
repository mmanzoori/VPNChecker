# VPN Checker — Android starter project

Version: 0.1.1 (prototype source)
Target device: Samsung, Android 15/16

## Current prototype features
- Discovers VPN apps by querying services advertising the standard `android.net.VpnService` action and requiring `android.permission.BIND_VPN_SERVICE` (without broad `QUERY_ALL_PACKAGES`).
- Lets the user select detected VPN apps.
- Launches a selected VPN app.
- Includes an optional Accessibility Service that looks only for visible, clickable labels matching Connect/Start/اتصال/شروع in the selected VPN packages.
- Checks whether Android reports an active VPN transport.
- Runs HTTPS probes against Telegram API and Instagram only.

## Important limitations
- This is source code, not a compiled APK. An Android SDK/Gradle build environment is required to produce the APK.
- Third-party VPN apps do not expose a universal connect API. Some apps require manual confirmation, an app-specific deep link, or a per-app adapter.
- The generic accessibility helper is best-effort and may click the wrong control in apps with ambiguous labels. Review and test each supported VPN app; do not enable it for untrusted apps.
- Android does not provide an ordinary app a reliable public API to identify which third-party VPN package owns the currently active VPN tunnel.
- HTTP reachability is not proof that the Telegram/Instagram app has authenticated successfully or loaded its feed. Actual app launch and authenticated-content verification need dedicated app-specific UI checks; this prototype does not claim those checks are complete.
- VPN discovery uses Android package-visibility intent queries for the standard VPN service action. Some proprietary VPN apps may not expose this standard service and therefore may not appear.

## Build
Open this folder in Android Studio (recent version with Android SDK 36 installed), allow Gradle sync, then Build > Build Bundle(s) / APK(s) > Build APK(s).

## Next engineering steps
1. Add a proper test-session state machine and per-VPN adapters.
2. Add app-specific connection recipes for OpenVPN Connect, WireGuard, V2RayNG, Hiddify, and other installed clients.
3. Add per-app launch and UI verification for Telegram and Instagram without treating mere app launch as success.
4. Persist results and export CSV.
5. Add timeout, retry, disconnect, and recovery handling.
