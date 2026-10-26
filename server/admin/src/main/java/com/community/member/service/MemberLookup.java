package com.community.member.service;

import com.community.member.exception.MemberNotFoundException;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 상세 서랍의 하위 구획 API(기능 제한·탈퇴 회원 연락처 — 커머스 0-4)가 경로의 회원 번호를 회원으로 바꾼다. 번호 형식이 틀리거나 없는 회원은 404 {@code MEMBER_NOT_FOUND}
 * (회원 상세와 같은 규칙 — 어떤 번호가 있는지 알리지 않는다).
 */
@Service
@Transactional(readOnly = true)
public class MemberLookup {

    private final UserRepository users;

    public MemberLookup(UserRepository users) {
        this.users = users;
    }

    /** 경로의 회원 번호(숫자 1~19자리) — 형식이 틀리면 404 */
    public static long parseId(String memberId) {
        if (memberId == null || memberId.isEmpty() || memberId.length() > 19 || !memberId.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new MemberNotFoundException();
        }
        try {
            return Long.parseLong(memberId);
        } catch (NumberFormatException e) {
            throw new MemberNotFoundException();
        }
    }

    /** 있는 회원(탈퇴 회원 포함 — 익명화된 행이 남는다) */
    public Member require(String memberId) {
        long id = parseId(memberId);
        UserEntity user = users.findById(id).orElseThrow(MemberNotFoundException::new);
        return new Member(id, user.getStatus() == UserStatus.WITHDRAWN, user.getDeletedAt());
    }

    /**
     * @param withdrawn   탈퇴했는가
     * @param withdrawnAt 탈퇴 시각(탈퇴 회원만)
     */
    public record Member(long id, boolean withdrawn, Instant withdrawnAt) {
    }
}
