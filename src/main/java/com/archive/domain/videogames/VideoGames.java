package com.archive.domain.videogames;
import com.archive.core.model.BaseMedia;
import com.archive.domain.videogames.dlc.Dlc;
import com.archive.domain.videogames.save.GameSave;
import com.archive.domain.videogames.mod.GameMod;
import com.archive.domain.videogames.mod.ModList;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

    // Child Collections
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("name ASC")
    private List<Dlc> dlcs = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("saveTimestamp DESC")
    private List<GameSave> saves = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("defaultLoadOrder ASC")
    private List<GameMod> mods = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("name ASC")
    private List<ModList> modLists = new ArrayList<>();

    // Helper methods to maintain bidirectional relationship consistency
    public void addDlc(Dlc dlc) {
        dlcs.add(dlc);
        dlc.setGame(this);
    }

    public void removeDlc(Dlc dlc) {
        dlcs.remove(dlc);
        dlc.setGame(null);
    }

    public void addSave(GameSave save) {
        saves.add(save);
        save.setGame(this);
    }

    public void removeSave(GameSave save) {
        saves.remove(save);
        save.setGame(null);
    }

    public void addMod(GameMod mod) {
        mods.add(mod);
        mod.setGame(this);
    }

    public void removeMod(GameMod mod) {
        mods.remove(mod);
        mod.setGame(null);
    }

    public void addModList(ModList modList) {
        modLists.add(modList);
        modList.setGame(this);
    }

    public void removeModList(ModList modList) {
        modLists.remove(modList);
        modList.setGame(null);
    }
}
