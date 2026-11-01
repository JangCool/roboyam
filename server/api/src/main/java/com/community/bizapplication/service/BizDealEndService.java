package com.community.bizapplication.service;

import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizDealPostStore;
import com.community.bizapplication.repository.master.BizDealPostStore.PostRow;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.exception.DealEndedException;
import com.community.hotdeal.service.DealEndService;
import com.community.hotdeal.service.DealSettingsReader;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업자 센터 [품절로 종료]({@code POST /api/business/applications/{id}/deal/end} — biz-hotdeal-contract 규칙 6). 남의 건 404 {@code BIZ_APPLICATION_NOT_FOUND},
 * 글이 없음(게시 전·실패·숨김·지움) 409 {@code BIZ_APPLICATION_STATE_CHANGED}, 이미 끝난 딜(종료·기간 종료) 409 {@code DEAL_ENDED}. 종료는 회원 핫딜 작성자 종료와 같은 길
 * ({@link DealEndService#endByAuthor} — 의심 구간 요청자 보상 · 처리 기록 {@code DEAL_ENDED_BY_BUSINESS}). 신청 건 상태는 그대로, 환불 없음.
 */
@Service
public class BizDealEndService {

    private final BizApplicationStore store;
    private final BizDealPostStore results;
    private final PostRepository posts;
    private final DealEndService ends;
    private final DealSettingsReader settings;

    public BizDealEndService(BizApplicationStore store, BizDealPostStore results, PostRepository posts, DealEndService ends, DealSettingsReader settings) {
        this.store = store;
        this.results = results;
        this.posts = posts;
        this.ends = ends;
        this.settings = settings;
    }

    @Transactional
    public void end(long userId, long applicationId) {
        var app = store.lockOwned(applicationId, userId).orElseThrow(BizApplicationException::notFound);
        PostRow row = results.find(app.id()).filter(PostRow::posted).orElseThrow(BizApplicationException::stateChanged);
        Instant now = ends.now();
        PostEntity post = posts.findById(row.postId()).orElseThrow(BizApplicationException::stateChanged);
        if (post.isGoneAt(now) || post.isHiddenAt(now)) {
            throw BizApplicationException.stateChanged();
        }
        PostDealEntity d = ends.lock(post.getId()).orElseThrow(BizApplicationException::stateChanged);
        if (!d.view(settings.current().autoExpireDays(), now).live()) {
            throw DealEndedException.action();
        }
        ends.endByAuthor(d, userId, now);
    }
}
