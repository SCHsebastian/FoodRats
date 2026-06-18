# Backend deploy log — 2026-06-18 (foodrats-de4ec)

What was deployed this session (firebase-tools logged in; gcloud authed as
schsebastiancardonahenao@gmail.com). All non-destructive (no data deletion).

| Step | Status | Notes |
|---|---|---|
| Cloud Functions (`--only functions`) | ✅ DONE | Deploy complete — 9 successful update ops (Gen-2, europe-west3; onPlateImageFinalized us-east1). No deletions. |
| IAM: Service Account Token Creator | ✅ DONE | Granted on the **compute** runtime SA `475840003160-compute@developer.gserviceaccount.com` (NOT `@appspot` — Gen-2 functions run as the compute SA; runbook §6 superseded for Gen-2). Verified via `gcloud functions describe mintPlateUrls`. Fixes V4 signed image URLs + data export. |
| Firestore rules (`--only firestore:rules`) | ✅ DONE | Released to cloud.firestore. |
| Firestore indexes (`--only firestore:indexes`) | ✅ DONE | authorId collection-group overrides deployed (covers human.md §C). |
| Storage rules (`--only storage`) | ✅ DONE | Released to firebase.storage. |
| Hosting (`--only hosting`) | ✅ DONE | Verified live (HTTP 200): /account-deletion, /.well-known/apple-app-site-association, /.well-known/assetlinks.json. assetlinks SHA-256 still placeholders (need Play console). |
| Catalog seed (`seed:catalog`) | ✅ DONE (2026-06-18) | User ran `gcloud auth application-default login` + `pnpm --dir functions seed:catalog`. Output: "Wrote 226 ingredients + 101 dish maps + 14 cuisines + 101 dish-cuisine maps to foodrats-de4ec". Ingredient picker + cuisine passport now have data. |

**Backend is 100% deployed — human.md blocks A, B, C are all complete.** Only account/console/device gates remain (RELEASE-CHECKLIST.md phases 1–6).

## Runtime SA note (important)
The Gen-2 functions runtime SA is `475840003160-compute@developer.gserviceaccount.com`.
Any future IAM grant for signing/Storage/Firestore should target THIS SA, not `@appspot`.

## Commands used
```
pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec --non-interactive
gcloud iam service-accounts add-iam-policy-binding 475840003160-compute@developer.gserviceaccount.com \
  --member="serviceAccount:475840003160-compute@developer.gserviceaccount.com" \
  --role="roles/iam.serviceAccountTokenCreator" --project foodrats-de4ec
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
pnpm dlx firebase-tools deploy --only storage --project foodrats-de4ec
pnpm dlx firebase-tools deploy --only firestore:indexes --project foodrats-de4ec
pnpm dlx firebase-tools deploy --only hosting --project foodrats-de4ec
```
