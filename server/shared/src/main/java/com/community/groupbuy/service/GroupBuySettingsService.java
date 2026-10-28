package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.sql.Array;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구 설정(「설정 › 커머스 › 공구 설정」, 계약 결정 8·10·51·53 — {@code group_buy_settings} 1행). 값의 범위·처음 값은 여기가 정본이고 화면은 응답의 {@code ranges}·{@code defaults} 로 그린다.
 * 바뀐 칸만 전→후 감사({@code GROUPBUY_SETTINGS_UPDATE}, 돈 관련 — 문턱·보상 상한·선점 시간이 돈과 시각에 닿는다). 읽는 쪽은 그 트랜잭션 안에서 DB 의 지금 값을 읽는다(결정 19).
 */
@Service
public class GroupBuySettingsService {

    /** 숫자 칸 하나 — 화면 이름 · 열 · 범위 · 처음 값 */
    public record Field(String name, String column, long min, long max, long defaultValue) {
    }

    /** 화면 차례 그대로(화면 {@code GroupBuySettingsValues}) */
    public static final List<Field> FIELDS = List.of(
            new Field("holdMinutes", "hold_minutes", 3, 30, 5),
            new Field("thresholdPeriodMaxDays", "demand_max_days", 1, 90, 14),
            new Field("firstComePeriodMaxDays", "first_come_max_days", 1, 180, 30),
            new Field("extensionMaxMinutes", "extension_minutes", 0, 120, 30),
            new Field("extensionWindowMinutes", "extension_window_minutes", 1, 30, 5),
            new Field("judgeGraceMinutes", "judge_grace_minutes", 0, 30, 2),
            new Field("compensationCap", "compensation_cap", 0, 1_000_000, 10_000),
            new Field("largeRefundThreshold", "large_refund_threshold", 0, 100_000_000, 100_000),
            new Field("addressRevealMinutes", "address_reveal_minutes", 1, 60, 10),
            new Field("expiredAddressRetentionDays", "expired_address_retention_days", 1, 365, 30),
            new Field("shipWithinBusinessDays", "ship_within_business_days", 1, 30, 3),
            new Field("supplyChoiceBusinessDays", "supply_choice_business_days", 1, 30, 3));

    /** 휴일 최대 수 */
    public static final int HOLIDAYS_MAX = 200;

    /** 저장된 값 한 벌 */
    public record Values(Map<String, Long> numbers, List<LocalDate> holidays, Instant updatedAt, String updatedBy) {
    }

    private final JdbcTemplate jdbc;
    private final AuditLogService audit;
    private final ObjectProvider<Clock> clocks;

    public GroupBuySettingsService(DataSource dataSource, AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.audit = audit;
        this.clocks = clocks;
    }

    @Transactional(readOnly = true)
    public Values load() {
        StringBuilder sql = new StringBuilder("SELECT ");
        FIELDS.forEach(f -> sql.append("s.").append(f.column()).append(", "));
        sql.append("s.holidays, s.updated_at, o.operator_name FROM group_buy_settings s LEFT JOIN operators o ON o.id = s.updated_by WHERE s.id = 1");
        return jdbc.queryForObject(sql.toString(), (rs, i) -> {
            Map<String, Long> numbers = new LinkedHashMap<>();
            for (int k = 0; k < FIELDS.size(); k++) {
                numbers.put(FIELDS.get(k).name(), rs.getLong(k + 1));
            }
            List<LocalDate> holidays = new ArrayList<>();
            Array array = rs.getArray(FIELDS.size() + 1);
            if (array != null) {
                for (Object d : (Object[]) array.getArray()) {
                    holidays.add(d instanceof Date date ? date.toLocalDate() : LocalDate.parse(d.toString()));
                }
            }
            Timestamp at = rs.getTimestamp(FIELDS.size() + 2);
            return new Values(numbers, holidays, at == null ? null : at.toInstant(), rs.getString(FIELDS.size() + 3));
        });
    }

    /**
     * 통째로 저장 — 빠진 칸·범위 밖은 400 {@code fields.values.<이름>}, 휴일은 겹침을 지우고 차례대로(최대 {@value #HOLIDAYS_MAX}).
     */
    @Transactional
    public Values save(Map<String, Long> numbers, List<LocalDate> holidays, long operatorId, String ip) {
        Map<String, Long> in = numbers == null ? Map.of() : numbers;
        for (Field f : FIELDS) {
            Long v = in.get(f.name());
            if (v == null || v < f.min() || v > f.max()) {
                throw ValidationException.of("values." + f.name(), new MessageRef("groupbuy.settings.range", f.min(), f.max()));
            }
        }
        TreeSet<LocalDate> days = new TreeSet<>();
        if (holidays != null) {
            holidays.stream().filter(Objects::nonNull).forEach(days::add);
        }
        if (days.size() > HOLIDAYS_MAX) {
            throw ValidationException.of("values.holidays", new MessageRef("groupbuy.settings.holidays.tooMany", HOLIDAYS_MAX));
        }
        jdbc.queryForObject("SELECT id FROM group_buy_settings WHERE id = 1 FOR UPDATE", Integer.class);
        Values before = load();
        StringBuilder sql = new StringBuilder("UPDATE group_buy_settings SET ");
        List<Object> args = new ArrayList<>();
        for (Field f : FIELDS) {
            sql.append(f.column()).append(" = ?, ");
            args.add(in.get(f.name()));
        }
        sql.append("holidays = ?::date[], updated_by = ?, updated_at = ? WHERE id = 1");
        args.add(days.stream().map(LocalDate::toString).toList().toString().replace('[', '{').replace(']', '}').replace(" ", ""));
        args.add(operatorId);
        args.add(Timestamp.from(clocks.getIfUnique(Clock::systemUTC).instant().truncatedTo(ChronoUnit.MICROS)));
        jdbc.update(sql.toString(), args.toArray());

        Map<String, Object> beforeChanged = new LinkedHashMap<>();
        Map<String, Object> afterChanged = new LinkedHashMap<>();
        for (Field f : FIELDS) {
            if (!Objects.equals(before.numbers().get(f.name()), in.get(f.name()))) {
                beforeChanged.put(f.name(), before.numbers().get(f.name()));
                afterChanged.put(f.name(), in.get(f.name()));
            }
        }
        List<LocalDate> newDays = List.copyOf(days);
        if (!before.holidays().equals(newDays)) {
            beforeChanged.put("holidays", before.holidays().size());
            afterChanged.put("holidays", newDays.size());
        }
        if (!afterChanged.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", beforeChanged);
            detail.put("after", afterChanged);
            audit.record(new AuditEvent(AuditActorType.OPERATOR, operatorId, AuditActions.GROUPBUY_SETTINGS_UPDATE, AuditResult.SUCCESS, "COMMERCE_SETTINGS",
                    "group-buy-settings", ip, detail));
        }
        return load();
    }
}
