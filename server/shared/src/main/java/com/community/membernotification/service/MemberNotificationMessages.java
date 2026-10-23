package com.community.membernotification.service;

import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageCondition;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageVariable;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.MessageValues;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import com.community.notificationtemplate.engine.RenderedInApp;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 회원 알림(회원 이벤트)의 문구 — 알림함(api, <b>읽을 때</b> 읽는 사람 언어)과 회원 알림 메일(보낼 때 받는 사람 언어)이 같은 길로 알림 문구 엔진에 요청한다.
 *
 * <p>알림 행은 예전처럼 사실(종류 · 일으킨 사람 · 글 · 댓글 · 덧붙임)만 저장하고 문구는 저장하지 않는다. 부르는 쪽이 그 사실을 {@link Facts} 로 모으면 이 클래스가 이벤트 변수
 * ({@code actorName}·{@code postTitle}·{@code excerpt}·{@code otherCount}·{@code amount}·{@code reason})와 조건 값({@code grouped}·{@code hideReason})으로 바꾼다.
 *
 * <p><b>기본 문구의 상황별 제목</b>: 코드에 있던 특수 문구 — 묶인 댓글 「…님 외 N개의 댓글」, 권리 침해 임시 숨김 — 는 기본 문구가 골라졌을 때만 지금처럼 번들 문구로 제목을 바꾼다
 * (운영자가 사용자 문구·규칙을 고르면 그 문구가 그대로 쓰인다 — 계약 미확정 6 의 가정).
 */
@Component
public class MemberNotificationMessages {

    private final NotificationMessageRenderer renderer;
    private final Messages messages;

    public MemberNotificationMessages(NotificationMessageRenderer renderer, Messages messages) {
        this.renderer = renderer;
        this.messages = messages;
    }

    /**
     * 회원 알림 한 건의 사실 — 문구 변수의 원천.
     *
     * @param actorName  일으킨 사람(댓글·답글 — 회원 닉네임 · 비회원 이름 · 「탈퇴한 회원」)
     * @param postTitle  글 제목(댓글 — 볼 수 없는 글이면 null)
     * @param excerpt    댓글·답글 앞부분(볼 수 없으면 null)
     * @param groupCount 묶인 댓글 수(1 = 하나)
     * @param amount     회수된 포인트
     * @param pointsKind 회수 종류({@code ADMIN_ADJUST})
     * @param note       회원에게 공개되는 회수 사유(없으면 null)
     * @param temporary  권리 침해 임시 숨김인가(기간이 있는 숨김)
     * @param snapshot   숨길 때 남긴 글 제목·댓글 앞부분
     */
    public record Facts(MemberNotificationType type, String actorName, String postTitle, String excerpt, int groupCount, long amount, String pointsKind, String note,
                        boolean temporary, String snapshot) {

        public static Facts comment(MemberNotificationType type, String actorName, String postTitle, String excerpt, int groupCount) {
            return new Facts(type, actorName, postTitle, excerpt, groupCount, 0, null, null, false, null);
        }

        public static Facts points(String kind, long amount, String note) {
            return new Facts(MemberNotificationType.POINTS_REVOKED, null, null, null, 1, amount, kind, note, false, null);
        }

        public static Facts hidden(MemberNotificationType type, String snapshot, boolean temporary) {
            return new Facts(type, null, null, null, 1, 0, null, null, temporary, snapshot);
        }
    }

    /** 알림함 한 줄 — {@code locale} 은 읽는 사람 언어. 본문이 비면 빈 글자 */
    public RenderedInApp inApp(Facts facts, Locale locale, String recipientName) {
        MessageRenderRequest request = request(facts, MessageChannel.IN_APP, locale).variable(MessageVariable.RECIPIENT_NAME, recipientName).build();
        RenderedInApp rendered = renderer.renderInApp(request);
        String variant = builtinVariantTitle(facts, renderer.pick(request));
        return variant == null ? rendered : new RenderedInApp(rendered.messageId(), variant, rendered.body());
    }

    /**
     * 회원 알림 메일 한 통 — {@code language} 는 받는 사람의 표시 언어(없으면 사이트 표시 기본 언어). 메일의 {@code title}·{@code body} 는 같은 언어로 그린 앱 알림 문구다.
     *
     * @param link 사이트 안 절대 주소(없으면 null — 버튼 없이)
     */
    public RenderedEmail email(Facts facts, String language, String recipientName, String link) {
        Locale locale = localeOf(language);
        RenderedInApp inApp = inApp(facts, locale, recipientName);
        return renderer.renderEmail(request(facts, MessageChannel.EMAIL, locale)
                .variable(MessageVariable.RECIPIENT_NAME, recipientName)
                .variable(MessageVariable.TITLE, inApp.title())
                .variable(MessageVariable.BODY, inApp.body())
                .variable(MessageVariable.LINK, link)
                .build());
    }

    /** 사이트 공개 주소(없으면 null) — 메일 링크의 앞 */
    public String siteUrl() {
        return renderer.site().publicUrl();
    }

    private MessageRenderRequest.Builder request(Facts facts, MessageChannel channel, Locale locale) {
        MessageRenderRequest.Builder builder = MessageRenderRequest.memberEvent(MemberEvent.of(facts.type()), channel).locale(locale);
        switch (facts.type()) {
            case COMMENT -> builder.variable("actorName", facts.actorName()).variable("postTitle", facts.postTitle()).variable("excerpt", facts.excerpt())
                    .variable("otherCount", Math.max(0, facts.groupCount() - 1))
                    .condition(MessageCondition.GROUPED, facts.groupCount() > 1 ? MessageCondition.GROUPED_VALUE : MessageCondition.SINGLE);
            case REPLY -> builder.variable("actorName", facts.actorName()).variable("excerpt", facts.excerpt());
            case POINTS_REVOKED -> builder.variable("amount", String.format("%,d", facts.amount())).variable("reason", pointsReason(facts, locale));
            case POST_HIDDEN -> builder.variable("postTitle", facts.snapshot())
                    .condition(MessageCondition.HIDE_REASON, facts.temporary() ? MessageCondition.RIGHTS : MessageCondition.REPORTS);
            case COMMENT_HIDDEN -> builder.variable("excerpt", facts.snapshot())
                    .condition(MessageCondition.HIDE_REASON, facts.temporary() ? MessageCondition.RIGHTS : MessageCondition.REPORTS);
        }
        return builder;
    }

    /** 포인트 회수 까닭 한 줄(번들 — 사유가 있으면 「운영 조정: 사유」) */
    private String pointsReason(Facts facts, Locale locale) {
        String kind = facts.pointsKind() == null ? "ADMIN_ADJUST" : facts.pointsKind();
        return facts.note() == null || facts.note().isBlank()
                ? messages.get(new MessageRef("member.notification.POINTS_REVOKED.body." + kind), locale)
                : messages.get(new MessageRef("member.notification.POINTS_REVOKED.body." + kind + ".note", facts.note()), locale);
    }

    /** 기본 문구가 골라졌을 때만 — 묶인 댓글 · 임시 숨김의 지금 번들 제목. 아니면 null */
    private String builtinVariantTitle(Facts facts, MessageValues picked) {
        if (picked == null || !picked.builtin()) {
            return null;
        }
        Locale locale = MessageLocales.locale(picked.locale());
        if (facts.type() == MemberNotificationType.COMMENT && facts.groupCount() > 1) {
            return messages.get(new MessageRef("member.notification.COMMENT.grouped", nz(facts.actorName()), String.valueOf(facts.groupCount() - 1)), locale);
        }
        if ((facts.type() == MemberNotificationType.POST_HIDDEN || facts.type() == MemberNotificationType.COMMENT_HIDDEN) && facts.temporary()) {
            return messages.get(new MessageRef("member.notification." + facts.type().name() + ".temporary"), locale);
        }
        return null;
    }

    /** 받는 회원의 언어 — 회원 표시 설정의 언어(ko·en), 없으면 사이트 표시 기본 언어, 그것도 없으면 ko */
    public Locale localeOf(String language) {
        String own = MessageLocales.of(language);
        if (own != null) {
            return MessageLocales.locale(own);
        }
        String site = MessageLocales.of(renderer.site().language());
        return MessageLocales.locale(site != null ? site : MessageLocales.KO);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
