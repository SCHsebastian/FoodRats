# Store listing copy — FoodRats (ready to paste)

App name: **FoodRats** · Package / Bundle id: `es.schsebastian.foodrats` · Languages: English (en-US) + Spanish (es-ES).
Category suggestion: **Social** (alt: Lifestyle / Food & Drink). Content rating target: **Everyone / 4+** (no objectionable content; user-generated photos within closed groups → see UGC note below).

> First submission is manual via the consoles (Play Console + App Store Connect). This file is the
> source text; paste per-locale. Char limits noted. Data-safety / privacy answers live in
> `docs/store-release/PUBLICATION.md`.

---

## Google Play

### en-US
**Title** (≤30): `FoodRats — meals with your crew`
**Short description** (≤80):
`Share one daily meal photo with your small crew. Scores, streaks, no calorie counting.`
**Full description** (≤4000):
```
FoodRats is a closed-group meal-sharing app for crews of 3–15 — friends, family, or coworkers.
Post one meal a day: a photo, a 1–10 score, the dish name, and an optional note. That's it.

No followers. No public feed. No calorie tracking. Just your people and what you ate today.

WHAT YOU GET
• One meal a day — a simple daily ritual, not another tracker.
• Private crews of 3–15 — invite by link or QR; nothing is public.
• Scores & streaks — rate plates 1–10, keep your crew's streak alive.
• Stats that are fun, not clinical — podiums, leaderboards, and a cuisine passport.
• Blind voting — rate before you see who cooked it (optional, per crew).
• Achievements — unlock badges as your crew builds its history.
• Weekly recap — a shareable story of your crew's week.

FoodRats is deliberately anti-obsession. There are no macros, no weigh-ins, no streaks that shame
you. It's a light, social way to stay in touch through food.

You can export your data or delete your account at any time, right from the app.
```

### es-ES
**Título** (≤30): `FoodRats — comidas con tu crew`
**Descripción corta** (≤80):
`Comparte una comida al día con tu grupo cerrado. Puntuaciones y rachas, sin contar calorías.`
**Descripción completa** (≤4000):
```
FoodRats es una app de comidas para grupos cerrados de 3 a 15 personas: amigos, familia o
compañeros. Publica una comida al día: una foto, una puntuación del 1 al 10, el nombre del plato
y una nota opcional. Nada más.

Sin seguidores. Sin feed público. Sin contar calorías. Solo tu gente y lo que comiste hoy.

QUÉ INCLUYE
• Una comida al día — un ritual sencillo, no otro contador.
• Crews privadas de 3 a 15 — invita por enlace o QR; nada es público.
• Puntuaciones y rachas — valora los platos del 1 al 10 y mantén viva la racha.
• Estadísticas divertidas, no clínicas — podios, clasificaciones y un pasaporte de cocinas.
• Votación a ciegas — valora antes de ver quién cocinó (opcional, por crew).
• Logros — desbloquea insignias según crece la historia de tu crew.
• Resumen semanal — una historia para compartir con la semana de tu crew.

FoodRats es deliberadamente anti-obsesión. Sin macros, sin pesarse, sin rachas que te culpabilicen.
Una forma ligera y social de seguir en contacto a través de la comida.

Puedes exportar tus datos o eliminar tu cuenta cuando quieras, desde la propia app.
```

**Release notes (What's new)** — per-release; no fastlane lane uploads this (every lane in
`fastlane/Fastfile` sets `skip_upload_metadata`/`skip_metadata`), so it's a manual console paste
every time. First release, en/es (historical, v1.0.0):
```
en-US: First release. Create a crew, post your daily meal, and keep the streak going.
es-ES: Primera versión. Crea una crew, publica tu comida del día y mantén la racha.
```
Current release notes: `docs/store-release/RELEASE-NOTES-v1.11.0.md` (en/es, Play + TestFlight
variants). Going forward each release gets its own `docs/store-release/RELEASE-NOTES-vX.Y.Z.md`
instead of overwriting this block.

---

## App Store Connect

### en-US
**Name** (≤30): `FoodRats`
**Subtitle** (≤30): `Daily meals with your crew`
**Promotional text** (≤170, editable anytime):
`One meal a day, shared with your small crew. Scores, streaks, and a weekly recap — no calorie counting, ever.`
**Keywords** (≤100, comma-separated, no spaces):
`meal,crew,friends,food,share,streak,group,private,score,dinner,social,photo,recap`
**Description** (≤4000): use the Play en-US full description above (drop the leading line break).

### es-ES
**Nombre** (≤30): `FoodRats`
**Subtítulo** (≤30): `Comidas diarias con tu crew`
**Texto promocional** (≤170):
`Una comida al día con tu grupo cercano. Puntuaciones, rachas y resumen semanal — sin contar calorías.`
**Palabras clave** (≤100):
`comida,crew,amigos,grupo,compartir,racha,privado,puntuacion,cena,social,foto,resumen`
**Descripción** (≤4000): usa la descripción completa de Play es-ES.

---

## Screenshots checklist (device-captured — needs a build on a device/emulator)

Capture from the **release** build, signed-in, with a seeded demo crew (so no real PII shows).
Required sets:
- **Android phone** (Play): 2–8 shots, 16:9 or 9:16, min 320px, max 3840px. PNG/JPEG.
- **iPhone 6.7"** (App Store, required): 1290×2796. **iPhone 6.5"** (1242×2688) recommended too.
- **iPad 12.9"** only if you ship an iPad build (the app is iPhone-class; can mark iPhone-only).

Suggested 5 frames (same order both stores):
1. Feed — a crew's day of meals (blurred placeholder → sharp).
2. Compose — capture a plate + score.
3. Stats — podium / leaderboard.
4. Cuisine passport — collected cuisines.
5. Weekly recap — a shareable story scene.

Feature graphic (Play, required): 1024×500 PNG. App icon is already in the project.

---

## UGC / review notes (paste into "Notes for review")
- Closed groups only (3–15 invited members); no public discovery or open feed.
- Photos and comments are visible only within a user's own crews.
- Account deletion is in-app (Profile → Delete account) and via public URL:
  https://foodrats-de4ec.web.app/account-deletion
- Sign-in is Google only (Credential Manager / GIDSignIn). A demo account can be provided on request.
