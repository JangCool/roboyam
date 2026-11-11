package com.community.aimember.service;

import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.llm.AiPromptConfig;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 뉴스 글 소재 찾기(ai-members-contract 「뉴스 글·프롬프트 관리 (2026-11-11)」). <b>사이트 안 글만</b> 본다 — 외부 뉴스·검색 API·제공사 검색 도구는 쓰지 않는다(사용자 지시).
 *
 * <ul>
 *   <li>소재 = 말머리 이름에 키워드(기본 「뉴스」)가 든 글, 최근 N일(기본 3일) — 쓴 지 {@link #SETTLE} 지난 것.</li>
 *   <li>게시판: 켜진 일반 게시판 중 AI 가 쓸 수 없는 「항상 막힘」({@link AiMemberRules#blockedReason})·익명 게시판이 아닌 곳. 그 AI 계정이 쓰는 게시판을 먼저 보고, 없으면 다른 게시판.</li>
 *   <li>글 조건(사람 회원 글 · 공개·정상 · 비밀·19금·익명·소모임 아님 · 이미 소재로 쓴 글 아님)은 {@link AiMemberQueries#newsCandidates}.</li>
 *   <li>최신 {@value #PICK_FROM}개 중 하나를 무작위로 — 여러 AI 가 같은 바퀴에 같은 글을 고르지 않게.</li>
 * </ul>
 */
@Component
public class AiNewsSources {

    private static final Logger log = LoggerFactory.getLogger(AiNewsSources.class);

    /** 올라온 뒤 이만큼은 기다린다(쓰자마자 소재로 삼지 않게 — 고치거나 지울 틈) */
    static final Duration SETTLE = Duration.ofMinutes(5);
    static final int PICK_FROM = 10;

    private final AiMemberQueries queries;
    private final BoardCatalog catalog;
    private final SiteSettingsService site;

    public AiNewsSources(AiMemberQueries queries, BoardCatalog catalog, SiteSettingsService site) {
        this.queries = queries;
        this.catalog = catalog;
        this.site = site;
    }

    /** 소재 한 건 — 제목·평문 본문(닉네임은 싣지 않는다) */
    public record Source(long postId, long boardId, String title, String contentText) {
    }

    public Optional<Source> find(AiMemberProfileEntity a, AiPromptConfig c, Instant now, RandomGenerator random) {
        BoardCatalogSnapshot snap = catalog.snapshot();
        String keyword = c.newsKeyword().toLowerCase(Locale.ROOT);
        Set<Long> prefixIds = new HashSet<>();
        List<Long> eligible = new ArrayList<>();
        for (EffectiveBoard b : snap.boards()) {
            if (!b.active() || !BoardFields.KIND_GENERAL.equals(b.kind()) || b.anonymous() || AiMemberRules.blockedReason(b) != null) {
                continue;
            }
            boolean any = false;
            for (BoardPrefixInfo p : b.prefixes()) {
                if (p.name() != null && p.name().toLowerCase(Locale.ROOT).contains(keyword)) {
                    prefixIds.add(p.id());
                    any = true;
                }
            }
            if (any) {
                eligible.add(b.id());
            }
        }
        if (prefixIds.isEmpty()) {
            return Optional.empty();
        }
        Instant since = now.minus(Duration.ofDays(c.newsDays()));
        Instant until = now.minus(SETTLE);
        Set<Long> mine = new HashSet<>(a.getBoardIds());
        List<Long> own = eligible.stream().filter(mine::contains).toList();
        List<AiMemberQueries.PostCandidate> found = own.isEmpty() ? List.of() : queries.newsCandidates(prefixIds, own, since, until, now, PICK_FROM);
        if (found.isEmpty()) {
            found = queries.newsCandidates(prefixIds, eligible, since, until, now, PICK_FROM);
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AiMemberQueries.PostCandidate p = found.get(random.nextInt(found.size()));
        return Optional.of(new Source(p.postId(), p.boardId(), p.title(), p.contentText()));
    }

    /** 사이트 안 글 주소(절대 — 글 보기 화면은 http(s) 주소만 링크로 그린다). 공개 주소가 없으면 null */
    public String link(long postId) {
        try {
            String base = site.current().map(SiteValues::publicUrl).orElse(null);
            return base == null || base.isBlank() ? null : base + "/posts/" + postId;
        } catch (RuntimeException e) {
            log.warn("사이트 공개 주소를 읽지 못해 뉴스 글 링크를 뺀다 원인={}", e.getClass().getSimpleName());
            return null;
        }
    }
}
