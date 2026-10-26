package com.community.group.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 소모임 분류 이름 규칙 — 앞뒤 공백을 다듬고 안쪽 공백을 하나로 줄인 뒤 1~{@value #NAME_MAX}자, {@code <}·{@code >}·줄바꿈·제어 문자 금지.
 * 같은 이름 막기는 {@link #key}(공백을 모두 빼고 소문자) — DB {@code ux_group_categories_name_key}.
 */
public final class GroupCategoryNames {

    static final int NAME_MAX = 20;

    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private GroupCategoryNames() {
    }

    /** 다듬은 이름(없으면 빈 글자) */
    static String normalize(String raw) {
        return raw == null ? "" : SPACES.matcher(raw.strip()).replaceAll(" ");
    }

    /** 같은 이름 비교 키 */
    static String key(String name) {
        return SPACES.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** 이름 문제 — 줄바꿈·제어 문자는 공백으로 줄이기 전의 값에서 본다 */
    static Optional<MessageRef> problem(String raw) {
        String stripped = raw == null ? "" : raw.strip();
        if (stripped.isEmpty()) {
            return Optional.of(new MessageRef("group.category.name.required"));
        }
        String name = normalize(stripped);
        if (name.codePointCount(0, name.length()) > NAME_MAX || hasForbidden(stripped)) {
            return Optional.of(new MessageRef("group.category.name.invalid", String.valueOf(NAME_MAX)));
        }
        return Optional.empty();
    }

    /** 문자열 ID → long. 형식이 틀리면 null */
    public static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasForbidden(String value) {
        return value.codePoints().anyMatch(c -> c == '<' || c == '>' || Character.isISOControl(c) || c == 0x2028 || c == 0x2029);
    }
}
