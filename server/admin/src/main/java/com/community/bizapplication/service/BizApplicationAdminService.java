package com.community.bizapplication.service;

import com.community.bizapplication.dto.BizAdminRequest;
import com.community.bizapplication.dto.BizAdminView;
import com.community.bizapplication.dto.BizContractFile;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.dto.BizUpload;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizDomain;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizApplicationReader.Queue;
import com.community.bizapplication.service.BizApplicationReader.Snapshot;
import com.community.bizapplication.service.BizApplicationReader.Summary;
import com.community.bizapplication.service.BizPricing.Amount;
import com.community.business.service.BusinessFields;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자 신청 건 심사(admin-web {@code /ad-reviews}·{@code /hotdeal-reviews}) — 응답 만들기와 입력 꺼내기·도메인 권한. 처리 규칙은 shared {@link BizApplicationReviewService},
 * 읽기는 {@link BizApplicationReader}. 큐(탭)는 상태·기한·환불 요청으로 서버가 가른다(환불 심사 대기가 먼저, 입금 대기는 기한이 지났으면 「입금 기한 지남」).
 *
 * <p><b>도메인 권한</b>(04-ia-review S2): 컨트롤러 입구는 두 도메인 중 하나의 권한(anyOf)만 본다. 여기서 목록은 고른 {@code domain}, 상세·기간 판정·처리는 <b>건의 종류가 속한
 * 도메인</b>으로 조회({@code ad.read}·{@code hotdeal.read})·처리({@code ad.manage}·{@code hotdeal.manage}) 권한을 다시 보고, 없으면 403 {@code PERMISSION_DENIED} + 거부 감사.
 * 환불(승인·거절·송금 완료)은 두 도메인 모두 {@code deal-order.refund} 하나라 다시 보지 않는다(Q2).
 */
@Service
public class BizApplicationAdminService {

    private final BizApplicationReader reader;
    private final BizApplicationReviewService review;
    private final Messages messages;
    private final AccessDeniedRecorder denied;
    private final BizApplicationProperties props;

    public BizApplicationAdminService(BizApplicationReader reader, BizApplicationReviewService review, Messages messages, AccessDeniedRecorder denied,
                                      BizApplicationProperties props) {
        this.props = props;
        this.reader = reader;
        this.review = review;
        this.messages = messages;
        this.denied = denied;
    }

    // ── 목록·상세 ───────────────────────────────────────────────────────────

    /**
     * 큐 목록 + 큐별 건수(그 도메인 안, 종류·검색 거르기 뒤). {@code domain} 필수(없거나 틀리면 400 {@code fields.domain}) → 그 도메인 조회 권한(403) →
     * 큐·종류(그 도메인의 종류가 아니면 400 {@code fields.kind}).
     */
    public BizAdminView.Page list(AdminActor actor, String domainText, String queueText, String kindText, String q) {
        if (domainText == null || domainText.isBlank()) {
            throw ValidationException.of("domain", new MessageRef("bizapp.field.required"));
        }
        BizDomain domain = parse(BizDomain.class, domainText, "domain");
        require(actor, domain.readPermission(), "ENDPOINT", "GET /api/admin/biz-applications?domain=" + domain.name());
        Queue queue = queueText == null || queueText.isBlank() ? Queue.REVIEW : parse(Queue.class, queueText, "queue");
        BizApplicationKind kind = kindText == null || kindText.isBlank() ? null : parse(BizApplicationKind.class, kindText, "kind");
        if (kind != null && kind.domain() != domain) {
            throw ValidationException.of("kind", new MessageRef("bizapp.value.invalid"));
        }
        Instant now = reader.now();
        List<Summary> all = reader.summaries(reader.forAdmin(domain, kind, q));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Queue x : Queue.values()) {
            counts.put(x.name(), 0L);
        }
        List<Summary> picked = new ArrayList<>();
        for (Summary s : all) {
            Queue at = BizApplicationReader.queue(s.app(), s.refund(), now);
            counts.merge(at.name(), 1L, Long::sum);
            if (at == queue) {
                picked.add(s);
            }
        }
        Comparator<Summary> order = switch (queue) {
            case REVIEW -> Comparator.comparing((Summary s) -> s.app().createdAt());
            case PAYMENT, DEPOSIT, DEPOSIT_OVERDUE -> Comparator.comparing((Summary s) -> s.app().deadlineAt(), Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing((Summary s) -> s.app().createdAt()).reversed();
        };
        picked.sort(order);
        return new BizAdminView.Page(picked.stream().map(s -> row(s.app(), s.items(), s.refund(), now)).toList(), counts);
    }

    public BizAdminView.Detail detail(AdminActor actor, long id) {
        canRead(actor, id);
        return view(actor, id);
    }

    public BizAdminView.Availability availability(AdminActor actor, long id, String startAt, String endAt) {
        canRead(actor, id);
        BizApplicationReviewService.Availability a = review.availability(id, startAt, endAt);
        return new BizAdminView.Availability(quote(a.items(), a.quote()), a.room().remaining(), a.room().capacity());
    }

    // ── 처리 ───────────────────────────────────────────────────────────────

    public BizAdminView.Detail approve(AdminActor actor, long id, BizAdminRequest.Approve body) {
        canManage(actor, id);
        BizAdminRequest.Approve b = body == null ? new BizAdminRequest.Approve(null, null, null, null, null, null) : body;
        BizAdminRequest.Period p = b.period() == null ? new BizAdminRequest.Period(null, null) : b.period();
        review.approve(op(actor), id, b.method(), b.supply(), b.adjustReason(), b.deadlineAt(), p.startAt(), p.endAt(), b.memo());
        return view(actor, id);
    }

    public BizAdminView.Detail reject(AdminActor actor, long id, BizAdminRequest.Reject body) {
        canManage(actor, id);
        review.reject(op(actor), id, body == null ? null : body.code(), body == null ? null : body.text());
        return view(actor, id);
    }

    public BizAdminView.Detail switchToManual(AdminActor actor, long id, BizAdminRequest.Manual body) {
        canManage(actor, id);
        review.switchToManual(op(actor), id, body == null ? null : body.deadlineAt(), body == null ? null : body.reason());
        return view(actor, id);
    }

    public BizAdminView.Detail uploadContracts(AdminActor actor, long id, List<MultipartFile> files, String replaceId) {
        canManage(actor, id);
        List<BizUpload> uploads = new ArrayList<>();
        for (MultipartFile f : files == null ? List.<MultipartFile>of() : files) {
            try {
                uploads.add(new BizUpload(f.getOriginalFilename(), f.isEmpty() ? null : f.getBytes()));
            } catch (IOException e) {
                throw ValidationException.of("file", new MessageRef("bizapp.file.required"));
            }
        }
        review.uploadContracts(op(actor), id, uploads, replaceId);
        return view(actor, id);
    }

    public BizAdminView.ContractFile contract(AdminActor actor, long id, long fileId) {
        canManage(actor, id);
        BizContractFile file = review.viewContract(op(actor), id, fileId);
        return new BizAdminView.ContractFile(file.url(), file.mimeType(), file.fileName());
    }

    public BizAdminView.Detail saveDeposit(AdminActor actor, long id, BizAdminRequest.Deposit body) {
        canManage(actor, id);
        BizAdminRequest.Deposit b = body == null ? new BizAdminRequest.Deposit(null, null, null, null) : body;
        review.saveDeposit(op(actor), id, b.amount(), b.depositedOn(), b.depositorName(), b.mismatchReason());
        return view(actor, id);
    }

    public BizAdminView.Detail confirm(AdminActor actor, long id) {
        canManage(actor, id);
        review.confirm(op(actor), id);
        return view(actor, id);
    }

    public BizAdminView.Detail extendDeposit(AdminActor actor, long id, BizAdminRequest.Deadline body) {
        canManage(actor, id);
        review.extendDeposit(op(actor), id, body == null ? null : body.deadlineAt());
        return view(actor, id);
    }

    public BizAdminView.Detail cancel(AdminActor actor, long id, BizAdminRequest.Reason body) {
        canManage(actor, id);
        review.cancel(op(actor), id, body == null ? null : body.reason());
        return view(actor, id);
    }

    public BizAdminView.Detail approveRefund(AdminActor actor, long id, BizAdminRequest.RefundApprove body) {
        review.approveRefund(op(actor), id, body == null ? null : body.amount(), body == null ? null : body.reason());
        return view(actor, id);
    }

    public BizAdminView.Detail denyRefund(AdminActor actor, long id, BizAdminRequest.Reason body) {
        review.denyRefund(op(actor), id, body == null ? null : body.reason());
        return view(actor, id);
    }

    public BizAdminView.Detail markTransferred(AdminActor actor, long id, BizAdminRequest.Transfer body) {
        review.markTransferred(op(actor), id, body == null ? null : body.note());
        return view(actor, id);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 처리 뒤 응답 — 권한은 이미 봤다 */
    private BizAdminView.Detail view(AdminActor actor, long id) {
        return detailOf(reader.snapshot(id), actor);
    }

    /** 건의 도메인 조회 권한(없는 건은 404 가 먼저) */
    private void canRead(AdminActor actor, long id) {
        require(actor, reader.app(id).kind().domain().readPermission(), BizApplicationOps.AUDIT_TARGET, Long.toString(id));
    }

    /** 건의 도메인 처리 권한(없는 건은 404 가 먼저) */
    private void canManage(AdminActor actor, long id) {
        require(actor, reader.app(id).kind().domain().managePermission(), BizApplicationOps.AUDIT_TARGET, Long.toString(id));
    }

    private void require(AdminActor actor, Permission permission, String targetType, String targetId) {
        if (!actor.can(permission)) {
            denied.record(actor, "MISSING_PERMISSION", targetType, targetId, List.of(permission.code()));
            throw new PermissionDeniedException();
        }
    }

    private BizAdminView.Row row(AppRow a, List<ItemRow> items, RefundRow refund, Instant now) {
        BizProduct capacity = BizApplicationReader.capacityProduct(items);
        return new BizAdminView.Row(Long.toString(a.id()), a.no(), a.kind().name(), capacity == null ? null : capacity.name(), BizApplicationReader.adminStatus(a, now),
                business(a), a.createdAt(), new BizAdminView.Period(a.startAt(), a.endAt()), a.preferredMethod().name(),
                a.approved() != null ? a.approved().total() : a.quote().total(), a.approved() == null ? null : a.deadlineAt(), BizApplicationReader.refundOpen(refund),
                a.source().name());
    }

    private BizAdminView.Detail detailOf(Snapshot s, AdminActor actor) {
        Instant now = reader.now();
        AppRow a = s.app();
        BizAdminView.Row row = row(a, s.items(), s.refund(), now);
        BizProduct capacity = BizApplicationReader.capacityProduct(s.items());
        int remaining = 1;
        int limit = 1;
        if (capacity != null) {
            BizApplicationReviewService.Availability room = review.availability(a.id(), a.startAt().toString(), a.endAt().toString());
            remaining = room.room().remaining();
            limit = room.room().capacity();
        }
        BizAdminView.Approval approval = a.approved() == null ? null : new BizAdminView.Approval(a.paymentMethod().name(), amount(a.approved()), a.adjustReason(),
                a.approvedAt(), s.approvedByName());
        BizAdminView.OnlinePayment online = a.paidAt() == null ? null : new BizAdminView.OnlinePayment(a.paidAt(), s.payment() == null ? null : s.payment().pgTxId());
        List<BizAdminView.Contract> contracts = s.contracts().stream().map(c -> new BizAdminView.Contract(Long.toString(c.id()), c.fileName(), c.contentType(),
                c.sizeBytes(), c.uploadedAt(), c.uploadedByName(), c.replacedAt(), c.replacedByName())).toList();
        BizAdminView.Deposit deposit = s.deposit() == null ? null : new BizAdminView.Deposit(s.deposit().amount(), s.deposit().depositedOn(), s.deposit().depositorName(),
                s.deposit().mismatchReason(), s.deposit().confirmedByName(), s.deposit().confirmedAt());
        RefundRow r = s.refund();
        BizAdminView.Refund refund = r == null ? null : new BizAdminView.Refund(r.requestedAt(), r.reason(), r.paidAmount(), r.proposedAmount(), r.usedDays(),
                r.totalDays(), r.status().name(), r.type().name(), r.method().name(), r.approvedAmount(), r.decisionReason(), r.transferStatus(), r.transferredAt(),
                r.completedAt());
        BizAdminView.Rejection rejection = a.rejectCode() == null ? null : new BizAdminView.Rejection(a.rejectCode().name(), a.rejectText());
        BizAdminView.Takedown takedown = a.takenDownAt() == null ? null : new BizAdminView.Takedown(a.takenDownAt(), a.takedownCause().name(), a.takedownText());
        List<BizAdminView.History> history = s.events().stream().map(e -> new BizAdminView.History(e.at(), e.action(), actorName(e.actorType(), e.actorName()),
                e.note())).toList();
        String policy = a.adPolicyVersion() == null ? null : messages.get(new MessageRef("bizapp.admin.policyVersion", a.adPolicyVersion(), a.refundPolicyVersion()));
        String applicant = s.applicantName();
        return new BizAdminView.Detail(row.id(), row.no(), row.kind(), row.adType(), row.status(), row.business(), row.submittedAt(), row.period(), row.preferredMethod(),
                row.total(), row.deadlineAt(), row.hasRefundRequest(), a.source().name(), applicant, content(a, s.creative() == null ? Map.of() : s.creative().content()),
                a.operatorMemo(), quote(s.items(), a.quote()), remaining, limit, approval, online, contracts, deposit, refund, rejection, takedown, policy, history,
                a.proposedSupply(), a.weight(), registrant(a, actor), props.selfApproval().name());
    }

    /** 종류별 내용 — admin-web {@code BizApplicationContent} 모양 */
    private static Map<String, Object> content(AppRow a, Map<String, Object> c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", a.kind().name());
        switch (a.kind()) {
            case AD_BANNER -> {
                out.put("imageFileId", c.get("imageFileId"));
                out.put("imageUrl", c.get("imageUrl"));
                out.put("linkUrl", c.get("linkUrl"));
                out.put("altText", c.get("altText"));
            }
            case AD_TEXT_LINE -> {
                out.put("text", c.get("text"));
                out.put("linkUrl", c.get("linkUrl"));
            }
            case HOTDEAL_POST -> {
                out.put("title", c.get("title"));
                out.put("body", c.get("body"));
                out.put("mallName", c.get("shopName"));
                out.put("dealUrl", c.get("linkUrl"));
                out.put("price", c.get("dealPrice"));
                out.put("originalPrice", c.get("listPrice"));
                out.put("shippingFee", c.get("shippingFee"));
                out.put("endsOn", c.get("endsOn"));
                out.put("stock", c.get("stock"));
                out.put("placement", c.get("placement"));
                out.put("affiliate", c.get("affiliate"));
            }
            case HOTDEAL_PLACEMENT -> {
                out.put("postId", c.get("postId"));
                out.put("postTitle", c.get("postTitle"));
                out.put("price", c.get("price"));
                out.put("originalPrice", c.get("originalPrice"));
                out.put("mallName", c.get("mallName"));
                out.put("categoryName", c.get("categoryName"));
            }
        }
        return out;
    }

    private static BizAdminView.Quote quote(List<ItemRow> items, Amount total) {
        BizProduct capacity = BizApplicationReader.capacityProduct(items);
        ItemRow main = items.stream().filter(i -> i.product() == capacity).findFirst().orElse(items.isEmpty() ? null : items.get(0));
        List<BizAdminView.QuoteLine> lines = items.stream().map(i -> new BizAdminView.QuoteLine(i.product().name(), i.product() == BizProduct.HOTDEAL_POST ? "POST" : "DAY",
                i.unitPrice(), i.units(), i.supply())).toList();
        return new BizAdminView.Quote(main == null ? 0 : main.unitPrice(), main == null ? 0 : main.units(),
                main == null || main.product() == BizProduct.HOTDEAL_POST ? "POST" : "DAY", total.supply(), total.vat(), total.total(), lines);
    }

    /** 운영자 등록 건의 등록자(사업자 신청 건은 null) */
    private BizAdminView.Registrant registrant(AppRow a, AdminActor actor) {
        if (a.source() != com.community.bizapplication.entity.BizSource.OPERATOR || a.createdBy() == null) {
            return null;
        }
        return new BizAdminView.Registrant(Long.toString(a.createdBy()), reader.operatorName(a.createdBy()), a.createdBy() == actor.operatorId());
    }

    private static BizAdminView.Business business(AppRow a) {
        return new BizAdminView.Business(a.businessProfileId() == null ? null : Long.toString(a.businessProfileId()), a.businessName(),
                BusinessFields.maskBizNo(a.bizNoLast4()));
    }

    private String actorName(BizActorType type, String name) {
        if (type == BizActorType.SYSTEM) {
            return messages.get(new MessageRef("business.history.actor.SYSTEM"));
        }
        return name == null ? messages.get(new MessageRef("business.history.actor.UNKNOWN")) : name;
    }

    private static BizAdminView.Amount amount(Amount a) {
        return new BizAdminView.Amount(a.supply(), a.vat(), a.total());
    }

    private static BizOperator op(AdminActor actor) {
        return new BizOperator(actor.operatorId(), actor.ip());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String text, String field) {
        try {
            return Enum.valueOf(type, text.strip());
        } catch (IllegalArgumentException e) {
            throw ValidationException.of(field, new MessageRef("bizapp.value.invalid"));
        }
    }

    /** ID 글자 — 틀리면 404 */
    public static long parseId(String text) {
        try {
            return Long.parseLong(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            throw BizApplicationException.notFound();
        }
    }
}
