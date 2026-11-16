# web (Angular)

Not scaffolded yet. From the repo root:

```bash
ng new web --prefix nt --style scss --routing --ssr false --skip-git
```

API client (generated from `api-spec/openapi.yaml`):

```bash
openapi-generator-cli generate -g typescript-angular -i api-spec/openapi.yaml -o web/src/app/core/api
```
