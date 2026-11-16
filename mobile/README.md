# mobile (Flutter)

Not scaffolded yet. From the repo root:

```bash
flutter create --org apps.digitworks --project-name naanayamthavanai --platforms android,ios mobile
```

This gives the package ID `apps.digitworks.naanayamthavanai`.

API client (generated from `api-spec/openapi.yaml`):

```bash
openapi-generator-cli generate -g dart-dio -i api-spec/openapi.yaml -o mobile/packages/api_client
```
