package com.community.counsel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 주간 「가장 도움 된 조언」 수 한 줄 — {@code counsel_weekly_picks}(shared V274, 게시판 · 주차(그 주 월요일, 한국 날짜) · 상담사마다 하나, 사람 회원 전체는 상담사 0).
 * 쌓기는 고를 때 한 문장({@code CounselPickStore#add} — {@code ON CONFLICT} upsert, 네이티브)뿐이라 만들기 메서드가 없다 — 읽기·질의용 매핑이다.
 */
@Entity
@Table(name = "counsel_weekly_picks")
@IdClass(CounselWeeklyPickEntity.Key.class)
public class CounselWeeklyPickEntity {

    @Id
    private Long boardId;
    @Id
    private LocalDate weekStart;
    @Id
    private Long aiMemberId;

    private Integer picks;

    protected CounselWeeklyPickEntity() {
    }

    /** 복합 키(게시판 · 주차 · 상담사) */
    public static class Key implements Serializable {

        private Long boardId;
        private LocalDate weekStart;
        private Long aiMemberId;

        protected Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(boardId, k.boardId) && Objects.equals(weekStart, k.weekStart) && Objects.equals(aiMemberId, k.aiMemberId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(boardId, weekStart, aiMemberId);
        }
    }

    public Long getBoardId() {
        return boardId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public Long getAiMemberId() {
        return aiMemberId;
    }

    public int getPicks() {
        return picks == null ? 0 : picks;
    }
}
