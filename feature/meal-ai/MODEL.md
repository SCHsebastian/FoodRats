# Food-101 classifier model

`src/commonMain/composeResources/files/food101.tflite` is the on-device dish
classifier loaded by `MediaPipeMealClassifier` (Android) via MediaPipe Tasks
Vision `ImageClassifier`.

## Source

- **Repo:** [STMicroelectronics/stm32ai-modelzoo](https://github.com/STMicroelectronics/stm32ai-modelzoo)
- **Path:** `image_classification/mobilenetv1/ST_pretrainedmodel_public_dataset/food101/mobilenetv1_a050_224_fft/mobilenetv1_a050_224_fft_int8.tflite`
- **Variant:** MobileNet v1, alpha 0.5, 224×224 input, int8 quantized, fine-tuned on the Food-101 public dataset (101 classes).
- **Size:** ~1.0 MB. **License:** ST model zoo terms (Apache-2.0 code; model weights under ST's model-zoo license — acceptable pre-launch; revisit before a public release).

The plan's original URL (`mobilenet_v1_0.5_224_fft_int8.tflite`) is dead — ST
reorganized the repo. The `mobilenetv1_a050_224_fft` variant above is the direct
equivalent (a050 = alpha 0.5). Fetched via the Git-LFS media endpoint:
`https://media.githubusercontent.com/media/STMicroelectronics/stm32ai-modelzoo/main/<path>`.

## Embedded labels + normalization metadata (resolved 2026-05-26)

The shipped `.tflite` now carries **TFLite Model Metadata**: a 101-entry label map
(`labels.txt`, `TENSOR_AXIS_LABELS` on the output tensor) and input
`NormalizationOptions(mean=127.5, std=127.5)`. So MediaPipe `Category.categoryName()`
returns the dish slug directly (e.g. `"lasagna"`), and `ClassifyDraftPlateUseCase`
calls `IngredientReadPort.suggestForDish("lasagna")` → matches the T26 seed map. No
app-side code change was needed, and Android + iOS are fixed together (they load the
same byte-identical model).

> Correction to the prior note: a metadata-*less* MediaPipe model does **not** put the
> index in `categoryName()` — that field is empty (`""`); the int is in `Category.index()`,
> and even that isn't guaranteed to be populated without metadata. Embedding the label
> map is the supported fix, which is why it was chosen over in-code index→slug mapping.

### Label source & ordering (authoritative)

The 101 labels and their **order** come from ST's own training config
(`mobilenetv1_a050_224_fft_config.yaml`, `dataset.class_names`) — NOT the generic
Food-101 `classes.txt`, because the model's output indices follow ST's order. The
list is checked into `feature/meal-ai/food101_labels.txt` (one slug per line, ST
order) and is **set-equal** to the T26 `dishIngredientMap` keys (verified). That file
is the source-of-truth artifact; it lives at the module root (not in
`composeResources/files/`) so it is not redundantly bundled — the labels already ride
inside the `.tflite`.

### Re-injecting (if the model is ever replaced)

Model facts confirmed for this variant: input tensor `UINT8 [1,224,224,3]`,
quant `scale=1/127.5, zero_point=127` (so raw uint8 [0,255] dequantizes to ≈[-1,1],
matching ST's `scale 1/127.5, offset -1` preprocessing → `mean=std=127.5`); output
`FLOAT32 [1,101]`.

```python
# pip install mediapipe   (macOS arm64 wheel omits the native _pywrap_metadata_version;
# stub it — it only stamps an informational min-parser-version)
import sys, types
for p in ("mediapipe.tasks.cc","mediapipe.tasks.cc.metadata","mediapipe.tasks.cc.metadata.python"):
    sys.modules[p] = types.ModuleType(p)
_pw = types.ModuleType("mediapipe.tasks.cc.metadata.python._pywrap_metadata_version")
_pw.GetMinimumMetadataParserVersion = lambda _b: "1.0.0"
sys.modules["mediapipe.tasks.cc.metadata.python._pywrap_metadata_version"] = _pw
sys.modules["mediapipe.tasks.cc.metadata.python"]._pywrap_metadata_version = _pw
from mediapipe.tasks.python.metadata.metadata_writers import image_classifier, metadata_writer
labels = [l.strip() for l in open("food101_labels.txt") if l.strip()]
w = image_classifier.MetadataWriter.create(
    bytearray(open("food101.tflite","rb").read()),
    input_norm_mean=[127.5], input_norm_std=[127.5],
    labels=metadata_writer.Labels().add(labels))
out, _ = w.populate()
open("food101.tflite","wb").write(out)   # then copy byte-identical to iosApp/iosApp/
```

Keep the `composeResources/files/food101.tflite` and `iosApp/iosApp/food101.tflite`
copies byte-identical (same sha256).

## iOS integration

On Android the model is read from `files/food101.tflite` via Compose Resources /
the MediaPipe `ImageClassifier` asset path. On iOS, MediaPipe Tasks Vision ships as
a CocoaPod / XCFramework integrated in the Xcode project (not via Gradle), so:

- A copy of the model lives at `iosApp/iosApp/food101.tflite` and **must be added to
  the iosApp target's "Copy Bundle Resources"** so `Bundle.main.path(forResource:)`
  resolves it. Keep it byte-identical to the `composeResources` copy.
- Inference runs in Swift (`iosApp/iosApp/MediaPipeClassifierBridge.swift`) and is
  bridged into Kotlin's `MediaPipeMealClassifier` (iosMain) through a lambda wired in
  `ContentView.swift` → `MainViewController` → `mealAiIosModule(...)` — the same
  convention used for GoogleSignIn and Crashlytics.
- The `MediaPipeTasksVision` dependency must be added to the Xcode project (CocoaPods
  `pod 'MediaPipeTasksVision'` or a vendored XCFramework — there is no official SPM
  distribution). Keep its version in sync with the Android
  `com.google.mediapipe:tasks-vision` artifact (currently `0.10.14`).
