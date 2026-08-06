package com.example.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme

@Preview(showBackground = true)
@Composable
fun LoadingStatePreview() {
    MyApplicationTheme {
        LoadingState()
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyStatePreview() {
    MyApplicationTheme {
        EmptyState(message = "No items found")
    }
}

@Preview(showBackground = true)
@Composable
fun ErrorStatePreview() {
    MyApplicationTheme {
        ErrorState(message = "An error occurred", onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
fun ConfirmationDialogPreview() {
    MyApplicationTheme {
        ConfirmationDialog(
            title = "Delete Item",
            text = "Are you sure you want to delete this item?",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = {},
            onDismiss = {}
        )
    }
}
