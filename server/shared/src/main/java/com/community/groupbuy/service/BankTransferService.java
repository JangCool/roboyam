package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.BankTransferStore;
import com.community.groupbuy.repository.master.BankTransferStore.TransferRow;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyRefundStore.RefundRow;
import com.community.payment.dto.ManualCancelMethod;
import com.community.payment.service.PaymentCancelManualService;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계좌 송금(commerce.md 9-3 방식 — 계약 결정 6·39·67). 계좌 암호문({@code ROBOYAM_DATA_KEY} — 용도 {@code commerce.bank.*}), 송금 완료 뒤 계좌를 지운다. 모두 감사(돈 관련, 계좌는 싣지 않는다).
 *
 * <ul>
 *   <li><b>전환</b>(결정 67): PG 가 영구 거절해 멈춘(GAVE_UP) 환불 — 결제 모듈 「수동 처리 완료(계좌 송금)」({@link PaymentCancelManualService})를 같은 트랜잭션에서 부르고,
 *       처리기가 환불 기록 방법을 BANK_TRANSFER 로 바꾸며 송금 대기 줄을 만든다. 금액은 그대로(금액을 바꾸는 전환은 받지 않는다).</li>
 *   <li>보상액 중 남은 결제액을 넘는 몫(결정 6·39)은 반품 완료 때 이미 송금 대기로 들어온다.</li>
 *   <li>계좌 받기(회원 또는 운영자 — 화면은 2b-4) → 송금 완료(운영자) → 환불 기록 완료.</li>
 * </ul>
 */
@Service
public class BankTransferService {

    public static final DataPurpose BANK = DataPurpose.of("commerce.bank.name");
    public static final DataPurpose ACCOUNT = DataPurpose.of("commerce.bank.account");
    public static final DataPurpose HOLDER = DataPurpose.of("commerce.bank.holder");

    private final BankTransferStore transfers;
    private final GroupBuyRefundStore refunds;
    private final PaymentCancelManualService manual;
    private final DataCipher cipher;
    private final GroupBuyAudit audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BankTransferService(BankTransferStore transfers, GroupBuyRefundStore refunds, PaymentCancelManualService manual, DataCipher cipher, GroupBuyAudit audit,
                               ApplicationEventPublisher events, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.transfers = transfers;
        this.refunds = refunds;
        this.manual = manual;
        this.cipher = cipher;
        this.audit = audit;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 멈춘 PG 환불을 계좌 송금으로 바꾼다.
     *
     * @return 송금 대기 줄 ID
     * @throws com.community.payment.exception.PaymentCancelNotGaveUpException 취소가 멈춘 상태(GAVE_UP)가 아니다(409)
     */
    public long convertRefund(long refundId, String reason, long operatorId, String ip) {
        String why = GroupBuyAudit.reason(reason);
        return tx.execute(status -> {
            RefundRow refund = refunds.find(refundId).orElseThrow(GroupBuyException::requestNotFound);
            if (refund.paymentCancelId() == null || !"PG_CANCEL".equals(refund.method())) {
                throw GroupBuyException.bankTransferState();
            }
            manual.resolve(refund.paymentCancelId(), ManualCancelMethod.BANK_TRANSFER, null, why, operatorId);
            TransferRow t = transfers.bySource("GROUP_BUY_REFUND", refundId).orElseThrow(GroupBuyException::bankTransferState);
            audit.record(operatorId, AuditActions.GROUPBUY_BANK_TRANSFER, GroupBuyAudit.TARGET_BANK_TRANSFER, t.id(), ip, Map.of("step", "CONVERT", "refundId",
                    Long.toString(refundId), "amount", t.amount(), "reason", why));
            return t.id();
        });
    }

    /** 계좌를 받는다(은행 1~30자 · 계좌 숫자·하이픈 6~30자 · 예금주 1~30자) — 송금 전이면 고칠 수 있다 */
    public void registerAccount(long transferId, String bank, String accountNo, String holder) {
        if (blankOrLonger(bank, 30) || accountNo == null || !accountNo.strip().matches("[0-9-]{6,30}") || blankOrLonger(holder, 30)) {
            throw ValidationException.of("account", new MessageRef("commerce.bank.account.invalid"));
        }
        cipher.requireConfigured();
        tx.executeWithoutResult(status -> {
            if (!transfers.setAccount(transferId, cipher.encrypt(BANK, bank.strip()), cipher.encrypt(ACCOUNT, accountNo.strip()), cipher.encrypt(HOLDER, holder.strip()))) {
                throw GroupBuyException.bankTransferState();
            }
        });
    }

    /** 송금 완료 — 계좌를 지우고 환불 기록을 완료로(공구 환불 원천일 때) */
    public void markSent(long transferId, long operatorId, String ip) {
        tx.executeWithoutResult(status -> {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            TransferRow t = transfers.find(transferId).orElseThrow(GroupBuyException::bankTransferState);
            if (!transfers.markSent(transferId, operatorId, now)) {
                throw GroupBuyException.bankTransferState();
            }
            if ("GROUP_BUY_REFUND".equals(t.sourceKind()) && refunds.completeBank(t.sourceId(), now)) {
                events.publishEvent(new GroupBuyEvents.RefundCompleted(t.sourceId(), t.participationId() == null ? 0 : t.participationId(), t.userId()));
            }
            audit.record(operatorId, AuditActions.GROUPBUY_BANK_TRANSFER, GroupBuyAudit.TARGET_BANK_TRANSFER, transferId, ip, Map.of("step", "SENT", "amount", t.amount(),
                    "source", t.sourceKind()));
        });
    }

    private static boolean blankOrLonger(String value, int max) {
        return value == null || value.isBlank() || value.strip().length() > max;
    }
}
