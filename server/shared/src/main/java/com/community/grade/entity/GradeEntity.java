package com.community.grade.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 등급 한 단계 — V9__grades.sql 의 {@code grades}. 등급은 서열({@code level}) 순서가 있는 목록이고 등급 설정이 원천이다(최소 2 · 최대 10단계).
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

    @Id
    private String gradeKey;

    private int level;
    private String label;

    /** 가입 후 일수 조건(0~3650). 기본 등급은 0 */
    private int minDays;

    /** 글+댓글 수 조건(0~100000). 기본 등급은 0 */
    private int minActivity;

    private String badgeColor;

    /** PrimeIcons 클래스. 없으면 null */
    private String badgeIcon;

    private Instant createdAt;
    private Instant updatedAt;

    protected GradeEntity() {
    }

    public static GradeEntity create(String gradeKey, int level, String label, int minDays, int minActivity, String badgeColor, String badgeIcon, Instant now) {
        GradeEntity grade = new GradeEntity();
        grade.gradeKey = gradeKey;
        grade.level = level;
        grade.label = label;
        grade.minDays = minDays;
        grade.minActivity = minActivity;
        grade.badgeColor = badgeColor;
        grade.badgeIcon = badgeIcon;
        grade.createdAt = now;
        grade.updatedAt = now;
        return grade;
    }

    /** 서열·이름·조건·뱃지를 고친다(키는 못 바꾼다). 바뀐 칸이 없으면 아무것도 하지 않는다 */
    public void change(int level, String label, int minDays, int minActivity, String badgeColor, String badgeIcon, Instant now) {
        if (this.level == level && this.label.equals(label) && this.minDays == minDays && this.minActivity == minActivity
                && this.badgeColor.equals(badgeColor) && Objects.equals(this.badgeIcon, badgeIcon)) {
            return;
        }
        this.level = level;
        this.label = label;
        this.minDays = minDays;
        this.minActivity = minActivity;
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
}
