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
| Catalog seed (`seed:catalog`) | ❌ USER STEP | Needs Application Default Credentials. Run: `gcloud auth application-default login` then `pnpm --dir functions seed:catalog`. Until run, ingredient picker + cuisine passport read empty (app still works). |

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
