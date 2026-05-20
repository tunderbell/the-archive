package com.archive.domain.videogames;
import com.archive.core.model.BaseMedia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.Set;

@Entity
@Table(name = "video_games")
@Getter
@Setter
@NoArgsConstructor

public class VideoGames extends BaseMedia {

    private String developer;
    private String publisher;
    private String engine;
    private Integer releaseYear;

    @Enumerated(EnumType.STRING)
    private GameStatus playStatus;

    @ElementCollection
    @CollectionTable(
        name = "game_platforms",
        joinColumns = @JoinColumn(name = "game_uuid")
    )
    @Column(name = "platform")
    private Set<String> platforms;

    @ElementCollection
    @CollectionTable(
        name = "game_genres",
        joinColumns = @JoinColumn(name = "game_uuid")
    )
    @Column(name = "genre")
    private Set<String> genres;

    // Metadata
    private Long playtimeMinutes; 
    private String storagePath;
    private String version; 
    private OffsetDateTime lastPlayed;

    // CSS Selector Overrides (Area 4: User-Defined Scrapers)
    // Useful for scraping Steam, GOG, or IGDB
    private String customStoreUrl;
    private String customPriceSelector;
    
}
