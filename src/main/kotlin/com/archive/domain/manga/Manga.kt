package com.archive.domain.manga

import com.archive.core.model.BaseMedia
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "manga")
open class Manga : BaseMedia() {

    var author: String? = null
    var artist: String? = null

    @Enumerated(EnumType.STRING)
    var type: MangaType? = null

    @Enumerated(EnumType.STRING)
    var status: MangaStatus? = null

    @ElementCollection
    @CollectionTable(
        name = "manga_genres",
        joinColumns = [JoinColumn(name = "manga_uuid")]
    )
    @Column(name = "genre")
    var genres: MutableSet<String> = mutableSetOf()

    // Harvesting/scraping state
    var storagePath: String? = null
    var totalChapters: Int = 0
    var downloadedChapters: Int = 0
    var lastChecked: OffsetDateTime? = null

    var isAdult: Boolean = false

    // CSS Selector Overrides (Area 4: User-Defined Scrapers)
    var customImageSelector: String? = null
    var customChapterSelector: String? = null

    @OneToMany(mappedBy = "manga", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var chapters: MutableList<Chapter> = mutableListOf()
}
