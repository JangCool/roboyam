package com.community.docintake.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizContractStore;
import com.community.bizapplication.repository.master.BizContractStore.ContractRow;
import com.community.bizapplication.service.BizApplicationOps;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.bizapplication.service.BizApplicationReviewService;
import com.community.bizapplication.service.BizFields;
import com.community.business.entity.BusinessCertificateDelivery;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessStatus;
import com.community.business.exception.BusinessException;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessStore;
import com.community.business.repository.master.BusinessStore.ProfileFileRow;
import com.community.business.service.BusinessNtsService;
import com.community.business.service.BusinessPii;
import com.community.docintake.dto.DocIntakeAdminRequest;
import com.community.docintake.dto.DocIntakeAdminView;
import com.community.docintake.entity.DocFileChannel;
import com.community.docintake.entity.DocIntakeChannel;
import com.community.docintake.entity.DocIntakeDoc;
import com.community.docintake.entity.DocIntakeHandling;
import com.community.docintake.exception.DocIntakeException;
import com.community.docintake.repository.master.DocIntakeStore;
import com.community.docintake.repository.master.DocIntakeStore.IntakeRow;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자 서류 「접수 기록」(admin-web {@code useDocIntakeApi}, docs/features/biz-doc-intake-contract.md) — 인쇄본·종이 팩스로 받은 계약서 사본(신청 건)·사업자등록증 사본(사업자 신원)의
 * 접수·처리 바꾸기·폐기·잘못 기록, 그리고 운영자의 등록증 사본 올리기(전자 팩스·스캔). 쓰기는 대상 줄(신청 건 {@code FOR UPDATE} · 사업자 신원 비관 잠금)을 잠근 트랜잭션에서 하고
 * 처리 기록(신청 건 {@code biz_application_events} · 사업자 {@code business_history})·감사를 같이 남긴다. 쓰기 응답은 그 대상의 묶음 전체.
 *
 * <ul>
 *   <li>권한: 계약서는 건의 도메인 권한(조회 {@code ad.read}·{@code hotdeal.read} / 쓰기 {@code ad.manage}·{@code hotdeal.manage}) — 컨트롤러 입구는 anyOf, 여기서 건의 도메인으로 다시 본다.
 *       등록증은 컨트롤러의 {@code business.read}·{@code business.manage}.</li>
 *   <li>계약서 새 접수·잘못 기록은 계약서 올리기 허용 상태(입금 대기 · 운영자 등록 건 심사 대기)에서만(아니면 409 {@code DOC_INTAKE_STATE_CHANGED}) — 근거 수를 같은 잠금 아래 맞춘다.
 *       처리 바꾸기·폐기는 확정 뒤에도 된다. 등록증 새 접수는 탈퇴 상태가 아닐 때만.</li>
 *   <li>처리 바꾸기·폐기·잘못 기록은 열린 줄(잘못 기록 아님 · 폐기 안 함)만 — 아니면 409. 다른 대상의 줄·없는 줄은 404 {@code DOC_INTAKE_NOT_FOUND}.</li>
 *   <li>계약서 폐기는 그 건의 대체되지 않은 계약서 파일을 스캔본으로 고른다(필수 — 5년 보존은 파일로). 등록증 폐기의 스캔본은 선택(그 사업자의 등록증 파일).</li>
 * </ul>
 */
@Service
public class DocIntakeAdminService {

    static final int PAGES_MIN = 1;
    static final int PAGES_MAX = 200;
    static final int MEMO_MAX = 200;
    static final int STORAGE_MAX = 100;
    static final int DISPOSE_REASON_MAX = 200;
    static final int VOID_REASON_MIN = 5;
    static final int VOID_REASON_MAX = 200;
    /** 등록증 사본 한 장 최대(10MB — 회원 올리기와 같다) */
    static final int FILE_MAX_BYTES = 10 * 1024 * 1024;

    private final DocIntakeStore intakes;
    private final BizApplicationStore apps;
    private final BizContractStore contracts;
    private final BizApplicationReader reader;
    private final BizApplicationOps ops;
    private final BusinessProfileRepository profiles;
    private final BusinessStore businessStore;
    private final BusinessPii pii;
    private final AuditLogService audit;
    private final AccessDeniedRecorder denied;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public DocIntakeAdminService(DocIntakeStore intakes, BizApplicationStore apps, BizContractStore contracts, BizApplicationReader reader, BizApplicationOps ops,
                                 BusinessProfileRepository profiles, BusinessStore businessStore, BusinessPii pii, AuditLogService audit, AccessDeniedRecorder denied,
                                 SnowflakeIdGenerator ids, PlatformTransactionManager transactions) {
        this.intakes = intakes;
        this.apps = apps;
        this.contracts = contracts;
        this.reader = reader;
        this.ops = ops;
        this.profiles = profiles;
        this.businessStore = businessStore;
        this.pii = pii;
        this.audit = audit;
        this.denied = denied;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.readTx = new TransactionTemplate(transactions);
        this.readTx.setReadOnly(true);
    }

    // ── 계약서 사본(신청 건) ─────────────────────────────────────────────────

    public DocIntakeAdminView.Bundle contractBundle(AdminActor actor, long applicationId) {
        canRead(actor, applicationId);
        return readTx.execute(status -> contractBundleOf(applicationId));
    }

    public DocIntakeAdminView.Bundle createContract(AdminActor actor, long applicationId, DocIntakeAdminRequest.Create body) {
        canManage(actor, applicationId);
        CreateInput in = createInput(body);
        return tx.execute(status -> {
            AppRow app = apps.lock(applicationId).orElseThrow(BizApplicationException::notFound);
            if (!BizApplicationReviewService.contractsAllowed(app)) {
                throw DocIntakeException.stateChanged();
            }
            Instant now = ops.now();
            long intakeId = ops.nextId();
            intakes.insert(in.row(intakeId, DocIntakeDoc.CONTRACT, applicationId, actor.operatorId(), now));
            apps.setPaperContractCount(applicationId, intakes.contractEvidence(applicationId), now);
            ops.event(applicationId, BizEventAction.PAPER_RECEIVED, BizActorType.OPERATOR, actor.operatorId(), in.channel().name(), now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_PAPER_RECEIVE, app, in.detail(intakeId));
            return contractBundleOf(applicationId);
        });
    }

    public DocIntakeAdminView.Bundle changeContractHandling(AdminActor actor, long applicationId, String intakeIdText, DocIntakeAdminRequest.Handling body) {
        canManage(actor, applicationId);
        long intakeId = intakeId(intakeIdText);
        HandlingInput in = handlingInput(body);
        return tx.execute(status -> {
            AppRow app = apps.lock(applicationId).orElseThrow(BizApplicationException::notFound);
            IntakeRow row = openRow(DocIntakeDoc.CONTRACT, applicationId, intakeId);
            Instant now = ops.now();
            if (!intakes.changeHandling(intakeId, in.handling(), in.storageLocation(), now)) {
                throw DocIntakeException.stateChanged();
            }
            ops.event(applicationId, BizEventAction.PAPER_HANDLING, BizActorType.OPERATOR, actor.operatorId(), in.handling().name(), now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_PAPER_HANDLING, app, handlingDetail(row, in));
            return contractBundleOf(applicationId);
        });
    }

    public DocIntakeAdminView.Bundle disposeContract(AdminActor actor, long applicationId, String intakeIdText, DocIntakeAdminRequest.Dispose body) {
        canManage(actor, applicationId);
        long intakeId = intakeId(intakeIdText);
        DisposeInput in = disposeInput(body, true);
        return tx.execute(status -> {
            AppRow app = apps.lock(applicationId).orElseThrow(BizApplicationException::notFound);
            IntakeRow row = openRow(DocIntakeDoc.CONTRACT, applicationId, intakeId);
            requireDisposedAfterReceived(row, in);
            ContractRow scan = contracts.find(applicationId, in.scanFileId()).filter(ContractRow::active).orElse(null);
            if (scan == null) {
                throw ValidationException.of("scanFileId", new MessageRef("docintake.scanFile.invalid"));
            }
            Instant now = ops.now();
            if (!intakes.dispose(intakeId, in.disposedOn(), in.reason(), scan.id(), null, actor.operatorId(), now)) {
                throw DocIntakeException.stateChanged();
            }
            ops.event(applicationId, BizEventAction.PAPER_DISPOSED, BizActorType.OPERATOR, actor.operatorId(), in.reason(), now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_PAPER_DISPOSE, app, disposeDetail(intakeId, in, scan.id()));
            return contractBundleOf(applicationId);
        });
    }

    public DocIntakeAdminView.Bundle voidContract(AdminActor actor, long applicationId, String intakeIdText, DocIntakeAdminRequest.MarkVoid body) {
        canManage(actor, applicationId);
        long intakeId = intakeId(intakeIdText);
        String reason = voidReason(body);
        return tx.execute(status -> {
            AppRow app = apps.lock(applicationId).orElseThrow(BizApplicationException::notFound);
            openRow(DocIntakeDoc.CONTRACT, applicationId, intakeId);
            if (!BizApplicationReviewService.contractsAllowed(app)) {
                throw DocIntakeException.stateChanged(); // 확정 뒤에는 근거를 줄이지 않는다(수동 확정 CHECK)
            }
            Instant now = ops.now();
            if (!intakes.markVoid(intakeId, reason, actor.operatorId(), now)) {
                throw DocIntakeException.stateChanged();
            }
            apps.setPaperContractCount(applicationId, intakes.contractEvidence(applicationId), now);
            ops.event(applicationId, BizEventAction.PAPER_VOIDED, BizActorType.OPERATOR, actor.operatorId(), reason, now);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_PAPER_VOID, app, Map.of("intakeId", Long.toString(intakeId), "reason", reason));
            return contractBundleOf(applicationId);
        });
    }

    // ── 사업자등록증 사본(사업자 신원) ────────────────────────────────────────

    public DocIntakeAdminView.Bundle certificateBundle(String profileIdText) {
        long profileId = profileId(profileIdText);
        return readTx.execute(status -> certificateBundleOf(profiles.findById(profileId).orElseThrow(BusinessException::notFound)));
    }

    public DocIntakeAdminView.Bundle createCertificate(AdminActor actor, String profileIdText, DocIntakeAdminRequest.Create body) {
        long profileId = profileId(profileIdText);
        CreateInput in = createInput(body);
        return tx.execute(status -> {
            BusinessProfileEntity p = profiles.findWithLockById(profileId).orElseThrow(BusinessException::notFound);
            if (p.getStatus() == BusinessStatus.WITHDRAWN) {
                throw DocIntakeException.stateChanged();
            }
            Instant now = ops.now();
            long intakeId = ids.nextId();
            intakes.insert(in.row(intakeId, DocIntakeDoc.CERTIFICATE, profileId, actor.operatorId(), now));
            businessStore.insertHistory(ids.nextId(), profileId, BusinessStore.PAPER_RECEIVED, BusinessStore.OPERATOR, actor.operatorId(), in.channel().name(), now);
            record(actor, AuditActions.BUSINESS_PAPER_RECEIVE, profileId, in.detail(intakeId));
            return certificateBundleOf(p);
        });
    }

    public DocIntakeAdminView.Bundle changeCertificateHandling(AdminActor actor, String profileIdText, String intakeIdText, DocIntakeAdminRequest.Handling body) {
        long profileId = profileId(profileIdText);
        long intakeId = intakeId(intakeIdText);
        HandlingInput in = handlingInput(body);
        return tx.execute(status -> {
            BusinessProfileEntity p = profiles.findWithLockById(profileId).orElseThrow(BusinessException::notFound);
            IntakeRow row = openRow(DocIntakeDoc.CERTIFICATE, profileId, intakeId);
            Instant now = ops.now();
            if (!intakes.changeHandling(intakeId, in.handling(), in.storageLocation(), now)) {
                throw DocIntakeException.stateChanged();
            }
            businessStore.insertHistory(ids.nextId(), profileId, BusinessStore.PAPER_HANDLING, BusinessStore.OPERATOR, actor.operatorId(), in.handling().name(), now);
            record(actor, AuditActions.BUSINESS_PAPER_HANDLING, profileId, handlingDetail(row, in));
            return certificateBundleOf(p);
        });
    }

    public DocIntakeAdminView.Bundle disposeCertificate(AdminActor actor, String profileIdText, String intakeIdText, DocIntakeAdminRequest.Dispose body) {
        long profileId = profileId(profileIdText);
        long intakeId = intakeId(intakeIdText);
        DisposeInput in = disposeInput(body, false);
        return tx.execute(status -> {
            BusinessProfileEntity p = profiles.findWithLockById(profileId).orElseThrow(BusinessException::notFound);
            IntakeRow row = openRow(DocIntakeDoc.CERTIFICATE, profileId, intakeId);
            requireDisposedAfterReceived(row, in);
            Long scanId = in.scanFileId();
            if (scanId != null && businessStore.profileDocuments(profileId).stream().noneMatch(d -> d.id() == scanId)) {
                throw ValidationException.of("scanFileId", new MessageRef("docintake.scanFile.invalid"));
            }
            Instant now = ops.now();
            if (!intakes.dispose(intakeId, in.disposedOn(), in.reason(), null, scanId, actor.operatorId(), now)) {
                throw DocIntakeException.stateChanged();
            }
            businessStore.insertHistory(ids.nextId(), profileId, BusinessStore.PAPER_DISPOSED, BusinessStore.OPERATOR, actor.operatorId(), in.reason(), now);
            record(actor, AuditActions.BUSINESS_PAPER_DISPOSE, profileId, disposeDetail(intakeId, in, scanId));
            return certificateBundleOf(p);
        });
    }

    public DocIntakeAdminView.Bundle voidCertificate(AdminActor actor, String profileIdText, String intakeIdText, DocIntakeAdminRequest.MarkVoid body) {
        long profileId = profileId(profileIdText);
        long intakeId = intakeId(intakeIdText);
        String reason = voidReason(body);
        return tx.execute(status -> {
            BusinessProfileEntity p = profiles.findWithLockById(profileId).orElseThrow(BusinessException::notFound);
            openRow(DocIntakeDoc.CERTIFICATE, profileId, intakeId);
            Instant now = ops.now();
            if (!intakes.markVoid(intakeId, reason, actor.operatorId(), now)) {
                throw DocIntakeException.stateChanged();
            }
            businessStore.insertHistory(ids.nextId(), profileId, BusinessStore.PAPER_VOIDED, BusinessStore.OPERATOR, actor.operatorId(), reason, now);
            record(actor, AuditActions.BUSINESS_PAPER_VOID, profileId, Map.of("intakeId", Long.toString(intakeId), "reason", reason));
            return certificateBundleOf(p);
        });
    }

    /**
     * 운영자의 등록증 사본 올리기(multipart {@code file} + {@code channel} {@code UPLOAD} 기본 · {@code FAX}) — 회원 올리기와 같은 저장(DB 암호문, 내용 첫 바이트로 PDF·JPG·PNG 판정,
     * 10MB). 올리면서 그 신원에 붙고 <b>그 사업자의 지금 등록증</b>이 된다(옛 사본 줄은 그 신원에 붙은 채 남는다 — 회원이 수정으로 사본을 바꿀 때와 같은 모델, 파기 배치가 신원 단위로
     * 지운다). 탈퇴·파기된 신원이면 409 {@code BUSINESS_STATE_CHANGED}. 상태·심사는 그대로. 처리 기록 {@code CERT_UPLOADED} · 감사 {@code BUSINESS_CERT_UPLOAD}.
     */
    public DocIntakeAdminView.Bundle uploadCertificate(AdminActor actor, String profileIdText, MultipartFile file, String channelText) {
        long profileId = profileId(profileIdText);
        DocFileChannel channel = fileChannel(channelText);
        byte[] data;
        try {
            data = file == null || file.isEmpty() ? null : file.getBytes();
        } catch (IOException e) {
            data = null;
        }
        if (data == null || data.length == 0) {
            throw ValidationException.of("file", new MessageRef("business.file.required"));
        }
        if (data.length > FILE_MAX_BYTES) {
            throw ValidationException.of("file", new MessageRef("business.file.tooLarge"));
        }
        String type = sniff(data);
        if (type == null) {
            throw ValidationException.of("file", new MessageRef("business.file.type"));
        }
        pii.requireConfigured();
        String name = fileName(file.getOriginalFilename(), type);
        byte[] encrypted = pii.encryptFile(data);
        String sha = sha256(data);
        int size = data.length;
        return tx.execute(status -> {
            BusinessProfileEntity p = profiles.findWithLockById(profileId).orElseThrow(BusinessException::notFound);
            if (p.getStatus() == BusinessStatus.WITHDRAWN || p.getErasedAt() != null) {
                throw BusinessException.stateChanged();
            }
            Instant now = ops.now();
            long fileId = ids.nextId();
            businessStore.insertOperatorDocument(fileId, p.getUserId(), profileId, name, type, size, encrypted, sha, channel, actor.operatorId(), now);
            p.useCertificate(fileId, now);
            profiles.flush();
            businessStore.insertHistory(ids.nextId(), profileId, BusinessStore.CERT_UPLOADED, BusinessStore.OPERATOR, actor.operatorId(), channel.name(), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("fileId", Long.toString(fileId));
            detail.put("fileName", name);
            detail.put("channel", channel.name());
            detail.put("size", size);
            record(actor, AuditActions.BUSINESS_CERT_UPLOAD, profileId, detail);
            return certificateBundleOf(p);
        });
    }

    // ── 묶음 ───────────────────────────────────────────────────────────────

    private DocIntakeAdminView.Bundle contractBundleOf(long applicationId) {
        Map<String, String> channels = new LinkedHashMap<>();
        for (ContractRow c : contracts.list(applicationId)) {
            channels.put(Long.toString(c.id()), c.channel().name());
        }
        return new DocIntakeAdminView.Bundle(new DocIntakeAdminView.Target(DocIntakeDoc.CONTRACT.name(), Long.toString(applicationId)),
                intakes.list(DocIntakeDoc.CONTRACT, applicationId).stream().map(DocIntakeAdminService::view).toList(), channels, List.of(), false);
    }

    private DocIntakeAdminView.Bundle certificateBundleOf(BusinessProfileEntity p) {
        long profileId = p.getId();
        Map<String, String> channels = new LinkedHashMap<>();
        List<DocIntakeAdminView.FaxFile> fax = new java.util.ArrayList<>();
        for (ProfileFileRow d : businessStore.profileDocuments(profileId)) {
            channels.put(Long.toString(d.id()), d.channel().name());
            if (d.channel() == DocFileChannel.FAX) {
                fax.add(new DocIntakeAdminView.FaxFile(Long.toString(d.id()), d.fileName(), d.contentType(), d.sizeBytes(), d.createdAt(), d.uploadedByName()));
            }
        }
        return new DocIntakeAdminView.Bundle(new DocIntakeAdminView.Target(DocIntakeDoc.CERTIFICATE.name(), Long.toString(profileId)),
                intakes.list(DocIntakeDoc.CERTIFICATE, profileId).stream().map(DocIntakeAdminService::view).toList(), channels, fax,
                p.getCertificateDelivery() == BusinessCertificateDelivery.MAIL_FAX);
    }

    private static DocIntakeAdminView.Intake view(IntakeRow r) {
        Long scan = r.scanContractFileId() != null ? r.scanContractFileId() : r.scanDocumentId();
        return new DocIntakeAdminView.Intake(Long.toString(r.id()), r.doc().name(), r.channel().name(), r.receivedOn(), r.receivedByName(), r.recordedAt(), r.pages(),
                r.memo(), r.handling().name(), r.storageLocation(), r.disposedOn(), r.disposedByName(), r.disposeReason(), scan == null ? null : Long.toString(scan),
                r.voidedAt(), r.voidedByName(), r.voidReason());
    }

    // ── 입력 ───────────────────────────────────────────────────────────────

    private record CreateInput(DocIntakeChannel channel, LocalDate receivedOn, Integer pages, String memo, DocIntakeHandling handling, String storageLocation) {

        DocIntakeStore.NewIntake row(long id, DocIntakeDoc doc, long ownerId, long operatorId, Instant now) {
            return new DocIntakeStore.NewIntake(id, doc, ownerId, channel, receivedOn, pages, memo, handling, storageLocation, operatorId, now);
        }

        Map<String, Object> detail(long intakeId) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("intakeId", Long.toString(intakeId));
            d.put("channel", channel.name());
            d.put("receivedOn", receivedOn.toString());
            if (pages != null) {
                d.put("pages", pages);
            }
            d.put("handling", handling.name());
            return d;
        }
    }

    private record HandlingInput(DocIntakeHandling handling, String storageLocation) {
    }

    private record DisposeInput(LocalDate disposedOn, String reason, Long scanFileId) {
    }

    private CreateInput createInput(DocIntakeAdminRequest.Create b) {
        DocIntakeAdminRequest.Create body = b == null ? new DocIntakeAdminRequest.Create(null, null, null, null, null, null) : b;
        BizFields f = BizFields.start();
        DocIntakeChannel channel = f.oneOf("channel", body.channel(), DocIntakeChannel::valueOf);
        LocalDate receivedOn = f.date("receivedOn", body.receivedOn());
        if (receivedOn != null && receivedOn.isAfter(today())) {
            f.add("receivedOn", "docintake.receivedOn.future");
        }
        Integer pages = body.pages();
        if (pages != null && (pages < PAGES_MIN || pages > PAGES_MAX)) {
            f.add("pages", "docintake.pages.invalid", PAGES_MIN, PAGES_MAX);
        }
        String memo = optional(f, "memo", body.memo(), MEMO_MAX);
        DocIntakeHandling handling = f.oneOf("handling", body.handling(), DocIntakeAdminService::openHandling);
        String storage = optional(f, "storageLocation", body.storageLocation(), STORAGE_MAX);
        f.throwIfAny();
        return new CreateInput(channel, receivedOn, pages, memo, handling, handling == DocIntakeHandling.KEEP ? storage : null);
    }

    private static HandlingInput handlingInput(DocIntakeAdminRequest.Handling b) {
        BizFields f = BizFields.start();
        DocIntakeHandling handling = f.oneOf("handling", b == null ? null : b.handling(), DocIntakeAdminService::openHandling);
        String storage = optional(f, "storageLocation", b == null ? null : b.storageLocation(), STORAGE_MAX);
        f.throwIfAny();
        return new HandlingInput(handling, handling == DocIntakeHandling.KEEP ? storage : null);
    }

    private DisposeInput disposeInput(DocIntakeAdminRequest.Dispose b, boolean scanRequired) {
        BizFields f = BizFields.start();
        LocalDate disposedOn = f.date("disposedOn", b == null ? null : b.disposedOn());
        if (disposedOn != null && disposedOn.isAfter(today())) {
            f.add("disposedOn", "docintake.disposedOn.range");
        }
        String reason = f.text("reason", b == null ? null : b.reason(), DISPOSE_REASON_MAX);
        String scanText = BizFields.strip(b == null ? null : b.scanFileId());
        Long scan = scanText == null ? null : BizFields.id(scanText);
        if (scanText == null && scanRequired) {
            f.add("scanFileId", "docintake.scanFile.required");
        } else if (scanText != null && scan == null) {
            f.add("scanFileId", "docintake.scanFile.invalid");
        }
        f.throwIfAny();
        return new DisposeInput(disposedOn, reason, scan);
    }

    private static String voidReason(DocIntakeAdminRequest.MarkVoid b) {
        String reason = BizFields.strip(b == null ? null : b.reason());
        if (reason == null || reason.length() < VOID_REASON_MIN || reason.length() > VOID_REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("docintake.reason.invalid", VOID_REASON_MIN, VOID_REASON_MAX));
        }
        return reason;
    }

    /** 고를 수 있는 처리 — 폐기함은 [폐기 처리] 로만(고르면 400) */
    private static DocIntakeHandling openHandling(String text) {
        DocIntakeHandling h = DocIntakeHandling.valueOf(text);
        return h.open() ? h : null;
    }

    /** 선택 글자 — 비면 null, 길면 오류 */
    private static String optional(BizFields f, String field, String value, int max) {
        String s = BizFields.strip(value);
        if (s != null && s.length() > max) {
            f.add(field, "bizapp.field.tooLong", max);
        }
        return s;
    }

    /** 파일 사본이 들어온 길 — 비우면 올림, 모르는 값이면 400 {@code fields.channel} */
    private static DocFileChannel fileChannel(String text) {
        String s = BizFields.strip(text);
        if (s == null) {
            return DocFileChannel.UPLOAD;
        }
        try {
            return DocFileChannel.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw ValidationException.of("channel", new MessageRef("business.value.invalid"));
        }
    }

    /** 그 대상의 열린 줄 — 없으면 404, 이미 잘못 기록·폐기했으면 409 */
    private IntakeRow openRow(DocIntakeDoc doc, long ownerId, long intakeId) {
        IntakeRow row = intakes.find(doc, ownerId, intakeId).orElseThrow(DocIntakeException::notFound);
        if (!row.open()) {
            throw DocIntakeException.stateChanged();
        }
        return row;
    }

    private static void requireDisposedAfterReceived(IntakeRow row, DisposeInput in) {
        if (in.disposedOn().isBefore(row.receivedOn())) {
            throw ValidationException.of("disposedOn", new MessageRef("docintake.disposedOn.range"));
        }
    }

    private static Map<String, Object> handlingDetail(IntakeRow row, HandlingInput in) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("intakeId", Long.toString(row.id()));
        d.put("from", row.handling().name());
        d.put("to", in.handling().name());
        if (in.storageLocation() != null) {
            d.put("storageLocation", in.storageLocation());
        }
        return d;
    }

    private static Map<String, Object> disposeDetail(long intakeId, DisposeInput in, Long scanId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("intakeId", Long.toString(intakeId));
        d.put("disposedOn", in.disposedOn().toString());
        d.put("reason", in.reason());
        if (scanId != null) {
            d.put("scanFileId", Long.toString(scanId));
        }
        return d;
    }

    // ── 권한·감사 ───────────────────────────────────────────────────────────

    /** 건의 도메인 조회 권한(없는 건은 404 가 먼저) */
    private void canRead(AdminActor actor, long applicationId) {
        require(actor, reader.app(applicationId).kind().domain().readPermission(), applicationId);
    }

    /** 건의 도메인 처리 권한(없는 건은 404 가 먼저) */
    private void canManage(AdminActor actor, long applicationId) {
        require(actor, reader.app(applicationId).kind().domain().managePermission(), applicationId);
    }

    private void require(AdminActor actor, Permission permission, long applicationId) {
        if (!actor.can(permission)) {
            denied.record(actor, "MISSING_PERMISSION", BizApplicationOps.AUDIT_TARGET, Long.toString(applicationId), List.of(permission.code()));
            throw new PermissionDeniedException();
        }
    }

    private void record(AdminActor actor, String action, long profileId, Map<String, Object> detail) {
        audit.record(new AuditEvent(AuditActorType.OPERATOR, actor.operatorId(), action, AuditResult.SUCCESS, BusinessNtsService.AUDIT_TARGET, Long.toString(profileId),
                actor.ip(), detail));
    }

    private LocalDate today() {
        return ServiceTime.today(ops.clock());
    }

    private static long intakeId(String text) {
        Long id = BizFields.id(text);
        if (id == null) {
            throw DocIntakeException.notFound();
        }
        return id;
    }

    private static long profileId(String text) {
        Long id = BizFields.id(text);
        if (id == null) {
            throw BusinessException.notFound();
        }
        return id;
    }

    // ── 등록증 파일(회원 올리기 MyBusinessService 와 같은 판정) ─────────────────────

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

    /** 표시용 파일 이름 — 경로·제어 문자를 빼고 120자, 없으면 종류별 기본 이름 */
    static String fileName(String original, String type) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty()) {
            name = "certificate" + (type.equals("application/pdf") ? ".pdf" : type.equals("image/png") ? ".png" : ".jpg");
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
