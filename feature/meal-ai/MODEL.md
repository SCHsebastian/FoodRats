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

## ⚠️ Known gap — no embedded labels/metadata

This `.tflite` ships **without TFLite Model Metadata and without an associated
label map**. Consequences:

- MediaPipe `ImageClassifier` loads and runs it, but `Category.categoryName()`
  returns the **numeric class index** (`"0"`…`"100"`), not a dish name.
- `ClassifyPlateUseCase` therefore calls `IngredientReadPort.suggestForDish("37")`
  rather than `suggestForDish("lasagna")` — so the dish→ingredient seed map
  (T26) will not match until labels are resolved.

**Before the classifier produces usable dish slugs, do ONE of:**

1. **Inject metadata** into the `.tflite` with the canonical Food-101 label list
   (alphabetical: `apple_pie`, `baby_back_ribs`, … `waffles`) using the MediaPipe
   Metadata Writer / `tflite-support`, then re-bundle. `categoryName()` then
   returns the dish slug directly. (Preferred — keeps the use case unchanged.)
2. **Map index → slug in code:** bundle a `food101_labels.txt` (same canonical
   order) and translate `DishLabel.dishSlug` (the index) to the label in the
   data layer before it reaches `ClassifyPlateUseCase`.

Either way, the **T26 seed `dishIngredientMap` keys must be the 101 canonical
Food-101 class names**. Verify the chosen label ordering matches ST's training
order against `…/food101/<variant>/<variant>_config.yaml` in the model zoo.

This gap is expected to be closed alongside the T31 device smoke.
