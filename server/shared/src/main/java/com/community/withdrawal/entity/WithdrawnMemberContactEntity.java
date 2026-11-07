package com.community.withdrawal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 탈퇴 회원 연락용 이메일 한 줄 — shared V84 의 {@code withdrawn_member_contacts}(분리 보관 — commerce.md 17-2 · 17-3). 이메일은 DataCipher 암호문({@code d<버전>:})뿐이다.
 * 줄은 {@code WithdrawnContactRows#upsert}(한 문장 upsert)가 만들고, 일반 회원 조회는 이 표를 읽지 않는다. {@code toString} 은 기본(칸 값을 싣지 않는다)이다.
 */
@Entity
@Table(name = "withdrawn_member_contacts")
public class WithdrawnMemberContactEntity {

    @Id
    private Long userId;

    private String emailCipher;
    private Instant withdrawnAt;
    private Instant purgeAfter;

    protected WithdrawnMemberContactEntity() {
    }
}
