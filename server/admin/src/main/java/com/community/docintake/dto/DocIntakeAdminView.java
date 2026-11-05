package com.community.docintake.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 사업자 서류 접수 기록(admin-web {@code useDocIntakeApi.types.ts} 의 {@code DocIntakeBundle}) 응답 모양 — 쓰기 응답도 그 대상의 묶음 전체 */
public final class DocIntakeAdminView {

    private DocIntakeAdminView() {
    }

    /** 접수 기록의 주인 — 계약서는 신청 건 id, 등록증은 사업자 id */
    public record Target(String doc, String ownerId) {
    }

    /** 종이 접수 한 줄 — 잘못 기록은 {@code voidedAt} 을 찍은 채 남는다(개수에 세지 않는다). 운영자 이름은 지워진 운영자면 null */
    public record Intake(String id, String doc, String channel, LocalDate receivedOn, String receivedBy, Instant recordedAt, Integer pages, String memo, String handling,
                         String storageLocation, LocalDate disposedOn, String disposedBy, String disposeReason, String scanFileId, Instant voidedAt, String voidedBy,
                         String voidReason) {
    }

    /** 등록증 전자 팩스 파일 — 그 사업자의 등록증 파일 중 들어온 길이 {@code FAX} 인 줄(파기되지 않은 것). 올린 운영자가 없으면(회원이 올림) null */
    public record FaxFile(String id, String fileName, String mimeType, long size, Instant receivedAt, String receivedBy) {
    }

    /**
     * @param fileChannels 파일 사본 id → 들어온 길({@code UPLOAD}·{@code FAX}) — 계약서는 그 건의 계약서 파일, 등록증은 그 사업자의 등록증 파일(파기되지 않은 것)
     * @param faxFiles     등록증 전자 팩스 파일(계약서는 늘 빈 목록)
     * @param mailDeclared 신청자가 등록증을 「우편·팩스로 보냄」으로 골랐나(등록증만 — 사업자 {@code certificateDelivery = MAIL_FAX})
     */
    public record Bundle(Target target, List<Intake> intakes, Map<String, String> fileChannels, List<FaxFile> faxFiles, boolean mailDeclared) {
    }
}
