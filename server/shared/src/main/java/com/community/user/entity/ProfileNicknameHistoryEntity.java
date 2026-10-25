package com.community.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 닉네임 변경 이력 한 줄 — V66 의 {@code profile_nickname_history}. <b>비공개</b>다: 공개 응답에는 이전 닉네임을 싣지 않고 관리자 회원 상세({@code member.pii})만 읽는다.
 * 줄은 추가만 하고 고치지 않는다. 회원 행이 지워지면 함께 지워진다(외래 키).
 */
@Entity
@Table(name = "profile_nickname_history")
public class ProfileNicknameHistoryEntity {

    @Id
    private Long id;

    private Long userId;
    private String oldNickname;
    private String newNickname;
    private Instant changedAt;

    protected ProfileNicknameHistoryEntity() {
    }

    public static ProfileNicknameHistoryEntity create(long id, long userId, String oldNickname, String newNickname, Instant changedAt) {
        ProfileNicknameHistoryEntity row = new ProfileNicknameHistoryEntity();
        row.id = id;
        row.userId = userId;
        row.oldNickname = oldNickname;
        row.newNickname = newNickname;
        row.changedAt = changedAt;
        return row;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOldNickname() {
        return oldNickname;
    }

    public String getNewNickname() {
        return newNickname;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
