package com.community.board.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import java.util.Set;

/**
 * 게시판 관리 입력 검사의 작은 규칙들 — 칸 정의({@link BoardFields})로 값 하나를 본다. 위반은 칸 경로 → 문구 키로 모아 서비스가 {@code ValidationException} 으로 던진다.
 * 문구는 admin-web {@code util-board-messages.ts} 의 칸 문구와 같은 뜻이다.
 */
final class BoardRules {

    private BoardRules() {
    }

    /**
     * 칸 정의가 있는 값들을 검사한다.
     *
     * @param values     칸 이름 → 값
     * @param specs      칸 정의(정의가 없는 칸은 건너뛴다 — 호출한 쪽이 따로 본다)
     * @param pathPrefix 칸 경로 앞에 붙일 글자(게시판 정책은 {@code policy.})
     * @param nullable   null 을 「기본값 따름」으로 받는가(게시판 정책). 아니면 null 은 빈 칸 오류
     * @param gradeKeys  등급 목록의 키
     * @param allowGuest 최소 자격에 GUEST 를 받는가
     */
    static void check(Map<String, Object> values, Map<String, BoardFields.Spec> specs, String pathPrefix, boolean nullable, Set<String> gradeKeys,
                      boolean allowGuest, Map<String, MessageRef> out) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            BoardFields.Spec spec = specs.get(entry.getKey());
            if (spec == null) {
                continue;
            }
            String path = pathPrefix + entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                if (!nullable && spec.kind() != BoardFields.Kind.LABEL) { // 단추 이름은 기본 설정에서도 비울 수 있다(= 화면 기본 이름)
                    out.put(path, new MessageRef("board.field.required"));
                }
                continue;
            }
            switch (spec.kind()) {
                case NUMBER -> {
                    if (!spec.accepts(value)) {
                        out.put(path, new MessageRef("board.field.range", String.valueOf(spec.min()), String.valueOf(spec.max())));
                    }
                }
                case BOOL, CHOICE, MULTI -> {
                    if (!spec.accepts(value)) {
                        out.put(path, new MessageRef("board.field.choice"));
                    }
                }
                case WRITE_MIN, COMMENT_MIN -> {
                    String level = String.valueOf(value);
                    if (BoardFields.GUEST.equals(level) && !allowGuest) {
                        out.put(path, new MessageRef(nullable ? "board.policy.guestNotAllowed" : "board.field.level"));
                    } else if (!validLevel(level, spec.kind() == BoardFields.Kind.COMMENT_MIN, gradeKeys)) {
                        out.put(path, new MessageRef("board.field.level"));
                    }
                }
                case GRADE -> {
                    if (!gradeKeys.contains(String.valueOf(value))) {
                        out.put(path, new MessageRef("board.field.grade"));
                    }
                }
                case LABEL -> {
                    if (!spec.accepts(value)) {
                        out.put(path, new MessageRef("board.field.label", String.valueOf(spec.max())));
                    }
                }
            }
        }
    }

    /** 최소 자격 값 — GUEST · STAFF · 등급 키, 댓글은 OFF 도 */
    static boolean validLevel(String value, boolean comment, Set<String> gradeKeys) {
        return BoardFields.GUEST.equals(value) || BoardFields.STAFF.equals(value) || (comment && BoardFields.OFF.equals(value)) || gradeKeys.contains(value);
    }

    /** 앞뒤 공백을 자른 글자(null 은 빈 글자) */
    static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    /** 앞뒤 공백을 자르고 비면 null */
    static String stripToNull(String value) {
        String stripped = strip(value);
        return stripped.isEmpty() ? null : stripped;
    }

    /** 줄바꿈 외 제어 문자가 있는가 */
    static boolean hasControl(String value) {
        return value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t');
    }

    /** 안내문 — 줄바꿈을 {@code \n} 하나로, 보이는 글자가 없으면 null */
    static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.isBlank() ? null : normalized;
    }

    /** 보이는 글자 수(코드 포인트) */
    static int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /** 문자열 ID → long. 형식이 틀리면 null */
    static Long parseId(String raw) {
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
}
