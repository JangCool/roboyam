package com.community.post.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.permission.dto.AdminActor;
import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.point.service.PointService.FeatureReward;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostFeatureEntity;
import com.community.post.exception.AdminPostNotFoundException;
import com.community.post.exception.PostStateInvalidException;
import com.community.post.repository.master.PostFeatureRepository;
import com.community.post.repository.master.PostRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우수 글 선정·취소(point-board-tier-contract — {@code POST·DELETE /api/admin/posts/{id}/feature}, 권한 {@code post.feature}). 글 행을 잠근 한 트랜잭션에서 선정 기록({@code post_features})·
 * 포인트·감사를 함께 한다.
 *
 * <ul>
 *   <li><b>선정</b>: 공개 글(지우지 않음·숨김 아님)만, 이미 선정돼 있으면 409 {@code POST_STATE_INVALID}. 선정 줄을 남기고 그 게시판 등급의 우수 글 포인트를
 *       {@link PointService#featurePost} 로 준다(보류 시간이 있으면 보류, 0 이거나 포인트 끈 게시판·비회원 글이면 선정만). 다시 선정해도 원장 유일이라 두 번 주지 않는다.</li>
 *   <li><b>취소</b>: 살아 있는 선정이 없으면 409 {@code POST_STATE_INVALID}(글 상태는 묻지 않는다 — 지운 글도 취소할 수 있다). 선정 줄에 취소를 적고 대기 중인 우수 글 보류를 취소한다.
 *       확정 뒤면 회수하지 않는다.</li>
 *   <li>감사 {@code POST_FEATURE}·{@code POST_UNFEATURE}(대상 POST) — 덧붙임: 선정은 지급 {@code grantedPoints}·보류 {@code heldPoints}·{@code releaseAt}, 취소는 취소한 보류 수.</li>
 * </ul>
 */
@Service
public class AdminPostFeatureService {

    private static final Logger log = LoggerFactory.getLogger(AdminPostFeatureService.class);

    private final PostRepository posts;
    private final PostFeatureRepository features;
    private final BoardCatalog catalog;
    private final PointService points;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public AdminPostFeatureService(PostRepository posts, PostFeatureRepository features, BoardCatalog catalog, PointService points, AuditLogService audit,
                                   SnowflakeIdGenerator ids) {
        this.posts = posts;
        this.features = features;
        this.catalog = catalog;
        this.points = points;
        this.audit = audit;
        this.ids = ids;
    }

    @Transactional
    public void feature(AdminActor actor, String idText) {
        PostEntity post = lock(idText);
        Instant now = Instant.now();
        PostModerationActions.requirePublic(post, now);
        if (features.findByPostIdAndUnfeaturedAtIsNull(post.getId()).isPresent()) {
            throw new PostStateInvalidException();
        }
        features.persist(PostFeatureEntity.create(ids.nextId(), post.getId(), post.getAuthorId(), actor.operatorId(), now));
        features.flush(); // 살아 있는 선정은 하나 — 유일 색인 위반은 여기서(포인트 보류의 확정 배치가 이 줄을 본다)
        EffectiveBoard board = catalog.snapshot().board(post.getBoardId()).orElse(null);
        String pointMode = board == null ? BoardFields.POINT_OFF : board.pointMode();
        FeatureReward reward = points.featurePost(post.getAuthorId(), post.getId(), pointMode);
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POST_FEATURE, AuditResult.SUCCESS).target("POST", Long.toString(post.getId()))
                .with("grantedPoints", reward.granted()).with("heldPoints", reward.held());
        audit.record(reward.releaseAt() == null ? event : event.with("releaseAt", reward.releaseAt().toString()));
        log.info("우수 글 선정 postId={} by operatorId={} granted={} held={}", post.getId(), actor.operatorId(), reward.granted(), reward.held());
    }

    @Transactional
    public void unfeature(AdminActor actor, String idText) {
        PostEntity post = lock(idText);
        Instant now = Instant.now();
        PostFeatureEntity feature = features.findByPostIdAndUnfeaturedAtIsNull(post.getId()).orElseThrow(PostStateInvalidException::new);
        feature.unfeature(actor.operatorId(), now);
        int cancelled = points.cancelHeld(PointService.REF_POST, post.getId(), PointReason.POST_FEATURED, PointService.HeldRelease.UNFEATURED.name());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POST_UNFEATURE, AuditResult.SUCCESS).target("POST", Long.toString(post.getId()))
                .with("cancelledHolds", cancelled));
        log.info("우수 글 선정 취소 postId={} by operatorId={} cancelledHolds={}", post.getId(), actor.operatorId(), cancelled);
    }

    private PostEntity lock(String idText) {
        Long id = AdminPostQueryService.parseId(idText);
        return (id == null ? java.util.Optional.<PostEntity>empty() : posts.findWithLockById(id)).orElseThrow(AdminPostNotFoundException::new);
    }
}
