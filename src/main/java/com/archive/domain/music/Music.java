package com.archive.domain.music;

import com.archive.core.model.BaseMedia;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.Set;



@Entity
@Table(name = "music")
@Getter
@Setter
@NoArgsConstructor
public class Music extends BaseMedia {
    private String artist;
    private String album;
    private Integer releaseYear;
    private Integer trackNumber;

    // Better for progress bar calculations
    private Long duration;


    @Enumerated(EnumType.STRING)
    private AudioFormat audioformat;

    private Integer bitrate;
    private Integer sampleRate;

    @ElementCollection
    @CollectionTable(
        name = "music_genres",
        joinColumns = @JoinColumn(name = "music_uuid")
    )
    @Column(name = "genre")
    private Set<String> genres;

    @Column(columnDefinition = "TEXT")
    private String lyrics;

    //local storage metadata
    private String storagePath;
    private String coverArtPath;

    //Scraoer
    private String customMetadatUrl;




}   
