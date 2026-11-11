package com.community.analytics.collect;

import com.community.analytics.repository.master.AnalyticsReferenceStore;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 방문 수집이 신호마다 보는 메인 DB 기준 목록의 <b>서버 메모리 사본</b>(서버마다 따로, 약 {@value #REFRESH_MILLIS}ms 마다 새로 읽는다 — site-analytics-contract 「3단계」):
 * 뺄 계정(운영자 회원 계정 · AI 회원의 회원 번호)과 있는 가젯 페이지 키. 원천은 {@link AnalyticsReferenceStore}.
 *
 * <ul>
 *   <li>주기 작업이 아니다 — 신호를 받을 때 사본이 낡았으면 그 요청이 한 번 다시 읽는다(한 번에 한 요청만, 나머지는 이전 사본을 쓴다). 처음 한 번은 모두 기다린다.</li>
 *   <li>읽기에 실패하면 이전 사본을 계속 쓰고 {@value #RETRY_MILLIS}ms 뒤에 다시 읽어 본다(처음부터 실패하면 빈 목록 — 뺄 계정 없음 · 가젯 페이지는 「알 수 없는 주소」).</li>
 *   <li>계정을 운영자에 잇거나 가젯 페이지를 만든 뒤 최대 1분 늦게 반영된다(통계라 허용).</li>
 * </ul>
 */
@Component
public class VisitReferenceCache {

    private static final Logger log = LoggerFactory.getLogger(VisitReferenceCache.class);

    static final long REFRESH_MILLIS = 60_000;
    static final long RETRY_MILLIS = 10_000;

    private record Snapshot(Set<Long> excludedMembers, Set<String> pageKeys, long nextAt) {
    }

    private final AnalyticsReferenceStore store;
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile Snapshot snapshot;

    public VisitReferenceCache(AnalyticsReferenceStore store) {
        this.store = store;
    }

    /** 통계에서 뺄 회원인가(운영자 회원 계정 · AI 회원). null(로그인하지 않음)은 아니다 */
    public boolean excludedMember(Long memberId) {
        return memberId != null && current().excludedMembers().contains(memberId);
    }

    /** 있는 가젯 페이지 키인가 */
    public boolean pageKeyExists(String key) {
        return key != null && current().pageKeys().contains(key);
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        long now = System.currentTimeMillis();
        if (s == null) {
            synchronized (this) {
                if (snapshot == null) {
                    snapshot = load(null, now);
                }
                return snapshot;
            }
        }
        if (now < s.nextAt() || !loading.compareAndSet(false, true)) {
            return s;
        }
        try {
            snapshot = load(s, now);
        } finally {
            loading.set(false);
        }
        return snapshot;
    }

    private Snapshot load(Snapshot previous, long now) {
        try {
            return new Snapshot(Set.copyOf(store.excludedMemberIds()), Set.copyOf(store.gadgetPageKeys()), now + REFRESH_MILLIS);
        } catch (RuntimeException e) {
            log.warn("방문 통계 기준 목록(뺄 계정·가젯 페이지)을 읽지 못해 이전 사본을 쓴다: {}", e.getClass().getSimpleName());
            return previous == null ? new Snapshot(Set.of(), Set.of(), now + RETRY_MILLIS)
                    : new Snapshot(previous.excludedMembers(), previous.pageKeys(), now + RETRY_MILLIS);
        }
    }
}
