# Domain context

## Title metadata

Desktop-facing descriptive information for a loaded title: canonical artwork, normalized tags,
cast credits, recommendations, and episode metadata. Extensions continue to own playback data
and provider-specific URLs; desktop title metadata may only enrich missing presentation fields.

## Anchored Session Summary

## Goal
Make webUI match desktop for extensions/settings/player/IPTV, fix proxy CORS for DRM and live TV playback, and achieve full cross-sync between desktop and server-app.

## Constraints & Preferences
- WebUI home page must show provider-specific categories (Trending, Popular, etc.) like the desktop app, not just a hacky empty-search fallback
- Server-app must load the same extensions from the shared `~/.local/share/CloudStreamDesktop/Extensions/` directory that the desktop app uses
- All API handlers must catch `Throwable` (not just `Exception`) because `NotImplementedError` extends `Error`
- POST requests with `Content-Type: application/json` must be allowed through CORS
- JW Player (CDN `KB5zFt7A.js`) is the player, replacing hls.js/dashjs
- Proxy must handle credentialed cross-origin requests (echo Origin, not `*`)

## Progress

### Done
- **TMDb name fix**: Set `name = "TMDB"` in both `Server.kt` and `PluginInit.kt` so it no longer shows "NONE"
- **Settings screen**: `SettingsScreen.tsx` + `SettingsApi.kt` (generic key-value settings backed by `DesktopDataStore`) — Appearance, Network, Advanced, About tabs
- **IPTV screen**: `IptvScreen.tsx` + `IptvApi.kt` (M3U parser via OkHttp) — enter URL, filter by group, play channels
- **Live TV button**: Red "Watch Live" button (full width, no pulse, matches desktop) for `type: "Live"` in `DetailsScreen.tsx`
- **Proxy CORS rewrite** (`LocalStreamProxy.kt`): bind `0.0.0.0`, URL generation uses `localhost`, OPTIONS preflight handlers, echo Origin, `Access-Control-Allow-Credentials: true`
- **JW Player integration**: Added script to `webui/index.html`, rewrote `PlayerScreen.tsx` with `jwplayer.setup()` + explicit `type: 'hls'`/`type: 'dash'` + proxy→direct fallback + DRM (clearkey/widevineLoading). Removed unused hls.js/dashjs deps, rewrote `PlayerScreen.tsx` with `jwplayer.setup()` + explicit `type: 'hls'`/`type: 'dash'`. Removed unused hls.js/dashjs deps
- **Extension toggle persistence** (`PluginsApi.kt`, `Server.kt`): toggle calls `ExtensionLoader.unloadPlugin()`/`loadAndInit()`, disabled list stored in `DesktopDataStore`, startup skips disabled plugins
- **Per-link headers forwarded to proxy** (`LinksApi.kt`): ExtractorLink.headers + subtitle headers merged into proxy session
- **Proxy upstream error passthrough** (`LocalStreamProxy.kt`): sends upstream error body as text
- **CORS fix** (`Server.kt`): `install(CORS)` at application level, Ktor-3 API (`allowMethod`, `allowHeader`)
- **Details API fallback** (`DetailsApi.kt`): `getApiFromNameNull` → `getApiFromUrlNull` → iterate all providers
- **Search crash fix** (`SearchApi.kt`, `DetailsApi.kt`): `catch (e: Throwable)` for `NotImplementedError`
- **MainPage API** (`MainPageApi.kt`): `GET /api/mainpage?source={name}` returns multi-category JSON
- **HomeScreen.tsx**: Rewired from empty-search hack to `api.mainpage(selectedSource)` with fallback
- **Plugin cross-sync** (`Server.kt`): `loadPlugins()` scans shared `PlatformPaths.extensionsDir`, loads via `ExtensionLoader.loadAndInit()`
- **`appcompat` removed**: AAR can't resolve on JVM; `android-stubs` provides the stub
- **DRM handling**: `LinksApi.kt` detects `DrmExtractorLink`, populates `drmKid`/`drmKey`/`drmUuid`/`drmLicenseUrl` on `LinkResult`. WebUI passes these to JW Player — ClearKey via `clearkey` param, Widevine/PlayReady via `widevineLoading` callback. Fields added to `types.ts` `LinkResult` interface
- **Extensions 3-tab rewrite** (`ExtensionsScreen.tsx`): Full Browse/Installed/Repositories layout:
  - Browse: search + language filter, lists plugins from all configured repos, Install button
  - Installed: toggle enable/disable, uninstall (DeleteIcon), update badge
  - Repositories: add/remove repo URLs
- **New API endpoints** (`PluginsApi.kt`):
  - `GET /api/plugins/browse` — fetches plugin lists from all repos (parses repo manifest → pluginList URLs → aggregated JSON)
  - `POST /api/plugins/install` — downloads JAR from `jarUrl`, saves to extensions dir, loads via `ExtensionLoader`
  - `DELETE /api/plugins/{name}` — unloads + deletes plugin JAR + companion `-jvm.jar`
- **WebUI API client**: added `browse()`, `installPlugin()`, `deletePlugin()` methods
- **Both compilations pass**: `./gradlew :server-app:compileKotlin` and `npx tsc --noEmit`

### Next Steps
1. **Test end-to-end**: verify DRM streams play through JW Player (ClearKey + Widevine), verify Browse tab fetches from real repos, verify install/uninstall flow
2. **Test POST /api/repositories 403** — curl directly to localhost:8080 to isolate Vite proxy vs Ktor issue
3. **Upstream 403/404 on some streams** — some M3U8 URLs from providers return 403/404 even with correct headers; may be expired tokens or provider-specific auth

## Key Decisions
- **Full plugin loading for server-app** — uses `ExtensionLoader.loadAndInit()` same as desktop-app. Catches individual plugin failures so one bad plugin doesn't block startup
- **`catch (e: Throwable)`** — `NotImplementedError` extends `Error` not `Exception`
- **MainPage API instead of empty-search fallback** — mirrors desktop's `provider.getMainPage()`
- **JW Player replaces custom video + hls.js/dashjs** — handles HLS/DASH/MP4 natively; type must be set explicitly for proxy URLs
- **Echo Origin instead of `*`** — JW Player's HLS shim uses credentialed XHR which requires exact Origin per browser spec
- **Per-link headers merged into shared proxy session** — avoids per-session-per-link complexity while forwarding provider-specific auth headers
- **Extension disabled state stored in shared DesktopDataStore** — same `datastore.json` used by both processes for cross-sync
- **Browse API server-side** — fetches repo manifests + plugin lists server-side via `java.net.http.HttpClient`, returning aggregated JSON to webUI; avoids CORS issues of client-side fetching
- **DrmExtractorLink detection via safe cast** — `link as? DrmExtractorLink` in callback, populates DRM fields alongside regular LinkResult fields
