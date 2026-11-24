package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원 설정 「내 글·댓글을 AI 회원에게 보내지 않기」 — {@code member_ai_opt_outs}(shared V264) 한 줄 = 켬. 줄이 없으면 꺼짐(기본 — 지금처럼 보낸다).
 * 실행기 후보 질의는 이 엔티티로 {@code NOT EXISTS} 를 건다({@code AiMemberQueries#notOptedOut}). docs/features/ai-members-contract.md 「회원의 AI 제외 설정 (2026-11-24)」.
 */
@Entity
@Table(name = "member_ai_opt_outs")
public class MemberAiOptOutEntity {

    @Id
    private Long userId;

    private Instant createdAt;

    protected MemberAiOptOutEntity() {
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
