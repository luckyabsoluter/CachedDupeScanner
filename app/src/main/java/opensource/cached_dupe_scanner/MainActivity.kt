package opensource.cached_dupe_scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import opensource.cached_dupe_scanner.core.DuplicateGroup
import opensource.cached_dupe_scanner.core.FileMetadata
import opensource.cached_dupe_scanner.core.ScanResult
import opensource.cached_dupe_scanner.ui.results.ScanResultScreen
import opensource.cached_dupe_scanner.ui.results.ScanUiState
import opensource.cached_dupe_scanner.ui.theme.CachedDupeScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CachedDupeScannerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScanResultScreen(
                        state = previewState(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun previewState(): ScanUiState {
    val fileA = FileMetadata(
        path = "storage/dir/a.txt",
        normalizedPath = "storage/dir/a.txt",
        sizeBytes = 100,
        lastModifiedMillis = 10,
        hashHex = "hash-1"
    )
    val fileB = FileMetadata(
        path = "storage/dir/b.txt",
        normalizedPath = "storage/dir/b.txt",
        sizeBytes = 100,
        lastModifiedMillis = 12,
        hashHex = "hash-1"
    )
    val fileC = FileMetadata(
        path = "storage/dir/c.txt",
        normalizedPath = "storage/dir/c.txt",
        sizeBytes = 120,
        lastModifiedMillis = 15,
        hashHex = "hash-2"
    )

    return ScanUiState.Success(
        ScanResult(
            scannedAtMillis = System.currentTimeMillis(),
            files = listOf(fileA, fileB, fileC),
            duplicateGroups = listOf(
                DuplicateGroup("hash-1", listOf(fileA, fileB))
            )
        )
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CachedDupeScannerTheme {
        ScanResultScreen(state = previewState())
    }
}