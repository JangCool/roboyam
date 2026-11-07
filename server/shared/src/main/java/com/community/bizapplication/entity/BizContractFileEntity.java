package com.community.bizapplication.entity;

import com.community.docintake.entity.DocFileChannel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 계약서 사본 한 장 — shared V112 {@code biz_contract_files}(V115 미리 올림 · V140 들어온 길). 내용은 암호문 bytea({@code content}) — 목록·정보 조회는 저장소가 프로젝션으로
 * 내용을 빼고 읽는다. 줄을 지우지 않고, 바꾸면 옛 줄에 「대체됨」을 적는다(저장소의 조건부 UPDATE). {@code toString} 을 두지 않는다(암호문).
 */
@Entity
@Table(name = "biz_contract_files")
public class BizContractFileEntity {

    @Id
    private Long id;

    /** 아직 건에 붙지 않은 미리 올린 사본은 null(V115) */
    private Long applicationId;
    private String fileName;
    private String contentType;
    private Integer sizeBytes;
    private byte[] content;
    private String sha256;
    private Long uploadedBy;
    private Instant uploadedAt;
    private Instant replacedAt;
    private Long replacedBy;
    private Long replacedByFileId;
    @Enumerated(EnumType.STRING)
    private DocFileChannel channel;

    protected BizContractFileEntity() {
    }

    /** 새 사본 — {@code applicationId} null 이면 미리 올림 */
    public static BizContractFileEntity create(long id, Long applicationId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256,
                                               DocFileChannel channel, long operatorId, Instant now) {
        BizContractFileEntity c = new BizContractFileEntity();
        c.id = id;
        c.applicationId = applicationId;
        c.fileName = fileName;
        c.contentType = contentType;
        c.sizeBytes = sizeBytes;
        c.content = encrypted;
        c.sha256 = sha256;
        c.channel = channel;
        c.uploadedBy = operatorId;
        c.uploadedAt = now;
        return c;
    }
}
