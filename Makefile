.PHONY: help backend frontend dev test test-backend test-frontend test-ios test-ios-xcode build build-backend build-ios build-ios-xcode build-ios-release-unsigned release-gate

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

release-gate: ## Pokreni backend, frontend, SwiftPM, Xcode testove i unsigned iOS Release build
	$(MAKE) test-backend
	$(MAKE) test-frontend
	$(MAKE) build
	swift package clean --package-path apps/ios/SpotLink
	$(MAKE) test-ios
	$(MAKE) test-ios-xcode
	$(MAKE) build-ios-release-unsigned

# ── podesavanje ───────────────────────────────────────────────────────────────

install: ## Instaliraj frontend zavisnosti
	npm install --prefix apps/frontend

env: ## Kopiraj .env.example u .env ako .env ne postoji
	@test -f .env || (cp .env.example .env && echo ".env kreiran iz .env.example")
