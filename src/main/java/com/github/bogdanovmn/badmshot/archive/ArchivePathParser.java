package com.github.bogdanovmn.badmshot.archive;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class ArchivePathParser {

    static final String DEFAULT_INDI_PLAYER = "misha";

    private static final Pattern YEAR = Pattern.compile("(\\d{4})");
    private static final Pattern MONTH_DIR = Pattern.compile("(\\d{4})-(\\d{2})");
    private static final Pattern DATE_DIR = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})-(t-)?(.+)");
    private static final Pattern INDI_FILE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})-indi(?:--(.+))?");

    public ArchivePath parse(String relPath) {
        String normalized = relPath.replace('\\', '/');
        String[] parts = normalized.split("/");
        List<String> errors = new ArrayList<>();

        ArchiveType type = parts.length == 0 ? null : ArchiveType.fromFolder(parts[0]);
        if (type == null) {
            errors.add("Неизвестный раздел архива: " + (parts.length == 0 ? "" : parts[0])
                + ". Ожидается games или indi");
            return invalid(normalized, null, errors);
        }

        return type == ArchiveType.GAMES
            ? parseGames(normalized, parts, errors)
            : parseIndi(normalized, parts, errors);
    }

    private ArchivePath parseGames(String relPath, String[] parts, List<String> errors) {
        if (parts.length != 5) {
            errors.add("Ожидается структура games/<год>/<год-месяц>/<год-месяц-день-клуб>/<файл>, получено сегментов: "
                + parts.length);
            return invalid(relPath, ArchiveType.GAMES, errors);
        }

        Integer year = parseYear(parts[1], errors);
        Integer month = parseMonthDir(parts[2], year, errors);

        LocalDate matchDate = null;
        String club = null;
        boolean tournament = false;
        String dateDir = parts[3];
        Matcher dateMatcher = DATE_DIR.matcher(dateDir);
        if (dateMatcher.matches()) {
            int dirYear = Integer.parseInt(dateMatcher.group(1));
            int dirMonth = Integer.parseInt(dateMatcher.group(2));
            int dirDay = Integer.parseInt(dateMatcher.group(3));
            tournament = dateMatcher.group(4) != null;
            club = dateMatcher.group(5);
            validateDateParts(dirYear, dirMonth, dirDay, year, month, dateDir, errors);
            try {
                matchDate = LocalDate.of(dirYear, dirMonth, dirDay);
            } catch (Exception e) {
                errors.add("Некорректная дата: " + dateDir);
            }
        } else {
            errors.add("Некорректное имя папки даты: " + dateDir);
        }

        List<String> players = parseGamePlayers(parts[4], dateDir, errors);

        return ArchivePath.builder()
            .type(ArchiveType.GAMES)
            .relPath(relPath)
            .sessionName(parts[3])
            .year(year)
            .month(month)
            .matchDate(matchDate)
            .club(club)
            .tournament(tournament)
            .players(players)
            .validationError(errors.isEmpty() ? null : String.join("; ", errors))
            .build();
    }

    private ArchivePath parseIndi(String relPath, String[] parts, List<String> errors) {
        if (parts.length != 4) {
            errors.add("Ожидается структура indi/<год>/<год-месяц>/<год-месяц-день-indi[--игрок1-игрок2]>.mp4, "
                + "получено сегментов: " + parts.length);
            return invalid(relPath, ArchiveType.INDI, errors);
        }

        Integer year = parseYear(parts[1], errors);
        Integer month = parseMonthDir(parts[2], year, errors);

        LocalDate matchDate = null;
        List<String> players = List.of();
        String base = stripExtension(parts[3], errors);
        if (base != null) {
            Matcher fileMatcher = INDI_FILE.matcher(base);
            if (fileMatcher.matches()) {
                int fileYear = Integer.parseInt(fileMatcher.group(1));
                int fileMonth = Integer.parseInt(fileMatcher.group(2));
                int fileDay = Integer.parseInt(fileMatcher.group(3));
                validateDateParts(fileYear, fileMonth, fileDay, year, month, parts[3], errors);
                try {
                    matchDate = LocalDate.of(fileYear, fileMonth, fileDay);
                } catch (Exception e) {
                    errors.add("Некорректная дата: " + parts[3]);
                }
                players = parseIndiPlayers(fileMatcher.group(4), parts[3], errors);
            } else {
                errors.add("Имя файла не соответствует формату индивидуальной тренировки: " + parts[3]);
            }
        }

        return ArchivePath.builder()
            .type(ArchiveType.INDI)
            .relPath(relPath)
            .sessionName("indi-" + parts[2])
            .year(year)
            .month(month)
            .matchDate(matchDate)
            .club(null)
            .tournament(false)
            .players(players)
            .validationError(errors.isEmpty() ? null : String.join("; ", errors))
            .build();
    }

    private ArchivePath invalid(String relPath, ArchiveType type, List<String> errors) {
        return ArchivePath.builder()
            .type(type)
            .relPath(relPath)
            .players(List.of())
            .validationError(String.join("; ", errors))
            .build();
    }

    private Integer parseYear(String value, List<String> errors) {
        if (!YEAR.matcher(value).matches()) {
            errors.add("Некорректный год: " + value);
            return null;
        }
        return Integer.parseInt(value);
    }

    private Integer parseMonthDir(String value, Integer year, List<String> errors) {
        Matcher monthDir = MONTH_DIR.matcher(value);
        if (!monthDir.matches()) {
            errors.add("Некорректное имя папки месяца: " + value);
            return null;
        }
        int folderYear = Integer.parseInt(monthDir.group(1));
        int folderMonth = Integer.parseInt(monthDir.group(2));
        if (year != null && folderYear != year) {
            errors.add("Год в папке месяца (" + folderYear + ") не совпадает с годом (" + year + ")");
        }
        if (folderMonth < 1 || folderMonth > 12) {
            errors.add("Некорректный месяц: " + value);
            return null;
        }
        return folderMonth;
    }

    private void validateDateParts(
        int dateYear,
        int dateMonth,
        int dateDay,
        Integer folderYear,
        Integer folderMonth,
        String source,
        List<String> errors
    ) {
        if (folderYear != null && dateYear != folderYear) {
            errors.add("Год в дате (" + dateYear + ") не совпадает с годом (" + folderYear + "): " + source);
        }
        if (folderMonth != null && dateMonth != folderMonth) {
            errors.add("Месяц в дате (" + dateMonth + ") не совпадает с месяцем (" + folderMonth + "): " + source);
        }
    }

    private String stripExtension(String fileName, List<String> errors) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            errors.add("Отсутствует расширение файла: " + fileName);
            return null;
        }
        String extension = fileName.substring(dot + 1);
        if (!extension.equalsIgnoreCase("mp4")) {
            errors.add("Неожиданное расширение файла: " + extension);
        }
        return fileName.substring(0, dot);
    }

    private List<String> parseGamePlayers(String fileName, String dateDir, List<String> errors) {
        String base = stripExtension(fileName, errors);
        if (base == null) {
            return List.of();
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

    private List<String> parseIndiPlayers(String playersPart, String fileName, List<String> errors) {
        if (playersPart == null) {
            return List.of(DEFAULT_INDI_PLAYER);
        }
        List<String> players = new ArrayList<>();
        for (String name : playersPart.split("-")) {
            if (name.isBlank()) {
                errors.add("Пустое имя игрока в: " + fileName);
                continue;
            }
            players.add(name);
        }
        if (players.size() != 2) {
            errors.add("Ожидается ровно 2 игрока, получено: " + players.size());
        }
        return players;
    }
}
