package com.community.user.entity;

import com.community.grade.entity.GradeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 닉네임·등급 등 공개되는 회원 정보 — V2__auth.sql 의 {@code profiles}(등급 칸은 V9__grades.sql 이 바꿨다). users 와 같은 ID 를 쓴다(1:1).
 * 지금은 루트 계정을 만들고 회원 조회·등급 판정에 필요한 칸만 매핑했다(프로필 이미지 등은 그 기능을 만들 때 더한다).
 *
 * <p>등급은 등급 설정({@link GradeEntity})의 키({@code grade_key})다. {@code gradeManual} 은 운영자가 등급을 <b>수동으로 올린</b> 회원 표시로,
 * 등급 옵션(수동 상향 회원의 이후 자동 승급)이 이 회원들에게만 적용된다.
 */
@Entity
@Table(name = "profiles")
@DynamicUpdate
public class ProfileEntity {

    @Id
    private Long userId;

    private String nickname;

    /** 한 줄 소개. 없으면 null */
    private String bio;

    /** 등급 키 — 등급 설정의 {@code grades.grade_key} */
    private String gradeKey;

    /** 운영자가 등급을 수동으로 올린 적이 있는가 */
    private boolean gradeManual;

    /** 가입 뒤 닉네임을 바꾼 횟수 — 가입 때 정한 닉네임은 세지 않는다(V66) */
    private short nicknameChangeCount;

    /** 마지막으로 닉네임을 바꾼 시각. 바꾼 적이 없으면 null */
    private Instant nicknameChangedAt;

    private Instant createdAt;
    private Instant updatedAt;

    protected ProfileEntity() {
    }

    /** 새 회원의 프로필 — 기본(첫) 등급으로 시작한다 */
    public static ProfileEntity create(long userId, String nickname, Instant now) {
        ProfileEntity profile = new ProfileEntity();
        profile.userId = userId;
        profile.nickname = nickname;
        profile.gradeKey = GradeEntity.BASE_KEY;
        profile.gradeManual = false;
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    /** 자동 승급 — 조건을 채워 시스템이 올렸다 */
    public void promoteAutomatically(String gradeKey, Instant now) {
        this.gradeKey = gradeKey;
        this.updatedAt = now;
    }

    /** 운영자가 등급을 바꿨다(수동 상향 표시를 남긴다) — 수동 변경 기능(grade.override)은 후속 */
    public void changeGradeManually(String gradeKey, Instant now) {
        this.gradeKey = gradeKey;
        this.gradeManual = true;
        this.updatedAt = now;
    }

    /**
     * 닉네임을 바꾼다 — 바꾼 횟수를 하나 늘리고 바꾼 시각을 남긴다. 형식·중복·횟수 한도는 부르는 서비스가 행을 잠근 뒤 먼저 확인한다.
     *
     * @return 바뀌기 전 닉네임(이력에 남긴다)
     */
    public String changeNickname(String nickname, Instant now) {
        String previous = this.nickname;
        this.nickname = nickname;
        this.nicknameChangeCount = (short) (this.nicknameChangeCount + 1);
        this.nicknameChangedAt = now;
        this.updatedAt = now;
        return previous;
    }

    /** 한 줄 소개를 바꾼다 — 빈 값은 null 로 저장한다(정규화·길이 검사는 부르는 쪽). 같은 값이면 고치지 않는다 */
    public void changeBio(String bio, Instant now) {
        String next = bio == null || bio.isEmpty() ? null : bio;
        if (Objects.equals(this.bio, next)) {
            return;
        }
        this.bio = next;
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getBio() {
        return bio;
    }

    /** 등급 키(예: SEEDLING) */
    public String getGradeKey() {
        return gradeKey;
    }

    public boolean isGradeManual() {
        return gradeManual;
    }

    /** 가입 뒤 닉네임을 바꾼 횟수 */
    public int getNicknameChangeCount() {
        return nicknameChangeCount;
    }

    public Instant getNicknameChangedAt() {
        return nicknameChangedAt;
    }

    /** 프로필을 만든 시각(가입과 같은 트랜잭션) */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
