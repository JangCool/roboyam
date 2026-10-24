package com.community.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 공개 사이트 회원이 고른 보기 설정(야간 모드·글자 크기·댓글 기본 정렬) — V73 의 {@code member_view_settings}. 회원마다 최대 한 행이다.
 *
 * <p>행이 없으면 기본값({@code AUTO}·{@code 100}·{@code OLDEST})을 따른다. 세 칸은 늘 함께 저장된다(통째로 바꾼다).
 * 서버는 값만 저장하고 적용은 화면이 한다. 값 검사는 부르는 쪽이 {@code ViewSettingsPolicy} 로 끝낸다.
 * 표시 형식(언어·시간대 — {@link MemberDisplayPreferenceEntity})과는 다른 표다.
 */
@Entity
@Table(name = "member_view_settings")
@DynamicUpdate
public class MemberViewSettingEntity {

    @Id
    private Long userId;

    private String theme;
    private short textScale;
    private String commentOrder;
    private Instant createdAt;
    private Instant updatedAt;

    protected MemberViewSettingEntity() {
    }

    /** 처음 저장할 때 — 세 칸은 검사를 마친 값이다 */
    public static MemberViewSettingEntity create(long userId, String theme, int textScale, String commentOrder, Instant now) {
        MemberViewSettingEntity setting = new MemberViewSettingEntity();
        setting.userId = userId;
        setting.theme = theme;
        setting.textScale = (short) textScale;
        setting.commentOrder = commentOrder;
        setting.createdAt = now;
        setting.updatedAt = now;
        return setting;
    }

    /** 값을 바꾼다 — 세 칸은 검사를 마친 값이다. 같은 값이면 아무것도 바꾸지 않는다 */
    public void change(String theme, int textScale, String commentOrder, Instant now) {
        if (theme.equals(this.theme) && textScale == this.textScale && commentOrder.equals(this.commentOrder)) {
            return;
        }
        this.theme = theme;
        this.textScale = (short) textScale;
        this.commentOrder = commentOrder;
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTheme() {
        return theme;
    }

    public int getTextScale() {
        return textScale;
    }

    public String getCommentOrder() {
        return commentOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
