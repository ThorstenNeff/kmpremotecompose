# REM-173 — Local example `.rc` server

A runnable **localhost** server that serves example `.rc` documents over HTTP, for the example app's
"load from server" mode. Built on REM-169 (`startLocalRcServer` + the page-registry). **Android + iOS
only; localhost-only** — public exposure is the human-gated REM-170 (parked), and there is no CORS/web.

## Run

```bash
# default port 8080
./gradlew :server:runExampleServer

# or pick a port
./gradlew :server:runExampleServer -PserverPort=9000
```

The server binds **`127.0.0.1` only**, prints its URL + the available pageIds, and serves
`GET /rc/{pageId}` (Content-Type `application/octet-stream`, strong ETag → `304` on revalidation).
`GET /rc` lists the registered pageIds. Stop with Ctrl-C (clean shutdown).

## pageId → doc map

The 10 **Area-B** ids match dev-1's example-app catalog **verbatim** (so the app's server-toggle
`ServerPage(rc_*)` maps 1:1), each served as the bundled lib-corpus `.rc` **verbatim**:

| pageId | bundled `.rc` |
|---|---|
| `rc_box` | `c_box.rc` |
| `rc_text` | `text_baseline.rc` |
| `rc_gradient` | `procedure_gradient1.rc` |
| `rc_moon` | `moon_phases.rc` |
| `rc_clock` | `clock_demo1_clock1.rc` |
| `rc_confetti` | `impulse_demo_confetti_demo.rc` |
| `rc_scroll` | `c_modifier_vertical_scroll.rc` |
| `rc_click` | `c_modifier_on_click.rc` |
| `rc_pie` | `good_pie_chart.rc` |
| `rc_compass` | `sensor_demo_compass.rc` |

Plus a few **procedural `document{}`** extras (demonstrate the byte-true procedural path):

| pageId | doc |
|---|---|
| `simple2` | the byte-true `procedure_simple2` oracle (full-window oval) |
| `oval` | a 300×300 full-rect oval |
| `circle` | a centered filled circle (400×400) |

`pageId` is an **allowlist key**, never a filesystem path (no traversal); an unknown id → `404`.
Corpus docs are bundled in `:server/src/main/resources/rc/`.

## App integration ("load from server")

The example app supplies a `loadRc` variant that fetches over HTTP. **Host per platform:** Android
emulator reaches the host machine at `10.0.2.2`; the iOS simulator shares the host network at
`127.0.0.1`.

```kotlin
// RC_SERVER_HOST: Android emulator = "10.0.2.2", iOS simulator = "127.0.0.1"
// port: the one the server printed (default 8080)
suspend fun loadFromServer(pageId: String): ByteArray =
    httpClient.get("http://$RC_SERVER_HOST:$port/rc/$pageId").readRawBytes()

// then:  RemoteComposeApp(loadRc = ::loadFromServer)
```

`httpClient` is a Ktor `HttpClient` (any engine). dev-1 wires this into the app's "load from server"
mode (coordinated via the PO). No app-side `:server`/JVM dependency is needed — only an HTTP client.
