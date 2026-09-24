package com.archive.ui;

import com.archive.ui.model.UserLayout;
import com.archive.ui.model.UserLayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * CLASS: UserLayoutService
 * ============================================================================
 * WHAT IT DOES:
 * Business service managing Dockview window configurations, workspace presets
 * (e.g. "OPS", "READ", "WAR"), and command bar positioning.
 *
 * WHY IT IS USED:
 * Bridges the React Dockview frontend with persistent database storage so users
 * never lose their customized workspace arrangements across sessions.
 * ============================================================================
 */
@Service
@Transactional
public class UserLayoutService {

    private final UserLayoutRepository layoutRepository;

    public UserLayoutService(UserLayoutRepository layoutRepository) {
        this.layoutRepository = layoutRepository;
    }

    /**
     * Saves or updates a layout preset configuration.
     *
     * @param name               Layout preset name (e.g., "OPS", "READ", "WAR", "default").
     * @param json               Serialized JSON string from Dockview's toJSON() API.
     * @param commandBarPosition Placement preference ("TOP" or "BOTTOM").
     * @return Persisted UserLayout entity.
     */
    public UserLayout saveLayout(String name, String json, String commandBarPosition) {
        UserLayout layout = layoutRepository.findByLayoutName(name)
                .orElse(new UserLayout());

        layout.setLayoutName(name);
        layout.setLayoutJson(json);
        if (commandBarPosition != null && !commandBarPosition.isBlank()) {
            layout.setCommandBarPosition(commandBarPosition.toUpperCase().trim());
        }
        return layoutRepository.save(layout);
    }

    /**
     * Retrieves a saved layout configuration by preset name.
     */
    @Transactional(readOnly = true)
    public Optional<UserLayout> getLayout(String name) {
        return layoutRepository.findByLayoutName(name);
    }

    /**
     * Retrieves all saved layout presets.
     */
    @Transactional(readOnly = true)
    public List<UserLayout> getAllLayouts() {
        return layoutRepository.findAll();
    }
}
