package com.pr0gramm.app.ui.compose.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.ui.Themes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeShowcase(theme: Themes) {
    Pr0grammTheme(theme) {
        Surface {
            Column {
                TopAppBar(
                    title = { Text(theme.name) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Body large", style = MaterialTheme.typography.bodyLarge)
                    Text("Body medium", style = MaterialTheme.typography.bodyMedium)
                    Text("Body small", style = MaterialTheme.typography.bodySmall)

                    Button(onClick = {}) { Text("Button") }

                    Card {
                        ListItem(
                            headlineContent = { Text("List item") },
                            trailingContent = { RadioButton(selected = true, onClick = {}) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Orange", showBackground = true)
@Composable
private fun PreviewOrange() = ThemeShowcase(Themes.ORANGE)

@Preview(name = "Green", showBackground = true)
@Composable
private fun PreviewGreen() = ThemeShowcase(Themes.GREEN)

@Preview(name = "Olive", showBackground = true)
@Composable
private fun PreviewOlive() = ThemeShowcase(Themes.OLIVE)

@Preview(name = "Blue", showBackground = true)
@Composable
private fun PreviewBlue() = ThemeShowcase(Themes.BLUE)

@Preview(name = "Pink", showBackground = true)
@Composable
private fun PreviewPink() = ThemeShowcase(Themes.PINK)

@Preview(name = "Black", showBackground = true)
@Composable
private fun PreviewBlack() = ThemeShowcase(Themes.BLACK)
