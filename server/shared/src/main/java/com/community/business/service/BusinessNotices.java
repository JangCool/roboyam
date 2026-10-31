package com.community.business.service;

import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import org.springframework.stereotype.Component;

/**
 * 사업자 신원 알림 — 회원(계정 알림, 끌 수 없음: 접수·승인·반려·정지·해제·재심사 대기)과 운영자(사업자 신청·재심사 대기). 업무 트랜잭션 안에서 마지막에 부른다
 * (발행기가 세이브포인트로 격리 — 실패해도 업무를 깨지 않는다). 인자에 개인정보 평문을 싣지 않는다(상호와 운영자가 적은 사유만).
 */
@Component
public class BusinessNotices {

    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String UNSUSPENDED = "UNSUSPENDED";
    public static final String RE_REVIEW = "RE_REVIEW";

    private final MemberNotificationPublisher members;
    private final NotificationPublisher operators;

    public BusinessNotices(MemberNotificationPublisher members, NotificationPublisher operators) {
        this.members = members;
        this.operators = operators;
    }

    /** 회원에게 — 종류(번들 {@code member.notification.BUSINESS.kind.<kind>}) · 상호 · 사유 */
    public void member(long userId, String kind, String companyName, String note) {
        members.business(userId, kind, companyName, note);
    }

    /** 운영자에게 — 처음 신청·재신청이면 사업자 신청 대기, 승인 뒤 상호·번호 수정이면 재심사 대기 */
    public void operators(long profileId, boolean reReview) {
        String id = Long.toString(profileId);
        operators.publish(reReview ? AdminNotificationEvent.BUSINESS_RE_REVIEW_PENDING : AdminNotificationEvent.BUSINESS_APPLICATION_PENDING,
                NotificationContext.empty().dedupe(id).link("profileId", id));
    }
}
