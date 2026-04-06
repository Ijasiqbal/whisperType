import Foundation

/// Logs only in DEBUG builds. Compiles to nothing in release.
@inline(__always)
func debugLog(_ message: @autoclosure () -> String) {
    #if DEBUG
    print(message())
    #endif
}
