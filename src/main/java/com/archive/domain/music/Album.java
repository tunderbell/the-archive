package com.archive.domain.music;

import com.archive.core.model.BaseMedia;
import com.archive.domain.music.track.Track;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "album")
@Getter
@Setter
@NoArgsConstructor
public class Album extends BaseMedia {

    private String artist;
    private Integer releaseYear;
    private String recordLabel;

    @Enumerated(EnumType.STRING)
    private AlbumType albumType = AlbumType.ALBUM;

    @ElementCollection
    @CollectionTable(
        name = "album_genres",
        joinColumns = @JoinColumn(name = "album_uuid")
    )
    @Column(name = "genre")
    private Set<String> genres;

    private String coverArtPath;
    private String customMetadataUrl;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
    @OrderBy("trackNumber ASC")
    private List<Track> tracks = new ArrayList<>();

    public void addTrack(Track track) {
        tracks.add(track);
        track.setAlbum(this);
    }

    public void removeTrack(Track track) {
        tracks.remove(track);
        track.setAlbum(null);
    }
}
