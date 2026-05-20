package com.archive.domain.anime;

import com.archive.core.model.BaseMedia;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.Set;

@Entity
@Table(name = "anime")
@Getter
@Setter
@NoArgsConstructor
public class Anime extends BaseMedia{

    private String studio;
    private String director;
    private Integer releaseYear;

    @Enumerated(EnumType.STRING)
    private AnimeFormat format;

    @Enumerated(EnumType.STRING)
    private AnimeStatus status;

    @ElementCollection
    @CollectionTable(
        name = "anime_genres",
        joinColumns = @JoinColumn(name = "anime_uuid")
    )
    @Column(name = "genre")
    private Set<String> genres;

    // Local Storage and Technical Metadata
    private String storagePath;
    private int episodesTotal;
    private int episodesDownloaded;
    private String resolution;
    private String audioType;  
    private OffsetDateTime lastChecked;

    // CSS Selector Overrides (Area 4: User-Defined Scrapers)
    private String customEpisodeSelector;
    private String customVideoSelector;
    
}
