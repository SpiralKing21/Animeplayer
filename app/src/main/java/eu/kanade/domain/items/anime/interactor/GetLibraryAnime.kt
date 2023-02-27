package eu.kanade.domain.items.anime.interactor

import eu.kanade.domain.items.anime.repository.AnimeRepository
import eu.kanade.domain.library.anime.LibraryAnime
import kotlinx.coroutines.flow.Flow

class GetLibraryAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<LibraryAnime> {
        return animeRepository.getLibraryAnime()
    }

    fun subscribe(): Flow<List<LibraryAnime>> {
        return animeRepository.getLibraryAnimeAsFlow()
    }
}
