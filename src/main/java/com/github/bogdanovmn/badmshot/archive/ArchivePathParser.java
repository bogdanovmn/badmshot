package com.github.bogdanovmn.badmshot.archive;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class ArchivePathParser {

    private static final Pattern YEAR = Pattern.compile("(\\d{4})");
    private static final Pattern MONTH_DIR = Pattern.compile("(\\d{4})-(\\d{2})");
    private static final Pattern DATE_DIR = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})-(t-)?(.+)");

    public ArchivePath parse(String relPath) {
        String normalized = relPath.replace('\\', '/');
        String[] parts = normalized.split("/");
        List<String> errors = new ArrayList<>();

        if (parts.length != 4) {
            errors.add("Ожидается структура <год>/<год-месяц>/<год-месяц-день-клуб>/<файл>, получено сегментов: " + parts.length);
            return ArchivePath.builder()
                .relPath(normalized)
                .players(List.of())
                .validationError(String.join("; ", errors))
                .build();
        }

        Integer year = parseYear(parts[0], errors);
        Integer month = null;
        if (year != null) {
            Matcher monthDir = MONTH_DIR.matcher(parts[1]);
            if (monthDir.matches()) {
                int folderYear = Integer.parseInt(monthDir.group(1));
                int folderMonth = Integer.parseInt(monthDir.group(2));
                if (folderYear != year) {
                    errors.add("Год в папке месяца (" + folderYear + ") не совпадает с годом (" + year + ")");
                }
                if (folderMonth < 1 || folderMonth > 12) {
                    errors.add("Некорректный месяц: " + parts[1]);
                } else {
                    month = folderMonth;
                }
            } else {
                errors.add("Некорректное имя папки месяца: " + parts[1]);
            }
        }

        LocalDate matchDate = null;
        String club = null;
        boolean tournament = false;
        String dateDir = parts[2];
        Matcher dateMatcher = DATE_DIR.matcher(dateDir);
        if (dateMatcher.matches()) {
            int dirYear = Integer.parseInt(dateMatcher.group(1));
            int dirMonth = Integer.parseInt(dateMatcher.group(2));
            int dirDay = Integer.parseInt(dateMatcher.group(3));
            tournament = dateMatcher.group(4) != null;
            club = dateMatcher.group(5);
            if (year != null && dirYear != year) {
                errors.add("Год в папке даты (" + dirYear + ") не совпадает с годом (" + year + ")");
            }
            if (month != null && dirMonth != month) {
                errors.add("Месяц в папке даты (" + dirMonth + ") не совпадает с месяцем (" + month + ")");
            }
            try {
                matchDate = LocalDate.of(dirYear, dirMonth, dirDay);
            } catch (Exception e) {
                errors.add("Некорректная дата: " + dateDir);
            }
        } else {
            errors.add("Некорректное имя папки даты: " + dateDir);
        }

        List<String> players = parsePlayers(parts[3], dateDir, errors);
        if (players.isEmpty() && errors.isEmpty()) {
            errors.add("Не удалось определить игроков");
        }

        return ArchivePath.builder()
            .relPath(normalized)
            .year(year)
            .month(month)
            .matchDate(matchDate)
            .club(club)
            .tournament(tournament)
            .players(players)
            .validationError(errors.isEmpty() ? null : String.join("; ", errors))
            .build();
    }

    private Integer parseYear(String value, List<String> errors) {
        if (!YEAR.matcher(value).matches()) {
            errors.add("Некорректный год: " + value);
            return null;
        }
        return Integer.parseInt(value);
    }

    private List<String> parsePlayers(String fileName, String dateDir, List<String> errors) {
        String base = fileName;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            errors.add("Отсутствует расширение файла: " + fileName);
        } else {
            String extension = fileName.substring(dot + 1);
            if (!extension.equalsIgnoreCase("mp4")) {
                errors.add("Неожиданное расширение файла: " + extension);
            }
            base = fileName.substring(0, dot);
        }

        String prefix = dateDir + "--";
        if (!base.startsWith(prefix)) {
            errors.add("Имя файла не соответствует папке: " + fileName);
            return List.of();
        }

        String playersPart = base.substring(prefix.length());
        if (playersPart.isBlank()) {
            errors.add("Не указаны игроки в имени файла: " + fileName);
            return List.of();
        }

        List<String> players = new ArrayList<>();
        for (String name : playersPart.split("-")) {
            if (name.isBlank()) {
                errors.add("Пустое имя игрока в: " + fileName);
                continue;
            }
            players.add(name);
        }
        if (players.size() != 2 && players.size() != 4) {
            errors.add("Ожидается 2 или 4 игрока, получено: " + players.size());
        }
        return players;
    }
}
