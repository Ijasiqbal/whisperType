import Foundation

enum RegionSelector {

    /// Returns the best Cloud Functions region based on the system timezone.
    static func bestRegion() -> String {
        // Check if user has a manual override
        if let override = UserDefaults.standard.string(forKey: Constants.selectedRegionKey),
           Constants.regions.contains(override) {
            return override
        }

        let tz = TimeZone.current
        let secondsFromGMT = tz.secondsFromGMT()
        let hoursFromGMT = secondsFromGMT / 3600

        // Asia/Pacific: UTC+3 to UTC+12
        if hoursFromGMT >= 3 && hoursFromGMT <= 12 {
            return "asia-south1"
        }

        // Europe/Africa: UTC-1 to UTC+3
        if hoursFromGMT >= -1 && hoursFromGMT < 3 {
            return "europe-west1"
        }

        // Americas (default): UTC-12 to UTC-1
        return "us-central1"
    }

    static var allRegions: [(id: String, name: String)] {
        [
            ("us-central1", "US Central"),
            ("asia-south1", "Asia South (India)"),
            ("europe-west1", "Europe West"),
        ]
    }
}
