package com.community.business.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 사업자 심사(admin-web {@code useBusinessApi.types.ts}) 응답 모양. 목록·상세는 가린 값만 싣고 평문 칸 자체가 없다(평문은 {@link Reveal} 만).
 */
public final class BusinessAdminView {

    private BusinessAdminView() {
    }

    /**
     * 국세청 조회 — 한 번도 조회하지 않았으면 {@code UNAVAILABLE} + 진위 {@code UNCHECKED} + 안내 문구(승인은 409 {@code BUSINESS_NTS_NOT_CHECKED}).
     *
     * @param validity 진위확인 — {@code MATCH}·{@code MISMATCH}(승인 잠금)·{@code UNCHECKED}(개업일 없는 옛 신청 — 사유를 적어 승인)·{@code UNAVAILABLE}(조회 불가 — 사유)
     */
    public record Nts(String status, String validity, Instant checkedAt, String taxType, LocalDate closedOn, String message) {
    }

    public record ServiceRow(String service, String status) {
    }

    public record Applicant(String memberId, String nickname) {
    }

    /** 목록 한 줄 */
    public record Row(String id, String name, String maskedRegNo, String status, boolean reReview, List<ServiceRow> services, Applicant applicant, Instant appliedAt,
                      Nts nts) {
    }

    public record Page(List<Row> items, long total, Map<String, Long> counts) {
    }

    public record Masked(String representative, String address, String phone, String email) {
    }

    public record Certificate(String fileName, String mimeType, long size, Instant uploadedAt, String lastViewedBy, Instant lastViewedAt) {
    }

    /**
     * 서비스 카드 — 영향 건수(진행·예약 · 대기) + 「서비스만 정지」 표시(사유 코드·글·시각·운영자 — 신원 정지를 따라간 정지면 null) + 그 서비스만 정지할 때의 영향 건수(2026-11-02)
     */
    public record ServiceCard(String service, String status, int liveCount, int pendingCount, String suspendCode, String suspendText, Instant suspendedAt,
                              String suspendedByName, Impact impact) {
    }

    public record Impact(int awaitingPayment, int scheduled, int running) {
    }

    public record Rejection(String code, String text) {
    }

    public record Suspension(String code, String text, Instant at) {
    }

    /** 처리 기록 한 줄 — {@code service} 는 서비스 하나에 대한 처리(서비스만 정지·해제)일 때만 */
    public record History(Instant at, String action, String actor, String note, String service) {
    }

    /** 상세 — 목록 한 줄의 칸 + 가린 신원·개업일(가리지 않는다 — 옛 신청이면 null)·사본·서비스 카드·영향 건수·사유·처리 기록 */
    public record Detail(String id, String name, String maskedRegNo, String status, boolean reReview, List<ServiceRow> services, Applicant applicant, Instant appliedAt,
                         Nts nts, Masked masked, LocalDate openedOn, Certificate certificate, List<ServiceCard> serviceCards, Impact impact, Rejection rejection, Suspension suspension,
                         List<History> history) {
    }

    /** 평문 — 화면은 {@code hideAt} 에 다시 가린다(복사 단추 없음) */
    public record Reveal(String regNo, String representative, String address, String phone, String email, Instant hideAt) {
    }

    /** 사업자등록증 사본 — {@code data:} 주소(공개 주소가 없다 — 열 때마다 열람 기록) */
    public record CertificateFile(String url, String mimeType, String fileName) {
    }
}
