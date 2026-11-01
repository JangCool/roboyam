package com.community.hotdeal.service;

import com.community.hotdeal.service.DealLinkJudge.ChainVerdict;
import com.community.hotdeal.service.DealLinkJudge.DomainRule;
import com.community.hotdeal.service.SiteAffiliateLinker.SiteStore;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 링크 규칙({@code deal_domains})·사이트 제휴 몰({@code deal_site_affiliates})의 <b>짧은 메모리 사본</b> — 판정({@link DealLinkJudge})·변환({@link SiteAffiliateLinker})이
 * 글 줄마다 표를 읽지 않게 한다. {@value #TTL_SECONDS}초마다 다시 읽고(관리자 저장이 공개 서버에 닿기까지 최대 그만큼), 실패하면 이전 사본을 쓴다.
 * 같은 서버 안에서 바꾸면 {@link #invalidate()}.
 */
@Component
public class DealRuleBook {

    private static final Logger log = LoggerFactory.getLogger(DealRuleBook.class);

    static final int TTL_SECONDS = 15;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final JdbcTemplate jdbc;
    private volatile Snapshot cached;

    /** 사본 */
    public record Snapshot(List<DomainRule> domains, List<SiteStore> siteStores, long loadedAt) {
    }

    public DealRuleBook(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Snapshot snapshot() {
        Snapshot current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current;
        }
        synchronized (this) {
            current = cached;
            long now = System.nanoTime();
            if (current != null && now - current.loadedAt < TTL_NANOS) {
                return current;
            }
            try {
                Snapshot loaded = new Snapshot(loadDomains(), loadStores(), now);
                cached = loaded;
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("핫딜 링크 규칙을 다시 읽지 못해 이전 값을 쓴다 원인={}", e.getClass().getSimpleName());
                cached = new Snapshot(current.domains, current.siteStores, now - TTL_NANOS + RETRY_NANOS);
                return cached;
            }
        }
    }

    public void invalidate() {
        cached = null;
    }

    /** 지금 규칙으로 체인 판정 */
    public ChainVerdict judge(List<String> hosts) {
        return DealLinkJudge.judgeChain(hosts, snapshot().domains());
    }

    /** 지금 켠 사이트 제휴 몰 가운데 이 링크가 가는 몰 */
    public Optional<SiteStore> siteStore(String productHost, String finalHost) {
        return SiteAffiliateLinker.match(snapshot().siteStores(), productHost, finalHost);
    }

    private List<DomainRule> loadDomains() {
        return jdbc.query("SELECT domain, rule, common, shortener, key_params, store_name FROM deal_domains",
                (rs, i) -> new DomainRule(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), strings(rs.getArray(5)), rs.getString(6)));
    }

    private List<SiteStore> loadStores() {
        return jdbc.query("SELECT id, domain, program_name, transform_type, template, query, enabled FROM deal_site_affiliates",
                (rs, i) -> new SiteStore(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7)));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String::valueOf).toList();
    }
}
