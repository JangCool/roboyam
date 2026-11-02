package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizDomain;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.stereotype.Component;

/**
 * 신청 건 알림(F9·F10) — 사업자(회원 알림 {@code BIZ_APPLICATION}, 끌 수 없는 거래 알림)와 운영자(심사 대기 · 진행 중 환불 심사). 업무 트랜잭션 안에서 마지막에 부른다
 * (발행기가 세이브포인트로 격리 — 실패해도 업무를 깨지 않는다). 인자에 개인정보를 싣지 않는다(제목·금액·기한·운영자가 적은 사유만).
 * 운영자 직접 등록 건(회원 없음)은 사업자 알림을 건너뛴다.
 */
@Component
public class BizApplicationNotices {

    /** 사업자 알림 종류 — 번들 {@code member.notification.BIZ_APPLICATION.kind.<이름>}({0} 금액, {1} 덧붙임) */
    public enum Kind {
        SUBMITTED, APPROVED, REJECTED, MANUAL, PAID, CONFIRMED, EXPIRED, PAYMENT_DUE, START_SOON, END_SOON, CANCELED, TAKEN_DOWN, RESTORED, REFUND_REQUESTED,
        REFUND_APPROVED, REFUND_DENIED, REFUNDED, AUTO_CANCELED,
        /** 사업자 핫딜 글이 핫딜 게시판에 올라갔다 · 올리지 못했다({1} 사유 코드 — 운영팀 확인 중, V123) */
        POSTED, POST_FAILED,
        /** 광고주 요청 일시 정지·다시 켜기·중단(V127 — {0} 중단은 환불 예상액). 운영팀이 요청을 받아 대신 처리했으면 {@code _BY_OPERATOR} 종류(문구 「운영팀이 요청을 받아…」) */
        PAUSED, RESUMED, STOPPED, PAUSED_BY_OPERATOR, RESUMED_BY_OPERATOR, STOPPED_BY_OPERATOR,
        /** 사업자 핫딜 글(결정 11·12): 종료 요청이 쌓여 운영팀 확인 중 · 운영팀이 그대로 두기로 함({1} 사유) · 운영진이 다시 진행 중으로({1} 사유). 운영진 종료는 핫딜 알림 DEAL_MANAGER_ENDED */
        DEAL_REVIEW, DEAL_KEPT, DEAL_REOPENED
    }

    private final MemberNotificationPublisher members;
    private final NotificationPublisher operators;

    public BizApplicationNotices(MemberNotificationPublisher members, NotificationPublisher operators) {
        this.members = members;
        this.operators = operators;
    }

    /** 사업자에게 — 회원이 없는 건(운영자 등록)은 건너뛴다 */
    public void member(AppRow app, Kind kind, long amount, String note) {
        if (app.userId() == null) {
            return;
        }
        members.bizApplication(app.userId(), kind.name(), app.id(), app.title(), amount, note);
    }

    /** 운영자에게 — 심사 대기(새 신청·소재 바꾸기). 건의 도메인으로 이벤트를 고른다(광고 → {@code AD_REVIEW_PENDING}, 핫딜 → {@code HOTDEAL_REVIEW_PENDING}) */
    public void reviewPending(AppRow app) {
        operators.publish(reviewEvent(app.kind().domain()), context(app.id()));
    }

    /** 운영자에게 — 진행 중 환불(일할) 심사 대기. 광고 → {@code AD_REFUND_REQUESTED}, 핫딜 → {@code HOTDEAL_REFUND_REQUESTED} */
    public void refundRequested(AppRow app) {
        operators.publish(refundEvent(app.kind().domain()), context(app.id()));
    }

    /** 운영자에게 — 사업자 핫딜 글을 시작 시각에 올리지 못했다({@code HOTDEAL_POST_FAILED}, 링크 핫딜 심사 서랍 · 본문에 까닭). 처리는 취소(전액 환불) — 서비스 정지면 정지를 풀어도 된다 */
    public void postFailed(AppRow app, String reason) {
        operators.publish(AdminNotificationEvent.HOTDEAL_POST_FAILED, context(app.id()).args(new MessageRef("notification.hotdeal.postFailed.reason." + reason)));
    }

    /** 운영자에게 — 사업자 핫딜 글에 종료 요청이 문턱만큼 모였다({@code HOTDEAL_BUSINESS_REVIEW}, 핫딜 글 서랍에서 [종료]·[유지] — 결정 11) */
    public void businessReview(long postId) {
        String id = Long.toString(postId);
        operators.publish(AdminNotificationEvent.HOTDEAL_BUSINESS_REVIEW, NotificationContext.empty().dedupe(id).link("postId", id));
    }

    /** 운영자에게 — 사업자 핫딜 글을 말머리 없이 올렸다({@code HOTDEAL_POST_PREFIX_DROPPED}). 말머리 상단 노출이 걸려 있으면 그 노출이 나오지 않는다는 문구를 붙인다 */
    public void postPrefixDropped(AppRow app, boolean categoryPlacement) {
        Object placement = categoryPlacement ? new MessageRef("notification.hotdeal.prefixDropped.categoryOff") : "";
        operators.publish(AdminNotificationEvent.HOTDEAL_POST_PREFIX_DROPPED, context(app.id()).args(placement));
    }

    public static AdminNotificationEvent reviewEvent(BizDomain domain) {
        return switch (domain) {
            case AD -> AdminNotificationEvent.AD_REVIEW_PENDING;
            case HOTDEAL -> AdminNotificationEvent.HOTDEAL_REVIEW_PENDING;
        };
    }

    public static AdminNotificationEvent refundEvent(BizDomain domain) {
        return switch (domain) {
            case AD -> AdminNotificationEvent.AD_REFUND_REQUESTED;
            case HOTDEAL -> AdminNotificationEvent.HOTDEAL_REFUND_REQUESTED;
        };
    }

    private static NotificationContext context(long applicationId) {
        String id = Long.toString(applicationId);
        return NotificationContext.empty().dedupe(id).link("id", id);
    }
}
