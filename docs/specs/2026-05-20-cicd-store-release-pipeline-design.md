# CI/CD — Release pipeline a Play Store y App Store (diseño)

**Fecha:** 2026-05-20
**Estado:** Propuesta (plan, sin implementar). Aprobar antes de tocar código.
**Ámbito:** GitHub Actions + Fastlane para `:androidApp` (Play) e `iosApp` (App Store).

---

## 0. TL;DR

Modelo de entrega elegido (decisiones ya tomadas contigo):

- **Continuous delivery a beta, no a producción.** Cada merge a `main` construye, firma y sube **automáticamente** a Play **Internal testing** y a **TestFlight**. Nadie publica a producción por accidente.
- **Producción es un acto deliberado y gobernado.** Se dispara empujando un **tag SemVer** `vX.Y.Z`. El workflow de producción queda detrás de un **GitHub Environment protegido** (`production`) que exige aprobación humana antes del paso de subida a la store. Así "tag = versión" y "prod = manual" conviven.
- **Versionado determinista.** `versionName` sale del tag; el `versionCode` (Android) y `CFBundleVersion` (iOS) se derivan de un contador monótono (`github.run_number` + offset). Cero edición manual de versiones en Gradle/Xcode.
- **Toda la firma vive en secrets**, nunca en el repo. Android con upload-key + Play App Signing; iOS con `match` + App Store Connect API key.
- **Coste cero en herramientas y CI.** Android compila en runners Linux gratuitos de GitHub. iOS se construye y firma **en el Mac del autor** (self-hosted runner o lane local), no en runners macOS de pago. Toda la cadena (Fastlane, `match`, R8) es open source. Los únicos pagos son las cuotas de las propias tiendas (ver §0.1).
- **R8/minify activado desde el primer release** (decisión confirmada): el primer AAB de producción sale ofuscado y con shrink de recursos, con sus reglas `-keep` y un smoke test del artefacto firmado.

Lo que hoy falta y este plan añade: build de release firmado, `signingConfig` en Gradle, R8/minify con reglas, Fastlane (`supply`/`pilot`/`deliver`), automatización de versión, tres workflows (`ci`, `release-beta`, `release-production`), gobernanza de ramas/entornos y el inventario de secrets.

## 0.1. Costes (honestidad de presupuesto)

Separar dos cosas que la gente confunde:

**Inevitable — cuota de las tiendas (no es "pagar por pasos extra", es el precio de publicar):**

| Concepto | Coste | Notas |
|---|---|---|
| Apple Developer Program | **99 USD/año** | Obligatorio para App Store *y* TestFlight. No existe alternativa gratuita ni local. |
| Google Play Developer | **25 USD una vez** | Pago único de por vida. |

**Evitable — y aquí lo llevamos a 0:**

| Concepto | Coste si se hace mal | Cómo lo evitamos |
|---|---|---|
| Runners macOS de GitHub (iOS) | x10 de minutos; en repo privado quema el presupuesto en pocos builds | Construir iOS en **tu Mac** (self-hosted runner o `fastlane ios beta` local) → 0 € |
| CI de terceros (Codemagic/Bitrise) | suscripción mensual | No se usan; todo en GitHub Actions + tu Mac |
| Fastlane, `match`, R8, keystores, API keys | 0 € | Open source / gratis |
| Runners Linux de GitHub (Android) | 2.000 min/mes gratis en privado, ilimitado en público | Android cabe de sobra en el tier gratis |

> Conclusión: el pipeline completo cuesta **99 USD/año + 25 USD una vez**, que es el suelo irreducible de tener una app en ambas tiendas. Ni un euro más en infraestructura.

---

## 1. Estado actual (línea base)

| Área | Hoy | Implicación |
|---|---|---|
| CI | `.github/workflows/ci.yml`: host tests en PR/push a `main`/`develop`. Reconstruye `google-services.json` desde `GOOGLE_SERVICES_JSON` y `googleServerClientId` desde secret. | Buena base de validación. No construye ni firma release, no despliega. |
| Versionado Android | `versionCode = 1`, `versionName = "1.0"` hardcodeados en `androidApp/build.gradle.kts`. | Imposible de automatizar tal cual; hay que parametrizar. |
| Firma Android | `buildTypes.release { isMinifyEnabled = false }`, **sin `signingConfig`**. | Un AAB de release hoy no se puede firmar para subir a Play. |
| Versionado iOS | `MARKETING_VERSION = 1.0`, `CURRENT_PROJECT_VERSION = 1` en `Config.xcconfig`. | Igual: parametrizar para CI. |
| Firma iOS | `CODE_SIGN_STYLE = Automatic`, `DEVELOPMENT_TEAM = 2AH7L26L78` en `project.pbxproj`. | Automatic no es reproducible en CI; pasamos a `match` (manual provisioning). |
| Firebase iOS | Resuelto por **SPM dentro de Xcode**, no por Gradle. Crashlytics requiere paso manual de dSYM. `CoreLocation.framework` a enlazar a mano. | El runner macOS resuelve SPM con `xcodebuild`, pero los pasos manuales del `.xcodeproj` deben estar commiteados antes del primer release iOS. |
| Config sensible | `google-services.json`, `GoogleService-Info.plist`, `local.properties` gitignored. | Patrón correcto; lo extendemos a keystore, API keys y service account. |
| Repo | `github.com/SCHsebastian/FoodRats`, rama por defecto `main`. | GitHub Actions es la opción natural. |

---

## 2. Modelo de ramas y release

```
 PR  ──────────────▶  ci.yml            (tests + smoke build, sin firmar, sin desplegar)
  │  (review + green)
  ▼
 main  ────────────▶  release-beta.yml  (auto)  → Play Internal + TestFlight
  │
  │  el maintainer decide cortar versión:
  │      git tag v1.4.0 && git push origin v1.4.0
  ▼
 tag vX.Y.Z ───────▶  release-production.yml     → [aprobación manual] → Play Production (rollout escalonado) + App Store (phased release)
```

Reglas:

- **`main` siempre desplegable.** Todo lo que entra en `main` ya pasó CI y un review.
- **Beta automática.** El merge a `main` no necesita intervención: el equipo de pruebas recibe la build en minutos.
- **Producción gobernada.** El tag construye desde ese commit exacto; el job de subida vive en el Environment `production` con *required reviewers*, así que se queda "en espera de aprobación" hasta que alguien con permiso le da al botón. Ese es el "manual" de "prod manual".
- **Tag = fuente de verdad de la versión.** El nombre de versión publicado en las stores es literalmente el tag (`v1.4.0` → `1.4.0`).

> Alternativa considerada y descartada: publicar a prod en cada merge ("directo a producción"). Se descartó porque ambas stores tienen revisión y un rollback en producción es caro; la promoción manual es la práctica estándar en empresas con apps en review.

---

## 3. Versionado

Dos números distintos por plataforma; no confundirlos:

- **Version name / marketing version** — legible, lo ve el usuario. Sale del tag SemVer.
- **Version code (Android) / build number `CFBundleVersion` (iOS)** — entero monótono, interno. Las stores **rechazan** una subida cuyo build number no sea estrictamente mayor que el anterior.

### Fórmula

| Campo | Beta (push a `main`) | Producción (tag `vX.Y.Z`) |
|---|---|---|
| `versionName` (Android) / `MARKETING_VERSION` (iOS) | `<último-tag>` sin la `v`, ej. `1.3.0` (la beta apunta a la versión en cocción) | `X.Y.Z` extraído del tag |
| `versionCode` / `CFBundleVersion` | `BASE + github.run_number` | `BASE + github.run_number` |

- `BASE` es un offset constante (p. ej. `10000`) para garantizar que el contador queda **por encima** de cualquier `versionCode` que ya exista en las stores hoy (hoy es `1`). Se fija una vez y no se vuelve a tocar.
- `github.run_number` es global y creciente por workflow. Para garantizar monotonía **entre beta y prod** se usa el `run_number` del repo (compartido), no uno por-workflow. Detalle de implementación: usar `github.run_number` del evento; si beta y prod son workflows distintos comparten el contador de runs del repositorio sólo si se calcula con la API. **Decisión:** usar `github.run_number` y, como es estrictamente creciente dentro de cada workflow pero no necesariamente entre dos workflows distintos, separar los espacios con offsets distintos (`BASE_BETA = 10000`, `BASE_PROD = 500000`) para que un build de prod siempre supere a cualquier beta. Así nunca hay colisión de build numbers en la store.
- Inyección en build (sin editar ficheros versionados):
  - Android: `./gradlew :androidApp:bundleRelease -PversionName=$VN -PversionCode=$VC` y el `build.gradle.kts` lee esas properties con fallback a los valores actuales para builds locales.
  - iOS: `agvtool` o sobreescritura del `Config.xcconfig` en el runner con `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` antes de `gym` (o pasar `MARKETING_VERSION=$VN CURRENT_PROJECT_VERSION=$VC` como overrides de `xcodebuild`).

> Si más adelante quieres betas con sufijo de pre-release legible (`1.3.0-beta.42`), Android lo admite en `versionName` directamente; iOS no lo admite en `MARKETING_VERSION` para App Store, pero TestFlight distingue builds por `CFBundleVersion`, así que el `run_number` ya cumple esa función.

---

## 4. Cambios en el código del proyecto

### 4.1 Gradle — `androidApp/build.gradle.kts`

1. **Versión parametrizable**:
   ```kotlin
   val ciVersionName = (project.findProperty("versionName") as String?) ?: "1.0"
   val ciVersionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
   // ...
   defaultConfig {
       versionName = ciVersionName
       versionCode = ciVersionCode
   }
   ```
2. **`signingConfig` de release leyendo de entorno** (el keystore se materializa desde un secret en el runner):
   ```kotlin
   signingConfigs {
       create("release") {
           val ksPath = System.getenv("ANDROID_KEYSTORE_PATH")
           if (ksPath != null) {
               storeFile = file(ksPath)
               storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
               keyAlias = System.getenv("ANDROID_KEY_ALIAS")
               keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
           }
       }
   }
   buildTypes {
       getByName("release") {
           signingConfig = signingConfigs.getByName("release")
           // ver decisión sobre minify abajo
       }
   }
   ```
   En local (sin las env vars) `signingConfig` queda sin `storeFile` y el build de release simplemente no firma — el flujo de debug no se ve afectado.
3. **R8 / minify — ACTIVADO desde el primer release (decisión confirmada).**
   ```kotlin
   getByName("release") {
       isMinifyEnabled = true
       isShrinkResources = true
       proguardFiles(
           getDefaultProguardFile("proguard-android-optimize.txt"),
           "proguard-rules.pro",
       )
       signingConfig = signingConfigs.getByName("release")
   }
   ```
   Riesgo real en este proyecto y mitigación obligatoria (forma parte de la fase 1, no se pospone): hay que escribir `androidApp/proguard-rules.pro` con reglas `-keep` para los puntos que usan reflexión o nombres en runtime:
   - **Koin**: normalmente robusto, pero mantener clases de módulos/inyección por constructor si R8 elimina metadatos.
   - **kotlinx-serialization**: `-keepclasseswithmembers @kotlinx.serialization.Serializable` y los `$$serializer` generados (DTOs de Firebase, rutas de navegación `@Serializable`).
   - **GitLive Firebase / Firebase SDK**: reglas consumidas vía AAR, pero verificar modelos/DTO de Firestore que se (de)serializan por nombre de campo.
   - **Compose**: el plugin de Compose ya aporta sus reglas; no suele hacer falta nada extra.
   **Verificación que cierra la fase 1:** construir el AAB release firmado, instalarlo en un dispositivo, y pasar un smoke test del flujo crítico (sign-in → crear/unirse a crew → publicar meal → feed → stats → notificación). Si algo peta en runtime, casi siempre es un `-keep` que falta. Sólo se da por buena la fase 1 cuando ese smoke test pasa con el artefacto **minificado**.

### 4.2 iOS — proyecto Xcode

- **Pasos manuales ya pendientes en `CLAUDE.md` que son prerequisito del primer release iOS** (deben quedar commiteados en `project.pbxproj` antes de que CI pueda construir):
  - Enlazar `CoreLocation.framework` (ImagePickerKMP 1.0.41).
  - Producto SPM **FirebaseCrashlytics** + Run Script de subida de dSYM.
  - Paquetes SPM de Firebase y GoogleSignIn (ya documentados en `iosApp/SETUP.md`).
- **Firma reproducible con `match`:** pasar `CODE_SIGN_STYLE` a `Manual` para el build de release de CI (vía un `.xcconfig` específico de CI o sobreescritura en `gym`), usando el certificado *App Store* y el provisioning profile que `match` provisiona. Para builds locales se puede seguir usando Automatic.
- **Scheme `iosApp` compartido** (ya existe en `xcshareddata/xcschemes/`), necesario para que `gym` lo encuentre.

### 4.3 Fastlane (nuevo, en `fastlane/`)

`fastlane/Fastfile` con lanes:

```ruby
platform :android do
  lane :beta do
    # AAB ya construido por Gradle en el step anterior
    upload_to_play_store(
      track: "internal",
      aab: "androidApp/build/outputs/bundle/release/androidApp-release.aab",
      json_key: ENV["PLAY_JSON_KEY_PATH"],
      skip_upload_apk: true
    )
  end
  lane :release do
    upload_to_play_store(
      track: "production",
      aab: "...release.aab",
      json_key: ENV["PLAY_JSON_KEY_PATH"],
      rollout: "0.2"   # rollout escalonado 20%
    )
  end
end

platform :ios do
  lane :beta do
    match(type: "appstore", readonly: true)
    build_app(scheme: "iosApp", export_method: "app-store")
    upload_to_testflight(api_key_path: ENV["ASC_KEY_PATH"], skip_waiting_for_build_processing: true)
  end
  lane :release do
    match(type: "appstore", readonly: true)
    build_app(scheme: "iosApp", export_method: "app-store")
    upload_to_app_store(
      api_key_path: ENV["ASC_KEY_PATH"],
      submit_for_review: true,
      phased_release: true,
      precheck_include_in_app_purchases: false,
      force: true
    )
  end
end
```

`Appfile` con `package_name` (`es.schsebastian.foodrats`), `app_identifier`, `team_id`.

---

## 5. Workflows de GitHub Actions

### 5.1 `ci.yml` (mejorar el existente)

- Mantener los host tests actuales.
- Añadir un job **smoke build sin firmar** para detectar roturas antes del merge:
  - Android: `./gradlew :androidApp:assembleDebug` (o `bundleRelease` sin firmar).
  - iOS (en el self-hosted runner Mac, ver §5.4): `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` + `xcodebuild build` del scheme, sólo en PRs que tocan `iosApp/` o `shared/`. Si no quieres exponer tu Mac a PRs de terceros (ver nota de seguridad en §5.4), deja el smoke iOS sólo para `main` y omítelo en CI de PR.
- Sin secrets de firma ni de stores aquí: CI nunca despliega.

### 5.2 `release-beta.yml` (nuevo)

```yaml
on:
  push:
    branches: [main]
permissions:
  contents: read
concurrency:
  group: release-beta
  cancel-in-progress: false   # no cancelar despliegues a medio subir
```

Dos jobs:

- **android** (`ubuntu-latest`, gratis): calcular versión → materializar `google-services.json` y keystore desde secrets → `bundleRelease` firmado y minificado → `fastlane android beta`.
- **ios** (`runs-on: [self-hosted, macos]` → tu Mac, gratis): calcular versión → materializar `GoogleService-Info.plist` y App Store Connect key desde secrets → `fastlane ios beta` (`match` readonly + `gym` + `pilot`).

### 5.4 Ejecución de iOS sin pagar runners macOS

El job de iOS no usa los runners macOS de pago de GitHub. Dos modos, elige uno:

- **A — Self-hosted runner en tu Mac (recomendado, mantiene el "automático").** Registras tu Mac como runner self-hosted del repo (`Settings → Actions → Runners → New self-hosted runner`, instalación guiada de Apple Silicon). Le pones la etiqueta `macos`. El job `ios` declara `runs-on: [self-hosted, macos]`. Resultado: el merge a `main` dispara el build de iOS y se ejecuta en tu Mac cuando esté encendido; si está apagado, el job queda en cola y arranca al encenderlo. Coste 0.
- **B — Lane local manual (lo más simple).** No hay job de iOS en CI. Tras un merge a `main`, en tu Mac corres `bundle exec fastlane ios beta`. Pierdes el disparo automático, pero es la opción de menor fricción de setup.

> **Seguridad del self-hosted runner.** Un runner self-hosted **nunca** debe ejecutar workflows disparados por PRs de forks no confiables: un atacante podría correr código arbitrario en tu Mac. Mitigación: el runner sólo atiende los workflows de **release** (push a `main` y tags), que provienen de código ya revisado y mergeado. El CI de PR (que sí ve código no confiable) se queda en runners Linux gestionados; si quieres smoke de iOS en PRs, restríngelo a PRs del propio repo, no de forks. Como el repo es de un solo autor el riesgo es bajo hoy, pero la regla se deja escrita para cuando entre más gente.

### 5.3 `release-production.yml` (nuevo)

```yaml
on:
  push:
    tags: ['v*.*.*']
permissions:
  contents: read
```

- Job de build (igual que beta pero `export_method`/track de producción y `BASE_PROD`): Android en Linux gratis, iOS en el self-hosted Mac.
- El **job de subida** declara `environment: production`. Ese Environment tiene **required reviewers** configurados en GitHub → el run se pausa en "Waiting" hasta aprobación. Eso materializa "prod manual".
- Subida con **rollout escalonado** (Play `rollout: 0.2`) y **phased release** (App Store), que se promocionan al 100% manualmente o por un workflow de `promote` posterior.
- Crea automáticamente una **GitHub Release** con notas (desde `CHANGELOG.md` o auto-generadas).

---

## 6. Inventario de secrets

Configurar como **GitHub Environments** (no como repo-wide), separando `production` del resto para poder exigir reviewers y restringir qué refs los usan.

### Android
| Secret | Qué es | Cómo se obtiene |
|---|---|---|
| `GOOGLE_SERVICES_JSON` *(ya existe)* | `google-services.json` en base64 | Firebase Console |
| `GOOGLE_SERVER_CLIENT_ID` *(ya existe)* | Web client id de OAuth | Google Cloud Console |
| `ANDROID_KEYSTORE_BASE64` | Upload keystore (.jks) en base64 | `keytool -genkeypair ...` una vez |
| `ANDROID_KEYSTORE_PASSWORD` | Password del store | — |
| `ANDROID_KEY_ALIAS` | Alias de la clave | — |
| `ANDROID_KEY_PASSWORD` | Password de la clave | — |
| `PLAY_SERVICE_ACCOUNT_JSON` | Service account JSON (base64) con permiso de release en Play | Play Console → API access |

### iOS
| Secret | Qué es | Cómo se obtiene |
|---|---|---|
| `GOOGLESERVICE_INFO_PLIST` | `GoogleService-Info.plist` en base64 | Firebase Console |
| `ASC_KEY_ID` | App Store Connect API Key ID | App Store Connect → Users and Access → Integrations |
| `ASC_ISSUER_ID` | Issuer ID de la API | idem |
| `ASC_KEY_P8` | El `.p8` de la API key en base64 | idem (sólo se descarga una vez) |
| `MATCH_GIT_URL` | Repo privado de certificados de `match` | Crear repo privado vacío |
| `MATCH_SSH_KEY` o `MATCH_GIT_TOKEN` | Acceso de lectura al repo de match | Deploy key / PAT |
| `MATCH_PASSWORD` | Passphrase de cifrado de match | Elegida al `match init` |

> **Mejora "empresa de verdad" (opcional, fase 2):** sustituir `PLAY_SERVICE_ACCOUNT_JSON` por **Workload Identity Federation con OIDC** (GitHub → Google Cloud) para eliminar credenciales de larga vida. Documentado como upgrade, no como requisito del MVP del pipeline.

---

## 7. Setup en las stores (lo que sólo puedes hacer tú, una vez)

Estos son los prerequisitos manuales fuera del repo. El pipeline no funciona hasta completarlos:

**Google Play**
1. Crear la app en Play Console con `applicationId es.schsebastian.foodrats`.
2. **Enrolar en Play App Signing** (Google custodia la clave de firma; tú sólo guardas la *upload key*). Estándar de empresa.
3. **Primera subida manual de un AAB**: Play **exige** que el primer artefacto de un track se suba a mano por la consola antes de habilitar la API. Es el gotcha clásico que rompe el primer `supply`.
4. Crear service account (Google Cloud) con rol de release y concederle acceso en Play Console.
5. Rellenar la ficha de la store (descripción, capturas, content rating, data safety) — Play no publica producción sin esto.

**App Store Connect**
1. Crear el App ID/bundle `es.schsebastian.foodrats` en el Apple Developer Portal y el registro de app en App Store Connect.
2. Generar la **App Store Connect API Key** (rol App Manager) y guardar el `.p8`.
3. `fastlane match init` contra un repo privado y `match appstore` para generar el certificado de distribución + provisioning profile.
4. Rellenar metadatos de la ficha (los gestionará `deliver` después, pero el registro debe existir).
5. Completar los pasos manuales de Xcode (CoreLocation, Crashlytics SPM + dSYM) y commitear el `project.pbxproj`.

---

## 8. Gobernanza / branch protection

Para que esto sea "de empresa" y no un cohete sin botón de aborto:

- **Branch protection en `main`:** requerir PR, ≥1 review, status check `ci` en verde, y rama actualizada antes de mergear. Bloquear push directo.
- **Tag protection rule** para `v*` (sólo maintainers pueden crear tags de release).
- **Environment `production`** con *required reviewers* y, si quieres, *wait timer*. Restringir a refs `v*`.
- **Secrets de producción** sólo en el Environment `production`; los de beta en su propio scope.
- **Concurrency** que no cancele despliegues en vuelo (`cancel-in-progress: false` en los workflows de release).
- **Rollout escalonado por defecto** (Play 20%, App Store phased) con promoción manual al 100%.

---

## 9. Riesgos y decisiones abiertas

| Tema | Riesgo / decisión | Recomendación |
|---|---|---|
| Minify/R8 en Android (activado en fase 1) | Puede romper Koin/Firebase/serialization en runtime | `proguard-rules.pro` + smoke test obligatorio del AAB minificado antes de cerrar fase 1 (§4.1) |
| Primer `supply` a Play | Falla si no se subió 1 AAB a mano antes | Documentado en §7.3; hacerlo antes del primer release-beta |
| Monotonía de build numbers entre beta y prod | Colisión rechazada por las stores | Offsets `BASE_BETA`/`BASE_PROD` separados (§3) |
| Coste de runners macOS | iOS necesita un Mac; los runners macOS de GitHub cuestan x10 | iOS se construye en tu Mac (self-hosted runner o lane local), §5.4 → 0 € |
| Seguridad del self-hosted runner | PRs de forks podrían ejecutar código en tu Mac | El runner sólo atiende workflows de release (main/tags), nunca CI de PR no confiable (§5.4) |
| `match` vs cloud signing (API key automatic) | `match` exige repo privado extra (gratis) | Recomendado `match` por reproducibilidad; alternativa documentada |
| SPM de Firebase no visible a Gradle | Builds iOS de test que tocan Firebase fallan en CI | Release iOS usa `xcodebuild`/`gym` (resuelve SPM), no las tasks Gradle de test |
| Crashlytics dSYM | Sin el Run Script, los crashes llegan sin simbolizar | Paso manual de Xcode en §7; commitear pbxproj |
| Secrets de larga vida (Play JSON) | Superficie de ataque | Fase 2: OIDC + Workload Identity Federation |

---

## 10. Plan de implementación por fases

1. **Fase 0 — prerequisitos manuales (tú):** pagar/crear Apple Developer Program (99 USD/año) y cuenta Play (25 USD), crear apps en ambas stores, Play App Signing + primer AAB manual, API keys, `match`, pasos Xcode, registrar tu Mac como self-hosted runner. (§7, §5.4)
2. **Fase 1 — Gradle + Fastlane + R8:** `signingConfig`, versión parametrizable y **`isMinifyEnabled = true` + `proguard-rules.pro`** en `androidApp`; `fastlane/` con `Appfile`/`Fastfile`; verificar `bundleRelease` firmado **y minificado** en local y pasar el smoke test del flujo crítico (§4.1).
3. **Fase 2 — beta:** `release-beta.yml`, cargar secrets de beta, primer despliegue a Play Internal + TestFlight desde un merge a `main` (iOS en tu Mac).
4. **Fase 3 — producción:** `release-production.yml`, Environment `production` con reviewers, tag protection, primer release tag `vX.Y.Z` con rollout escalonado.
5. **Fase 4 — hardening:** OIDC para Play (eliminar el JSON de larga vida), notas de release automáticas, promoción al 100% por workflow, badges.

---

## 11. Qué tendría que aprobar/decidir antes de implementar

Decisiones ya confirmadas: **R8/minify en el primer release** (fase 1); **coste cero en infra** → iOS en tu Mac (self-hosted runner o lane local).

Pendientes de confirmar:

1. ¿iOS por **self-hosted runner** (mantiene el automático al mergear) o **lane local manual** (`fastlane ios beta` a mano)? (recomiendo self-hosted runner)
2. ¿Track de beta en Play: **Internal testing** (instantáneo, hasta 100 testers) o **Closed testing**? (recomiendo Internal)
3. ¿Quién(es) van como *required reviewers* del Environment `production`? (probablemente solo tú)
4. ¿Confirmas `match` (repo privado de certs, gratis) para firma iOS? (recomendado)
5. Offset base de `versionCode` (propongo `BASE_BETA=10000`, `BASE_PROD=500000`).

Con esas respuestas puedo escribir todos los ficheros (workflows, Fastfile, cambios de Gradle, `proguard-rules.pro`, xcconfig de CI) en una sola tanda.
