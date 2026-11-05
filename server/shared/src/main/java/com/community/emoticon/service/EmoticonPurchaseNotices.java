package com.community.emoticon.service;

import com.community.emoticon.entity.EmoticonRefundKind;
import com.community.emoticon.repository.master.EmoticonPurchaseStore.PurchaseRow;
import com.community.membernotification.service.MemberNotificationPublisher;
import org.springframework.stereotype.Component;

/**
 * 로보티콘 유료 구매의 회원 알림(후속 결정 2, 2026-11-05 — 회원 알림 종류 {@code EMOTICON_PURCHASE}, 받는 사람 = 산 회원). 끌 수 없는 거래 알림.
 *
 * <ul>
 *   <li><b>언제</b>: 결제·환불 <b>업무 트랜잭션 안에서</b> 부른다 — 알림 행이 그 트랜잭션에 함께 쓰여(발행기의 세이브포인트) <b>커밋돼야 보이고</b>, 되돌려지면 없다. 메일은 커밋 뒤
 *       (회원 이벤트 설정의 이메일이 켜져 있을 때만). 프로젝트 규약(업무 트랜잭션 밖에서 알림 행을 만들지 않는다 — notification.md)을 따른 것이라 커밋 뒤 별도 트랜잭션으로 쓰지 않는다.</li>
 *   <li><b>한 번만</b>: 부르는 자리가 그 일의 「한 번만 일어나는 전이」 바로 뒤다 — 구매 완료 = 구매 PENDING → PAID 조건부 UPDATE 를 이긴 쪽, 셀프·예외 환불 = PAID → REFUNDED
 *       조건부 UPDATE 를 이긴 쪽, 자동 취소 = 결제 모듈이 취소 한 건마다 한 번 부르는 {@code onCanceled}(SUCCEEDED 조건부) 또는 멈춘 취소의 수동 처리({@code GAVE_UP → MANUAL} 조건부)
 *       — 한 취소에 둘 중 하나만 온다. 같은 결제 확인을 다시 보내거나(결제 모듈이 처리기를 다시 부르지 않는다) 같은 환불 키로 다시 보내도(처음 응답) 두 번 나가지 않는다.</li>
 *   <li><b>종류</b>({@code kind} — 번들 {@code member.notification.EMOTICON_PURCHASE.kind.<kind>}): {@link #PAID} 구매 완료 · {@link #AUTO_CANCELED} 결제를 받아들이지 않아 자동 취소
 *       (늦은 결제·금액 다름·그새 무료가 됨·이미 가짐·판매 중 아님 — 까닭은 싣지 않는다) · {@link #REFUNDED_SELF} 셀프 환불 접수 · {@link #REFUNDED_EXCEPTION} 운영자 예외 환불
 *       (운영자 사유는 싣지 않는다 — 내부 메모일 수 있다).</li>
 *   <li><b>PG 취소가 끝내 실패(GAVE_UP)</b>: 회원에게 알리지 않는다 — 운영자에게 결제 이상 알림이 가고, 운영자가 직접 처리(수동 처리 완료)하면 그때 자동 취소 알림이 한 번 나간다.
 *       환불은 이미 접수 알림이 갔고 상태(구매 내역 「환불됨」)도 이미 바뀌어 있다.</li>
 *   <li><b>이동</b>: 구매 완료·환불 → 내 로보티콘({@code /me/robotikon} — 구매 내역이 같은 화면), 자동 취소 → 그 팩({@code /robotikon/{packId}} — 구매 내역에는 결제 완료 계열만 나온다).</li>
 * </ul>
 */
@Component
public class EmoticonPurchaseNotices {

    public static final String PAID = "PAID";
    public static final String AUTO_CANCELED = "AUTO_CANCELED";
    public static final String REFUNDED_SELF = "REFUNDED_SELF";
    public static final String REFUNDED_EXCEPTION = "REFUNDED_EXCEPTION";
    public static final String MY_PATH = "/me/robotikon";

    private final MemberNotificationPublisher members;

    public EmoticonPurchaseNotices(MemberNotificationPublisher members) {
        this.members = members;
    }

    /** 구매 완료(결제를 받아들였다) */
    public void paid(PurchaseRow p) {
        members.emoticonPurchase(p.userId(), PAID, p.id(), p.packId(), p.packName(), p.amount(), MY_PATH);
    }

    /** 결제를 받아들이지 않아 전액 자동 취소됐다 — 금액은 취소한 금액(PG 금액이 달랐으면 그 금액) */
    public void autoCanceled(PurchaseRow p, long amount) {
        members.emoticonPurchase(p.userId(), AUTO_CANCELED, p.id(), p.packId(), p.packName(), amount, "/robotikon/" + p.packId());
    }

    /** 환불을 넣었다(셀프 · 운영자 예외) */
    public void refunded(PurchaseRow p, EmoticonRefundKind kind) {
        members.emoticonPurchase(p.userId(), kind == EmoticonRefundKind.SELF ? REFUNDED_SELF : REFUNDED_EXCEPTION, p.id(), p.packId(), p.packName(), p.amount(), MY_PATH);
    }
}
