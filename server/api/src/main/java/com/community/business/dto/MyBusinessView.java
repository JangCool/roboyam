package com.community.business.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 사업자 센터(user-web {@code useBusinessApi} 의 신원 부분) 응답 모양. 등록번호·연락처는 가린 값만 내려 준다(대표자·이메일·주소는 본인이 고칠 칸이라 평문).
 */
public final class MyBusinessView {

    private MyBusinessView() {
    }

    /**
     * {@code GET/POST/PUT /api/business/me}. 신청 전이면 {@code status: NONE} 이고 나머지 칸은 null.
     *
     * @param status   {@code NONE}·{@code PENDING}·{@code APPROVED}·{@code REJECTED}·{@code SUSPENDED} — 재심사 중은 {@code APPROVED} + {@code reReview}
     * @param reReview 승인 뒤 상호·번호를 고쳐 다시 심사 중(F7) — 이미 예약·진행 중인 건은 그대로, 새 신청만 막힌다
     * @param openedOn 개업일 — 개업일 칸이 생기기 전 신청이면 null
     * @param services 서비스별 이용 상태(2026-11-02 — 신원이 승인인데 한 서비스만 정지면 그 줄이 {@code SUSPENDED} + 사유). 신청 전이면 빈 목록
     */
    public record Profile(String status, String companyName, String bizNoMasked, String ownerName, LocalDate openedOn, String phoneMasked, String email, Address address,
                          Certificate certificate, Instant submittedAt, Instant reviewedAt, String rejectCode, String rejectReason, String suspendReason,
                          boolean reReview, List<Service> services) {

        public static Profile none() {
            return new Profile("NONE", null, null, null, null, null, null, null, null, null, null, null, null, null, false, List.of());
        }
    }

    /** 서비스 한 줄 — 「서비스만 정지」면 사유 코드·글·시각(신원 정지를 따라간 정지면 null) */
    public record Service(String service, String status, String suspendCode, String suspendReason, Instant suspendedAt) {
    }

    public record Address(String postcode, String address1, String address2) {
    }

    public record Certificate(String fileId, String name, long size) {
    }

    /** {@code POST /api/business/files} — 올린 사업자등록증 사본(미리보기 주소는 주지 않는다 — 공개 주소가 없다) */
    public record File(String fileId, String name, long size, String contentType, String previewUrl) {
    }
}
