package com.community.emoticon.repository.master;

import com.community.emoticon.entity.QEmoticonContractConsentEntity;
import com.community.site.entity.QSiteSettingEntity;
import com.community.site.entity.SiteSettingEntity;
import com.community.terms.entity.QTermsVersionEntity;
import com.community.terms.entity.TermsKind;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 상품화 계약 — 문서는 약관 게시본({@code terms_versions} 의 종류 {@code ROBOTIKON_CONTRACT}, 약관 관리가 쓴다), 동의 기록은 {@code emoticon_contract_consents}(V143 — 추가만).
 * 「지금 판」 = 시행일이 사이트 표시 시간대의 오늘 이전(오늘 포함)인 가장 큰 버전 — {@code TermsTimeline} 과 같은 규칙이다.
 *
 * <p>QueryDSL 이다({@link EmoticonStoreSupport}). Native SQL 로 남긴 것과 이유: 동의 넣기({@code ON CONFLICT (user_id, version) DO NOTHING} — 같은 판에 이미 동의했으면 처음 시각을 남기고
 * 넣었는지만 돌려준다).
 */
@Repository
public class EmoticonContractStore extends EmoticonStoreSupport {

    /** 약관 게시본 한 판 — 본문은 정화된 HTML */
    public record ContractVersion(int version, LocalDate effectiveDate, String bodyHtml) {
    }

    /** 동의 한 줄 */
    public record Consent(int version, Instant agreedAt) {
    }

    private static final QEmoticonContractConsentEntity C = QEmoticonContractConsentEntity.emoticonContractConsentEntity;
    private static final QEmoticonContractConsentEntity C2 = new QEmoticonContractConsentEntity("c2");
    private static final QTermsVersionEntity T = QTermsVersionEntity.termsVersionEntity;
    private static final QSiteSettingEntity S = QSiteSettingEntity.siteSettingEntity;

    public EmoticonContractStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** 사이트 표시 시간대({@code site_settings.display_zone_id}) — 행이 없거나 읽을 수 없으면 처음 값 */
    public ZoneId siteZone() {
        String zone = query.select(S.displayZoneId).from(S).where(S.id.eq(SiteSettingEntity.ROW_ID)).fetchFirst();
        if (zone == null) {
            zone = SiteSettingEntity.DEFAULT_ZONE_ID;
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            return ZoneId.of(SiteSettingEntity.DEFAULT_ZONE_ID);
        }
    }

    /** 그날의 지금 판 — 게시본이 없거나 모두 시행 전이면 비어 있다 */
    public Optional<ContractVersion> current(LocalDate today) {
        Tuple t = query.select(T.version, T.effectiveDate, T.body).from(T).where(T.kind.eq(TermsKind.ROBOTIKON_CONTRACT), T.effectiveDate.loe(today))
                .orderBy(T.version.desc()).fetchFirst();
        return Optional.ofNullable(t).map(r -> new ContractVersion(r.get(T.version), r.get(T.effectiveDate), r.get(T.body)));
    }

    /** 그 작가의 동의 기록 — 오래된 것 먼저 */
    public List<Consent> consents(long userId) {
        return query.select(C.version, C.agreedAt).from(C).where(C.userId.eq(userId)).orderBy(C.agreedAt.asc(), C.version.asc()).fetch().stream()
                .map(t -> new Consent(t.get(C.version), t.get(C.agreedAt))).collect(Collectors.toList());
    }

    /**
     * 작가마다 가장 최근 동의 — (동의 시각, 판) 이 가장 큰 줄: 더 늦은 줄(시각이 늦거나, 같은 시각의 더 큰 판)이 없는 줄(예전 {@code DISTINCT ON … ORDER BY agreed_at DESC, version DESC}
     * 와 같은 줄 — 회원 × 판 유일).
     */
    public Map<Long, Consent> latest(Collection<Long> userIds) {
        Map<Long, Consent> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(C.userId, C.version, C.agreedAt).from(C)
                .where(C.userId.in(userIds), JPAExpressions.selectOne().from(C2)
                        .where(C2.userId.eq(C.userId), C2.agreedAt.gt(C.agreedAt).or(C2.agreedAt.eq(C.agreedAt).and(C2.version.gt(C.version)))).notExists())
                .fetch()) {
            out.put(t.get(C.userId), new Consent(t.get(C.version), t.get(C.agreedAt)));
        }
        return out;
    }

    /** 그 판에 동의했나 */
    public boolean agreed(long userId, int version) {
        return query.selectOne().from(C).where(C.userId.eq(userId), C.version.eq(version)).fetchFirst() != null;
    }

    /** 작가들 중 그 판에 동의한 사람 */
    public Set<Long> agreedUsers(int version, Collection<Long> userIds) {
        Set<Long> out = new HashSet<>();
        if (userIds.isEmpty()) {
            return out;
        }
        out.addAll(query.select(C.userId).from(C).where(C.version.eq(version), C.userId.in(userIds)).fetch());
        return out;
    }

    /**
     * 동의 한 줄을 더한다 — 같은 판에 이미 동의했으면 넣지 않는다(추가만, 처음 동의 시각이 남는다). 넣었으면 참.
     * Native SQL 인 이유: {@code ON CONFLICT DO NOTHING} — 동시에 두 번 동의해도 유일 위반 없이 하나만 남고 넣었는지를 한 문장으로 안다.
     */
    public boolean insert(long id, long userId, int version, Instant agreedAt, Long packId) {
        return jdbc.update("INSERT INTO emoticon_contract_consents (id, user_id, version, agreed_at, pack_id) VALUES (?, ?, ?, ?, ?) ON CONFLICT (user_id, version) DO NOTHING",
                id, userId, version, OffsetDateTime.ofInstant(agreedAt, ZoneOffset.UTC), packId) == 1;
    }

    /** 그 판에 동의한 서로 다른 작가 수 */
    public int agreedCount(int version) {
        return (int) n(query.select(C.count()).from(C).where(C.version.eq(version)).fetchFirst());
    }
}
