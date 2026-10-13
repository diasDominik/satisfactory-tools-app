package io.github.diasdominik.sft.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AboutScreen(onBack: () -> Unit) {
    DetailScaffold("About", onBack) {
        item {
            Text("Satisfactory Tools", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            SectionCard("Credits") {
                Text(
                    "A mobile port of Satisfactory Tools (satisfactorytools.com) by greeny, with help from Lucek and other " +
                        "contributors. The original is open source under the MIT license at github.com/greeny/SatisfactoryTools.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SectionCard("Differences from the website") {
                Text(
                    "• The production planner solves on your device, so it works offline. Results can differ slightly from the " +
                        "website's server-side solver when several equally good solutions exist.\n" +
                        "• Share links are replaced by .sft export and import, which are compatible with the website's export files.\n" +
                        "• Item and building icons are placeholders: the game's icons belong to Coffee Stain Studios and may not be " +
                        "redistributed by derivative works.\n" +
                        "• Only game version 1.0 (and the 1.0 FICSMAS data) is supported.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SectionCard("Disclaimer") {
                Text(
                    "Satisfactory is a trademark of Coffee Stain Studios. This app is a fan project and is not affiliated with " +
                        "or endorsed by Coffee Stain Studios.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
