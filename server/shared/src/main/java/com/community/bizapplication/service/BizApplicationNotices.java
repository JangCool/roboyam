package com.community.bizapplication.service;

import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
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
        REFUND_APPROVED, REFUND_DENIED, REFUNDED, AUTO_CANCELED
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

    /** 운영자에게 — 심사 대기(새 신청·소재 바꾸기) */
    public void reviewPending(long applicationId) {
        String id = Long.toString(applicationId);
        operators.publish(AdminNotificationEvent.BIZ_APPLICATION_REVIEW_PENDING, NotificationContext.empty().dedupe(id).link("id", id));
    }

    /** 운영자에게 — 진행 중 환불(일할) 심사 대기 */
    public void refundRequested(long applicationId) {
        String id = Long.toString(applicationId);
        operators.publish(AdminNotificationEvent.BIZ_APPLICATION_REFUND_REQUESTED, NotificationContext.empty().dedupe(id).link("id", id));
    }
}
