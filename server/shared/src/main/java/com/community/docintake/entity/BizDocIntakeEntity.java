package com.community.docintake.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 사업자 서류 종이 접수 한 줄 — shared V140 {@code biz_doc_intakes}. 대상은 서류에 따라 하나 — 계약서({@link DocIntakeDoc#CONTRACT})면 신청 건, 등록증이면 사업자 신원.
 * 줄을 지우지 않는다(사업자 삭제만 그 신원 줄을 지운다). 처리·폐기·잘못 기록은 저장소의 조건부 UPDATE(아직 열린 줄일 때만)가 한다.
 */
@Entity
@Table(name = "biz_doc_intakes")
public class BizDocIntakeEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private DocIntakeDoc doc;
    private Long applicationId;
    private Long profileId;
    @Enumerated(EnumType.STRING)
    private DocIntakeChannel channel;
    private LocalDate receivedOn;
    private Integer pages;
    private String memo;
    @Enumerated(EnumType.STRING)
    private DocIntakeHandling handling;
    private String storageLocation;
    private Long receivedBy;
    private Instant recordedAt;
    private Instant updatedAt;
    private LocalDate disposedOn;
    private Instant disposedAt;
    private Long disposedBy;
    private String disposeReason;
    private Long scanContractFileId;
    private Long scanDocumentId;
    private Instant voidedAt;
    private Long voidedBy;
    private String voidReason;

    protected BizDocIntakeEntity() {
    }

    /** 새 접수 줄 — 대상 ID 는 서류에 맞는 칸 하나에 */
    public static BizDocIntakeEntity create(long id, DocIntakeDoc doc, long ownerId, DocIntakeChannel channel, LocalDate receivedOn, Integer pages, String memo,
                                            DocIntakeHandling handling, String storageLocation, long operatorId, Instant now) {
        BizDocIntakeEntity i = new BizDocIntakeEntity();
        i.id = id;
        i.doc = doc;
        i.applicationId = doc == DocIntakeDoc.CONTRACT ? ownerId : null;
        i.profileId = doc == DocIntakeDoc.CERTIFICATE ? ownerId : null;
        i.channel = channel;
        i.receivedOn = receivedOn;
        i.pages = pages;
        i.memo = memo;
        i.handling = handling;
        i.storageLocation = storageLocation;
        i.receivedBy = operatorId;
        i.recordedAt = now;
        i.updatedAt = now;
        return i;
    }
}
