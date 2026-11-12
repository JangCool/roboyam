package com.community.holiday.repository.master;

import com.community.holiday.entity.HolidaySource;
import com.community.holiday.entity.QSiteHolidayEntity;
import com.community.holiday.entity.QSiteHolidaySyncEntity;
import com.community.holiday.entity.SiteHolidayEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

/**
 * 사이트 쉬는 날 표({@code site_holidays}, shared V150 — 출처 공공데이터·기본값·회사 휴무일, 같은 날 여러 줄) 와 받기 결과({@code site_holiday_sync}, 1행) 읽기·쓰기.
 * 읽기는 프로젝션(이름 바꾸기·지우기는 대량 문장). 트랜잭션은 부르는 서비스의 것을 따른다.
 *
 * <p>Native SQL 로 남긴 것: 받기 결과 1행 덮어쓰기({@code INSERT … ON CONFLICT (id) DO UPDATE} — 처음 받을 때 행이 없다).
 */
@Repository
public class SiteHolidayStore extends IrobitRepositoryQDSL {

    /**
     * 한 줄.
     *
     * @param createdByName 넣은 운영자 이름(공공데이터·기본값 · 옮긴 줄 · 지워진 운영자는 null)
     */
    public record Row(long id, LocalDate date, String name, HolidaySource source, boolean substitute, String createdByName, Instant createdAt) {
    }

    /** 받기 결과(1행) */
    public record SyncRow(Instant ranAt, String outcome, List<Integer> appliedYears, List<Integer> failedYears, int changed, String ranByName) {
    }

    private static final QSiteHolidayEntity H = QSiteHolidayEntity.siteHolidayEntity;
    private static final QSiteHolidaySyncEntity SYNC = QSiteHolidaySyncEntity.siteHolidaySyncEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    private static final ConstructorExpression<Row> ROW = Projections.constructor(Row.class, H.id, H.holidayDate, H.name, H.source, H.substitute, OPERATOR.operatorName,
            H.createdAt);
    /** 날짜 → 공휴일 먼저(회사 휴무일 뒤) → ID */
    private static final OrderSpecifier<?>[] ORDER = {H.holidayDate.asc(), new CaseBuilder().when(H.source.eq(HolidaySource.OPERATOR)).then(1).otherwise(0).asc(),
            H.id.asc()};

    private final SiteHolidayRepository holidays;
    private final EntityManager entityManager;

    public SiteHolidayStore(JPAQueryFactory query, SiteHolidayRepository holidays, EntityManager entityManager) {
        super(query);
        this.holidays = holidays;
        this.entityManager = entityManager;
    }

    /** 모든 쉬는 날(날짜 차례, 겹침 없이) */
    public List<LocalDate> allDates() {
        return query.select(H.holidayDate).distinct().from(H).orderBy(H.holidayDate.asc()).fetch();
    }

    /** {@code from}~{@code to}(양 끝 포함)의 줄 — 날짜 차례 */
    public List<Row> between(LocalDate from, LocalDate to) {
        return rows().where(H.holidayDate.between(from, to)).orderBy(ORDER).fetch();
    }

    /** 그 해의 한 출처 줄 */
    public List<Row> ofYear(int year, HolidaySource source) {
        return rows().where(H.source.eq(source), H.holidayDate.between(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))).orderBy(ORDER).fetch();
    }

    public Optional<Row> find(long id) {
        return Optional.ofNullable(rows().where(H.id.eq(id)).fetchOne());
    }

    /** 그날 회사 휴무일이 있는가 */
    public boolean operatorDayExists(LocalDate date) {
        return query.selectOne().from(H).where(H.source.eq(HolidaySource.OPERATOR), H.holidayDate.eq(date)).fetchFirst() != null;
    }

    /** 새 줄 — 만든 ID(DB 가 매긴다 — 넣는 문장이 바로 나간다) */
    public long insert(LocalDate date, String name, HolidaySource source, boolean substitute, Long createdBy, Instant now) {
        SiteHolidayEntity row = SiteHolidayEntity.create(date, name, source, substitute, createdBy, now);
        holidays.persist(row);
        entityManager.detach(row); // 값은 프로젝션으로 읽는다(이름 바꾸기는 대량 UPDATE)
        return row.getId();
    }

    /** 이름·대체공휴일 여부 바꾸기 */
    public void rename(long id, String name, boolean substitute, Instant now) {
        query.update(H).set(H.name, name).set(H.substitute, substitute).set(H.updatedAt, now).where(H.id.eq(id)).execute();
    }

    public boolean delete(long id) {
        return query.delete(H).where(H.id.eq(id)).execute() == 1;
    }

    /** 그 해의 기본값(SEED) 줄 지우기 — 지운 수 */
    public int deleteSeedOfYear(int year) {
        return (int) query.delete(H).where(H.source.eq(HolidaySource.SEED), H.holidayDate.between(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))).execute();
    }

    /**
     * 받기 결과 남기기(1행을 덮어쓴다).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT (id) DO UPDATE} — 처음 받을 때는 행이 없고, 두 서버가 동시에 받아도 한 줄로 끝나야 한다.
     */
    public void recordSync(Instant ranAt, String outcome, List<Integer> appliedYears, List<Integer> failedYears, int changed, Long ranBy) {
        entityManager.createNativeQuery("INSERT INTO site_holiday_sync (id, ran_at, outcome, applied_years, failed_years, changed, ran_by) "
                        + "VALUES (1, ?1, ?2, cast(?3 AS integer[]), cast(?4 AS integer[]), ?5, ?6) "
                        + "ON CONFLICT (id) DO UPDATE SET ran_at = EXCLUDED.ran_at, outcome = EXCLUDED.outcome, applied_years = EXCLUDED.applied_years, "
                        + "failed_years = EXCLUDED.failed_years, changed = EXCLUDED.changed, ran_by = EXCLUDED.ran_by")
                .unwrap(NativeQuery.class)
                .setParameter(1, ranAt).setParameter(2, outcome).setParameter(3, array(appliedYears)).setParameter(4, array(failedYears)).setParameter(5, changed)
                .setParameter(6, ranBy, Long.class).executeUpdate();
    }

    public Optional<SyncRow> lastSync() {
        Tuple t = query.select(SYNC.ranAt, SYNC.outcome, SYNC.appliedYears, SYNC.failedYears, SYNC.changed, OPERATOR.operatorName).from(SYNC)
                .leftJoin(OPERATOR).on(OPERATOR.id.eq(SYNC.ranBy)).where(SYNC.id.eq(1)).fetchOne();
        if (t == null) {
            return Optional.empty();
        }
        return Optional.of(new SyncRow(t.get(SYNC.ranAt), t.get(SYNC.outcome), ints(t.get(SYNC.appliedYears)), ints(t.get(SYNC.failedYears)), t.get(SYNC.changed),
                t.get(OPERATOR.operatorName)));
    }

    private JPAQuery<Row> rows() {
        return query.select(ROW).from(H).leftJoin(OPERATOR).on(OPERATOR.id.eq(H.createdBy));
    }

    private static List<Integer> ints(Integer[] values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(values));
    }

    /** PostgreSQL 배열 글자({@code {2025,2026}}) */
    private static String array(List<Integer> values) {
        return values.toString().replace('[', '{').replace(']', '}').replace(" ", "");
    }
}
