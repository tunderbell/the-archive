package com.archive.domain.videogames.save;

import com.archive.domain.videogames.VideoGames;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_save")
@Getter
@Setter
@NoArgsConstructor
public class GameSave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_uuid", nullable = false)
    private VideoGames game;

    @Column(nullable = false)
    private String saveName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String storagePath;

    private Long fileSizeBytes;

    private String gameVersion;

    private Long playtimeMinutesAtSave;

    private boolean isBackup;

    private OffsetDateTime saveTimestamp;

    @CreationTimestamp
    @Column(name = "date_added", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateAdded;

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastUpdated;
}
