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
                }
        }
    }
}
