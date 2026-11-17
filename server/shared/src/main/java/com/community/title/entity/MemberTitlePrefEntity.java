package com.community.title.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원 대표 칭호 — {@code member_title_prefs}(V240). 칭호 없음 + 숨김 아님 = 기본(가진 작가 칭호 중 가장 높은 것) · 숨김 = 「안 보이기」.
 */
@Entity
@Table(name = "member_title_prefs")
public class MemberTitlePrefEntity {

    @Id
    private Long userId;
    private Long representativeTitleId;
    private boolean hidden;
    private Instant updatedAt;

    protected MemberTitlePrefEntity() {
    }

    public static MemberTitlePrefEntity create(long userId, Long titleId, boolean hidden, Instant now) {
        MemberTitlePrefEntity e = new MemberTitlePrefEntity();
        e.userId = userId;
        e.change(titleId, hidden, now);
        return e;
    }

    public void change(Long titleId, boolean hidden, Instant now) {
        this.representativeTitleId = hidden ? null : titleId;
        this.hidden = hidden;
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRepresentativeTitleId() {
        return representativeTitleId;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
