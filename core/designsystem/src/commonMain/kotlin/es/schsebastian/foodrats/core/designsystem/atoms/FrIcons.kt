package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

// NOTE: Camera-related icons (AddAPhoto, Camera, Image, NoPhotography) are in
// material-icons-extended which does not publish a KMP-compatible artifact for iOS.
// We substitute with material-icons-core equivalents for the multiplatform scaffold;
// replace with platform-specific icon packs when the capture feature is hardened.
object FrIcons {
    val Back: ImageVector          = Icons.Filled.ArrowBack
    val Camera: ImageVector        = Icons.Filled.Add        // placeholder for Camera
    val AddPhoto: ImageVector      = Icons.Filled.Add        // placeholder for AddAPhoto
    val GalleryImport: ImageVector = Icons.Filled.List       // placeholder for Image
    val CameraOff: ImageVector     = Icons.Filled.Warning    // placeholder for NoPhotography
    val Settings: ImageVector      = Icons.Filled.Settings
}
