import Capacitor

final class CloudlaneViewController: CAPBridgeViewController {
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(LivePhotoPlugin())
    }
}
