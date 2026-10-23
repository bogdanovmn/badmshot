package com.github.bogdanovmn.badmshot.archive;

public enum ArchiveType {
    GAMES("games", "Игры"),
    INDI("indi", "Индивидуальные");

    private final String folder;
    private final String displayName;

    ArchiveType(String folder, String displayName) {
        this.folder = folder;
        this.displayName = displayName;
    }

    public String folder() {
        return folder;
    }

    public String displayName() {
        return displayName;
    }

    public static ArchiveType fromFolder(String value) {
        for (ArchiveType type : values()) {
            if (type.folder.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
