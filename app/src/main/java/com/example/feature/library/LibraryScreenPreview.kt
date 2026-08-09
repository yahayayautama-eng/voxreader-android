package com.example.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.domain.repository.Book
import com.example.ui.theme.MyApplicationTheme

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
fun LibraryScreenSuccessPreview() {
    MyApplicationTheme {
        LibraryScreenContent(
            uiState = LibraryUiState.Success(
                listOf(
                    Book("1", "Clean Code", "Robert C. Martin"),
                    Book("2", "Effective Kotlin", "Marcin Moskala")
                )
            ),
            onAction = {}
        )
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
fun LibraryScreenEmptyPreview() {
    MyApplicationTheme {
        LibraryScreenContent(
            uiState = LibraryUiState.Empty,
            onAction = {}
        )
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
fun LibraryScreenLoadingPreview() {
    MyApplicationTheme {
        LibraryScreenContent(
            uiState = LibraryUiState.Loading,
            onAction = {}
        )
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
fun LibraryScreenErrorPreview() {
    MyApplicationTheme {
        LibraryScreenContent(
            uiState = LibraryUiState.Error("Failed to load books."),
            onAction = {}
        )
    }
}
