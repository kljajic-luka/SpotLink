# Screenshot Assets

Current README screenshots live in [screenshots](screenshots).

They were generated from the real native iOS application on June 4, 2026:

- Backend: `make backend` with the `dev` profile and H2 seed data.
- Simulator: iPhone 16, iOS 18.1.
- App: `SpotLinkApp` Debug build, launched unsigned with `CODE_SIGNING_ALLOWED=NO`.
- Test account: checked-in dev seed customer `korisnik@spotlink.rs` / `Demo1234!`.
- Screenshot source: the running SwiftUI app in Simulator, captured through the Build iOS Apps simulator workflow.

Capture commands:

```bash
env PATH=/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin make backend
xcodebuild -project apps/ios/SpotLink.xcodeproj \
  -scheme SpotLinkApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO build
```

The screenshots cover login, registration, parking search, booking setup, profile, and the legal/support/account deletion surface. They are not mockups; the README gallery uses only native iPhone captures.

Obsolete foundation-era screenshots were removed because they no longer represented the current application surfaces.
