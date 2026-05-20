package com.archive.domain.manga;

import com.archive.core.model.BaseMedia;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.Set;

@Entity
@Table(name = "manga")
@Getter
@Setter
@NoArgsConstructor
public class Manga extends BaseMedia {

    private String author;
    private String artist;


    @Enumerated(EnumType.STRING)
    private MangaType type;

    @Enumerated(EnumType.STRING)
    private MangaStatus status;

    @ElementCollection
    @CollectionTable(
        name = "manga_genres", 
        joinColumns = @JoinColumn(name = "manga_uuid") // Matches BaseMedia name = "uuid"
    )
    @Column(name = "genre")
    private Set<String> genres;


    //Harvesting/scraping
    private String storagePath;
    private int totalChapters;
    private int downloadedChapters;
    private OffsetDateTime lastChecked;
    
    private boolean isAdult;

    // CSS Selector Overrides (Area 4: User-Defined Scrapers)
    private String customImageSelector;
    private String customChapterSelector;

}
