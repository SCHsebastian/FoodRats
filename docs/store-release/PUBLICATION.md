# Publicación en stores — guía consolidada

Todo lo referente a publicar FoodRats en Google Play y App Store. Pasos manuales de consola,
privacidad y submission que NO se pueden hacer desde código. Origen: `docs/session/human.md` §D.

Estado de los gates de backend (bloques A–C) está en `docs/session/human.md`. Aquí va solo lo de
**publicación / store / privacidad**.

---

## Estado de la parte codeable (ya hecha, verificada)

| Item | Archivo | Verificación |
|---|---|---|
| Manifest collection-off (Android) | `androidApp/src/main/AndroidManifest.xml` — 5 `meta-data` (`firebase_analytics_collection_enabled=false` + 4 Consent-Mode defaults `false`) | `:androidApp:processDebugMainManifest` EXIT=0 |
| Plist collection-off (iOS) | `iosApp/iosApp/Info.plist` — `FIREBASE_ANALYTICS_COLLECTION_ENABLED=false`, `FirebaseAutomaticScreenReportingEnabled=false` | `plutil -lint` OK |
| Privacy manifest (iOS) | `iosApp/iosApp/PrivacyInfo.xcprivacy` (nuevo) — `NSPrivacyTracking=false`, 7 tipos de datos, 3 Required-Reason APIs | `plutil -lint` OK |

Seguro porque `applyConsent(true)` reactiva la colección del SDK al dar consentimiento
(`FirebaseAnalyticsTracker` / `IosAnalyticsTracker`). Por defecto: colección apagada.

---

## D1 — Google Play Data Safety form (MANUAL, consola)

### Cómo llegar
1. [Google Play Console](https://play.google.com/console) → app **FoodRats**.
2. **Policy and programs** → **App content**.
3. **Data safety** → **Start / Manage**.

### Sección 1 — Data collection & security
- ¿Recolecta o comparte datos? → **Sí**.
- ¿Cifrados en tránsito? → **Sí** (Firebase HTTPS/TLS).
- ¿El usuario puede pedir borrado? → **Sí**, borrado **in-app** + **URL pública de borrado**.

### Sección 2 — Data types a declarar

| Categoría Play | Tipo | Collected | Shared | Purpose | Opcional |
|---|---|---|---|---|---|
| Personal info | Name | Sí | No | App functionality | No (Google Sign-In) |
| Personal info | Email address | Sí | No | App functionality | No |
| Personal info | User IDs | Sí | No | App functionality | No |
| Location | Approximate location | Sí | No | App functionality | **Sí** (tag opcional) |
| Photos and videos | Photos | Sí | No | App functionality | No |
| Messages | Other in-app messages | Sí | No | App functionality | Sí (comentarios) |
| App activity | App interactions | Sí | No | **Analytics** | Sí (consent-gated) |
| App info & performance | Crash logs | Sí | No | Analytics / App functionality | No |
| App info & performance | Diagnostics | Sí | No | Analytics | No |
| Device or other IDs | Device or other IDs | Sí | No | App functionality | No (token FCM) |

**Reglas de consistencia:**
- **Shared = No** en todos (Firebase es procesador, no "sharing" según Google).
- App interactions / Diagnostics: marcar que el usuario puede **opt-out** → **consent-gated**
  (por defecto apagado, coherente con `collection_enabled=false` del manifest).
- Debe cuadrar EXACTO con `iosApp/iosApp/PrivacyInfo.xcprivacy` (Name, Email, UserID, Photos,
  Coarse Location, Product Interaction, Crash Data) + Device IDs (FCM) + Messages (comentarios).

### Pre-requisitos
- App creada en Play Console.
- Borrado de cuenta necesita **URL pública** además del flujo in-app (landing en `foodrats.app`).
- Se guarda como borrador; se publica con el primer release.

---

## D — Resto de items manuales de submission

### iOS — Xcode (D2)
- [ ] Añadir `iosApp/iosApp/StoryShareBridge.swift` al target `iosApp` (Compile Sources).
- [ ] Añadir `iosApp/iosApp/PrivacyInfo.xcprivacy` al target `iosApp` (**Copy Bundle Resources**).
      Sin esto Apple rechaza el build en submission.
- [ ] Confirmar que `LSApplicationQueriesSchemes` (`instagram-stories`, `instagram`) sobrevive al build.

### Deep links / hosting (D3) — ✅ HOSTEADO en el dominio Firebase

Los archivos de asociación YA están servidos y verificados en el dominio del hosting actual
(`foodrats-de4ec.web.app`). Rutas cubiertas: `/meal`, `/crew`, `/invite`.

- `website/.well-known/apple-app-site-association` → https://foodrats-de4ec.web.app/.well-known/apple-app-site-association
  (HTTP 200, `Content-Type: application/json` ✅) — completo, no necesita más datos.
- `website/.well-known/assetlinks.json` → https://foodrats-de4ec.web.app/.well-known/assetlinks.json
  (HTTP 200 ✅) — **placeholders de SHA-256 pendientes** (ver abajo).
- `firebase.json` hosting: `appAssociation: AUTO` + headers `Content-Type application/json` para ambos.
- iOS `iosApp/iosApp/iosApp.entitlements`: añadido `applinks:foodrats-de4ec.web.app` (junto a `foodrats.app`).
- Android `AndroidManifest.xml`: intent-filter `autoVerify` ahora incluye host `foodrats-de4ec.web.app`.

**Pendiente para que Android App Links VERIFIQUE (manual):**
- [ ] Rellenar los 2 SHA-256 en `website/.well-known/assetlinks.json`
      (`REPLACE_WITH_PLAY_APP_SIGNING_SHA256`, `REPLACE_WITH_UPLOAD_KEY_SHA256`) — Play Console →
      App integrity → App signing. Luego redeploy: `firebase deploy --only hosting`.
      (iOS AASA no necesita fingerprints; ya funciona.)
- [ ] Xcode: target `iosApp` → Signing & Capabilities → **Associated Domains** (registra el
      entitlement en el App ID). Build con `-allowProvisioningUpdates`.

**Si luego usas el dominio propio `foodrats.app`** (lo que asume el código, `DeepLinks.WEB_HOST`):
- [ ] Mapear `foodrats.app` como dominio custom en Firebase Hosting (servirá los mismos `.well-known`).
- [ ] La página OG/unfurl para `/invite/{code}` (rich preview en chats) sigue pendiente — server-only.

- Contrato de referencia: `deeplinks/` y `deeplinks/README.md`. Test local:
  `xcrun simctl openurl booted "foodrats://app/crew/c-1"` /
  `adb shell am start -a android.intent.action.VIEW -d "https://foodrats-de4ec.web.app/invite/AB2K9P" es.schsebastian.foodrats`

### 16-KB (D4)
- [ ] Solo confirmar en consola que el AAB subido pasa el pre-launch 16-KB check
      (ya fixed en código vía MediaPipe 0.10.35).

### URL pública de borrado de cuenta — ✅ PUBLICADA
- Página: `website/account-deletion/index.html` (Firebase Hosting, `cleanUrls`).
- **URL viva:** https://foodrats-de4ec.web.app/account-deletion (HTTP 200 verificado).
- Usar esa URL en el Data Safety de Play y en App Store Connect.
- Deploy: `pnpm dlx firebase-tools deploy --only hosting --project foodrats-de4ec`.
- Para usar `foodrats.app/account-deletion`: mapear dominio custom en Firebase Hosting.

---

## App Store Connect — privacidad (paralelo de D1 para iOS)
- Las "App Privacy" labels en App Store Connect deben reflejar los mismos tipos del
  `PrivacyInfo.xcprivacy`. Mismo mapeo que la tabla de Play arriba.
- `NSPrivacyTracking=false` → en ASC, "Data Not Used to Track You".

---

## Referencias
- `docs/session/human.md` — lista maestra de gates manuales (A–F).
- `docs/cicd-runbook.md` — runbook operativo de release.
- `docs/specs/2026-05-20-cicd-store-release-pipeline-design.md` — diseño del pipeline.
- `docs/analytics/TRACKING_PLAN.md` — plan de tracking (qué eventos, sin PII).
