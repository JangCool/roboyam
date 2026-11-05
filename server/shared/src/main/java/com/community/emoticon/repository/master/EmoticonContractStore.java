package com.community.emoticon.repository.master;

import com.community.site.entity.SiteSettingEntity;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 상품화 계약 — 문서는 약관 게시본({@code terms_versions} 의 종류 {@code ROBOTIKON_CONTRACT}, 약관 관리가 쓴다), 동의 기록은 {@code emoticon_contract_consents}(V143 — 추가만).
 * 「지금 판」 = 시행일이 사이트 표시 시간대의 오늘 이전(오늘 포함)인 가장 큰 버전 — {@code TermsTimeline} 과 같은 규칙이다(JDBC 인 이유는 {@link EmoticonPackStore} 와 같다).
 */
@Repository
public class EmoticonContractStore {

    /** 약관 게시본 한 판 — 본문은 정화된 HTML */
    public record ContractVersion(int version, LocalDate effectiveDate, String bodyHtml) {
    }

    /** 동의 한 줄 */
    public record Consent(int version, Instant agreedAt) {
    }

    private final JdbcTemplate jdbc;

    public EmoticonContractStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 사이트 표시 시간대({@code site_settings.display_zone_id}) — 행이 없거나 읽을 수 없으면 처음 값 */
    public ZoneId siteZone() {
        List<String> zones = jdbc.queryForList("SELECT display_zone_id FROM site_settings WHERE id = ?", String.class, SiteSettingEntity.ROW_ID);
        String zone = zones.isEmpty() || zones.get(0) == null ? SiteSettingEntity.DEFAULT_ZONE_ID : zones.get(0);
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            return ZoneId.of(SiteSettingEntity.DEFAULT_ZONE_ID);
        }
    }

    /** 그날의 지금 판 — 게시본이 없거나 모두 시행 전이면 비어 있다 */
    public Optional<ContractVersion> current(LocalDate today) {
        return jdbc.query("SELECT version, effective_date, body FROM terms_versions WHERE kind = 'ROBOTIKON_CONTRACT' AND effective_date <= ? ORDER BY version DESC LIMIT 1",
                (rs, i) -> new ContractVersion(rs.getInt(1), rs.getObject(2, LocalDate.class), rs.getString(3)), today).stream().findFirst();
    }

    /** 그 작가의 동의 기록 — 오래된 것 먼저 */
    public List<Consent> consents(long userId) {
        return jdbc.query("SELECT version, agreed_at FROM emoticon_contract_consents WHERE user_id = ? ORDER BY agreed_at, version", (rs, i) -> new Consent(rs.getInt(1),
                rs.getObject(2, OffsetDateTime.class).toInstant()), userId);
    }

    /** 작가마다 가장 최근 동의 */
    public Map<Long, Consent> latest(Collection<Long> userIds) {
        Map<Long, Consent> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        String marks = String.join(", ", java.util.Collections.nCopies(userIds.size(), "?"));
        jdbc.query("SELECT DISTINCT ON (user_id) user_id, version, agreed_at FROM emoticon_contract_consents WHERE user_id IN (" + marks + ") "
                + "ORDER BY user_id, agreed_at DESC, version DESC", rs -> {
                    out.put(rs.getLong(1), new Consent(rs.getInt(2), rs.getObject(3, OffsetDateTime.class).toInstant()));
                }, userIds.toArray());
        return out;
    }

    /** 그 판에 동의했나 */
    public boolean agreed(long userId, int version) {
        return !jdbc.queryForList("SELECT 1 FROM emoticon_contract_consents WHERE user_id = ? AND version = ?", Integer.class, userId, version).isEmpty();
    }

    /** 작가들 중 그 판에 동의한 사람 */
    public java.util.Set<Long> agreedUsers(int version, Collection<Long> userIds) {
        java.util.Set<Long> out = new java.util.HashSet<>();
        if (userIds.isEmpty()) {
            return out;
        }
        String marks = String.join(", ", java.util.Collections.nCopies(userIds.size(), "?"));
        Object[] args = new Object[userIds.size() + 1];
        args[0] = version;
        int i = 1;
        for (Long id : userIds) {
            args[i++] = id;
        }
        out.addAll(jdbc.queryForList("SELECT user_id FROM emoticon_contract_consents WHERE version = ? AND user_id IN (" + marks + ")", Long.class, args));
        return out;
    }

    /** 동의 한 줄을 더한다 — 같은 판에 이미 동의했으면 넣지 않는다(추가만, 처음 동의 시각이 남는다). 넣었으면 참 */
    public boolean insert(long id, long userId, int version, Instant agreedAt, Long packId) {
        return jdbc.update("INSERT INTO emoticon_contract_consents (id, user_id, version, agreed_at, pack_id) VALUES (?, ?, ?, ?, ?) ON CONFLICT (user_id, version) DO NOTHING",
                id, userId, version, OffsetDateTime.ofInstant(agreedAt, ZoneOffset.UTC), packId) == 1;
    }

    /** 그 판에 동의한 서로 다른 작가 수 */
    public int agreedCount(int version) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM emoticon_contract_consents WHERE version = ?", Integer.class, version);
        return n == null ? 0 : n;
    }
}
