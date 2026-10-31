package com.community.bizapplication.service;

import com.community.audit.dto.AuditActions;
import com.community.bizapplication.dto.BizContractFile;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.dto.BizUpload;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRejectCode;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.DepositRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizContractStore;
import com.community.bizapplication.repository.master.BizContractStore.ContractRow;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizPeriods.Period;
import com.community.bizapplication.service.BizPricing.Amount;
import com.community.bizapplication.service.BizTransitions.Action;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.time.ServiceTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영자 쪽 신청 건 처리(admin-web {@code /ad-reviews}·{@code /hotdeal-reviews}, API {@code /api/admin/biz-applications}) — 승인·반려·수동 전환·계약서 사본·입금 확인·예약 확정·입금 기한·취소·환불 승인·거절·송금 완료.
 * 처리는 신청 건 행을 잠그고 상태를 다시 본다 — 이미 바뀌었으면 409 {@code BIZ_APPLICATION_STATE_CHANGED}. 감사·처리 기록·알림은 같은 트랜잭션. 권한은 관리자 앱 컨트롤러가 본다.
 */
@Service
public class BizApplicationReviewService {

    /** 계약서 사본 용도(AAD) */
    public static final DataPurpose CONTRACT = DataPurpose.of("biz.contract");
    static final int FILE_MAX_BYTES = 10 * 1024 * 1024;
    static final int FILES_MAX = 10;

    /** 기간 판정 — 요금표 계산 + 남은 자리 */
    public record Availability(List<ItemRow> items, Amount quote, BizSlotService.Room room) {
    }

    private final BizApplicationStore store;
    private final BizContractStore contracts;
    private final BizRefundStore refunds;
    private final BizSlotService slots;
    private final BizApplicationOps ops;
    private final BizApplicationMemberService pricing;
    private final DataCipher cipher;
    private final TransactionTemplate tx;

    public BizApplicationReviewService(BizApplicationStore store, BizContractStore contracts, BizRefundStore refunds, BizSlotService slots, BizApplicationOps ops,
                                       BizApplicationMemberService pricing, DataCipher cipher, PlatformTransactionManager transactions) {
        this.store = store;
        this.contracts = contracts;
        this.refunds = refunds;
        this.slots = slots;
        this.ops = ops;
        this.pricing = pricing;
        this.cipher = cipher;
        this.tx = new TransactionTemplate(transactions);
    }

    // ── 기간 판정 ───────────────────────────────────────────────────────────

    /** 그 기간의 요금표 계산 + 남은 자리(자기 자신 제외, 잠그지 않음) */
    public Availability availability(long id, String startText, String endText) {
        BizFields f = BizFields.start();
        Period period = period(f, startText, endText, false);
        f.throwIfAny();
        return tx.execute(status -> {
            AppRow app = store.find(id).orElseThrow(BizApplicationException::notFound);
            List<ItemRow> items = pricing.items(id, products(app), period);
            Amount quote = BizPricing.amountOf(items.stream().mapToLong(ItemRow::supply).sum());
            BizProduct capacity = BizApplicationReader.capacityProduct(items);
            BizSlotService.Room room = capacity == null ? new BizSlotService.Room(false, 1, 1) : slots.room(capacity, period.start(), period.end(), id);
            return new Availability(items, quote, room);
        });
    }

    // ── 심사 ───────────────────────────────────────────────────────────────

    /**
     * 승인 — 심사 대기만. 시작이 지난 기간 400 {@code fields.period}(F6 — 화면이 같은 길이로 뒤로 옮겨 보낸다), 요금표와 다른 공급가에 사유 없음 400 {@code fields.adjustReason},
     * 자리 없음 409 {@code BIZ_SLOT_FULL}(상품마다 어드바이저리 잠금 아래 다시 판정). 승인 순간부터 자리를 잡는다. 금액 스냅샷 고정, 소재 버전 고정(A2).
     */
    public void approve(BizOperator actor, long id, String methodText, Long supply, String adjustReasonText, String deadlineText, String startText, String endText) {
        BizFields f = BizFields.start();
        BizPaymentMethod method = f.oneOf("method", methodText, BizPaymentMethod::valueOf);
        if (supply == null || supply < 1) {
            f.add("supply", "bizapp.amount.invalid");
        }
        Period period = period(f, startText, endText, true);
        Instant deadline = f.instant("deadlineAt", deadlineText);
        String adjust = BizFields.strip(adjustReasonText);
        if (adjust != null && adjust.length() > 200) {
            f.add("adjustReason", "bizapp.field.tooLong", 200);
        }
        Instant now = ops.now();
        if (deadline != null && !deadline.isAfter(now)) {
            f.add("deadlineAt", "bizapp.deadline.past");
        } else if (deadline != null && period != null && method == BizPaymentMethod.ONLINE && deadline.isAfter(period.start())) {
            f.add("deadlineAt", "bizapp.deadline.afterStart");
        }
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            BizApplicationStatus to = BizTransitions.require(method == BizPaymentMethod.ONLINE ? Action.APPROVE_ONLINE : Action.APPROVE_MANUAL, app.status());
            List<ItemRow> items = pricing.items(id, products(app), period);
            Amount quote = BizPricing.amountOf(items.stream().mapToLong(ItemRow::supply).sum());
            boolean adjusted = supply != quote.supply();
            if (adjusted && adjust == null) {
                throw com.community.zconfiguration.exception.ValidationException.of("adjustReason", new com.community.zconfiguration.message.MessageRef("bizapp.adjustReason.required"));
            }
            slots.lockAndRequire(items.stream().map(ItemRow::product).toList(), period.start(), period.end(), id);
            Amount approved = BizPricing.amountOf(supply);
            store.replaceItems(id, items, now);
            if (!store.approve(id, to, period.start(), period.end(), quote, approved, adjusted ? adjust : null, method, deadline, actor.operatorId(), now)) {
                throw BizApplicationException.stateChanged();
            }
            store.approveLatestCreative(id, now);
            boolean moved = !period.start().equals(app.startAt()) || !period.end().equals(app.endAt());
            List<String> notes = new ArrayList<>();
            if (moved) {
                notes.add("PERIOD_MOVED");
            }
            if (adjusted) {
                notes.add(adjust);
            }
            ops.event(id, method == BizPaymentMethod.ONLINE ? BizEventAction.APPROVED_ONLINE : BizEventAction.APPROVED_MANUAL, BizActorType.OPERATOR, actor.operatorId(),
                    notes.isEmpty() ? null : String.join(" · ", notes), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", method.name());
            detail.put("supply", supply);
            detail.put("total", approved.total());
            detail.put("startAt", period.start().toString());
            detail.put("endAt", period.end().toString());
            detail.put("deadlineAt", deadline.toString());
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_APPROVE, app, detail);
            if (adjusted) {
                ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_PRICE_ADJUST, app,
                        Map.of("quoteSupply", quote.supply(), "supply", supply, "reason", adjust));
            }
            AppRow after = store.find(id).orElseThrow();
            ops.notices().member(after, method == BizPaymentMethod.ONLINE ? BizApplicationNotices.Kind.APPROVED : BizApplicationNotices.Kind.MANUAL, approved.total(),
                    ServiceTime.dateOf(deadline).toString());
        });
    }

    /** 반려 — 심사 대기만(결제 전이라 환불 없음). 사유 코드 + 글 1~200자 */
    public void reject(BizOperator actor, long id, String codeText, String textValue) {
        BizFields f = BizFields.start();
        BizRejectCode code = f.oneOf("code", codeText, BizRejectCode::valueOf);
        String text = f.text("text", textValue, 200);
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            BizTransitions.require(Action.REJECT, app.status());
            Instant now = ops.now();
            if (!store.reject(id, code, text, now)) {
                throw BizApplicationException.stateChanged();
            }
            ops.event(id, BizEventAction.REJECTED, BizActorType.OPERATOR, actor.operatorId(), code.name(), now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_REJECT, app, Map.of("code", code.name(), "text", text));
            ops.notices().member(app, BizApplicationNotices.Kind.REJECTED, 0, text);
        });
    }

    /** 결제 대기 → 수동 결제(입금 대기) — 열린 결제를 먼저 닫는다. 입금 기한(지금 이후) · 사유 1~200자 */
    public void switchToManual(BizOperator actor, long id, String deadlineText, String reasonText) {
        BizFields f = BizFields.start();
        Instant deadline = f.instant("deadlineAt", deadlineText);
        String reason = f.text("reason", reasonText, 200);
        Instant now = ops.now();
        if (deadline != null && !deadline.isAfter(now)) {
            f.add("deadlineAt", "bizapp.deadline.past");
        }
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = ops.closePaymentsThenLock(id, null, "BIZ_MANUAL_SWITCH");
            BizTransitions.require(Action.MANUAL_SWITCH, app.status());
            if (!store.switchManual(id, deadline, now)) {
                throw BizApplicationException.stateChanged();
            }
            ops.event(id, BizEventAction.MANUAL_SWITCH, BizActorType.OPERATOR, actor.operatorId(), reason, now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_MANUAL_SWITCH, app, Map.of("deadlineAt", deadline.toString(), "reason", reason));
            ops.notices().member(app, BizApplicationNotices.Kind.MANUAL, app.approved().total(), ServiceTime.dateOf(deadline).toString());
        });
    }

    // ── 수동 결제 ───────────────────────────────────────────────────────────

    /**
     * 계약서 사본 올리기 — 입금 대기만. PDF·JPG·PNG(내용 첫 바이트로 판정) 한 장 10MB, 한 번에 1~10장. {@code replaceId} 를 주면 그 사본 하나를 「대체됨」으로(이때 정확히 1장).
     * 원본은 지우지 않는다. 대체되지 않은 사본 수를 같은 트랜잭션에서 맞춘다(수동 확정 CHECK).
     */
    public void uploadContracts(BizOperator actor, long id, List<BizUpload> files, String replaceIdText) {
        List<BizUpload> list = files == null ? List.of() : files;
        Long replaceId = BizFields.strip(replaceIdText) == null ? null : BizFields.id(replaceIdText);
        if (list.isEmpty() || list.size() > FILES_MAX || (BizFields.strip(replaceIdText) != null && (replaceId == null || list.size() != 1))) {
            throw com.community.zconfiguration.exception.ValidationException.of("file", new com.community.zconfiguration.message.MessageRef("bizapp.file.count", FILES_MAX));
        }
        record Prepared(String name, String type, byte[] encrypted, String sha, int size) {
        }
        List<Prepared> prepared = new ArrayList<>();
        cipher.requireConfigured();
        for (BizUpload u : list) {
            byte[] data = u.data();
            if (data == null || data.length == 0) {
                throw com.community.zconfiguration.exception.ValidationException.of("file", new com.community.zconfiguration.message.MessageRef("bizapp.file.required"));
            }
            if (data.length > FILE_MAX_BYTES) {
                throw com.community.zconfiguration.exception.ValidationException.of("file", new com.community.zconfiguration.message.MessageRef("bizapp.file.tooLarge"));
            }
            String type = sniff(data);
            if (type == null) {
                throw com.community.zconfiguration.exception.ValidationException.of("file", new com.community.zconfiguration.message.MessageRef("bizapp.file.type"));
            }
            prepared.add(new Prepared(fileName(u.originalName(), type), type, cipher.encryptBytes(CONTRACT, data), sha256(data), data.length));
        }
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            if (app.status() != BizApplicationStatus.AWAITING_DEPOSIT) {
                throw BizApplicationException.stateChanged();
            }
            Instant now = ops.now();
            List<String> names = new ArrayList<>();
            for (Prepared p : prepared) {
                long fileId = ops.nextId();
                contracts.insert(fileId, id, p.name(), p.type(), p.size(), p.encrypted(), p.sha(), actor.operatorId(), now);
                if (replaceId != null && !contracts.replace(replaceId, id, fileId, actor.operatorId(), now)) {
                    throw BizApplicationException.stateChanged();
                }
                names.add(p.name());
            }
            store.setContractCount(id, contracts.activeCount(id), now);
            ops.event(id, replaceId == null ? BizEventAction.CONTRACT_RECEIVED : BizEventAction.CONTRACT_REPLACED, BizActorType.OPERATOR, actor.operatorId(),
                    String.valueOf(prepared.size()), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("files", names);
            if (replaceId != null) {
                detail.put("replaced", Long.toString(replaceId));
            }
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_CONTRACT_UPLOAD, app, detail);
        });
    }

    /** 계약서 사본 열람 — 열 때마다 처리 기록 + 감사(어느 상태든) */
    public BizContractFile viewContract(BizOperator actor, long id, long fileId) {
        return tx.execute(status -> {
            AppRow app = store.find(id).orElseThrow(BizApplicationException::notFound);
            ContractRow row = contracts.find(id, fileId).orElseThrow(BizApplicationException::contractNotFound);
            byte[] plain = cipher.decryptBytes(CONTRACT, contracts.content(id, fileId).orElseThrow(BizApplicationException::contractNotFound));
            Instant now = ops.now();
            ops.event(id, BizEventAction.CONTRACT_VIEWED, BizActorType.OPERATOR, actor.operatorId(), row.fileName(), now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_CONTRACT_VIEW, app, Map.of("fileId", Long.toString(fileId), "fileName", row.fileName()));
            return new BizContractFile("data:" + row.contentType() + ";base64," + Base64.getEncoder().encodeToString(plain), row.contentType(), row.fileName());
        });
    }

    /**
     * 입금 확인 기록 — 입금 대기만. 입금액 ≥ 1, 입금일 ≤ 오늘(한국), 입금자명 1~60자, 입금액 ≠ 확정 금액이면 사유 1~200자. 고치면 덮는다(처리 기록·감사 {@code BIZ_DEPOSIT_CONFIRM}).
     */
    public void saveDeposit(BizOperator actor, long id, Long amount, String depositedOnText, String depositorNameText, String mismatchText) {
        BizFields f = BizFields.start();
        if (amount == null || amount < 1) {
            f.add("amount", "bizapp.amount.invalid");
        }
        LocalDate depositedOn = f.date("depositedOn", depositedOnText);
        if (depositedOn != null && depositedOn.isAfter(ServiceTime.today(ops.clock()))) {
            f.add("depositedOn", "bizapp.depositedOn.future");
        }
        String depositor = f.text("depositorName", depositorNameText, 60);
        String mismatch = BizFields.strip(mismatchText);
        if (mismatch != null && mismatch.length() > 200) {
            f.add("mismatchReason", "bizapp.field.tooLong", 200);
        }
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            if (app.status() != BizApplicationStatus.AWAITING_DEPOSIT) {
                throw BizApplicationException.stateChanged();
            }
            boolean differs = amount != app.approved().total();
            if (differs && mismatch == null) {
                throw com.community.zconfiguration.exception.ValidationException.of("mismatchReason",
                        new com.community.zconfiguration.message.MessageRef("bizapp.mismatchReason.required"));
            }
            Instant now = ops.now();
            store.upsertDeposit(id, amount, depositedOn, depositor, differs ? mismatch : null, actor.operatorId(), now);
            store.setDepositConfirmed(id, now, now);
            ops.event(id, BizEventAction.DEPOSIT_CONFIRMED, BizActorType.OPERATOR, actor.operatorId(), differs ? "MISMATCH · " + mismatch : null, now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("amount", amount);
            detail.put("depositedOn", depositedOn.toString());
            detail.put("approvedTotal", app.approved().total());
            if (differs) {
                detail.put("mismatchReason", mismatch);
            }
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_DEPOSIT_CONFIRM, app, detail);
        });
    }

    /** 예약 확정(수동) — 대체되지 않은 계약서 1장 이상 + 입금 기록 둘 다 없으면 409 {@code BIZ_DEPOSIT_INCOMPLETE}. DB CHECK 가 한 번 더 막는다 */
    public void confirm(BizOperator actor, long id) {
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            BizTransitions.require(Action.CONFIRM, app.status());
            Instant now = ops.now();
            int active = contracts.activeCount(id);
            DepositRow deposit = store.deposit(id).orElse(null);
            if (active < 1 || deposit == null) {
                throw BizApplicationException.depositIncomplete();
            }
            store.setContractCount(id, active, now);
            ops.move(app, Action.CONFIRM, now);
            ops.event(id, BizEventAction.SCHEDULED, BizActorType.OPERATOR, actor.operatorId(), null, now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_CONFIRM, app, Map.of("contracts", active, "deposit", deposit.amount()));
            ops.notices().member(app, BizApplicationNotices.Kind.CONFIRMED, deposit.amount(), null);
            ops.scheduled(app);
        });
    }

    /** 입금 기한 바꾸기(연장) — 입금 대기만, 지금 이후 */
    public void extendDeposit(BizOperator actor, long id, String deadlineText) {
        BizFields f = BizFields.start();
        Instant deadline = f.instant("deadlineAt", deadlineText);
        if (deadline != null && !deadline.isAfter(ops.now())) {
            f.add("deadlineAt", "bizapp.deadline.past");
        }
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            Instant now = ops.now();
            if (app.status() != BizApplicationStatus.AWAITING_DEPOSIT || !store.setDepositDeadline(id, deadline, now)) {
                throw BizApplicationException.stateChanged();
            }
            ops.event(id, BizEventAction.DEPOSIT_DEADLINE, BizActorType.OPERATOR, actor.operatorId(), ServiceTime.dateOf(deadline).toString(), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("from", app.depositDeadlineAt() == null ? null : app.depositDeadlineAt().toString());
            detail.put("to", deadline.toString());
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_DEPOSIT_DEADLINE, app, detail);
        });
    }

    /**
     * 운영자 취소 — 결제 대기·입금 대기(입금 기록이 있으면 수동 전액 환불 송금 대기) · 시작 전 예약(전액 환불). 진행 중은 환불 승인으로만. 사유 1~200자.
     */
    public void cancel(BizOperator actor, long id, String reasonText) {
        BizFields f = BizFields.start();
        String reason = f.text("reason", reasonText, 200);
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = ops.closePaymentsThenLock(id, null, "BIZ_OPERATOR_CANCEL");
            BizTransitions.require(Action.OPERATOR_CANCEL, app.status());
            Instant now = ops.now();
            long refunded = 0;
            if (app.status() == BizApplicationStatus.SCHEDULED) {
                if (BizPhase.of(app.startAt(), app.endAt(), now) != BizPhase.UPCOMING || refunds.lockOpen(id).isPresent()) {
                    throw BizApplicationException.stateChanged();
                }
                refunded = ops.paidAmount(app);
                ops.cancelWithFullRefund(app, BizActorType.OPERATOR, actor.operatorId(), reason, now);
            } else {
                refunded = app.status() == BizApplicationStatus.AWAITING_DEPOSIT ? store.deposit(id).map(DepositRow::amount).orElse(0L) : 0;
                ops.cancelBeforePayment(app, Action.OPERATOR_CANCEL, BizActorType.OPERATOR, actor.operatorId(), reason, now);
            }
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_CANCEL, app, Map.of("from", app.status().name(), "reason", reason, "refund", refunded));
            ops.notices().member(app, BizApplicationNotices.Kind.CANCELED, refunded, reason);
        });
    }

    // ── 환불 심사 ───────────────────────────────────────────────────────────

    /**
     * 환불 승인 — 심사 대기 줄만. 0 ≤ 금액 ≤ 결제액 − 이미 환불, 사유 1~200자. 노출을 멈추고(취소) 온라인은 금액 > 0 이면 PG 취소, 수동은 송금 대기.
     */
    public void approveRefund(BizOperator actor, long id, Long amount, String reasonText) {
        BizFields f = BizFields.start();
        if (amount == null || amount < 0) {
            f.add("amount", "bizapp.refund.amount");
        }
        String reason = f.text("reason", reasonText, 200);
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            RefundRow open = refunds.lockOpen(id).orElseThrow(BizApplicationException::stateChanged);
            BizTransitions.require(Action.REFUND_APPROVE, app.status());
            long limit = open.paidAmount() - refunds.approvedTotal(id);
            if (amount > limit) {
                throw com.community.zconfiguration.exception.ValidationException.of("amount", new com.community.zconfiguration.message.MessageRef("bizapp.refund.amount"));
            }
            Instant now = ops.now();
            boolean manual = open.method() == BizPaymentMethod.MANUAL;
            if (!refunds.decide(open.id(), BizRefundStatus.APPROVED, amount, reason, manual && amount > 0 ? BizRefundStore.TRANSFER_PENDING : null,
                    amount == 0 ? now : null, actor.operatorId(), now)) {
                throw BizApplicationException.stateChanged();
            }
            ops.move(app, Action.REFUND_APPROVE, now);
            if (!manual && amount > 0) {
                ops.requestPgCancel(app, open.id(), amount, actor.operatorId());
            }
            ops.event(id, BizEventAction.REFUND_APPROVED, BizActorType.OPERATOR, actor.operatorId(), String.valueOf(amount), now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_REFUND_APPROVE, app,
                    Map.of("refundId", Long.toString(open.id()), "amount", amount, "proposed", open.proposedAmount(), "reason", reason));
            ops.notices().member(app, BizApplicationNotices.Kind.REFUND_APPROVED, amount, null);
            ops.closedAfterSchedule(app);
        });
    }

    /** 환불 거절 — 심사 대기 줄만, 상태는 그대로(예약·내림) */
    public void denyRefund(BizOperator actor, long id, String reasonText) {
        BizFields f = BizFields.start();
        String reason = f.text("reason", reasonText, 200);
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            RefundRow open = refunds.lockOpen(id).orElseThrow(BizApplicationException::stateChanged);
            Instant now = ops.now();
            if (!refunds.decide(open.id(), BizRefundStatus.DENIED, null, reason, null, null, actor.operatorId(), now)) {
                throw BizApplicationException.stateChanged();
            }
            ops.event(id, BizEventAction.REFUND_DENIED, BizActorType.OPERATOR, actor.operatorId(), reason, now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_REFUND_DENY, app, Map.of("refundId", Long.toString(open.id()), "reason", reason));
            ops.notices().member(app, BizApplicationNotices.Kind.REFUND_DENIED, 0, reason);
        });
    }

    /** 수동 환불 송금 완료 표시 — 가장 최근 환불이 송금 대기일 때만. 메모 0~200자 */
    public void markTransferred(BizOperator actor, long id, String noteText) {
        String note = BizFields.strip(noteText);
        if (note != null && note.length() > 200) {
            throw com.community.zconfiguration.exception.ValidationException.of("note", new com.community.zconfiguration.message.MessageRef("bizapp.field.tooLong", 200));
        }
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            RefundRow latest = refunds.latest(id).orElseThrow(BizApplicationException::stateChanged);
            Instant now = ops.now();
            if (!BizRefundStore.TRANSFER_PENDING.equals(latest.transferStatus()) || !refunds.markTransferred(latest.id(), actor.operatorId(), note, now)) {
                throw BizApplicationException.stateChanged();
            }
            long amount = latest.approvedAmount() == null ? 0 : latest.approvedAmount();
            ops.event(id, BizEventAction.REFUND_TRANSFERRED, BizActorType.OPERATOR, actor.operatorId(), String.valueOf(amount), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("refundId", Long.toString(latest.id()));
            detail.put("amount", amount);
            if (note != null) {
                detail.put("note", note);
            }
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_REFUND_TRANSFER, app, detail);
            ops.notices().member(app, BizApplicationNotices.Kind.REFUNDED, amount, null);
        });
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private List<BizProduct> products(AppRow app) {
        return store.items(app.id()).stream().map(ItemRow::product).toList();
    }

    /** 관리자 기간 — ISO 순간, 분 단위, 끝 > 시작. {@code future} 면 시작이 지금 뒤여야 한다(F6) */
    private Period period(BizFields f, String startText, String endText, boolean future) {
        Instant start = f.instant("period", startText);
        Instant end = f.has("period") ? null : f.instant("period", endText);
        if (start == null || end == null) {
            return null;
        }
        if (!end.isAfter(start) || !BizPeriods.minuteAligned(start) || !BizPeriods.minuteAligned(end)) {
            f.add("period", "bizapp.period.invalid");
            return null;
        }
        if (future && !start.isAfter(ops.now())) {
            f.add("period", "bizapp.period.started");
            return null;
        }
        return new Period(start, end);
    }

    /** 첫 바이트로 종류 판정 — PDF {@code %PDF-} · JPEG {@code FF D8 FF} · PNG {@code 89 50 4E 47 0D 0A 1A 0A} */
    static String sniff(byte[] d) {
        if (d.length >= 5 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F' && d[4] == '-') {
            return "application/pdf";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G' && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }

    /** 표시용 파일 이름 — 경로·제어 문자를 빼고 120자 */
    static String fileName(String original, String type) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty()) {
            name = "contract" + (type.equals("application/pdf") ? ".pdf" : type.equals("image/png") ? ".png" : ".jpg");
        }
        return name.length() <= 120 ? name : name.substring(name.length() - 120);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
