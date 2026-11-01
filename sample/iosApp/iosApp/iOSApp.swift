import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        MainViewController_appleKt.initChronos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
