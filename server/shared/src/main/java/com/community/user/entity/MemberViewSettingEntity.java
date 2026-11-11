package com.community.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 공개 사이트 회원이 고른 보기 설정(야간 모드·글자 크기·댓글 기본 정렬) — V73 의 {@code member_view_settings}. 회원마다 최대 한 행이다.
 * 글자 크기는 PC({@code text_scale})·휴대폰({@code text_scale_mobile}, V185)을 따로 둔다.
 *
 * <p>행이 없으면 기본값({@code AUTO}·PC {@code 100}·휴대폰 {@code 110}·{@code OLDEST})을 따른다. 세 칸은 늘 함께 저장된다(통째로 바꾼다).
 * 서버는 값만 저장하고 적용은 화면이 한다. 값 검사는 부르는 쪽이 {@code ViewSettingsPolicy} 로 끝낸다.
 * 표시 형식(언어·시간대 — {@link MemberDisplayPreferenceEntity})과는 다른 표다.
 */
@Entity
@Table(name = "member_view_settings")
@DynamicUpdate
public class MemberViewSettingEntity {

    /** 행이 없을 때의 기본값 — 회원 보기 설정(api {@code ViewSettingsPolicy})과 관리자 회원 상세(admin)가 같이 쓴다 */
    public static final String DEFAULT_THEME = "AUTO";
    /** PC 기본 100, 휴대폰 기본 110 — 사용자 2026-11-11 */
    public static final int DEFAULT_TEXT_SCALE = 100;
    public static final int DEFAULT_TEXT_SCALE_MOBILE = 110;
    public static final String DEFAULT_COMMENT_ORDER = "OLDEST";
    /** 데스크톱 메뉴 위치 — 자동(운영자 설정 > 테마 기본을 따른다), V105 */
    public static final String DEFAULT_MENU_POSITION = "AUTO";
    /** 글 목록 제목 굵게 — 기본 보통 굵기(V198) */
    public static final boolean DEFAULT_TITLE_BOLD = false;

    @Id
    private Long userId;

    private String theme;
    private short textScale;
    private short textScaleMobile;
    private String commentOrder;
    private String menuPosition;
    private boolean titleBold;
    private Instant createdAt;
    private Instant updatedAt;

    protected MemberViewSettingEntity() {
    }

    /** 처음 저장할 때 — 다섯 칸은 검사를 마친 값이다 */
    public static MemberViewSettingEntity create(long userId, String theme, int textScale, int textScaleMobile, String commentOrder, String menuPosition,
            Instant now) {
        return create(userId, theme, textScale, textScaleMobile, commentOrder, menuPosition, DEFAULT_TITLE_BOLD, now);
    }

    /** 처음 저장할 때 — 제목 굵게(V198)까지 */
    public static MemberViewSettingEntity create(long userId, String theme, int textScale, int textScaleMobile, String commentOrder, String menuPosition,
            boolean titleBold, Instant now) {
        MemberViewSettingEntity setting = new MemberViewSettingEntity();
        setting.userId = userId;
        setting.theme = theme;
        setting.textScale = (short) textScale;
        setting.textScaleMobile = (short) textScaleMobile;
        setting.commentOrder = commentOrder;
        setting.menuPosition = menuPosition;
        setting.titleBold = titleBold;
        setting.createdAt = now;
        setting.updatedAt = now;
        return setting;
    }

    /** 값을 바꾼다 — 다섯 칸은 검사를 마친 값이다. 같은 값이면 아무것도 바꾸지 않는다 */
    public void change(String theme, int textScale, int textScaleMobile, String commentOrder, String menuPosition, Instant now) {
        change(theme, textScale, textScaleMobile, commentOrder, menuPosition, this.titleBold, now);
    }

    /** 값을 바꾼다 — 제목 굵게(V198)까지. 같은 값이면 아무것도 바꾸지 않는다 */
    public void change(String theme, int textScale, int textScaleMobile, String commentOrder, String menuPosition, boolean titleBold, Instant now) {
        if (theme.equals(this.theme) && textScale == this.textScale && textScaleMobile == this.textScaleMobile && commentOrder.equals(this.commentOrder)
                && menuPosition.equals(this.menuPosition) && titleBold == this.titleBold) {
            return;
        }
        this.theme = theme;
        this.textScale = (short) textScale;
        this.textScaleMobile = (short) textScaleMobile;
        this.commentOrder = commentOrder;
        this.menuPosition = menuPosition;
        this.titleBold = titleBold;
        this.updatedAt = now;
    }

    /** 글 목록 제목 굵게(V198) */
    public boolean isTitleBold() {
        return titleBold;
    }

    public String getMenuPosition() {
        return menuPosition;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTheme() {
        return theme;
    }

    /** PC 글자 크기 */
    public int getTextScale() {
        return textScale;
    }

    /** 휴대폰 글자 크기(V185) */
    public int getTextScaleMobile() {
        return textScaleMobile;
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
