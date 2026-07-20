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
4. Crea un **repo privado vacío** para `match` y genera/rota los certificados desde tu Mac
   con la lane `rotate_signing` (no-interactiva: autentica con la ASC API key, sin Apple ID/2FA;
   fuerza `readonly: false` para poder escribir, ya que el Matchfile es `readonly(true)`). Vuelve
   a correrla cada vez que cambien las *capabilities* del App ID (p. ej. al activar Sign in with Apple):
   ```bash
   bundle install   # instala fastlane en vendor/bundle (sin sudo, ya gitignored)
   ASC_KEY_ID=<key-id> ASC_ISSUER_ID=<issuer-id> ASC_KEY_PATH=</ruta/AuthKey_XXX.p8> \
   MATCH_GIT_URL=<repo-privado> MATCH_PASSWORD=<passphrase> \
     bundle exec fastlane ios rotate_signing
   ```

### 2. Pasos manuales de Xcode (commitea el `project.pbxproj`)

Según `CLAUDE.md` (sección iOS), antes del primer build de release iOS:
- Añade los paquetes SPM de Firebase y GoogleSignIn (ver `iosApp/SETUP.md`).
- Añade el producto SPM **FirebaseCrashlytics** + el Run Script de subida de dSYM.

> **dSYMs en Crashlytics.** El Run Script en el archive no sube los símbolos de forma fiable
> (por eso 1.10.2 / 10031 quedó con un dSYM faltante). Las lanes `ios beta`/`ios release` ya
> suben los dSYM en **cada** build (`build_ios` → `upload_dsyms_to_crashlytics`, localizando
> `upload-symbols` en el checkout SPM de `firebase-ios-sdk`). Para un build ya publicado que
> reporte "missing dSYM", usa la lane de remediación (descarga los dSYM de App Store Connect y
> los sube a Crashlytics):
> ```bash
> ASC_KEY_ID=<key-id> ASC_ISSUER_ID=<issuer-id> ASC_KEY_PATH=</ruta/AuthKey_XXX.p8> \
>   bundle exec fastlane ios refresh_dsyms version:1.10.2 build_number:10031
> ```
> (sin argumentos usa 1.10.2/10031 por defecto; `latest:true` toma el último build procesado).

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
| `GOOGLE_SERVICE_INFO_PLIST` | ambos | `base64 -i iosApp/iosApp/GoogleService-Info.plist` |
| `ASC_KEY_ID` / `ASC_ISSUER_ID` | ambos | de la API key |
| `ASC_KEY_P8` | ambos | `base64 -i AuthKey_XXXX.p8` |
| `MATCH_GIT_URL` | ambos | URL del repo privado de match |
| `MATCH_PASSWORD` | ambos | passphrase elegida en `match init` |
| `MATCH_GIT_BASIC_AUTHORIZATION` | ambos | `printf 'usuario:PAT' \| base64` (acceso de lectura al repo de match) |

> En macOS `base64 -i fichero` no añade saltos de línea. En Linux usa `base64 -w0 fichero`.

#### Restricción de la Maps API key (acción obligatoria)

La feed inyecta una clave de Static Maps vía la propiedad Gradle `googleMapsApiKey`
(→ `BuildConfig.MAPS_API_KEY`, ver `androidApp/build.gradle.kts` y
`feature/feed/.../MapsApiKey.kt`). Esa clave **se embebe en el APK/AAB**, así que un
extractor puede leerla — la única defensa real es restringirla en Google Cloud Console.
Antes de publicar:

1. **Google Cloud Console → APIs & Services → Credentials →** la Maps API key.
2. **Application restrictions → Android apps:** añade el package name `es.schsebastian.foodrats`
   con la **huella SHA-1** del certificado de firma. Usa el SHA-1 de la *app key* de Play
   App Signing (Play Console → Setup → App integrity) **y** el de la upload key, para que
   tanto los builds firmados por Google como los locales/CI funcionen.
3. **API restrictions → Restrict key:** déjala limitada **solo a la Static Maps API**
   (la única que la app llama). Nada más.

> No es un secreto que se pueda esconder; el control de daños es la restricción
> package + SHA-1 + API. Una clave sin restringir filtrada deja la cuota (y la factura)
> abierta a cualquiera.

#### `GOOGLE_SERVER_CLIENT_ID` — es un id OAuth público, no un secreto

El web client id de OAuth (`GOOGLE_SERVER_CLIENT_ID` / propiedad `googleServerClientId`,
→ `BuildConfig.GOOGLE_SERVER_CLIENT_ID`) es **público por diseño**: se envía al
dispositivo y aparece en cada petición de Sign-In. No hace falta rotarlo ni ocultarlo;
está en la tabla de secrets solo por comodidad de inyección. **No lo "arregles"** tratándolo
como credencial sensible — el secreto correspondiente es el *client secret*, que esta app
no usa (flujo Sign-In nativo).

### 5. Protección de ramas y tags

- **Branch protection en `main`:** requerir PR, ≥1 review, check `CI` en verde, rama actualizada; prohibir push directo.
- **Tag protection rule** para `v*`: solo maintainers crean tags de release.

### 6. Firma de URLs de imágenes — `mintPlateUrls` (#15)

Las fotos de plato y los avatares ya **no** se sirven con URLs de token público
(`getDownloadUrl()`): las reglas de Storage las deniegan (`read: if false`). El cliente
guarda la *ruta* del objeto y la resuelve a una URL firmada V4 de corta duración (15 min)
con la callable `mintPlateUrls` (región `europe-west3`), que verifica pertenencia a la crew
antes de firmar.

- **IAM (obligatorio):** la firma V4 con `getSignedUrl()` necesita el permiso
  `iam.serviceAccounts.signBlob`. Concede a la service account de la función el rol
  **Service Account Token Creator** (`roles/iam.serviceAccountTokenCreator`) sobre sí misma.
  Sin esto `getSignedUrl` lanza en runtime y **no carga ninguna imagen**.
  ```bash
  gcloud iam service-accounts add-iam-policy-binding \
    foodrats-de4ec@appspot.gserviceaccount.com \
    --member="serviceAccount:foodrats-de4ec@appspot.gserviceaccount.com" \
    --role="roles/iam.serviceAccountTokenCreator" --project foodrats-de4ec
  ```
- **iOS:** añade el producto **FirebaseFunctions** al target en Xcode (SPM), igual que
  FirebaseAuth/Firestore/Storage — el binding KMP de GitLive lo necesita en link/runtime.
- **Deploy:** la callable va con el resto de funciones (`pnpm --dir functions run deploy`);
  las reglas, con `pnpm dlx firebase-tools deploy --only storage --project foodrats-de4ec`.

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

> **Bloqueo previo en Play (cuentas personales nuevas):** hasta que Google
> conceda el *production access*, la API rechaza CUALQUIER release (incluso
> `draft`) en los tracks `production` y `beta` (open testing) con
> `Precondition check failed` — `internal` y `alpha` sí funcionan (verificado
> por sondas API el 2026-07-20). Requisito: closed test con ≥12 testers
> opted-in durante 14 días seguidos → botón **Apply for production access** en
> el Dashboard de Play Console (revisión de hasta ~7 días). Cuando lo
> concedan, relanza solo el job Android fallido:
> `gh run rerun <run-id> --failed`.

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
