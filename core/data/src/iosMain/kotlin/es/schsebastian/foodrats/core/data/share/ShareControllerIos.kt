package es.schsebastian.foodrats.core.data.share

// Stub: iOS share is delegated to UIActivityViewController via Swift; wire the lambda
// from ContentView.swift when native share is needed. For now, logs to console.
class ShareControllerIos(private val shareAction: (String) -> Unit = { text ->
    println("[ShareControllerIos] shareText stub: $text")
}) : ShareController {
    override fun shareText(text: String) = shareAction(text)
}
