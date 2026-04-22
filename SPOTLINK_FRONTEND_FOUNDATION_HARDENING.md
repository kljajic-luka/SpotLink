# SpotLink Frontend Foundation Hardening

Datum: 2026-04-22

## Audit rezime

Frontend foundation modul po modul je pregledan:

- Core
- DesignSystem
- Networking
- Auth
- UserProfile
- Vehicles
- Locations
- Reservations
- Payments
- Support
- Notifications
- Operator
- Admin
- Analytics
- SharedComponents

Zakljucak audita:

- Arhitektura je vec cista i konzistentna (standalone Angular, strict TS, modularni service/model boundary).
- Endpoint putanje su vec stabilne i predstavljaju frontend ugovor prema backend-u.
- Najveci prostor za hardening je bio u:
  - jacoj tipizaciji query parametara,
  - sigurnijoj serializaciji query vrednosti,
  - konfiguracionoj konzistentnosti interceptora,
  - fallback ponasanju za prikaz datuma/iznosa u reservation view-model helperu,
  - pokrivenosti osnovnim testovima.

## Sta je promenjeno

### 1) Networking hardening

- Uvedeni su tipovi za query parametre (`QueryParamPrimitive`, `QueryParamValue`, `QueryParams`).
- `ApiClient` serializacija query parametara je ojacana:
  - podrzani su `string`, `number`, `boolean`, `Date` i nizovi tih tipova,
  - `null` i `undefined` se preskacu,
  - nepodrzane objektne vrednosti se bezbedno preskacu,
  - nevalidni brojevi i nevalidan `Date` se ne salju.

### 2) Interceptor konzistentnost

- Dodata je centralna konfiguracija za interceptor konstante:
  - XSRF cookie/header nazivi,
  - retry parametri i lista endpoint prefiksa bez retry-a.
- `auth-credentials` i `retry` interceptor sada koriste ove centralne konstante.

### 3) Reservations view-model hardening

- `reservation.view-model` je ojacan za defanzivno formatiranje:
  - nevalidan ili prazan datum vraca `N/A`,
  - nevalidan currency kod ima fallback format iznosa.

### 4) Design-system a11y polish

- `sl-text-field` sada povezuje hint tekst sa kontrolom preko `aria-describedby`.

### 5) Fokusirani testovi (bez framework migracije)

Dodati su testovi:

- `core/idempotency-key.util.spec.ts`
- `core/storage.service.spec.ts`
- `networking/api-client.service.spec.ts`
- `reservations/reservation.view-model.spec.ts`
- `design-system/components/ui-button.component.spec.ts`

## Sta namerno nije menjano

- Nisu menjane endpoint putanje u servisima (ocuvan frontend/backend ugovor).
- Nije menjana backend struktura niti backend fajlovi.
- Nije uvodjena velika refaktorizacija ni nova arhitektura.
- Nisu menjani nazivi domena/entiteta van postojecih foundation pravila.
- Nije uvodjen novi test framework niti CI migracija.

## API contract pretpostavke

- Backend podrzava cookie sesiju i `withCredentials` tok.
- Backend podrzava XSRF cookie/header obrazac:
  - cookie: `XSRF-TOKEN`
  - header: `X-XSRF-TOKEN`
- Backend obradjuje idempotency kroz DTO polje `idempotencyKey` za:
  - `POST /reservations`
  - `POST /payments/intents`
- Pagination shape prati frontend `ApiPage<T>` obrazac (`content`, `totalElements`, `totalPages`, `page`, `size`).
- Role-level autorizacija za operator/admin putanje je backend odgovornost.

## Preostali frontend rizici

- Nema E2E testova za kompletan tok (search -> quote -> reservation -> payment).
- Nema ugovorne validacije nad realnim backend OpenAPI fajlom (trenutno draft/ocekivanje).
- Error poruke su trenutno staticke i nisu internacionalizovane.
- Retry pravila su centralizovana, ali i dalje staticki definisana (bez runtime feature flag-a).

## Preporuceni sledeci frontend koraci kada backend foundation sleti

1. Uspostaviti automatsku proveru uskladjenosti frontend servisa sa backend OpenAPI specifikacijom.
2. Dodati integration testove za auth/session i reservation/payment tokove.
3. Povezati role-aware route guard scenarije sa realnim backend role odgovorima.
4. Dodati i18n sloj za user-facing error poruke iz `api-error` interceptora.
5. Dodati osnovni observability sloj (request correlation i error telemetry) uskladjen sa backend CORS pravilima.
