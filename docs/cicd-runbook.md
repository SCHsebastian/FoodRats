# CI/CD runbook — release a Play Store y App Store

Guía operativa para poner en marcha el pipeline descrito en
`docs/specs/2026-05-20-cicd-store-release-pipeline-design.md`. Los ficheros del
pipeline ya están en el repo; esto cubre los pasos manuales (los que solo puede
hacer una persona con acceso a las cuentas) y cómo se opera el día a día.

> Coste real: **99 USD/año** (Apple Developer Program) + **25 USD una vez**
> (Google Play). Nada más; toda la infra es gratis (Linux de GitHub + tu Mac).

---

## Mapa de lo que ya está en el repo

| Fichero | Para qué |
|---|---|
| `androidApp/build.gradle.kts` | versión por `-PversionName/-PversionCode`, `signingConfig` release desde env, R8/minify ON |
| `androidApp/proguard-rules.pro` | reglas `-keep` (kotlinx-serialization, Crashlytics) |
| `Gemfile` | fija Fastlane |
| `fastlane/Appfile` `Matchfile` `Fastfile` | lanes `android beta/release`, `ios beta/release` |
| `scripts/ci/compute_version.sh` | calcula versionName/Code (beta vs prod) |
| `.github/workflows/ci.yml` | tests + smoke build R8 en PRs |
| `.github/workflows/release-beta.yml` | push a `main` → Play Internal + TestFlight |
| `.github/workflows/release-production.yml` | tag `vX.Y.Z` → Play Prod + App Store (con aprobación) |

---

## Fase 0 — setup único (hazlo una vez, en orden)

### 1. Cuentas y apps en las tiendas

**Google Play**
1. Paga el registro de desarrollador (25 USD) y crea la app con package `es.schsebastian.foodrats`.
2. **Enrola en Play App Signing** (Google custodia la clave de firma de la app; tú solo guardas la *upload key*).
3. Genera la **upload key** en local:
   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   Guarda el `.jks` y las contraseñas en un gestor de secretos — no en el repo.
4. **Sube a mano el primer AAB** por la consola (Play exige un primer artefacto manual antes de habilitar la API). Construye uno con:
   ```bash
   ANDROID_KEYSTORE_PATH=$PWD/upload.jks ANDROID_KEYSTORE_PASSWORD=... \
   ANDROID_KEY_ALIAS=upload ANDROID_KEY_PASSWORD=... \
   ./gradlew :androidApp:bundleRelease -PversionName=0.1.0 -PversionCode=10001
   ```
5. Crea una **service account** en Google Cloud, dale rol de release y concédele acceso en Play Console → Users and permissions. Descarga su JSON.
6. Rellena la ficha (descripción, capturas, content rating, data safety).

**Apple**
1. Paga el Apple Developer Program (99 USD/año).
2. Crea el **App ID** `es.schsebastian.foodrats` y el registro de app en App Store Connect.
3. Crea una **App Store Connect API Key** (rol App Manager) y descarga el `.p8` (solo se puede una vez). Apunta Key ID e Issuer ID.
4. Crea un **repo privado vacío** para `match` y genera los certificados desde tu Mac:
   ```bash
   bundle install
   bundle exec fastlane match appstore   # MATCH_GIT_URL y MATCH_PASSWORD en el entorno
   ```

### 2. Pasos manuales de Xcode (commitea el `project.pbxproj`)

Según `CLAUDE.md` (sección iOS), antes del primer build de release iOS:
- Añade los paquetes SPM de Firebase y GoogleSignIn (ver `iosApp/SETUP.md`).
- Enlaza `CoreLocation.framework` (ImagePickerKMP).
- Añade el producto SPM **FirebaseCrashlytics** + el Run Script de subida de dSYM.

### 3. Self-hosted runner en tu Mac

1. GitHub → repo → **Settings → Actions → Runners → New self-hosted runner** → macOS / arm64. Sigue el instalador.
2. **Asígnale la etiqueta `macos`** (los workflows usan `runs-on: [self-hosted, macos]`).
3. Requisitos en el Mac: Xcode + command line tools, JDK (lo provisiona el wrapper, pero ten un JDK 21 a mano), Ruby 3.3. Arráncalo con `./run.sh` o instálalo como servicio (`svc.sh install && svc.sh start`).
4. **Seguridad:** este runner solo debe atender los workflows de release (push a `main` y tags). No lo expongas a CI de PRs de forks no confiables.

### 4. GitHub Environments y secrets

Crea dos environments en **Settings → Environments**: `beta` y `production`.
- En `production`: marca **Required reviewers** (ponte a ti) y, opcional, *wait timer*. Restringe deployment a tags `v*`.

Carga estos secrets (en el environment correspondiente, o repo-wide los comunes):

| Secret | Dónde | Valor |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | ambos | `base64 -i androidApp/google-services.json` |
| `GOOGLE_SERVER_CLIENT_ID` | ambos | web client id de OAuth |
| `ANDROID_KEYSTORE_BASE64` | ambos | `base64 -i upload.jks` |
| `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` | ambos | de la upload key |
| `PLAY_SERVICE_ACCOUNT_JSON` | ambos | `base64 -i play-service-account.json` |
| `GOOGLESERVICE_INFO_PLIST` | ambos | `base64 -i iosApp/iosApp/GoogleService-Info.plist` |
| `ASC_KEY_ID` / `ASC_ISSUER_ID` | ambos | de la API key |
| `ASC_KEY_P8` | ambos | `base64 -i AuthKey_XXXX.p8` |
| `MATCH_GIT_URL` | ambos | URL del repo privado de match |
| `MATCH_PASSWORD` | ambos | passphrase elegida en `match init` |
| `MATCH_GIT_BASIC_AUTHORIZATION` | ambos | `printf 'usuario:PAT' \| base64` (acceso de lectura al repo de match) |

> En macOS `base64 -i fichero` no añade saltos de línea. En Linux usa `base64 -w0 fichero`.

### 5. Protección de ramas y tags

- **Branch protection en `main`:** requerir PR, ≥1 review, check `CI` en verde, rama actualizada; prohibir push directo.
- **Tag protection rule** para `v*`: solo maintainers crean tags de release.

---

## Operación del día a día

### Sacar una beta
Simplemente **mergea a `main`**. `release-beta.yml` construye y sube a Play
Internal y TestFlight automáticamente (iOS en tu Mac; si está apagado, el job
espera en cola).

### Sacar una versión a producción
```bash
git tag v1.4.0
git push origin v1.4.0
```
Esto dispara `release-production.yml`. Los jobs se quedan **en espera de
aprobación** (environment `production`); apruébalos desde la pestaña Actions y
se publica con rollout escalonado (Play 20%, App Store phased). Sube el rollout
al 100% desde las consolas cuando estés conforme.

### Probar lanes en local (sin CI)
```bash
bundle install
# Android (necesita un AAB ya construido y el JSON de Play):
PLAY_JSON_KEY_PATH=play.json bundle exec fastlane android beta
# iOS (en tu Mac, con los env de match y ASC):
bundle exec fastlane ios beta
```

---

## Verificación obligatoria de R8 (no la saltes)

R8 está activado desde el primer release. Antes de confiar en el primer AAB de
producción, instala el AAB **minificado** en un dispositivo y recorre el flujo
crítico: sign-in → crear/unirse a crew → publicar meal → feed → stats →
notificación. Si algo casca en runtime, casi siempre falta un `-keep` en
`androidApp/proguard-rules.pro` (sospecha primero de DTOs `@Serializable` de
Firestore y de las rutas de navegación). Itera ahí hasta que el flujo pase.
