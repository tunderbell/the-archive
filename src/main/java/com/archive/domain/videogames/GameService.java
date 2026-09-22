package com.archive.domain.videogames;

import com.archive.domain.videogames.dlc.Dlc;
import com.archive.domain.videogames.dlc.DlcRepository;
import com.archive.domain.videogames.mod.*;
import com.archive.domain.videogames.save.GameSave;
import com.archive.domain.videogames.save.GameSaveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class GameService {

    private final VideoGamesRepository gameRepository;
    private final DlcRepository dlcRepository;
    private final GameSaveRepository saveRepository;
    private final GameModRepository modRepository;
    private final ModListRepository modListRepository;

    public GameService(VideoGamesRepository gameRepository,
                       DlcRepository dlcRepository,
                       GameSaveRepository saveRepository,
                       GameModRepository modRepository,
                       ModListRepository modListRepository) {
        this.gameRepository = gameRepository;
        this.dlcRepository = dlcRepository;
        this.saveRepository = saveRepository;
        this.modRepository = modRepository;
        this.modListRepository = modListRepository;
    }

    // --- Core Game Lifecycle ---

    public VideoGames createGame(VideoGames game) {
        return gameRepository.save(game);
    }

    @Transactional(readOnly = true)
    public Optional<VideoGames> getGameById(UUID id) {
        return gameRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<VideoGames> getAllGames() {
        return gameRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<VideoGames> searchGames(String query) {
        return gameRepository.findByTitleContainingIgnoreCase(query);
    }

    public VideoGames updatePlayStatus(UUID gameId, GameStatus status) {
        VideoGames game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with ID: " + gameId));
        game.setPlayStatus(status);
        return gameRepository.save(game);
    }

    public VideoGames logPlaytime(UUID gameId, long additionalMinutes) {
        VideoGames game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with ID: " + gameId));
        long current = game.getPlaytimeMinutes() != null ? game.getPlaytimeMinutes() : 0L;
        game.setPlaytimeMinutes(current + additionalMinutes);
        game.setLastPlayed(OffsetDateTime.now());
        return gameRepository.save(game);
    }

    public void deleteGame(UUID id) {
        gameRepository.deleteById(id);
    }

    // --- DLC Management ---

    public Dlc registerDlc(UUID gameId, Dlc dlc) {
        VideoGames game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with ID: " + gameId));
        game.addDlc(dlc);
        return dlcRepository.save(dlc);
    }

    public Dlc toggleDlcInstalled(UUID dlcId, boolean installed) {
        Dlc dlc = dlcRepository.findById(dlcId)
                .orElseThrow(() -> new IllegalArgumentException("DLC not found with ID: " + dlcId));
        dlc.setInstalled(installed);
        return dlcRepository.save(dlc);
    }

    @Transactional(readOnly = true)
    public List<Dlc> getDlcsForGame(UUID gameId) {
        return dlcRepository.findByGameIdOrderByNameAsc(gameId);
    }

    // --- Save File Snapshots & Backups ---

    public GameSave createSaveSnapshot(UUID gameId, String saveName, String storagePath, Long fileSizeBytes, String gameVersion, boolean isBackup) {
        VideoGames game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with ID: " + gameId));

        GameSave save = new GameSave();
        save.setSaveName(saveName);
        save.setStoragePath(storagePath);
        save.setFileSizeBytes(fileSizeBytes);
        save.setGameVersion(gameVersion);
        save.setPlaytimeMinutesAtSave(game.getPlaytimeMinutes());
        save.setBackup(isBackup);
        save.setSaveTimestamp(OffsetDateTime.now());

        game.addSave(save);
        return saveRepository.save(save);
    }

    @Transactional(readOnly = true)
    public List<GameSave> getSavesForGame(UUID gameId) {
        return saveRepository.findByGameIdOrderBySaveTimestampDesc(gameId);
    }

    // --- Mod Management ---

    public GameMod installMod(UUID gameId, GameMod mod) {
        VideoGames game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with ID: " + gameId));
        game.addMod(mod);
        return modRepository.save(mod);
    }

    public GameMod toggleModEnabled(UUID modId, boolean enabled) {
        GameMod mod = modRepository.findById(modId)
                .orElseThrow(() -> new IllegalArgumentException("Mod not found with ID: " + modId));
        mod.setEnabled(enabled);
        return modRepository.save(mod);
    }

    @Transactional(readOnly = true)
    public List<GameMod> getModsForGame(UUID gameId) {
        return modRepository.findByGameIdOrderByDefaultLoadOrderAsc(gameId);
    }

    // --- Mod Lists (Presets) ---

    public ModList createModList(UUID gameId, String name, String description) {
        VideoGames game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with ID: " + gameId));
        ModList modList = new ModList();
        modList.setName(name);
        modList.setDescription(description);
        game.addModList(modList);
        return modListRepository.save(modList);
    }

    public ModList addModToModList(UUID modListId, UUID modId, int loadOrder) {
        ModList modList = modListRepository.findById(modListId)
                .orElseThrow(() -> new IllegalArgumentException("ModList not found with ID: " + modListId));
        GameMod mod = modRepository.findById(modId)
                .orElseThrow(() -> new IllegalArgumentException("Mod not found with ID: " + modId));

        ModListItem item = new ModListItem();
        item.setMod(mod);
        item.setLoadOrder(loadOrder);
        item.setEnabled(true);
        modList.addItem(item);

        return modListRepository.save(modList);
    }

    /**
     * Activates a ModList preset for a game, deactivating other presets and synchronizing mod states.
     */
    public ModList activateModList(UUID gameId, UUID modListId) {
        List<ModList> allLists = modListRepository.findByGameIdOrderByNameAsc(gameId);
        ModList targetList = null;

        for (ModList ml : allLists) {
            if (ml.getId().equals(modListId)) {
                ml.setActive(true);
                targetList = ml;
            } else {
                ml.setActive(false);
            }
        }

        if (targetList == null) {
            throw new IllegalArgumentException("ModList not found with ID: " + modListId);
        }

        modListRepository.saveAll(allLists);
        return targetList;
    }
}
