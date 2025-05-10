package com.responser.utils;

import com.responser.config.ConfigManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;


public class MentionChecker {
    private static final Map<String, String> TRANSLIT_MAP = new LinkedHashMap<>();

    static {
        // Сначала двузвучные и трёхзвучные («shch», «yo» и т.п.), чтобы корректно обрабатывать «ch», «sh»...
        TRANSLIT_MAP.put("shch", "щ");
        TRANSLIT_MAP.put("ay", "э");
        TRANSLIT_MAP.put("yo", "ё");
        TRANSLIT_MAP.put("zh", "ж");
        TRANSLIT_MAP.put("kh", "х");
        TRANSLIT_MAP.put("ts", "ц");
        TRANSLIT_MAP.put("ch", "ч");
        TRANSLIT_MAP.put("sh", "ш");
        TRANSLIT_MAP.put("yu", "ю");
        TRANSLIT_MAP.put("ya", "я");
        // Однозвучные
        TRANSLIT_MAP.put("a", "а");
        TRANSLIT_MAP.put("b", "б");
        TRANSLIT_MAP.put("v", "в");
        TRANSLIT_MAP.put("g", "г");
        TRANSLIT_MAP.put("d", "д");
        TRANSLIT_MAP.put("e", "е");
        TRANSLIT_MAP.put("z", "з");
        TRANSLIT_MAP.put("i", "и");
        TRANSLIT_MAP.put("y", "й");
        TRANSLIT_MAP.put("k", "к");
        TRANSLIT_MAP.put("l", "л");
        TRANSLIT_MAP.put("m", "м");
        TRANSLIT_MAP.put("n", "н");
        TRANSLIT_MAP.put("o", "о");
        TRANSLIT_MAP.put("p", "п");
        TRANSLIT_MAP.put("r", "р");
        TRANSLIT_MAP.put("s", "с");
        TRANSLIT_MAP.put("t", "т");
        TRANSLIT_MAP.put("u", "у");
        TRANSLIT_MAP.put("f", "ф");
        TRANSLIT_MAP.put("h", "х");
        TRANSLIT_MAP.put("’", "ь");
        TRANSLIT_MAP.put("'", "ь");
    }

    /**
     * Примитивная транслитерация English → русский
     */
    public static String transliterateToCyrillic(String latin) {
        StringBuilder rus = new StringBuilder();
        String lower = latin.toLowerCase();
        int i = 0, len = lower.length();

        while (i < len) {
            boolean matched = false;
            // Попытаемся найти максимальный (длиной до 4) префикс в карте
            for (int l = 4; l > 0; l--) {
                if (i + l <= len) {
                    String chunk = lower.substring(i, i + l);
                    String repl = TRANSLIT_MAP.get(chunk);
                    if (repl != null) {
                        rus.append(repl);
                        i += l;
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                // Если не нашли — просто копируем символ «как есть»
                rus.append(lower.charAt(i));
                i++;
            }
        }
        return rus.toString();
    }

    public static String checkMention(String text, String name) {
        String lowerText = text.toLowerCase();
        String lowerName = name.toLowerCase();

        // Генерируем все вариации
        String englishNoDigits = lowerName.replaceAll("\\d", "");
        String nameRus = transliterateToCyrillic(lowerName);
        String russianNoDigits = nameRus.replaceAll("\\d", "");

        // Используем Set, чтобы избежать дубликатов, и сохраняем порядок добавления
        Set<String> variations = new LinkedHashSet<>();
        variations.add(lowerName);
        variations.add(nameRus);
        variations.add(englishNoDigits);
        variations.add(russianNoDigits);

        ConfigManager cfg = ConfigManager.getInstance();
        String customMentions = cfg.get("customMentions");
        if (customMentions != null && !customMentions.isEmpty()) {
            String[] customArray = customMentions.split(",");
            for (String custom : customArray) {
                variations.add(custom.strip().toLowerCase());
            }
        }

        // Удаляем пустые строки
        List<String> toCheck = variations.stream()
                .filter(var -> var != null && !var.isEmpty())
                .collect(Collectors.toList());

        System.out.println(toCheck);

        // Ищем первую подходящую вариацию
        for (String variant : toCheck) {
            if (lowerText.contains(variant)) {
                return variant;
            }
        }
        return null;
    }
}