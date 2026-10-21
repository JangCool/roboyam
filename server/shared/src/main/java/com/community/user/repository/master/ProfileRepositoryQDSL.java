package com.community.user.repository.master;

import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepositoryQDSL extends RoboyamRepositoryQDSL {

    public ProfileRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 닉네임 중복 확인(대소문자 무시). ux_profiles_nickname(lower(nickname)) 과 같은 기준이다 — <b>양쪽을 DB 의 {@code lower()} 로 비교</b>해
     * 그 함수 인덱스를 탄다.
     */
    public boolean existsByNickname(String nickname) {
        QProfileEntity profile = QProfileEntity.profileEntity;
        return query.selectOne().from(profile)
                .where(profile.nickname.lower().eq(Expressions.stringTemplate("lower({0})", nickname)))
                .fetchFirst() != null;
    }
}
