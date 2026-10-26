package com.github.bogdanovmn.badmshot.archive;

import java.util.List;

final class ArchivePathBuilder {

    private ArchivePathBuilder() {
    }

    static String buildRelPath(String relPath, List<String> players, String comment, ArchiveType type) {
        int slash = relPath.lastIndexOf('/');
        String parent = slash < 0 ? "" : relPath.substring(0, slash);
        String fileName = relPath.substring(slash + 1);
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? ".mp4" : fileName.substring(dot);
        String commentSuffix = comment == null || comment.isBlank() ? "" : "--" + comment;
        String name;
        if (type == ArchiveType.INDI) {
            String base = dot < 0 ? fileName : fileName.substring(0, dot);
            int marker = base.lastIndexOf("-indi");
            String datePart = marker < 0 ? base : base.substring(0, marker);
            String suffix = players.size() == 1
                ? ""
                : "--" + String.join("-", players);
            name = datePart + "-indi" + suffix + commentSuffix + extension;
        } else {
            String dateDir = parent.substring(parent.lastIndexOf('/') + 1);
            name = dateDir + "--" + String.join("-", players) + commentSuffix + extension;
        }
        return parent.isEmpty() ? name : parent + "/" + name;
    }
}
