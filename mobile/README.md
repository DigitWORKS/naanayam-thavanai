# mobile (Flutter)

Package ID `apps.digitworks.naanayamthavanai`. Riverpod (state), Dio (HTTP), flutter_secure_storage (tokens).

## First-time setup

`lib/`, `test/`, `pubspec.yaml` and the debug Android manifest are in the repo; the platform folders are generated.
`flutter create` only adds missing files, so it keeps ours. From the repo root:

```bash
flutter create --org apps.digitworks --project-name naanayamthavanai --platforms android,ios mobile
cd mobile
flutter pub get
flutter test
```

If `pub get` cannot resolve a version, run `flutter pub upgrade --major-versions`.

## Run against the local service

| Target | API URL |
|---|---|
| Android emulator | `http://10.0.2.2:8080/v1` (default) |
| iOS simulator | `http://localhost:8080/v1` (default) |
| Physical phone (same Wi-Fi) | `flutter run --dart-define=API_BASE_URL=http://<mac-lan-ip>:8080/v1` |

- Android debug builds allow plain http (`android/app/src/debug/AndroidManifest.xml`).
- iOS: for plain http to a LAN IP, add `NSAppTransportSecurity` → `NSAllowsLocalNetworking = YES` to `ios/Runner/Info.plist`.
- Release builds need `<uses-permission android:name="android.permission.INTERNET"/>` in
  `android/app/src/main/AndroidManifest.xml` (Flutter adds it only to debug/profile).

## Structure

```
lib/
  core/        config (API URL), money (paise, decimal strings), api_client (bearer + refresh-once),
               api_error, token_store, providers
  theme/       colours and fonts from the design canvas
  widgets/     shared form widgets (phone field, PIN boxes, labels, sections), brand mark
  features/
    auth/          login, register (free trial), session (SignedIn / SignedOut)
    shell/         bottom navigation by role, account / log out, placeholders
    clients/       client list (search, Behind / No active loan / area filters), new client, client detail
    dashboard/     owner home
    handover/      cash handovers (owner confirm / dispute)
    loans/         loan list by status, new loan with live preview, loan detail (schedule / payments), pre-close quote
    collections/   today's due list (agent home)
    payments/      record payment (cash / UPI + UTR), idempotent clientTxnId
    settings/      owner business settings and plan card
```

Bottom navigation: owner Home / Today / Clients / Loans / More (Reports, Settings under More); agent Today /
Clients / Handover / More. Owners collect and manage clients too. Tabs not built yet show a placeholder.

## Notes

- Payments: `clientTxnId` is created when the form opens and reused on retry. If a submit gets no
  response, the form locks and only allows "Try again" with the same data, so a payment is never recorded twice.
- API models are hand-written for now; switch to a client generated from `api-spec/openapi.yaml` once the
  screen set settles (`openapi-generator-cli generate -g dart-dio -i api-spec/openapi.yaml -o mobile/packages/api_client`).
