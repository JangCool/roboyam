package com.community.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 출석 한 줄 — {@code point_attendances}(V89), 회원·날(한국 시간)마다 하나({@code ux_point_attendances_user_day}). 넣기는 저장소 {@code PointAttendanceStore} 의
 * {@code INSERT … ON CONFLICT (user_id, day) DO NOTHING}(native — 같은 날 두 번 눌러도 한 줄만, 한 번만 성공)이고 고치지 않는다. 읽기는 저장소의 QueryDSL 프로젝션.
 */
@Entity
@Table(name = "point_attendances")
public class PointAttendanceEntity {

    @Id
    private Long id;

    private Long userId;
    /** 출석한 날(한국 시간) — {@code day} 는 HQL 함수 이름과 겹쳐 속성 이름을 달리 둔다 */
    @Column(name = "day")
    private LocalDate attendanceDay;
    /** 이 출석의 연속 일수(1 이상) */
    private int streak;
    private Instant createdAt;

    protected PointAttendanceEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getAttendanceDay() {
        return attendanceDay;
    }

    public int getStreak() {
        return streak;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
