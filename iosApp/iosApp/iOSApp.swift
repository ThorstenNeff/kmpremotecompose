import SwiftUI
import Shared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // REM-34: kmprc://render?rc=<name> deep-link selects the bundled doc; RcRouter (shared)
                // is observed by the Compose app, which reloads. Unknown name -> rc-error at load.
                .onOpenURL { url in
                    let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems
                    RcRouter.shared.select(name: items?.first(where: { $0.name == "rc" })?.value)
                    // REM-37 E-D1: &live=1 opts into the live-animation loop (clocks tick, cube3d spins).
                    RcRouter.shared.live = (items?.first(where: { $0.name == "live" })?.value == "1")
                    // REM-62: &t=<sec> pins the static-mode frame (deterministic non-zero capture, spread
                    // analog-clock hands). Absent / 0 / invalid → t=0 (the original static path).
                    RcRouter.shared.setStaticTime(value: items?.first(where: { $0.name == "t" })?.value)
                    // REM-91: &density=<f> forces a uniform render density for cross-target parity (iOS-Sim
                    // is fixed @3x). Absent / invalid → nil = real platform density (original path).
                    RcRouter.shared.setForcedDensity(value: items?.first(where: { $0.name == "density" })?.value)
                }
        }
    }
}
