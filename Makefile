.PHONY: help backend frontend dev test test-backend test-frontend test-ios build build-backend

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

test: test-backend test-frontend ## Pokreni backend + frontend testove

# ── build ─────────────────────────────────────────────────────────────────────

build: ## CI build Angular fronenda (CI=1, produkcija)
	npm --prefix apps/frontend run build:ci

build-backend: ## Pakuj backend JAR (preskoci testove)
	mvn -f apps/backend/pom.xml package -DskipTests

build-ios: ## Build iOS Swift paketa
	swift build --package-path apps/ios/SpotLink

# ── podesavanje ───────────────────────────────────────────────────────────────

install: ## Instaliraj frontend zavisnosti
	npm install --prefix apps/frontend

env: ## Kopiraj .env.example u .env ako .env ne postoji
	@test -f .env || (cp .env.example .env && echo ".env kreiran iz .env.example")
