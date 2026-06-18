# TestFlight — beta para amigos (pruebas EXTERNAS)

Guía para sacar una build de prueba a tus amigos por TestFlight, antes de lanzar
al público. Camino elegido: **pruebas externas** (los amigos prueban por email,
sin acceso a tu cuenta).

## Estado actual (ya hecho en App Store Connect)

- App creada: **FoodRats** — App ID `6781682875`, bundle `es.schsebastian.foodrats`,
  iOS, idioma principal Español (España), SKU `foodrats`.
- TestFlight → **Información para las pruebas** rellenada: descripción beta +
  correo de comentarios `hello@chsumiapps.com`.
- (Existe un grupo interno vacío "Amigos" de un intento previo; puedes ignorarlo
  o borrarlo — no se usa en el camino externo.)

## Bloqueo actual: hace falta una build primero

App Store Connect **no muestra la sección de Pruebas externas hasta que subes y
procesa una primera build**. Por eso ahora mismo no se puede crear el grupo
externo ni añadir a los testers. Secuencia correcta: **subir build → aparece
Pruebas externas → crear grupo + añadir emails → enviar a Beta App Review**.

## Amigos a añadir (en cuanto haya build)

Estos 5 emails se añaden como testers externos al grupo:

- cakorocamora@gmail.com
- Social@rolyelx.com
- juandi_bv10@outlook.com
- Fernandezmorenonicolas@gmail.com
- antonioperezin00@gmail.com

---

## Paso 0 — Prerrequisitos en tu mano (portal Apple / CI)

No se pueden hacer desde aquí (requieren el Developer portal, tus llaves de firma
y el hosting). El primero **bloquea la firma de distribución**, así que hazlo
antes de subir la build.

### 0a — Entitlements del App ID + regenerar firma (bloquea Paso 1)

La app ya trae activos *Associated Domains* y *Push*; si el App ID no los declara,
la firma de distribución falla.

1. ✅ **HECHO (2026-06-18):** en
   [developer.apple.com](https://developer.apple.com/account/resources/identifiers/list)
   → Identifiers → `es.schsebastian.foodrats` se activaron **Associated Domains**,
   **Push Notifications** y **Sign In with Apple** (como *primary App ID*) y se
   guardó (Apple avisó de que invalida los perfiles existentes → por eso el paso 2).
2. **PENDIENTE (tu Mac):** regenera los perfiles con los entitlements ya activos.
   Usa la lane `rotate_signing` (NO el `match --force` a pelo: la lane autentica con
   la **ASC API key** → sin prompt de Apple ID / 2FA, y fuerza `readonly: false` para
   poder reescribir el perfil; el Matchfile es `readonly(true)` por defecto):
   ```bash
   bundle install   # una vez; instala fastlane en vendor/bundle (sin sudo)
   ASC_KEY_ID=<key-id> ASC_ISSUER_ID=<issuer-id> ASC_KEY_PATH=</ruta/AuthKey_XXX.p8> \
   MATCH_GIT_URL=<repo-privado-de-certs> MATCH_PASSWORD=<passphrase-del-repo> \
     bundle exec fastlane ios rotate_signing
   ```
   La lane corre `app_store_connect_api_key` + `match(type: appstore, readonly: false,
   force: true)`. Las lanes `ios beta`/`release` luego consumen el perfil en readonly.

   **Alternativa sin fastlane:** si firmas a mano en Xcode con **firma automática**,
   basta con que Xcode regenere el perfil al hacer *Archive* (con Sign in with Apple ya
   incluido) — no necesitas `match` para una subida manual puntual.

### 0b — SHA-256 reales en assetlinks.json + redeploy — APLAZADO (Android no listo)

> **No hace falta para la beta iOS.** Es solo para los App Links de Android antes
> del lanzamiento público en Google Play, que aún no está listo. Aplazado.

⚠️ **Aviso (visto 2026-06-18):** la **cuenta de desarrollador de Google Play está
cerrada** (por inactividad, cerrada el 8 jun 2026). No hay app ni Play App Signing,
así que los dos SHA-256 reales **no existen todavía** y no se pueden poner. Para
publicar en Android habrá que **crear una cuenta de Google Play nueva** (25 USD) y
enrolar Play App Signing; entonces se sacan los SHA-256 y se rellena este fichero.
Nada de esto bloquea la beta de iOS para tus amigos.

---

## Paso 1 — Subir una build (en tu Mac)

El sandbox de Cowork es Linux: no puede compilar la app KMP/iOS. La build sale de
tu Mac.

### Opción A — Xcode (lo más simple para una subida manual)

1. Abre `iosApp/iosApp.xcworkspace` (el workspace, no el `.xcodeproj`).
2. Esquema **iosApp**, destino **Any iOS Device (arm64)**.
3. Número de build: `MARKETING_VERSION` = `1.0`, `CURRENT_PROJECT_VERSION` = `1`.
   El **build number debe subir en cada subida** (1, 2, 3…).
4. *Product → Archive* (dispara el Gradle del framework KMP; ten un JDK 21 a mano).
5. Organizer → *Distribute App → App Store Connect → Upload* (firma automática,
   equipo `2AH7L26L78`).

### Opción B — Fastlane (si ya configuraste `match` + ASC API key)

```bash
# raíz del repo, en tu Mac, con los env de match y ASC
bundle exec fastlane ios beta
```

La lane `ios beta` hace `match` (firma), `gym` (build) y `upload_to_testflight`.
Setup de `match` en `docs/cicd-runbook.md` (Fase 0).

## Paso 2 — Cumplimiento de exportación

Tras procesar, TestFlight pide *"Cumplimiento de exportación"*. FoodRats solo usa
cifrado estándar (HTTPS/Firebase) → **exenta**. Para no volver a verlo, añade a
`iosApp/iosApp/Info.plist`: `ITSAppUsesNonExemptEncryption` = `NO`.

## Paso 3 — Crear grupo externo y añadir a los amigos

Cuando la build esté procesada, en TestFlight aparece **Pruebas externas**:

1. **+** junto a *Pruebas externas* → nombre del grupo (p. ej. "Amigos").
2. **Añadir testers** → pega los 5 emails de arriba.
3. Asigna la build al grupo.

(Avísame cuando la build esté arriba y te hago este paso desde aquí.)

## Paso 4 — Beta App Review (solo externas, una vez)

La primera build externa pasa una revisión de Apple (~24-48h). FoodRats obliga a
iniciar sesión, así que el revisor necesita poder entrar.

**Actualización (2026-06-18): Sign in with Apple YA funciona en iOS.** El botón
"Continuar con Apple" ahora inicia sesión de verdad (flujo nativo
`ASAuthorizationController` → Firebase `OAuthProvider("apple.com")`; build de iOS
verificada). Esto es lo que Apple prefiere (Guideline 4.8) y deja al revisor usar
**su propio Apple ID** — sin cuenta demo, sin desactivar 2FA, sin notas especiales.

> **Requisitos para que el botón Apple autentique de verdad** (el código está hecho;
> esto es manual y hay que hacerlo ANTES de enviar a revisión):
> 1. Firebase console → Authentication → Sign-in method → **habilitar Apple**.
> 2. Apple Developer → App ID `es.schsebastian.foodrats` → activar la capacidad
>    **Sign in with Apple**; regenera el perfil de distribución (firma automática de
>    Xcode al archivar, o `match appstore --force`). El entitlement
>    `com.apple.developer.applesignin` ya está en `iosApp.entitlements`.

Con eso, en *App Store Connect → TestFlight → Información para las pruebas →
Información para el equipo de revisión* basta con marcar **"Es necesario iniciar
sesión"** y poner en **Notas**:

  ```text
  ES — Pulsa "Continuar con Apple" e inicia sesión con tu propio Apple ID.
  Tras entrar verás el feed de tu crew.

  EN — Tap "Continue with Apple" and sign in with your own Apple ID.
  After signing in you'll see your crew's feed.
  ```

### Fallback — cuenta Google dedicada (si no llegas a habilitar Apple a tiempo)

Si por lo que sea el proveedor Apple no está habilitado cuando envías a revisión, el
camino sin tocar la app sigue valiendo (Google sí está en producción): crea una cuenta
Google de prueba (`foodrats.review@gmail.com`), **desactiva la 2FA** (o usa una
*contraseña de aplicación*) — si no, Google bloquea el login desde el dispositivo del
revisor — entra una vez para crear una crew con una comida publicada, y en la info de
revisión marca "Es necesario iniciar sesión", pon usuario+contraseña y como nota:
"Pulsa 'Continuar con Google' con las credenciales de arriba (2FA desactivada)".

Sin un camino de login funcional, Apple rechaza la build externa por no poder pasar del login.

---

## Checklist

- [x] **(Paso 0a-1)** Activar Associated Domains + Push en el App ID ✅ hecho 2026-06-18
- [ ] **(Paso 0a-2)** Activar **Sign in with Apple** en el App ID + regenerar firma:
      `bundle exec fastlane ios rotate_signing` (no-interactivo vía ASC API key) — o,
      si subes a mano, Xcode auto al *Archive* con `-allowProvisioningUpdates`
- [ ] **Firebase console** → Authentication → Sign-in method → **habilitar Apple**
      (sin esto el botón Apple falla en runtime)
- [ ] Subir build (Xcode Organizer o `fastlane ios beta`)
- [ ] Esperar a que procese en TestFlight
- [ ] Cumplimiento de exportación (o `ITSAppUsesNonExemptEncryption=NO`)
- [ ] Crear grupo externo y añadir los 5 emails
- [ ] Login del revisor: Sign in with Apple (su Apple ID) — o cuenta Google demo de fallback
- [ ] Enviar a Beta App Review y esperar aprobación
- [ ] Confirmar que a los amigos les llega la invitación de TestFlight
