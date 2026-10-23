package com.community.notificationtemplate.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 문구의 대상 하나 — 운영자 이벤트 · 회원 이벤트 · 필수 안내 메일. 코드가 정한다({@link MessageTargetCatalog}). 문구 하나 = 대상 × 채널 × 언어.
 *
 * @param area           이벤트의 영역({@code security}·{@code system}·{@code member}·{@code content} … — 알림 화면과 같은 값). 필수 안내 메일은 null
 * @param severity       운영자 이벤트의 기본 심각도(Slack · Mattermost 템플릿의 색 막대) — 그 밖은 null
 * @param security       보안 메일(필수 안내 메일 중 인증·초대·임시 비밀번호·승인번호) — 필수 변수를 지우면 저장 안 됨
 * @param channels       나가는 채널(차례 {@link MessageChannel})
 * @param inApp          기본 앱 알림 문구를 번들에서 만드는 방법(이벤트만 — 필수 안내 메일은 null)
 * @param samplePath     미리보기의 {@code link} 견본 경로(받는 사람의 사이트 주소 뒤) — 없으면 null
 */
public record MessageTarget(MessageTargetType type, String code, MessageAudience audience, String area, MessageSeverity severity, String nameKey,
                            String descriptionKey, boolean security, List<MessageChannelDef> channels, InAppText inApp, String samplePath) {

    public MessageTarget {
        channels = List.copyOf(channels);
    }

    /**
     * 기본 앱 알림 문구의 출처 — 번들 문구의 {@code {0}} {@code {1}} 자리를 이름 붙은 변수로 바꿔 쓴다(지금 알림 문구 그대로).
     *
     * @param titleKey    제목 번들 키
     * @param bodyKey     본문 번들 키(없으면 null — {@code bodyLiteral})
     * @param bodyLiteral 번들에 없는 본문(회원 알림 — 언어와 상관없는 변수 한 줄, 예: {@code {{excerpt}}})
     * @param argNames    번들 인자 자리 차례의 변수 이름(쓰지 않는 자리는 null)
     */
    public record InAppText(String titleKey, String bodyKey, String bodyLiteral, List<String> argNames) {

        public InAppText {
            argNames = argNames == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argNames));
        }
    }

    public Optional<MessageChannelDef> channel(MessageChannel channel) {
        return channels.stream().filter(def -> def.channel() == channel).findFirst();
    }

    public boolean has(MessageChannel channel) {
        return channel(channel).isPresent();
    }
}
