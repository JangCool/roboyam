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

    /** 국세청 조회 — 한 번도 조회하지 않았으면 {@code UNAVAILABLE} + 안내 문구(승인은 409 {@code BUSINESS_NTS_NOT_CHECKED}) */
    public record Nts(String status, Instant checkedAt, String taxType, LocalDate closedOn, String message) {
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

    public record ServiceCard(String service, String status, int liveCount, int pendingCount) {
    }

    public record Impact(int awaitingPayment, int scheduled, int running) {
    }

    public record Rejection(String code, String text) {
    }

    public record Suspension(String code, String text, Instant at) {
    }

    public record History(Instant at, String action, String actor, String note) {
    }

    /** 상세 — 목록 한 줄의 칸 + 가린 신원·사본·서비스 카드·영향 건수·사유·처리 기록 */
    public record Detail(String id, String name, String maskedRegNo, String status, boolean reReview, List<ServiceRow> services, Applicant applicant, Instant appliedAt,
                         Nts nts, Masked masked, Certificate certificate, List<ServiceCard> serviceCards, Impact impact, Rejection rejection, Suspension suspension,
                         List<History> history) {
    }

    /** 평문 — 화면은 {@code hideAt} 에 다시 가린다(복사 단추 없음) */
    public record Reveal(String regNo, String representative, String address, String phone, String email, Instant hideAt) {
    }

    /** 사업자등록증 사본 — {@code data:} 주소(공개 주소가 없다 — 열 때마다 열람 기록) */
    public record CertificateFile(String url, String mimeType, String fileName) {
    }
}
