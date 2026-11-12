package it.picone.miotreno.ui.componenti

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import it.picone.miotreno.ui.theme.Spazio

/** Schermata a lista con la barra grande M3 Expressive che si riduce scorrendo. */
@Composable
fun SchermataConBarra(
    titolo: String,
    sottotitolo: String? = null,
    spaziatura: Dp = Spazio.l,
    contenuto: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(titolo) },
                subtitle = sottotitolo?.let { { Text(it) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { interno ->
        LazyColumn(
            Modifier.padding(interno).fillMaxSize(),
            contentPadding = PaddingValues(start = Spazio.pagina, end = Spazio.pagina, top = Spazio.s, bottom = Spazio.xl),
            verticalArrangement = Arrangement.spacedBy(spaziatura),
            content = contenuto,
        )
    }
}
