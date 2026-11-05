package com.community.docintake.dto;

/** 사업자 서류 접수 기록 처리 본문 — 검사는 서비스(400 {@code fields.*}) */
public final class DocIntakeAdminRequest {

    private DocIntakeAdminRequest() {
    }

    /**
     * 새 종이 접수.
     *
     * @param channel         {@code MAIL}·{@code VISIT}·{@code FAX}(종이)
     * @param receivedOn      {@code YYYY-MM-DD}, 한국 날짜 오늘 이전
     * @param pages           1~200(선택)
     * @param memo            0~200자(선택)
     * @param handling        {@code KEEP}·{@code DISPOSE_PENDING}(폐기함은 [폐기 처리] 로만)
     * @param storageLocation 0~100자 — 계속 보관일 때만 남긴다
     */
    public record Create(String channel, String receivedOn, Integer pages, String memo, String handling, String storageLocation) {
    }

    /** 처리 바꾸기 — 계속 보관 ↔ 스캔 후 폐기 예정 */
    public record Handling(String handling, String storageLocation) {
    }

    /**
     * 폐기 — 되돌릴 수 없다.
     *
     * @param disposedOn 접수일 ~ 오늘(한국 날짜)
     * @param reason     1~200자
     * @param scanFileId 스캔본 파일 id — 계약서는 필수(그 건의 대체되지 않은 계약서 파일), 등록증은 선택(그 사업자의 등록증 파일)
     */
    public record Dispose(String disposedOn, String reason, String scanFileId) {
    }

    /** 잘못 기록 — 사유 5~200자 */
    public record MarkVoid(String reason) {
    }
}
