package com.community.installation;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.installation.dto.TablePack;
import com.community.installation.service.TablePackRestorer;
import com.community.rewardevent.repository.master.RewardEventStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 팩(데모 팩·구조 스냅숏)을 넣은 뒤 화면 번호 시퀀스가 데이터와 맞는지 — 코드가 {@code nextval} 로 부르는 시퀀스(사업자 신청 번호 {@code BA-<연도>-<일련번호>}·보상 이벤트 번호)도
 * 칸에 묶여(V221 {@code OWNED BY}) 설치기가 같이 맞춰야 첫 새 건이 이미 있는 번호를 만들지 않는다(2026-11-15 데모 설치 DB 의 광고 수동 등록 503).
 */
class TablePackSequenceTest extends CommerceApiTestBase {

    /** 칸에 묶이지 않아도 되는 시퀀스 — 팩이 넣는 데이터와 상관없다 */
    private static final List<String> UNOWNED_OK = List.of(
            "batch_run_db_id_seq",        // DB 배치 로그 줄 ID — 같은 칸에 Java 쪽 Snowflake ID 가 섞여 최댓값으로 맞추면 안 된다(V20). 팩에 batch_runs 가 없다
            "point_lot_migration_id_seq"  // V85 전환 때 한 번만 쓴 순번
    );

    @Autowired
    private TablePackRestorer restorer;
    @Autowired
    private RewardEventStore rewardEvents;

    private Long rewardEventId;
    private Long applicationId;

    @AfterEach
    void cleanUp() {
        if (rewardEventId != null) {
            jdbc.update("DELETE FROM reward_events WHERE id = ?", rewardEventId);
        }
        if (applicationId != null) {
            jdbc.update("DELETE FROM biz_applications WHERE id = ?", applicationId);
        }
    }

    /** 시퀀스가 다음에 줄 값(부르지 않고 읽는다) */
    private long peek(String sequence) {
        return jdbc.queryForObject("SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END FROM " + sequence, Long.class);
    }

    private static TablePack.Table table(String name, Map<String, Object> row) {
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        row.keySet().forEach(c -> columns.put(c, "text")); // 형식 글자는 데모 팩의 시각·ID 옮기기용 — 여기서는 쓰지 않는다
        return new TablePack.Table(name, TablePack.Mode.UPSERT.name(), List.of("id"), columns, List.of(row), List.of());
    }

    @Test
    void 팩이_넣은_화면_번호_다음부터_매긴다() {
        // 시퀀스가 아직 닿지 않은 번호를 팩이 넣는다 — 데모 팩으로 설치한 새 DB 와 같은 상황(시퀀스는 처음 값, 데이터는 BA-2026-00016·이벤트 2번까지)
        long eventNo = peek("reward_event_no_seq") + 50;
        long applicationSerial = peek("biz_application_no_seq") + 50;
        Instant start = Instant.now().truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(7, ChronoUnit.DAYS);
        rewardEventId = ids.nextId();
        applicationId = ids.nextId();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", Long.toString(rewardEventId));
        event.put("no", Long.toString(eventNo));
        event.put("name", "데모 보상 이벤트");
        event.put("mode", "BULK");
        event.put("bulk_scope", "ALL");
        event.put("start_at", start.toString());
        event.put("end_at", end.toString());
        event.put("created_at", Instant.now().toString());
        event.put("updated_at", Instant.now().toString());

        Map<String, Object> application = new LinkedHashMap<>();
        application.put("id", Long.toString(applicationId));
        application.put("application_no", String.format("BA-2026-%05d", applicationSerial));
        application.put("kind", "AD_BANNER");
        application.put("status", "SUBMITTED");
        application.put("source", "OPERATOR");
        application.put("advertiser_name", "데모 광고주");
        application.put("title", "데모 배너");
        application.put("start_at", start.toString());
        application.put("end_at", end.toString());
        application.put("preferred_method", "MANUAL");
        application.put("quote_supply", "0");
        application.put("quote_vat", "0");
        application.put("quote_total", "0");
        application.put("stop_fee_rate", "0");
        application.put("created_by", Long.toString(newOperator())); // 운영자 등록 건은 등록한 운영자가 있다(ck_ba_operator_created)
        application.put("created_at", Instant.now().toString());
        application.put("updated_at", Instant.now().toString());

        TablePack pack = new TablePack(1, "irobit", Instant.now().toString(), Map.of(),
                List.of(table("reward_events", event), table("biz_applications", application)));
        tx.executeWithoutResult(status -> restorer.restore(pack, true, true, (t, row, columns) -> row));

        // 보상 이벤트 만들기가 부르는 번호 · 광고 수동 등록(BizApplicationStore#nextNo)이 부르는 시퀀스
        long nextEventNo = tx.execute(status -> rewardEvents.nextNo());
        assertThat(nextEventNo).isEqualTo(eventNo + 1);
        assertThat(jdbc.queryForObject("SELECT nextval('biz_application_no_seq')", Long.class)).isEqualTo(applicationSerial + 1);
    }

    @Test
    void 코드가_부르는_시퀀스는_모두_칸에_묶여_있다() {
        // 칸에 묶이지 않은 시퀀스는 설치기가 맞추지 못한다 — 새 번호 시퀀스를 만들면 마이그레이션에서 번호 칸에 OWNED BY 로 묶는다
        List<String> unowned = jdbc.queryForList("""
                SELECT c.relname FROM pg_class c
                WHERE c.relkind = 'S' AND c.relnamespace = 'public'::regnamespace
                  AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid
                                  AND d.refobjsubid > 0 AND d.deptype IN ('a', 'i'))
                ORDER BY c.relname""", String.class);
        assertThat(unowned).containsExactlyInAnyOrderElementsOf(UNOWNED_OK);
    }
}
