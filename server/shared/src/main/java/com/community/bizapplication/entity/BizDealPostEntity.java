package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사업자 핫딜 게시 결과 — shared V123 {@code biz_deal_posts}(신청 건 하나에 한 줄, PK). 넣기는 「먼저 넣은 쪽이 이긴다」라 저장소의 네이티브 {@code ON CONFLICT DO NOTHING}
 * 이다 — 만들기 메서드가 없는 읽기·질의용 매핑. 상태는 {@code BizDealPostStore.POSTED}·{@code FAILED} 글자.
 */
@Entity
@Table(name = "biz_deal_posts")
public class BizDealPostEntity {

    @Id
    private Long applicationId;

    private Long postId;
    private String status;
    private String failReason;
    private Instant hiddenAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected BizDealPostEntity() {
    }
}
