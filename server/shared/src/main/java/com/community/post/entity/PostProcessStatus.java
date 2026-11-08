package com.community.post.entity;

import com.community.board.dto.EffectiveBoard;
import java.util.Arrays;
import java.util.Optional;

/**
 * 글의 처리 상태(V166 {@code posts.process_status}) — 처리 상태를 쓰는 게시판({@link EffectiveBoard#statusEnabled()})의 글만 뜻이 있다.
 * 이름은 한 번 정하면 바꾸지 않는다(행·알림 덧붙임이 이 값으로 가리킨다). 화면 이름은 번들 {@code post.processStatus.<이름>}.
 */
public enum PostProcessStatus {

    /** 접수 — 상태를 쓰는 게시판의 새 글, 값이 없는 글도 이렇게 보인다 */
    RECEIVED,
    /** 확인 중 */
    CHECKING,
    /** 진행 중 */
    IN_PROGRESS,
    /** 완료 */
    DONE,
    /** 보류 */
    ON_HOLD,
    /** 반려 */
    REJECTED,
    /** 중복 */
    DUPLICATE;

    /** 대소문자 무시, 앞뒤 공백 무시 — 모르는 값·빈 값이면 비어 있다 */
    public static Optional<PostProcessStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String name = raw.strip().toUpperCase(java.util.Locale.ROOT);
        return Arrays.stream(values()).filter(s -> s.name().equals(name)).findFirst();
    }

    /**
     * 응답의 {@code processStatus} — 게시판이 상태를 쓰지 않으면 null, 쓰면 글 값(없으면 {@link #RECEIVED}).
     *
     * @param stored 글의 {@code process_status}(없으면 null)
     */
    public static String effective(EffectiveBoard board, String stored) {
        if (board == null || !board.statusEnabled()) {
            return null;
        }
        return parse(stored).orElse(RECEIVED).name();
    }

    /** 응답의 {@code processStatusAt} — 게시판이 상태를 쓰지 않으면 null */
    public static java.time.Instant effectiveAt(EffectiveBoard board, java.time.Instant stored) {
        return board == null || !board.statusEnabled() ? null : stored;
    }
}
