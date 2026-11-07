package com.community.terms.repository.master;

import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 약관 변경 메일({@code TermsChangeMails})의 받는 회원 — 지워지지 않았고 탈퇴하지 않았으며 이메일이 있고 인증을 마친 회원(정지 회원 포함). <b>트랜잭션은 부르는 서비스가 연다</b>(게시 트랜잭션).
 */
@Repository
public class TermsChangeMailRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QUserEntity USER = QUserEntity.userEntity;

    public TermsChangeMailRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 받는 회원 id(id 차례 — 보낼 때를 이 차례로 나눠 미룬다) */
    public List<Long> recipientIds() {
        return query.select(USER.id).from(USER)
                .where(USER.deletedAt.isNull(), USER.status.ne(UserStatus.WITHDRAWN), USER.email.isNotNull(), USER.emailVerifiedAt.isNotNull())
                .orderBy(USER.id.asc()).fetch();
    }
}
