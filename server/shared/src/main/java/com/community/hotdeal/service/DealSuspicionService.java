package com.community.hotdeal.service;

import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.repository.master.DealSuspicionQueries;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위장 홍보 의심 후보 계산(확정 가정 「위장 홍보 후보」, 서버 단계 고정 규칙 9·10) — 배치 {@code hotdeal-suspicion} 이 부른다. <b>자동 제재 없음</b> — 후보 표({@code deal_suspicions})에
 * 열린 후보 하나씩 두고 새로 열리면 관리자 알림 {@code HOTDEAL_SUSPECTED_PROMO}.
 *
 * <ul>
 *   <li>판매처 = 최종 호스트(없으면 호스트). <b>대형몰</b>(링크 규칙 {@code common} — 접미사 일치)은 빼고, <b>사업자 글</b>({@code source = BUSINESS})은 셈에서 뺀다. 지운 글도 센다
 *       (지우고 다시 올려 피하지 못하게 — 하루 글 한도와 같다). IP·기기 정보는 쓰지 않는다(법무 확인 — 1차는 안 씀).</li>
 *   <li><b>회원 후보</b>: 같은 회원이 {@code suspicionWindowDays} 안에 같은 판매처에 {@code suspicionMinPosts} 건 이상(0 = 끔).</li>
 *   <li><b>판매처 후보</b>: 같은 판매처를 {@code storeSuspicionWindowDays} 안에 서로 다른 회원 {@code storeSuspicionMembers} 명 이상(0 = 끔)이 올리고, 그 회원들이 같은 기간에 올린 핫딜 중
 *       <b>다른 판매처</b> 글의 비율이 {@code storeSuspicionOtherRatioPercent}% <b>미만</b>.</li>
 *   <li><b>열린 후보</b>: 대상(종류·판매처·회원)마다 하나 — 이미 열려 있으면 수만 고친다(다시 알리지 않는다). 운영자가 무시(DISMISSED)한 대상은 무시한 뒤 새 글이 생겨야 다시 연다.
 *       조건에서 벗어난 열린 후보를 저절로 닫지는 않는다(운영자가 무시).</li>
 * </ul>
 * 집계·후보 표 문장(native — 집계 함수·{@code bigint[]}·엔티티 없는 표)은 저장소 {@link DealSuspicionQueries} 다.
 */
@Service
public class DealSuspicionService {

    public static final String MEMBER = "MEMBER";
    public static final String STORE = "STORE";

    /** 후보 줄의 글 ID 최대 수 */
    static final int POST_IDS_MAX = 200;

    private final DealSuspicionQueries queries;
    private final DealSettingsReader settings;
    private final SnowflakeIdGenerator ids;
    private final DealNotices notices;

    public DealSuspicionService(DealSuspicionQueries queries, DealSettingsReader settings, SnowflakeIdGenerator ids, DealNotices notices) {
        this.queries = queries;
        this.settings = settings;
        this.ids = ids;
        this.notices = notices;
    }

    /** 계산한 후보 하나 */
    public record Candidate(String kind, Long memberId, String host, String storeName, int postCount, int memberCount, Integer otherRatioPercent, int windowDays,
                            List<Long> postIds, Instant lastPostAt) {
    }

    /** 한 번 계산한 결과 — 본 후보 수 · 새로 연 후보 수 */
    public record Tally(int seen, int opened) {
    }

    /** 회원 후보를 계산해 맞춘다 */
    @Transactional
    public Tally detectMembers(Instant now) {
        return apply(memberCandidates(now), now);
    }

    /** 판매처 후보를 계산해 맞춘다 */
    @Transactional
    public Tally detectStores(Instant now) {
        return apply(storeCandidates(now), now);
    }

    /** 회원 후보(끔이면 빈 목록) */
    public List<Candidate> memberCandidates(Instant now) {
        DealSettings s = settings.current();
        if (s.suspicionMinPosts() <= 0) {
            return List.of();
        }
        int days = s.suspicionWindowDays();
        return queries.memberCandidates(now.minus(Duration.ofDays(days)), s.suspicionMinPosts()).stream().map(r -> candidate(MEMBER, r, days)).toList();
    }

    /** 판매처 후보(끔이면 빈 목록) */
    public List<Candidate> storeCandidates(Instant now) {
        DealSettings s = settings.current();
        if (s.storeSuspicionMembers() <= 0) {
            return List.of();
        }
        int days = s.storeSuspicionWindowDays();
        return queries.storeCandidates(now.minus(Duration.ofDays(days)), s.storeSuspicionMembers(), s.storeSuspicionOtherRatioPercent()).stream()
                .map(r -> candidate(STORE, r, days)).toList();
    }

    private static Candidate candidate(String kind, DealSuspicionQueries.CandidateRow r, int days) {
        return new Candidate(kind, r.memberId(), r.host(), r.storeName(), r.postCount(), r.memberCount(), r.otherRatioPercent(), days, r.postIds(), r.lastPostAt());
    }

    private Tally apply(List<Candidate> candidates, Instant now) {
        int opened = 0;
        for (Candidate c : candidates) {
            opened += upsert(c, now) ? 1 : 0;
        }
        return new Tally(candidates.size(), opened);
    }

    /** 열린 후보가 있으면 수만 고치고, 없으면(무시한 뒤 새 글이 없으면 건너뛴다) 연다 — 새로 열었으면 true(관리자 알림) */
    private boolean upsert(Candidate c, Instant now) {
        long memberKey = c.memberId() == null ? 0L : c.memberId();
        List<Long> postIds = c.postIds().stream().limit(POST_IDS_MAX).toList();
        if (queries.refreshOpen(c.kind(), c.host(), memberKey, c.postCount(), c.memberCount(), c.otherRatioPercent(), c.windowDays(), postIds, cut(c.storeName()), now)) {
            return false;
        }
        Instant dismissed = queries.lastDismissedAt(c.kind(), c.host(), memberKey);
        if (dismissed != null && !c.lastPostAt().isAfter(dismissed)) {
            return false; // 무시한 뒤 새 글이 없다
        }
        long id = ids.nextId();
        if (!queries.insertOpen(id, c.kind(), c.memberId(), c.host(), cut(c.storeName()), c.postCount(), c.memberCount(), c.otherRatioPercent(), c.windowDays(), postIds,
                now)) {
            return false; // 같은 대상을 다른 실행이 막 열었다
        }
        notices.suspected(id);
        return true;
    }

    private static String cut(String name) {
        if (name == null) {
            return null;
        }
        return name.codePointCount(0, name.length()) <= 40 ? name : name.substring(0, name.offsetByCodePoints(0, 40));
    }
}
