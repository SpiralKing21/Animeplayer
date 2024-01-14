package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import tachiyomi.i18n.MR

enum class StartScreen(val titleRes: StringResource, val tab: Tab) {
    ANIME(MR.strings.label_anime, AnimeLibraryTab),
    MANGA(MR.strings.manga, MangaLibraryTab),
    BROWSER(MR.strings.browse, BrowseTab()),
    UPDATES(MR.strings.label_recent_updates, UpdatesTab(fromMore = false, inMiddle = false)),
}
