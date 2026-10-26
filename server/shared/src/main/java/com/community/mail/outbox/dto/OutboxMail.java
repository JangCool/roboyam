package com.community.mail.outbox.dto;

import com.community.notificationtemplate.catalog.RequiredMail;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 메일 대기열에 넣을 메일 한 통 — 받는 회원·종류·문구 변수. <b>받는 주소는 넣지 않는다</b>(보낼 때 {@code users.email} 을 읽는다).
 *
 * @param dedupeKey 같은 일로 두 번 넣지 않게 하는 열쇠(예: {@code GROUPBUY_JOINED:<참여 ID>}) — 1~200자, 대기열 안에서 유일
 * @param kind      필수 안내 메일 종류(문구·메일 템플릿은 알림 문구 엔진이 고른다 — 결정 11)
 * @param variables 문구 변수({@code {{이름}}} → 값). 글·수만 넣는다. 보낸 뒤에는 남기지 않는다
 */
public record OutboxMail(String dedupeKey, long userId, RequiredMail kind, Map<String, Object> variables) {

    public static final int DEDUPE_KEY_MAX = 200;

    public OutboxMail {
        if (dedupeKey == null || dedupeKey.isBlank() || dedupeKey.length() > DEDUPE_KEY_MAX) {
            throw new IllegalArgumentException("메일 대기열의 중복 방지 열쇠는 1~200자다");
        }
        if (kind == null) {
            throw new IllegalArgumentException("메일 종류가 없다");
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        if (variables != null) {
            variables.forEach((name, value) -> {
                if (name != null && value != null) { // 값이 없는 변수는 넣지 않는다(엔진이 발송에서 지운다)
                    kept.put(name, value);
                }
            });
        }
        variables = Collections.unmodifiableMap(kept);
    }
}
