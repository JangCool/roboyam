package com.community.business.entity;

import com.community.docintake.entity.DocFileChannel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사업자등록증 사본 한 장 — shared V111 {@code business_documents}(V140 들어온 길·올린 운영자). 내용은 암호문 bytea({@code content}, 파기하면 null) — 정보 조회는 저장소가
 * 프로젝션으로 내용을 빼고 읽는다. 붙이기·파기만 저장소의 UPDATE 가 고친다. {@code toString} 을 두지 않는다(암호문).
 */
@Entity
@Table(name = "business_documents")
public class BusinessDocumentEntity {

    /** 종류(지금은 등록증 하나) */
    public static final String KIND_CERTIFICATE = "CERTIFICATE";

    @Id
    private Long id;

    /** 주인 회원 */
    private Long userId;
    /** 붙은 신원 — 아직 신청에 붙이지 않았으면 null */
    private Long profileId;
    private String kind;
    private String fileName;
    private String contentType;
    private Integer sizeBytes;
    private byte[] content;
    private String sha256;
    private Instant createdAt;
    private Instant attachedAt;
    private Instant erasedAt;
    @Enumerated(EnumType.STRING)
    private DocFileChannel channel;
    /** 올린 운영자 — 회원이 올렸으면 null */
    private Long uploadedBy;

    protected BusinessDocumentEntity() {
    }

    /** 회원이 올린 사본(아직 신원에 붙지 않음) */
    public static BusinessDocumentEntity byMember(long id, long userId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256,
                                                  Instant now) {
        return base(id, userId, fileName, contentType, sizeBytes, encrypted, sha256, DocFileChannel.UPLOAD, now);
    }

    /** 운영자가 올린 사본(전자 팩스·스캔본 — 올리면서 그 신원에 붙는다) */
    public static BusinessDocumentEntity byOperator(long id, long userId, long profileId, String fileName, String contentType, int sizeBytes, byte[] encrypted,
                                                    String sha256, DocFileChannel channel, long operatorId, Instant now) {
        BusinessDocumentEntity d = base(id, userId, fileName, contentType, sizeBytes, encrypted, sha256, channel, now);
        d.profileId = profileId;
        d.uploadedBy = operatorId;
        d.attachedAt = now;
        return d;
    }

    private static BusinessDocumentEntity base(long id, long userId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256,
                                               DocFileChannel channel, Instant now) {
        BusinessDocumentEntity d = new BusinessDocumentEntity();
        d.id = id;
        d.userId = userId;
        d.kind = KIND_CERTIFICATE;
        d.fileName = fileName;
        d.contentType = contentType;
        d.sizeBytes = sizeBytes;
        d.content = encrypted;
        d.sha256 = sha256;
        d.channel = channel;
        d.createdAt = now;
        return d;
    }
}
