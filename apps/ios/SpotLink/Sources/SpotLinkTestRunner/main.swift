import Testing
import SpotLinkTestSupport

@main
struct SpotLinkTestRunner {
    static func main() async {
        await Testing.__swiftPMEntryPoint() as Never
    }
}
