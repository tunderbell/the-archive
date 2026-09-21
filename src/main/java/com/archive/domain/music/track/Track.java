package com.archive.domain.music.track;

import com.archive.domain.music.Album;
import com.archive.domain.music.AudioFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "track")
@Getter
@Setter
@NoArgsConstructor
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    // Optional album link: NULL if this is a standalone track
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_uuid", nullable = true)
    private Album album;

    @Column(nullable = false)
    private String title;

    private String artist; // Track-level artist (e.g. featured artists)

    private Integer trackNumber; // Nullable for standalone tracks

    private Integer discNumber = 1;

    private Long durationSeconds;

    @Enumerated(EnumType.STRING)
    private AudioFormat audioFormat;

    private Integer bitrate;
    private Integer sampleRate;

    @Column(columnDefinition = "TEXT")
    private String lyrics;

    @Column(columnDefinition = "TEXT")
    private String storagePath;

    @Column(columnDefinition = "TEXT")
    private String sourceUrl;

    private boolean downloaded;

    @CreationTimestamp
    @Column(name = "date_added", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateAdded;

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastUpdated;
}
