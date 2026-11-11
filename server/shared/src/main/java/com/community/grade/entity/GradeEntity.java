package com.community.grade.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 등급 한 단계 — V9__grades.sql 의 {@code grades}(V196 — 최소 레벨·도트 캐릭터, 최대 11단계). 등급은 서열({@code level}) 순서가 있는 목록이고 등급 설정이 원천이다(최소 2 · 최대 11단계).
 * 승급 조건 = 최소 레벨({@code minLevel}) AND 가입 후 일수 AND 글+댓글 수(남겨 둔 조건, 새 기본값 0) — docs/features/grade-level-contract.md 2절.
 * 키({@code gradeKey})는 만든 뒤 바뀌지 않는 식별자다(시드 SEEDLING·REGULAR·LOYAL·VETERAN, 새 등급은 서버가 GRADE_&lt;번호&gt; 로 발급).
 * 첫 등급(서열 1, 키 {@link #BASE_KEY})은 기본 등급이라 승급 조건이 없고 지우거나 옮길 수 없다. 가입한 회원은 기본 등급으로 시작한다.
 * 이름·뱃지는 운영자가 입력한 값이라 한 언어로 저장한다.
 */
@Entity
@Table(name = "grades")
@DynamicUpdate
public class GradeEntity {

    /** 기본(첫) 등급의 키 — 지울 수 없어 늘 있다. 새 회원이 처음 받는 등급 */
    public static final String BASE_KEY = "SEEDLING";

    /** 도트 캐릭터 11종 — 서열 순 기본값이기도 하다(V196 {@code ck_grades_sprite} 와 같다) */
    public static final java.util.List<String> SPRITES = java.util.List.of("SPROUT", "BOLT", "ANTENNA", "BATTERY", "GEAR", "HEART", "LIGHTNING", "STAR", "CROWN",
            "LEGEND", "MYTH");

    /** 서열 {@code level} 의 기본 도트(서열 순, 11 넘으면 마지막) */
    public static String defaultSprite(int level) {
        return SPRITES.get(Math.max(0, Math.min(level, SPRITES.size()) - 1));
    }

    @Id
    private String gradeKey;

    private int level;
    private String label;

    /** 가입 후 일수 조건(0~3650). 기본 등급은 0 */
    private int minDays;

    /** 글+댓글 수 조건(0~100000). 기본 등급은 0 */
    private int minActivity;

    /** 최소 레벨 조건(1~10000, V196). 기본 등급은 1 */
    private int minLevel = 1;

    /** 도트 캐릭터({@link #SPRITES} 중 하나, V196) — 공개 화면이 작성자 줄·프로필에 그린다 */
    private String sprite;

    private String badgeColor;

    /** PrimeIcons 클래스. 없으면 null */
    private String badgeIcon;

    private Instant createdAt;
    private Instant updatedAt;

    protected GradeEntity() {
    }

    public static GradeEntity create(String gradeKey, int level, String label, int minLevel, int minDays, int minActivity, String badgeColor, String badgeIcon,
                                     String sprite, Instant now) {
        GradeEntity grade = new GradeEntity();
        grade.gradeKey = gradeKey;
        grade.level = level;
        grade.label = label;
        grade.minLevel = minLevel;
        grade.minDays = minDays;
        grade.minActivity = minActivity;
        grade.sprite = sprite;
        grade.badgeColor = badgeColor;
        grade.badgeIcon = badgeIcon;
        grade.createdAt = now;
        grade.updatedAt = now;
        return grade;
    }

    /** 서열·이름·조건·뱃지·도트를 고친다(키는 못 바꾼다). 바뀐 칸이 없으면 아무것도 하지 않는다 */
    public void change(int level, String label, int minLevel, int minDays, int minActivity, String badgeColor, String badgeIcon, String sprite, Instant now) {
        if (this.level == level && this.label.equals(label) && this.minLevel == minLevel && this.minDays == minDays && this.minActivity == minActivity
                && this.badgeColor.equals(badgeColor) && Objects.equals(this.badgeIcon, badgeIcon) && Objects.equals(this.sprite, sprite)) {
            return;
        }
        this.level = level;
        this.label = label;
        this.minLevel = minLevel;
        this.minDays = minDays;
        this.minActivity = minActivity;
        this.sprite = sprite;
        this.badgeColor = badgeColor;
        this.badgeIcon = badgeIcon;
        this.updatedAt = now;
    }

    public String getGradeKey() {
        return gradeKey;
    }

    public int getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }

    public int getMinDays() {
        return minDays;
    }

    public int getMinActivity() {
        return minActivity;
    }

    public String getBadgeColor() {
        return badgeColor;
    }

    public String getBadgeIcon() {
        return badgeIcon;
    }

    public int getMinLevel() {
        return minLevel;
    }

    /** 도트 캐릭터 — 비어 있을 수 없지만(V196 NOT NULL) 없으면 서열 기본값 */
    public String getSprite() {
        return sprite == null ? defaultSprite(level) : sprite;
    }
}
