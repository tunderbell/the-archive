package com.archive.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;


/**
 * BaseMedia serves as the foundation for all media types in the application.
 * MappedSuperclass ensures child entities (Manga, Anime, etc.) inherit these columns.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String title;

    // 'TEXT' is safer for long synopses than the default VARCHAR(255)
    @Column(columnDefinition = "TEXT")
    private String description;


    // Explicitly set the database-level default to match the Java initialization
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'PRIVATE'")
    private Visibility visibility = Visibility.PRIVATE;

    @CreationTimestamp
    @Column(name = "date_added", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateAdded;

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastUpdated;

    // URL to the original source (e.g., webtoon.xyz)
    private String sourceUrl;

    /**
     * Remote or cached URL to the media's thumbnail or cover art.
     * Mapped as TEXT in SQL to avoid VARCHAR(255) truncation on long CDN URLs,
     * tokenized image links, or base64 data URI strings.
     * Inherited by all media subclasses (Manga, Anime, Album, VideoGames).
     */
    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

}