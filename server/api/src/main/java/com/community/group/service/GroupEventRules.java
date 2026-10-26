package com.community.group.service;

import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.request.GroupEventRequest;
import com.community.group.entity.GroupEventMode;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 소모임 일정 입력 규칙 — 상태가 없고 「지금」을 받는 순수 함수(community api-groups.md 9절 · 원본 {@code GroupEventRules}). 틀린 칸은 {@code errors} 에 모아 한꺼번에 알린다.
 * 글자 수 한도는 원본 설정 기본값(제목 60 · 설명 1000 · 장소 100 · 취소 사유 100 — DB 칸 길이와 같다), 시작·길이 한도는 관리자 앱 운영 한도({@link GroupRuleValues.Policy}).
 */
public final class GroupEventRules {

    public static final int TITLE_MAX = 60;
    public static final int DESCRIPTION_MAX = 1000;
    public static final int PLACE_MAX = 100;
    public static final int CANCEL_REASON_MAX = 100;

    private GroupEventRules() {
    }

    /** 검사를 통과한 일정 내용 — 온라인이면 {@code place} 는 null */
    public record Parsed(String title, String description, Instant startsAt, Instant endsAt, GroupEventMode mode, String place, boolean offlineAck) {
    }

    /**
     * 입력을 읽고 검사한다.
     *
     * @param policy         지금 적용되는 운영 한도(시작 최대·길이 최대)
     * @param now            지금
     * @param lockedStartsAt 이미 시작한 일정을 고칠 때 그 일정의 시작 시각 — 이 값과 같아야 하고 「지금보다 뒤」 검사는 하지 않는다. 아니면 null
     * @param errors         틀린 칸을 모은다
     * @return 읽은 내용(틀린 칸이 있어도 읽은 만큼 채운다 — 부르는 쪽이 {@code errors} 가 비었는지 본다)
     */
    public static Parsed parse(GroupEventRequest in, GroupRuleValues.Policy policy, Instant now, Instant lockedStartsAt, Map<String, MessageRef> errors) {
        String title = in == null || in.title() == null ? "" : in.title().replace("\u0000", "").replaceAll("\\s+", " ").strip();
        if (title.isEmpty()) {
            errors.put("title", new MessageRef("group.event.title.required"));
        } else if (length(title) > TITLE_MAX) {
            errors.put("title", new MessageRef("group.event.title.length", String.valueOf(TITLE_MAX)));
        }

        String description = in == null || in.description() == null ? null : in.description().replace("\u0000", "").replace("\r\n", "\n").strip();
        if (description != null && description.isEmpty()) {
            description = null;
        }
        if (description != null && length(description) > DESCRIPTION_MAX) {
            errors.put("description", new MessageRef("group.event.description.length", String.valueOf(DESCRIPTION_MAX)));
        }

        GroupEventMode mode = GroupEventMode.of(in == null ? null : in.mode()).orElse(null);
        if (mode == null) {
            errors.put("mode", new MessageRef("group.event.mode.invalid"));
        }

        String place = in == null || in.place() == null ? null : in.place().replace("\u0000", "").replaceAll("\\s+", " ").strip();
        if (place != null && place.isEmpty()) {
            place = null;
        }
        if (mode == GroupEventMode.OFFLINE) {
            if (place == null) {
                errors.put("place", new MessageRef("group.event.place.required"));
            } else if (length(place) > PLACE_MAX) {
                errors.put("place", new MessageRef("group.event.place.length", String.valueOf(PLACE_MAX)));
            }
        } else {
            place = null;
        }

        Instant startsAt = parseTime(in == null ? null : in.startsAt());
        if (startsAt == null) {
            errors.put("startsAt", new MessageRef("group.event.startsAt.invalid"));
        } else if (lockedStartsAt != null) {
            if (!startsAt.equals(lockedStartsAt)) {
                errors.put("startsAt", new MessageRef("group.event.startsAt.locked"));
            }
        } else if (!startsAt.isAfter(now)) {
            errors.put("startsAt", new MessageRef("group.event.startsAt.past"));
        } else if (startsAt.isAfter(now.plus(Duration.ofDays(policy.eventMaxDaysAhead())))) {
            errors.put("startsAt", new MessageRef("group.event.startsAt.tooFar", String.valueOf(policy.eventMaxDaysAhead())));
        }

        Instant endsAt = null;
        if (in != null && in.endsAt() != null && !in.endsAt().isBlank()) {
            endsAt = parseTime(in.endsAt());
            if (endsAt == null) {
                errors.put("endsAt", new MessageRef("group.event.endsAt.invalid"));
            } else if (startsAt != null && !endsAt.isAfter(startsAt)) {
                errors.put("endsAt", new MessageRef("group.event.endsAt.beforeStart"));
            } else if (startsAt != null && endsAt.isAfter(startsAt.plus(Duration.ofDays(policy.eventMaxLengthDays())))) {
                errors.put("endsAt", new MessageRef("group.event.endsAt.tooLong", String.valueOf(policy.eventMaxLengthDays())));
            } else if (lockedStartsAt != null && !endsAt.isAfter(now)) {
                errors.put("endsAt", new MessageRef("group.event.endsAt.pastOngoing"));
            }
        }
        return new Parsed(title, description, startsAt, endsAt, mode, place, in != null && Boolean.TRUE.equals(in.offlineAck()));
    }

    /** 취소 사유 — 비면 null. 길면 {@code errors.reason} */
    public static String cancelReason(String raw, Map<String, MessageRef> errors) {
        String reason = raw == null ? null : raw.replace("\u0000", "").strip();
        if (reason == null || reason.isEmpty()) {
            return null;
        }
        if (length(reason) > CANCEL_REASON_MAX) {
            errors.put("reason", new MessageRef("group.event.reason.length", String.valueOf(CANCEL_REASON_MAX)));
        }
        return reason;
    }

    /** ISO-8601 시각(오프셋 포함, 예 {@code 2026-10-14T11:00:00Z})을 읽는다. 틀리면 null */
    static Instant parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.strip()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
