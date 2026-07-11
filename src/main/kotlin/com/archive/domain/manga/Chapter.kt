package com.archive.domain.manga

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chapter")
open class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manga_uuid", nullable = false)
    var manga: Manga? = null

    @Column(nullable = false)
    var chapterNumber: Double = 0.0

    var title: String? = null

    @Column(columnDefinition = "TEXT")
    var storagePath: String? = null

    var downloaded: Boolean = false

    @CreationTimestamp
    @Column(name = "date_added", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var dateAdded: OffsetDateTime? = null

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var lastUpdated: OffsetDateTime? = null
}
