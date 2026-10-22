package com.community.post.service;

import com.community.grade.service.GradePromotionService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 글·댓글을 쓴 회원의 즉시 승급(활동 수 조건 — docs/domain/operator-permissions.md "글·댓글 수처럼 사건으로 판정 가능한 조건은 즉시").
 *
 * <p>글쓰기({@link PostWriteService#create})·댓글쓰기({@code CommentService#create})가 <b>저장 트랜잭션이 커밋된 뒤</b> 부른다 — 승급은
 * {@link GradePromotionService#promoteOnActivity} 의 별도 트랜잭션이라 실패해도 이미 저장된 글·댓글은 그대로이고 응답도 성공이다(로그만 남긴다).
 * 놓친 승급은 그 회원의 다음 글·댓글 때 다시 판정된다. 비회원 글은 부르지 않는다.
 */
@Component
public class WriterPromotion {

    private static final Logger log = LoggerFactory.getLogger(WriterPromotion.class);

    private final GradePromotionService promotions;

    public WriterPromotion(GradePromotionService promotions) {
        this.promotions = promotions;
    }

    /** @param authorId 쓴 회원 — 비회원이면 null(아무것도 하지 않는다) */
    public void afterWrite(Long authorId) {
        if (authorId == null) {
            return;
        }
        try {
            promotions.promoteOnActivity(authorId, Instant.now())
                    .ifPresent(gradeKey -> log.info("활동 수 조건으로 등급을 올렸다 userId={} grade={}", authorId, gradeKey));
        } catch (RuntimeException e) {
            // 예외 문구에 SQL 인자가 실릴 수 있어 클래스 이름만 남긴다
            log.warn("즉시 승급 판정에 실패했다 — 글·댓글 저장에는 영향이 없다 userId={} 원인={}", authorId, e.getClass().getSimpleName());
        }
    }
}
