package com.archive.domain.manga.chapter;

import com.archive.domain.manga.Manga;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chapter")
@Getter
@Setter
@NoArgsConstructor
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manga_uuid", nullable = false)
    @JsonIgnoreProperties({"chapters", "hibernateLazyInitializer", "handler"})
    private Manga manga;

    @Column(nullable = false)
    private Double chapterNumber;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String storagePath;

    @Column(columnDefinition = "TEXT")
    private String sourceUrl;

    private boolean downloaded;

    private Integer pageCount;

    private boolean isRead = false;

    private Integer lastReadPage = 0;

    @Column(columnDefinition = "TEXT")
    private String cbzPath;

    @CreationTimestamp
    @Column(name = "date_added", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateAdded;

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastUpdated;
}
