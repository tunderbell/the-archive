package com.archive.core.model;

import lombok.Getter;

@Getter
public enum Visibility {
    // Defined with (Label, Hex Color Code, Description)
    PRIVATE("Restricted", "#FF3333", "Local-only. Not visible to the workspace."),
    WORKSPACE("Shared", "#F1C40F", "Visible to all members of your current vault."),
    GLOBAL("Public", "#2ECC71", "Visible to the entire network.");

    private final String label;
    private final String colorCode;
    private final String description;

    Visibility(String label, String colorCode, String description) {
        this.label = label;
        this.colorCode = colorCode;
        this.description = description;
    }
}