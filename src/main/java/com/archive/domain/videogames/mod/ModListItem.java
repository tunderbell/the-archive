package com.archive.domain.videogames.mod;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "mod_list_item")
@Getter
@Setter
@NoArgsConstructor
public class ModListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_list_uuid", nullable = false)
    private ModList modList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mod_uuid", nullable = false)
    private GameMod mod;

    private int loadOrder;

    private boolean enabled = true;
}
