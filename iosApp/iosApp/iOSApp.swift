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
                    let rc = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                        .queryItems?
                        .first(where: { $0.name == "rc" })?
                        .value
                    RcRouter.shared.select(name: rc)
                }
        }
    }
}
