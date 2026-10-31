package com.community.membernotification.service;

import com.community.group.entity.GroupManagerPermission;
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
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    /** 운영자가 숨긴 글·댓글의 숨김 알림 — {@link Facts#result()} 와 알림 덧붙임 {@code by} 의 값 */
    public static final String BY_STAFF = "STAFF";
    /** 신고 처리 결과 — 조치됨(숨김·삭제·자동 숨김 확정) */
    public static final String REPORT_ACTIONED = "ACTIONED";
    /** 신고 처리 결과 — 위반이 확인되지 않음(무시) */
    public static final String REPORT_NO_VIOLATION = "NO_VIOLATION";

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
     * @param result     닉네임 그림 검토 결과({@code APPROVED}·{@code REJECTED}·{@code REVOKED}) · 의견 고정 요청 결과 · 운영자 숨김({@link #BY_STAFF} — 글·댓글 숨김 알림)
     *                   · 신고 처리 결과({@link #REPORT_ACTIONED}·{@link #REPORT_NO_VIOLATION}) — 그 밖 null. 반려·해제·거절·운영자 숨김·운영자 삭제 사유는 {@code note}
     * @param reasonCode 운영자 조치 사유 코드(신고 사유 체계 — 관리자 신고 관리의 숨김·삭제, 없으면 null). 있으면 사유 변수는 「코드 이름: note」
     * @param targetType 신고 처리 결과의 대상 종류({@code POST}·{@code COMMENT}·{@code GROUP}) — 그 밖 null
     * @param restriction 걸린 기능 제한(기능 제한 알림만 — 운영 메모 없음)
     * @param expiring    소멸 예정(소멸 예정 알림만 — 양은 {@code amount})
     */
    public record Facts(MemberNotificationType type, String actorName, String postTitle, String excerpt, int groupCount, long amount, String pointsKind, String note,
                        boolean temporary, String snapshot, String result, String reasonCode, String targetType, Restriction restriction, Expiring expiring) {

        /** 소멸 예정 알림 밖의 사실 — 소멸 예정은 null */
        public Facts(MemberNotificationType type, String actorName, String postTitle, String excerpt, int groupCount, long amount, String pointsKind, String note,
                     boolean temporary, String snapshot, String result, String reasonCode, String targetType, Restriction restriction) {
            this(type, actorName, postTitle, excerpt, groupCount, amount, pointsKind, note, temporary, snapshot, result, reasonCode, targetType, restriction, null);
        }

        /**
         * 포인트 소멸 예정(commerce.md 14 — 소멸 예정 알림 배치).
         *
         * @param date 가장 가까운 소멸일(한국 날짜)
         * @param days 알림 기간(일 — {@code point_settings.expiry_notice_days})
         */
        public record Expiring(LocalDate date, int days) {
        }

        /** 소멸 예정 알림의 사실 — {@code amount} 는 알림 기간 안 소멸 예정 합 */
        public static Facts expiring(long amount, LocalDate date, int days) {
            return new Facts(MemberNotificationType.POINTS_EXPIRING, null, null, null, 1, amount, null, null, false, null, null, null, null, null,
                    new Expiring(date, days));
        }

        /** 기능 제한 알림 밖의 사실 — 제한은 null */
        public Facts(MemberNotificationType type, String actorName, String postTitle, String excerpt, int groupCount, long amount, String pointsKind, String note,
                     boolean temporary, String snapshot, String result, String reasonCode, String targetType) {
            this(type, actorName, postTitle, excerpt, groupCount, amount, pointsKind, note, temporary, snapshot, result, reasonCode, targetType, null);
        }

        /**
         * 기능 제한 알림의 사실(commerce.md 12-3 — 종류·끝·공개 사유).
         *
         * @param kind     {@code POINT_FREEZE} · {@code GROUP_BUY_BAN} · {@code CHARGE_BAN}
         * @param until    끝나는 순간(무기한이면 null)
         * @param reason   회원에게 공개하는 사유(자동 부여면 null — {@code autoRule} 의 번들 문구를 쓴다)
         * @param autoRule 자동 부여 규칙(운영자 부여면 null)
         */
        public record Restriction(String kind, Instant until, String reason, String autoRule) {
        }

        public static Facts restricted(String kind, Instant until, String reason, String autoRule) {
            return new Facts(MemberNotificationType.FEATURE_RESTRICTED, null, null, null, 1, 0, null, null, false, null, null, null, null,
                    new Restriction(kind, until, reason, autoRule));
        }

        /** 검토 결과 칸이 없는 예전 모양 — 결과는 null */
        public Facts(MemberNotificationType type, String actorName, String postTitle, String excerpt, int groupCount, long amount, String pointsKind, String note,
                     boolean temporary, String snapshot) {
            this(type, actorName, postTitle, excerpt, groupCount, amount, pointsKind, note, temporary, snapshot, null, null, null);
        }

        /** 사유 코드·대상 종류 칸이 없는 모양 — 둘 다 null */
        public Facts(MemberNotificationType type, String actorName, String postTitle, String excerpt, int groupCount, long amount, String pointsKind, String note,
                     boolean temporary, String snapshot, String result) {
            this(type, actorName, postTitle, excerpt, groupCount, amount, pointsKind, note, temporary, snapshot, result, null, null);
        }

        /** 쪽지 받음 — 보낸 회원의 닉네임(메일은 보낼 때 지금 닉네임으로 채운다) */
        public static Facts dm(String actorName) {
            return new Facts(MemberNotificationType.DM, actorName, null, null, 1, 0, null, null, false, null, null);
        }

        /** 포인트 상점 환불 신청 수락 — 품목 이름({@code snapshot})·돌려준 포인트({@code amount}) */
        public static Facts refundApproved(String itemName, long points) {
            return new Facts(MemberNotificationType.POINT_SHOP_REFUND_APPROVED, null, null, null, 1, points, null, null, false, itemName, null);
        }

        /** 포인트 상점 환불 신청 거절 — 품목 이름({@code snapshot})·사유({@code note}) */
        public static Facts refundRejected(String itemName, String reason) {
            return new Facts(MemberNotificationType.POINT_SHOP_REFUND_REJECTED, null, null, null, 1, 0, null, reason, false, itemName, null);
        }

        /**
         * 소모임 알림(GROUP_*) — 소모임 이름({@code snapshot})·사유({@code note} — 반려·거절·비활성)·가입 신청한 회원 닉네임({@code actorName} — 가입 신청만).
         */
        public static Facts group(MemberNotificationType type, String groupName, String reason, String actorName) {
            return new Facts(type, actorName, null, null, 1, 0, null, reason, false, groupName, null);
        }

        /**
         * 내 공개 소모임 글이 비공개로 되돌려졌다(V91) — 소모임 이름({@code snapshot})·글 제목({@code postTitle})·처리자 역할({@code result} — LEADER · MANAGER).
         */
        public static Facts groupPostUnpublished(String groupName, String postTitle, String actorRole) {
            return new Facts(MemberNotificationType.GROUP_POST_UNPUBLISHED, null, postTitle, null, 1, 0, null, null, false, groupName, actorRole);
        }

        /**
         * 소모임 매니저 알림(V92 — {@code GROUP_MANAGER_*} · {@code GROUP_KICKED_BY_MANAGER}) — 소모임 이름({@code snapshot}) · 일으킨 매니저 닉네임({@code actorName} — 사퇴·매니저 강퇴,
         * 메일은 보낼 때 지금 닉네임으로 채운다) · 맡은 일 키를 쉼표로 이은 것({@code note} — 임명·맡은 일 바뀜) · 내보낸 멤버 닉네임({@code excerpt} — 매니저 강퇴).
         */
        public static Facts groupManager(MemberNotificationType type, String groupName, String actorName, String permissionKeys, String targetName) {
            return new Facts(type, actorName, null, targetName, 1, 0, null, permissionKeys, false, groupName, null);
        }

        /**
         * 소모임 일정 알림(V93 — {@code GROUP_EVENT}) — 소모임 이름({@code snapshot}) · 일정 제목({@code postTitle}) · 한 일({@code result} — CREATED · UPDATED · CANCELED)
         * · 취소 사유({@code note} — 적었을 때만).
         */
        public static Facts groupEvent(String groupName, String eventTitle, String action, String reason) {
            return new Facts(MemberNotificationType.GROUP_EVENT, null, eventTitle, null, 1, 0, null, reason, false, groupName, action);
        }

        /**
         * 소모임 글 공개 알림(V94 — {@code GROUP_PUBLISH_*} · {@code GROUP_POST_PUBLISHED} · {@code GROUP_POST_MEMBERS_ONLY} · {@code GROUP_POST_RELEASED}) — 소모임 이름({@code snapshot})
         * · 글 제목({@code postTitle}) · 처리자 역할({@code result} — LEADER · MANAGER, 요청 알림은 null) · 거절 사유({@code note}) · 요청한 작성자 닉네임({@code actorName} — 요청 알림만,
         * 메일은 보낼 때 지금 닉네임으로 채운다).
         */
        public static Facts groupPublish(MemberNotificationType type, String groupName, String postTitle, String actorRole, String reason, String actorName) {
            return new Facts(type, actorName, postTitle, null, 1, 0, null, reason, false, groupName, actorRole);
        }

        /** 소모임 공개 범위가 바뀌었다(V94 — {@code GROUP_VISIBILITY_CHANGED}) — 소모임 이름({@code snapshot}) · 바뀐 값({@code result} — PUBLIC · PRIVATE) */
        public static Facts groupVisibilityChanged(String groupName, String visibility) {
            return new Facts(MemberNotificationType.GROUP_VISIBILITY_CHANGED, null, null, null, 1, 0, null, null, false, groupName, visibility);
        }

        /** 닉네임 그림 검토 결과 — 반려·해제면 사유 */
        public static Facts nicknameReviewed(String result, String reason) {
            return new Facts(MemberNotificationType.NICKNAME_IMAGE_REVIEWED, null, null, null, 1, 0, null, reason, false, null, result);
        }

        /** 의견 고정 요청이 옴 — 요청한 회원(메일은 보낼 때 지금 닉네임으로 채운다)·글 제목·의견 앞부분 */
        public static Facts pinRequested(String actorName, String postTitle, String excerpt) {
            return new Facts(MemberNotificationType.OPINION_PIN_REQUESTED, actorName, postTitle, excerpt, 1, 0, null, null, false, null, null);
        }

        /** 의견 고정 요청 결과({@code APPROVED}·{@code REJECTED}) — 거절이면 사유(없으면 null) */
        public static Facts pinReviewed(String result, String reason, String postTitle) {
            return new Facts(MemberNotificationType.OPINION_PIN_REVIEWED, null, postTitle, null, 1, 0, null, reason, false, null, result);
        }

        public static Facts comment(MemberNotificationType type, String actorName, String postTitle, String excerpt, int groupCount) {
            return new Facts(type, actorName, postTitle, excerpt, groupCount, 0, null, null, false, null);
        }

        public static Facts points(String kind, long amount, String note) {
            return new Facts(MemberNotificationType.POINTS_REVOKED, null, null, null, 1, amount, kind, note, false, null);
        }

        /** 운영자가 글을 숨김(관리자 게시글 관리) — 글 제목·사유 */
        public static Facts hiddenByStaff(String snapshot, String reason) {
            return new Facts(MemberNotificationType.POST_HIDDEN, null, null, null, 1, 0, null, reason, false, snapshot, BY_STAFF);
        }

        /** 운영자가 글을 지움(관리자 게시글 관리) — 글 제목·사유 */
        public static Facts removed(String snapshot, String reason) {
            return new Facts(MemberNotificationType.POST_REMOVED, null, null, null, 1, 0, null, reason, false, snapshot, null);
        }

        /**
         * 운영자가 글·댓글을 숨김(관리자 신고 관리 — 사유 코드 + 글).
         *
         * @param type {@code POST_HIDDEN} · {@code COMMENT_HIDDEN}
         */
        public static Facts hiddenByStaff(MemberNotificationType type, String snapshot, String reason, String reasonCode) {
            return new Facts(type, null, null, null, 1, 0, null, reason, false, snapshot, BY_STAFF, reasonCode, null);
        }

        /**
         * 운영자가 글·댓글을 지움(관리자 신고 관리 — 사유 코드 + 글).
         *
         * @param type {@code POST_REMOVED} · {@code COMMENT_REMOVED}
         */
        public static Facts removed(MemberNotificationType type, String snapshot, String reason, String reasonCode) {
            return new Facts(type, null, null, null, 1, 0, null, reason, false, snapshot, null, reasonCode, null);
        }

        /**
         * 내 신고의 처리 결과.
         *
         * @param result     {@link #REPORT_ACTIONED} · {@link #REPORT_NO_VIOLATION}
         * @param targetType {@code POST} · {@code COMMENT} · {@code GROUP}(소모임 — V95)
         * @param snapshot   신고 당시 글 제목·댓글 앞부분
         */
        public static Facts reportResult(String result, String targetType, String snapshot) {
            return new Facts(MemberNotificationType.REPORT_RESULT, null, null, null, 1, 0, null, null, false, snapshot, result, null, targetType);
        }

        /**
         * 공동구매 알림(커머스 2b-4) — 종류({@code result} — {@code GroupBuyNotifications.Kind} 이름)·공구 제목({@code snapshot})·금액({@code amount})·덧붙임({@code note} — 주문 번호·날짜).
         */
        public static Facts groupBuy(String kind, String title, long amount, String note) {
            return new Facts(MemberNotificationType.GROUP_BUY, null, null, null, 1, amount, null, note, false, title, kind);
        }

        /**
         * 사업자 신원 알림(V111) — 종류({@code result} — SUBMITTED·APPROVED·REJECTED·SUSPENDED·UNSUSPENDED·RE_REVIEW)·상호({@code snapshot})·사유({@code note}).
         */
        public static Facts business(String kind, String companyName, String note) {
            return new Facts(MemberNotificationType.BUSINESS, null, null, null, 1, 0, null, note, false, companyName, kind);
        }

        /**
         * 사업자 신청 건 알림(V112) — 종류({@code result} — {@code BizApplicationNotices.Kind} 이름)·제목({@code snapshot})·금액({@code amount})·덧붙임({@code note} — 기한·사유).
         */
        public static Facts bizApplication(String kind, String title, long amount, String note) {
            return new Facts(MemberNotificationType.BIZ_APPLICATION, null, null, null, 1, amount, null, note, false, title, kind);
        }

        /** 운영자가 숨긴 글의 숨김 알림인가 */
        public boolean byStaff() {
            return BY_STAFF.equals(result);
        }

        public static Facts hidden(MemberNotificationType type, String snapshot, boolean temporary) {
            return new Facts(type, null, null, null, 1, 0, null, null, temporary, snapshot);
        }
    }

    /** 알림함 한 줄 — {@code locale} 은 읽는 사람 언어. 본문이 비면 빈 글자 */
    public RenderedInApp inApp(Facts facts, Locale locale, String recipientName) {
        MessageRenderRequest request = request(facts, MessageChannel.IN_APP, locale).variable(MessageVariable.RECIPIENT_NAME, recipientName).build();
        RenderedInApp rendered = renderer.renderInApp(request);
        MessageValues picked = renderer.pick(request);
        String variant = builtinVariantTitle(facts, picked);
        if (variant == null) {
            return rendered;
        }
        String body = builtinVariantBody(facts, picked);
        return new RenderedInApp(rendered.messageId(), variant, body == null ? rendered.body() : body);
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
            case POST_HIDDEN -> builder.variable("postTitle", facts.snapshot()).variable("reason", actionReason(facts, locale))
                    .condition(MessageCondition.HIDE_REASON, facts.byStaff() ? MessageCondition.STAFF : facts.temporary() ? MessageCondition.RIGHTS : MessageCondition.REPORTS);
            case POST_REMOVED -> builder.variable("postTitle", facts.snapshot()).variable("reason", actionReason(facts, locale));
            case COMMENT_HIDDEN -> builder.variable("excerpt", facts.snapshot()).variable("reason", actionReason(facts, locale))
                    .condition(MessageCondition.HIDE_REASON, facts.byStaff() ? MessageCondition.STAFF : facts.temporary() ? MessageCondition.RIGHTS : MessageCondition.REPORTS);
            case COMMENT_REMOVED -> builder.variable("excerpt", facts.snapshot()).variable("reason", actionReason(facts, locale));
            case REPORT_RESULT -> builder.variable("result", messages.get(new MessageRef("member.notification.REPORT_RESULT.result." + reportResult(facts.result())), locale))
                    .variable("target", facts.snapshot())
                    .condition(MessageCondition.REPORT_RESULT, reportResult(facts.result()));
            case DM -> builder.variable("actorName", facts.actorName());
            case NICKNAME_IMAGE_REVIEWED -> builder.variable("result", messages.get(new MessageRef(reviewResultKey(facts.result())), locale))
                    .variable("reason", facts.note() == null ? "" : facts.note());
            case OPINION_PIN_REQUESTED -> builder.variable("actorName", facts.actorName()).variable("postTitle", facts.postTitle())
                    .variable("excerpt", facts.excerpt());
            case OPINION_PIN_REVIEWED -> builder.variable("result", messages.get(new MessageRef(pinResultKey(facts.result())), locale))
                    .variable("postTitle", facts.postTitle()).variable("reason", facts.note() == null ? "" : facts.note());
            case FEATURE_RESTRICTED -> builder.variable("restriction", restrictionName(facts, locale)).variable("until", restrictionUntil(facts, locale))
                    .variable("reason", restrictionReason(facts, locale));
            case POINT_SHOP_REFUND_APPROVED -> builder.variable("itemName", nz(facts.snapshot())).variable("amount", String.format("%,d", facts.amount()));
            case POINT_SHOP_REFUND_REJECTED -> builder.variable("itemName", nz(facts.snapshot())).variable("reason", nz(facts.note()));
            case GROUP_APPROVED, GROUP_JOIN_APPROVED, GROUP_KICKED, GROUP_DISSOLVED, GROUP_LEADER_TRANSFERRED, GROUP_REACTIVATED ->
                    builder.variable("groupName", nz(facts.snapshot()));
            case GROUP_REJECTED, GROUP_JOIN_REJECTED, GROUP_DEACTIVATED -> builder.variable("groupName", nz(facts.snapshot())).variable("reason", nz(facts.note()));
            case GROUP_JOIN_REQUEST -> builder.variable("groupName", nz(facts.snapshot())).variable("actorName", nz(facts.actorName()));
            case GROUP_POST_UNPUBLISHED -> builder.variable("groupName", nz(facts.snapshot())).variable("postTitle", nz(facts.postTitle()))
                    .variable("actorRole", messages.get(new MessageRef("member.notification.GROUP_POST_UNPUBLISHED.actor."
                            + ("MANAGER".equals(facts.result()) ? "MANAGER" : "LEADER")), locale));
            case GROUP_MANAGER_APPOINTED, GROUP_MANAGER_PERMISSIONS_CHANGED -> builder.variable("groupName", nz(facts.snapshot()))
                    .variable("permissions", managerTasks(facts.note(), locale));
            case GROUP_MANAGER_REMOVED -> builder.variable("groupName", nz(facts.snapshot()));
            case GROUP_MANAGER_RESIGNED -> builder.variable("groupName", nz(facts.snapshot())).variable("actorName", nz(facts.actorName()));
            case GROUP_KICKED_BY_MANAGER -> builder.variable("groupName", nz(facts.snapshot())).variable("actorName", nz(facts.actorName()))
                    .variable("targetName", nz(facts.excerpt()));
            case GROUP_EVENT -> builder.variable("groupName", nz(facts.snapshot()))
                    .variable("eventAction", messages.get(new MessageRef("member.notification.GROUP_EVENT.action." + eventAction(facts.result())), locale))
                    .variable("eventTitle", nz(facts.postTitle())).variable("reason", nz(facts.note()));
            case GROUP_PUBLISH_REQUESTED -> builder.variable("groupName", nz(facts.snapshot())).variable("actorName", nz(facts.actorName()))
                    .variable("postTitle", nz(facts.postTitle()));
            case GROUP_PUBLISH_APPROVED, GROUP_PUBLISH_REJECTED, GROUP_POST_PUBLISHED, GROUP_POST_MEMBERS_ONLY, GROUP_POST_RELEASED ->
                    builder.variable("groupName", nz(facts.snapshot())).variable("postTitle", nz(facts.postTitle()))
                            .variable("actorRole", groupActorRole(facts.result(), locale)).variable("reason", nz(facts.note()));
            case GROUP_VISIBILITY_CHANGED -> builder.variable("groupName", nz(facts.snapshot()))
                    .variable("visibility", messages.get(new MessageRef("member.notification.GROUP_VISIBILITY_CHANGED.to."
                            + ("PUBLIC".equals(facts.result()) ? "PUBLIC" : "PRIVATE")), locale));
            case GROUP_BUY -> builder.variable("groupBuyTitle", nz(facts.snapshot()))
                    .variable("detail", messages.get(new MessageRef("member.notification.GROUP_BUY.kind." + nz(facts.result()), String.format("%,d", facts.amount()),
                            nz(facts.note())), locale));
            case BUSINESS -> builder.variable("companyName", nz(facts.snapshot()))
                    .variable("detail", messages.get(new MessageRef("member.notification.BUSINESS.kind." + nz(facts.result()), nz(facts.note())), locale));
            case BIZ_APPLICATION -> builder.variable("bizTitle", nz(facts.snapshot()))
                    .variable("detail", messages.get(new MessageRef("member.notification.BIZ_APPLICATION.kind." + nz(facts.result()), String.format("%,d", facts.amount()),
                            nz(facts.note())), locale));
            case POINTS_EXPIRING -> builder.variable("amount", String.format("%,d", facts.amount()))
                    .variable("date", facts.expiring() == null || facts.expiring().date() == null ? "" : facts.expiring().date().toString())
                    .variable("days", facts.expiring() == null ? "" : String.valueOf(facts.expiring().days()));
        }
        return builder;
    }

    /** 맡은 일 키(쉼표로 이은 것, 고정 순서) → 읽는 사람 언어의 이름을 「 · 」로 이은 한 줄(번들 group.managerPermission.<키>, 모르는 키는 건너뛴다) */
    private String managerTasks(String keys, Locale locale) {
        if (keys == null || keys.isBlank()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (String key : keys.split(",")) {
            String k = key.strip();
            if (GroupManagerPermission.of(k).isPresent()) {
                names.add(messages.get(new MessageRef("group.managerPermission." + k), locale));
            }
        }
        return String.join(" · ", names);
    }

    /** 걸린 기능 이름(번들 — 「공동구매 참여」) */
    private String restrictionName(Facts facts, Locale locale) {
        return facts.restriction() == null ? "" : messages.get(new MessageRef("restriction.feature." + facts.restriction().kind()), locale);
    }

    /** 끝 — 한국 시간 「YYYY-MM-DD HH:mm까지」, 무기한이면 「해제될 때까지」 */
    private String restrictionUntil(Facts facts, Locale locale) {
        Instant until = facts.restriction() == null ? null : facts.restriction().until();
        return until == null ? messages.get(new MessageRef("restriction.until.none"), locale)
                : messages.get(new MessageRef("restriction.until.at", UNTIL_FORMAT.format(until.atZone(ServiceTime.ZONE))), locale);
    }

    /** 공개 사유 — 자동 부여면 규칙의 번들 문구. 운영 메모는 이 사실에 없다 */
    private String restrictionReason(Facts facts, Locale locale) {
        Facts.Restriction restriction = facts.restriction();
        if (restriction == null) {
            return "";
        }
        if (restriction.reason() != null && !restriction.reason().isBlank()) {
            return restriction.reason();
        }
        return restriction.autoRule() == null ? "" : messages.get(new MessageRef("restriction.autoRule." + restriction.autoRule() + ".reason"), locale);
    }

    private static final DateTimeFormatter UNTIL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 운영자 조치 사유 한 줄 — 사유 코드가 있으면 「코드 이름: 글」(글이 없으면 코드 이름만, 관리자 신고 관리), 없으면 운영자가 적은 글 그대로(관리자 게시글 관리). 둘 다 없으면 빈 글자
     */
    private String actionReason(Facts facts, Locale locale) {
        String note = facts.note() == null || facts.note().isBlank() ? null : facts.note();
        if (facts.reasonCode() == null) {
            return note == null ? "" : note;
        }
        String label = messages.get(new MessageRef("report.reason." + facts.reasonCode()), locale);
        return note == null ? label : messages.get(new MessageRef("member.notification.actionReason", label, note), locale);
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
        if ((facts.type() == MemberNotificationType.POST_HIDDEN || facts.type() == MemberNotificationType.COMMENT_HIDDEN) && facts.byStaff()) {
            return messages.get(new MessageRef("member.notification." + facts.type().name() + ".staff"), locale);
        }
        if (facts.type() == MemberNotificationType.REPORT_RESULT) {
            String target = "COMMENT".equals(facts.targetType()) || "GROUP".equals(facts.targetType()) ? facts.targetType() : "POST";
            return messages.get(new MessageRef("member.notification.REPORT_RESULT.title." + reportResult(facts.result()) + "." + target), locale);
        }
        if ((facts.type() == MemberNotificationType.POST_HIDDEN || facts.type() == MemberNotificationType.COMMENT_HIDDEN) && facts.temporary()) {
            return messages.get(new MessageRef("member.notification." + facts.type().name() + ".temporary"), locale);
        }
        if (facts.type() == MemberNotificationType.NICKNAME_IMAGE_REVIEWED) {
            return messages.get(new MessageRef("member.notification.NICKNAME_IMAGE_REVIEWED.title." + reviewResult(facts.result())), locale);
        }
        if (facts.type() == MemberNotificationType.OPINION_PIN_REVIEWED) {
            return messages.get(new MessageRef("member.notification.OPINION_PIN_REVIEWED.title." + pinResult(facts.result())), locale);
        }
        return null;
    }

    /** 기본 문구가 골라졌을 때만 — 운영자 숨김의 본문(글 제목 · 사유). 그 밖은 null(그려진 본문 그대로) */
    private String builtinVariantBody(Facts facts, MessageValues picked) {
        if (picked == null || !picked.builtin()) {
            return null;
        }
        if ((facts.type() == MemberNotificationType.POST_HIDDEN || facts.type() == MemberNotificationType.COMMENT_HIDDEN) && facts.byStaff()) {
            Locale locale = MessageLocales.locale(picked.locale());
            return messages.get(new MessageRef("member.notification." + facts.type().name() + ".staff.body", nz(facts.snapshot()), actionReason(facts, locale)), locale);
        }
        if (facts.type() == MemberNotificationType.GROUP_EVENT && facts.note() != null && !facts.note().isBlank()) {
            // 사유를 적은 일정 취소 — 본문 「일정 제목 · 사유」
            Locale locale = MessageLocales.locale(picked.locale());
            return messages.get(new MessageRef("member.notification.GROUP_EVENT.reasonBody", nz(facts.postTitle()), facts.note()), locale);
        }
        if (facts.type() == MemberNotificationType.GROUP_PUBLISH_REJECTED && facts.note() != null && !facts.note().isBlank()) {
            // 사유를 적은 공개 요청 거절 — 본문 「글 제목 · 사유」(일정 취소와 같은 꼴)
            Locale locale = MessageLocales.locale(picked.locale());
            return messages.get(new MessageRef("member.notification.GROUP_EVENT.reasonBody", nz(facts.postTitle()), facts.note()), locale);
        }
        return null;
    }

    /** 소모임 처리자 역할(「모임장이」·「매니저가」 — 글 되돌림 알림과 같은 번들) — 모르는 값은 모임장으로 본다 */
    private String groupActorRole(String role, Locale locale) {
        return messages.get(new MessageRef("member.notification.GROUP_POST_UNPUBLISHED.actor." + ("MANAGER".equals(role) ? "MANAGER" : "LEADER")), locale);
    }

    /** 일정 알림의 한 일 — 모르는 값은 등록으로 본다 */
    private static String eventAction(String action) {
        return "UPDATED".equals(action) || "CANCELED".equals(action) ? action : "CREATED";
    }

    /** 신고 처리 결과 — 모르는 값은 조치됨으로 본다 */
    private static String reportResult(String result) {
        return REPORT_NO_VIOLATION.equals(result) ? REPORT_NO_VIOLATION : REPORT_ACTIONED;
    }

    /** 검토 결과 — 모르는 값은 승인으로 본다(예전 기록의 모양이 달라도 목록이 멈추지 않게) */
    private static String reviewResult(String result) {
        return "REJECTED".equals(result) || "REVOKED".equals(result) ? result : "APPROVED";
    }

    /** 고정 요청 결과 — 모르는 값은 승인으로 본다 */
    private static String pinResult(String result) {
        return "REJECTED".equals(result) ? result : "APPROVED";
    }

    private static String pinResultKey(String result) {
        return "member.notification.OPINION_PIN_REVIEWED.result." + pinResult(result);
    }

    private static String reviewResultKey(String result) {
        return "member.notification.NICKNAME_IMAGE_REVIEWED.result." + reviewResult(result);
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
