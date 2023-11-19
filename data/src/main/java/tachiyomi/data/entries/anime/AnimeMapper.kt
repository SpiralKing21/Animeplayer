package tachiyomi.data.entries.anime

import eu.kanade.tachiyomi.model.UpdateStrategy
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime

val animeMapper: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, UpdateStrategy, Long, Long, Long?) -> Anime =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, initialized, viewerFlags, episodeFlags, coverLastModified, dateAdded, updateStrategy, calculateInterval, lastModifiedAt, favoriteModifiedAt ->
        Anime(
            id = id,
            source = source,
            favorite = favorite,
            lastUpdate = lastUpdate ?: 0,
            nextUpdate = nextUpdate ?: 0,
            fetchInterval = calculateInterval.toInt(),
            dateAdded = dateAdded,
            viewerFlags = viewerFlags,
            episodeFlags = episodeFlags,
            coverLastModified = coverLastModified,
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status,
            thumbnailUrl = thumbnailUrl,
            updateStrategy = updateStrategy,
            initialized = initialized,
            lastModifiedAt = lastModifiedAt,
            favoriteModifiedAt = favoriteModifiedAt,
        )
    }

val libraryAnime: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, UpdateStrategy, Long, Long, Long?, Long, Double, Long, Long, Long, Double, Long) -> LibraryAnime =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, initialized, viewerFlags, episodeFlags, coverLastModified, dateAdded, updateStrategy, calculateInterval, lastModifiedAt, favoriteModifiedAt, totalCount, seenCount, latestUpload, episodeFetchedAt, lastSeen, bookmarkCount, category ->
        LibraryAnime(
            anime = animeMapper(
                id,
                source,
                url,
                artist,
                author,
                description,
                genre,
                title,
                status,
                thumbnailUrl,
                favorite,
                lastUpdate,
                nextUpdate,
                initialized,
                viewerFlags,
                episodeFlags,
                coverLastModified,
                dateAdded,
                updateStrategy,
                calculateInterval,
                lastModifiedAt,
                favoriteModifiedAt,
            ),
            category = category,
            totalEpisodes = totalCount,
            seenCount = seenCount.toLong(),
            bookmarkCount = bookmarkCount.toLong(),
            latestUpload = latestUpload,
            episodeFetchedAt = episodeFetchedAt,
            lastSeen = lastSeen,
        )
    }