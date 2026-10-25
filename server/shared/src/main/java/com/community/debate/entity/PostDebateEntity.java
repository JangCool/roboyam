package com.community.debate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 글의 찬반 토론(V79 {@code post_debates}) — 행이 있으면 그 글의 토론이 열려 있다(하이라이트·노출과 무관). docs/features/post-debate-contract.md.
 * 닫기 = 행을 지운다(이미 고른 입장·쓴 의견·고정·고정 요청은 그대로). 만들기·바꾸기·닫기는 {@link com.community.debate.service.PostDebateWriter} 가 한다.
 */
@Entity
@Table(name = "post_debates")
public class PostDebateEntity {

    /** 글쓴이(회원) — 글쓴이가 바꾸고 닫을 수 있다 */
    public static final String BY_MEMBER = "MEMBER";
    /** 그 게시판 매니저(회원) — 공개 API 에는 {@link #BY_OPERATOR} 로 보인다(글쓴이에게 읽기 전용) */
    public static final String BY_MANAGER = "MANAGER";
    /** 운영자(관리자 앱) */
    public static final String BY_OPERATOR = "OPERATOR";

    @Id
    private Long postId;

    /** 「중립도 받기」 — 끄면 새로 중립을 고를 수 없다(이미 고른 중립·쓴 중립 의견은 그대로) */
    private boolean neutralEnabled;
    /** 「입장 바꾸기 허용」 — 끄면 한 번 고른 입장을 다른 값으로 바꿀 수 없다(처음 고르기는 된다). 나중에 꺼도 이미 고른 입장·의견은 그대로 */
    private boolean stanceChangeAllowed;
    private String openedByKind;
    private Long openedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private String updatedByKind;
    private Long updatedBy;

    protected PostDebateEntity() {
    }

    /** 설정을 바꾼다 */
    public void change(boolean neutralEnabled, boolean stanceChangeAllowed, String byKind, Long by, Instant now) {
        this.neutralEnabled = neutralEnabled;
        this.stanceChangeAllowed = stanceChangeAllowed;
        this.updatedByKind = byKind;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    /** 글쓴이가 연 토론인가 */
    public boolean openedByMember() {
        return BY_MEMBER.equals(openedByKind);
    }

    /** API 의 {@code openedBy} — MEMBER · OPERATOR(매니저가 연 것도 OPERATOR — 글쓴이에게 읽기 전용이라는 뜻이 같다) */
    public String openedByView() {
        return openedByMember() ? BY_MEMBER : BY_OPERATOR;
    }

    public Long getPostId() {
        return postId;
    }

    public boolean isNeutralEnabled() {
        return neutralEnabled;
    }

    public boolean isStanceChangeAllowed() {
        return stanceChangeAllowed;
    }

    public String getOpenedByKind() {
        return openedByKind;
    }

    public Long getOpenedBy() {
        return openedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedByKind() {
        return updatedByKind;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
