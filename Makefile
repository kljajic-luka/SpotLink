.PHONY: help backend frontend dev test test-backend test-frontend test-ios test-ios-xcode build build-backend validate-backend-runtime-config build-backend-image build-ios build-ios-xcode build-ios-release-unsigned build-ios-staging-unsigned validate-ios-privacy-config validate-ios-signed-config check-ios-signing-env-staging check-ios-signing-env-release generate-ios-staging-export-options generate-ios-release-export-options archive-ios-staging-signed archive-ios-release-signed export-ios-staging-testflight export-ios-release-testflight release-gate

IOS_ARCHIVE_DIR ?= build/ios/archives
IOS_EXPORT_DIR ?= build/ios/exports
IOS_EXPORT_OPTIONS_DIR ?= build/ios/export-options
BACKEND_IMAGE ?= spotlink-backend:local
IOS_STAGING_ARCHIVE_PATH ?= $(IOS_ARCHIVE_DIR)/SpotLinkStaging.xcarchive
IOS_RELEASE_ARCHIVE_PATH ?= $(IOS_ARCHIVE_DIR)/SpotLinkRelease.xcarchive
IOS_STAGING_EXPORT_PATH ?= $(IOS_EXPORT_DIR)/staging
IOS_RELEASE_EXPORT_PATH ?= $(IOS_EXPORT_DIR)/release
IOS_STAGING_EXPORT_OPTIONS ?= $(IOS_EXPORT_OPTIONS_DIR)/SpotLinkStaging-ExportOptions.plist
IOS_RELEASE_EXPORT_OPTIONS ?= $(IOS_EXPORT_OPTIONS_DIR)/SpotLinkRelease-ExportOptions.plist
IOS_STAGING_EXPORT_TEMPLATE := apps/ios/ExportOptions/AppStoreConnect-Staging.plist
IOS_RELEASE_EXPORT_TEMPLATE := apps/ios/ExportOptions/AppStoreConnect-Release.plist

## ─── SpotLink lokalni razvoj ─────────────────────────────────────────────────
## Koristi make <target>. Backend podrazumevano koristi H2 in-memory bazu.

help: ## Prikaz dostupnih naredbi
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ── pokretanje ────────────────────────────────────────────────────────────────

backend: ## Pokreni backend (H2 in-memory, profil=dev, port 8080)
	mvn -f apps/backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev

frontend: ## Pokreni Angular dev server (port 4200)
	npm --prefix apps/frontend run start

dev: ## Pokreni backend i frontend paralelno
	$(MAKE) -j2 backend frontend

# ── testovi ───────────────────────────────────────────────────────────────────

test-backend: ## Pokreni backend testove
	mvn -f apps/backend/pom.xml clean test

test-frontend: ## Pokreni frontend testove (ChromeHeadless, CI=1)
	npm --prefix apps/frontend run test:ci

test-ios: ## Pokreni iOS Swift testove
	swift test --package-path apps/ios/SpotLink

test-ios-xcode: ## Pokreni Xcode unit/UI testove preko SpotLinkApp sheme
	@echo "Available iOS simulators:"
	@xcrun simctl list devices available
	@SIMULATOR_UDID="$$(xcrun simctl list devices available -j | python3 -c 'import json, sys; data = json.load(sys.stdin); devices = [device for runtime in data.get("devices", {}).values() for device in runtime if device.get("isAvailable")]; iphones = [device for device in devices if device.get("name", "").startswith("iPhone")]; selected = (iphones or devices); print(selected[0]["udid"] if selected else "")')" ; \
	if [ -z "$$SIMULATOR_UDID" ]; then \
		echo "No available iOS simulators found for Xcode tests."; \
		exit 1; \
	fi; \
	xcodebuild test \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkApp \
		-destination "platform=iOS Simulator,id=$$SIMULATOR_UDID" \
		CODE_SIGNING_ALLOWED=NO

test: test-backend test-frontend ## Pokreni backend + frontend testove

# ── build ─────────────────────────────────────────────────────────────────────

build: ## CI build Angular fronenda (CI=1, produkcija)
	npm --prefix apps/frontend run build:ci

build-backend: ## Pakuj backend JAR (preskoci testove)
	mvn -f apps/backend/pom.xml package -DskipTests

validate-backend-runtime-config: ## Pokreni backend staging/prod runtime guard i logging testove
	mvn -f apps/backend/pom.xml -Dtest=RuntimeSafetyGuardTest,AuthServiceLoggingTest,HealthEndpointTest test

build-backend-image: ## Build provider-neutral backend container image ako je Docker dostupan
	@if ! command -v docker >/dev/null 2>&1; then \
		echo "Docker is not installed or not on PATH. Install Docker to build $(BACKEND_IMAGE)."; \
		exit 1; \
	fi
	docker build -f apps/backend/Dockerfile -t "$(BACKEND_IMAGE)" .

build-ios: ## Build iOS Swift paketa
	swift build --package-path apps/ios/SpotLink

build-ios-xcode: ## Build iOS app targeta za genericki simulator preko SpotLinkApp sheme
	xcodebuild build \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkApp \
		-destination 'generic/platform=iOS Simulator' \
		CODE_SIGNING_ALLOWED=NO

build-ios-release-unsigned: ## Validiraj unsigned iOS Release build bez Apple signing kredencijala
	xcodebuild build \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkApp \
		-configuration Release \
		-destination 'generic/platform=iOS' \
		CODE_SIGNING_ALLOWED=NO

build-ios-staging-unsigned: ## Validiraj unsigned iOS Staging build preko SpotLinkStaging sheme
	xcodebuild build \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkStaging \
		-configuration Staging \
		-destination 'generic/platform=iOS' \
		CODE_SIGNING_ALLOWED=NO

validate-ios-privacy-config: ## Lintuj iOS privacy manifest, entitlements i Info.plist
	plutil -lint apps/ios/Resources/PrivacyInfo.xcprivacy apps/ios/SpotLink.entitlements apps/ios/Resources/Info.plist

validate-ios-signed-config: validate-ios-privacy-config ## Validiraj signed archive/export konfiguraciju bez Apple kredencijala
	@plutil -lint $(IOS_STAGING_EXPORT_TEMPLATE) $(IOS_RELEASE_EXPORT_TEMPLATE)
	@xcodebuild -showBuildSettings \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkStaging \
		-configuration Staging \
		-destination 'generic/platform=iOS' \
		CODE_SIGNING_ALLOWED=NO | grep -q "PRODUCT_BUNDLE_IDENTIFIER = com.spotlink.app.staging"
	@xcodebuild -showBuildSettings \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkStaging \
		-configuration Staging \
		-destination 'generic/platform=iOS' \
		CODE_SIGNING_ALLOWED=NO | grep -q "SPOTLINK_ENV = staging"
	@xcodebuild -showBuildSettings \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkApp \
		-configuration Release \
		-destination 'generic/platform=iOS' \
		CODE_SIGNING_ALLOWED=NO | grep -q "PRODUCT_BUNDLE_IDENTIFIER = com.spotlink.app"
	@xcodebuild -showBuildSettings \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkApp \
		-configuration Release \
		-destination 'generic/platform=iOS' \
		CODE_SIGNING_ALLOWED=NO | grep -q "SPOTLINK_ENV = production"
	@echo "Signed iOS archive/export config is valid. Apple credentials are checked only by signed archive/export targets."

check-ios-signing-env-staging:
	@if [ -z "$(SPOTLINK_APPLE_TEAM_ID)" ]; then \
		echo "SPOTLINK_APPLE_TEAM_ID is required for signed iOS archives."; \
		exit 2; \
	fi
	@if [ -z "$(SPOTLINK_STAGING_PROFILE_SPECIFIER)" ]; then \
		echo "SPOTLINK_STAGING_PROFILE_SPECIFIER is required for com.spotlink.app.staging."; \
		exit 2; \
	fi

check-ios-signing-env-release:
	@if [ -z "$(SPOTLINK_APPLE_TEAM_ID)" ]; then \
		echo "SPOTLINK_APPLE_TEAM_ID is required for signed iOS archives."; \
		exit 2; \
	fi
	@if [ -z "$(SPOTLINK_RELEASE_PROFILE_SPECIFIER)" ]; then \
		echo "SPOTLINK_RELEASE_PROFILE_SPECIFIER is required for com.spotlink.app."; \
		exit 2; \
	fi

generate-ios-staging-export-options: check-ios-signing-env-staging
	@mkdir -p "$(IOS_EXPORT_OPTIONS_DIR)"
	@cp "$(IOS_STAGING_EXPORT_TEMPLATE)" "$(IOS_STAGING_EXPORT_OPTIONS)"
	@/usr/libexec/PlistBuddy -c "Set :teamID $(SPOTLINK_APPLE_TEAM_ID)" "$(IOS_STAGING_EXPORT_OPTIONS)"
	@/usr/libexec/PlistBuddy -c "Set :provisioningProfiles:com.spotlink.app.staging $(SPOTLINK_STAGING_PROFILE_SPECIFIER)" "$(IOS_STAGING_EXPORT_OPTIONS)"
	@echo "Generated $(IOS_STAGING_EXPORT_OPTIONS)"

generate-ios-release-export-options: check-ios-signing-env-release
	@mkdir -p "$(IOS_EXPORT_OPTIONS_DIR)"
	@cp "$(IOS_RELEASE_EXPORT_TEMPLATE)" "$(IOS_RELEASE_EXPORT_OPTIONS)"
	@/usr/libexec/PlistBuddy -c "Set :teamID $(SPOTLINK_APPLE_TEAM_ID)" "$(IOS_RELEASE_EXPORT_OPTIONS)"
	@/usr/libexec/PlistBuddy -c "Set :provisioningProfiles:com.spotlink.app $(SPOTLINK_RELEASE_PROFILE_SPECIFIER)" "$(IOS_RELEASE_EXPORT_OPTIONS)"
	@echo "Generated $(IOS_RELEASE_EXPORT_OPTIONS)"

archive-ios-staging-signed: check-ios-signing-env-staging validate-ios-signed-config ## Signed Staging xcarchive za internal TestFlight pripremu
	@mkdir -p "$(IOS_ARCHIVE_DIR)"
	xcodebuild archive \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkStaging \
		-configuration Staging \
		-destination 'generic/platform=iOS' \
		-archivePath "$(IOS_STAGING_ARCHIVE_PATH)" \
		CODE_SIGNING_ALLOWED=YES \
		CODE_SIGN_STYLE=Manual \
		DEVELOPMENT_TEAM="$(SPOTLINK_APPLE_TEAM_ID)" \
		PROVISIONING_PROFILE_SPECIFIER="$(SPOTLINK_STAGING_PROFILE_SPECIFIER)"

archive-ios-release-signed: check-ios-signing-env-release validate-ios-signed-config ## Signed Release xcarchive za App Store/TestFlight pripremu
	@mkdir -p "$(IOS_ARCHIVE_DIR)"
	xcodebuild archive \
		-project apps/ios/SpotLink.xcodeproj \
		-scheme SpotLinkApp \
		-configuration Release \
		-destination 'generic/platform=iOS' \
		-archivePath "$(IOS_RELEASE_ARCHIVE_PATH)" \
		CODE_SIGNING_ALLOWED=YES \
		CODE_SIGN_STYLE=Manual \
		DEVELOPMENT_TEAM="$(SPOTLINK_APPLE_TEAM_ID)" \
		PROVISIONING_PROFILE_SPECIFIER="$(SPOTLINK_RELEASE_PROFILE_SPECIFIER)"

export-ios-staging-testflight: archive-ios-staging-signed ## Exportuj Staging IPA za human-controlled App Store Connect upload
	$(MAKE) generate-ios-staging-export-options
	@mkdir -p "$(IOS_STAGING_EXPORT_PATH)"
	xcodebuild -exportArchive \
		-archivePath "$(IOS_STAGING_ARCHIVE_PATH)" \
		-exportPath "$(IOS_STAGING_EXPORT_PATH)" \
		-exportOptionsPlist "$(IOS_STAGING_EXPORT_OPTIONS)"

export-ios-release-testflight: archive-ios-release-signed ## Exportuj Release IPA za human-controlled App Store Connect upload
	$(MAKE) generate-ios-release-export-options
	@mkdir -p "$(IOS_RELEASE_EXPORT_PATH)"
	xcodebuild -exportArchive \
		-archivePath "$(IOS_RELEASE_ARCHIVE_PATH)" \
		-exportPath "$(IOS_RELEASE_EXPORT_PATH)" \
		-exportOptionsPlist "$(IOS_RELEASE_EXPORT_OPTIONS)"

release-gate: ## Pokreni backend, frontend, SwiftPM, Xcode testove i unsigned iOS Release/Staging buildove
	$(MAKE) test-backend
	$(MAKE) test-frontend
	$(MAKE) build
	$(MAKE) validate-ios-privacy-config
	swift package clean --package-path apps/ios/SpotLink
	$(MAKE) test-ios
	$(MAKE) test-ios-xcode
	$(MAKE) build-ios-release-unsigned
	$(MAKE) build-ios-staging-unsigned

# ── podesavanje ───────────────────────────────────────────────────────────────

install: ## Instaliraj frontend zavisnosti
	npm install --prefix apps/frontend

env: ## Kopiraj .env.example u .env ako .env ne postoji
	@test -f .env || (cp .env.example .env && echo ".env kreiran iz .env.example")
