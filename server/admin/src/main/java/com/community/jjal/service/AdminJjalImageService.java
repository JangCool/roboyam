package com.community.jjal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.exception.JjalNotFoundException;
import com.community.jjal.repository.master.JjalFileRepository;
import com.community.media.service.ImageStorage;
import com.community.permission.dto.AdminActor;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 보는 짤 원본 — 관리자 글·신고 서랍의 본문 짤({@code AdminBodyJjalView.adminUrl}). 내린 짤(비공개 위치)·살아 있는 짤(공개 위치) 어느 쪽이든 저장소에서 그대로 읽는다.
 * <b>내려간 짤</b>(공개 주소로 못 보는 짤)을 내보내면 감사 기록 {@code JJAL_ORIGINAL_VIEW}(개인정보 열람 분류)를 남긴다 — 짤 신고 사건의 그림
 * ({@code GET /api/admin/reports/{id}/jjal-image})도 {@link #recordOriginalView} 로 같은 기록을 남긴다(2026-11-25 사용자 「원본 열람도 감사 기록 남기게」).
 * 공개 중인 짤은 누구나 보는 그림이라 남기지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class AdminJjalImageService {

    /** 운영자 전용 원본 주소 — admin-web 이 같은 출처로 부르는 상대 경로 */
    public static final String IMAGE_PATH = "/api/admin/jjals/%s/image";
    /** 감사 대상 종류 — 대상 ID 는 짤 공개 ID */
    public static final String AUDIT_TARGET = "JJAL";

    /** 짤 원본 바이트와 형식 */
    public record JjalImage(byte[] data, String contentType) {
    }

    private final JjalFileRepository jjalFiles;
    private final ImageStorage storage;
    private final AuditLogService audit;

    public AdminJjalImageService(JjalFileRepository jjalFiles, ImageStorage storage, AuditLogService audit) {
        this.jjalFiles = jjalFiles;
        this.storage = storage;
        this.audit = audit;
    }

    /** 원본 파일이 남아 있는 짤의 운영자 주소 — 불법촬영물로 확정해 지운 짤(해시만 남음)이면 null */
    public static String adminUrl(JjalFileEntity f) {
        return f.getStorageKey() == null ? null : String.format(IMAGE_PATH, f.getPublicId());
    }

    /** 짤 원본 — 없는 공개 ID · 파일이 없는 짤(불법촬영물로 지움) · 저장소에 파일이 없으면 404 JJAL_NOT_FOUND. 내려간 짤이면 열람 감사 기록 */
    @Transactional
    public JjalImage image(AdminActor actor, String publicId) {
        JjalFileEntity f = publicId == null || publicId.isBlank() ? null : jjalFiles.findByPublicId(publicId).orElse(null);
        if (f == null || f.getStorageKey() == null) {
            throw new JjalNotFoundException();
        }
        byte[] data = storage.readBytes(f.getStorageKey()).orElseThrow(JjalNotFoundException::new);
        recordOriginalView(actor, f, null);
        return new JjalImage(data, f.getContentType());
    }

    /**
     * 내려간 짤 원본을 운영자에게 내보낸 기록 — 대상 JJAL + 공개 ID, {@code status}·{@code blockedReason}, 신고 사건 그림이면 {@code caseId}. 공개 중인 짤이면 남기지 않는다.
     * 호출하는 쪽이 쓰기 트랜잭션 안에서 부른다(감사 기록은 같은 트랜잭션에 들어간다).
     */
    public void recordOriginalView(AdminActor actor, JjalFileEntity f, String caseId) {
        if (f.servableAt(Instant.now())) {
            return;
        }
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.JJAL_ORIGINAL_VIEW, AuditResult.SUCCESS)
                .target(AUDIT_TARGET, f.getPublicId())
                .with("status", f.getStatus() == null ? null : f.getStatus().name());
        if (f.getBlockedReason() != null) {
            event = event.with("blockedReason", f.getBlockedReason());
        }
        if (caseId != null) {
            event = event.with("caseId", caseId);
        }
        audit.record(event);
    }
}
