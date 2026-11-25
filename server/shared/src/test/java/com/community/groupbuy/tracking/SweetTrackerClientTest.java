package com.community.groupbuy.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.tracking.ParcelTrackingClient.Outcome;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 스마트택배 응답 읽기(계약 결정 90) — 단계·완료·이력(시각 KST → 순간, 기사 이름·전화는 버림)·오류 코드(본문 {@code status:false} 와 HTTP 상태 둘 다). HTTP 없이 읽기만.
 */
class SweetTrackerClientTest {

    private static Map<String, Object> detail(String timeString, String kind, String where, int level) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("code", null);
        d.put("kind", kind);
        d.put("level", level);
        d.put("manName", "김기사");
        d.put("telno", "02-000-0000");
        d.put("telno2", "010-1234-5678");
        d.put("time", 1L);
        d.put("timeString", timeString);
        d.put("where", where);
        return d;
    }

    @Test
    void 배송_완료_응답은_단계_6_완료_시각은_완료_줄의_KST_시각() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("complete", true);
        body.put("completeYN", "Y");
        body.put("level", 6);
        body.put("result", "Y");
        body.put("invoiceNo", "123456789012");
        body.put("trackingDetails", List.of(detail("2026-11-24 18:10:00", "배달완료", "부산 해운대", 6), detail("2026-11-24 09:00:00", "배송출발", "부산 해운대", 5),
                detail("2026-11-23 20:00:00", "집화처리", "서울 강남", 2)));

        ParcelTrackingClient.Tracking t = SweetTrackerClient.parseTracking(200, body);

        assertThat(t.outcome()).isEqualTo(Outcome.OK);
        assertThat(t.level()).isEqualTo(6);
        assertThat(t.complete()).isTrue();
        assertThat(t.completedAt()).as("KST 18:10 = UTC 09:10").isEqualTo(Instant.parse("2026-11-24T09:10:00Z"));
        assertThat(t.events()).extracting(ParcelTrackingClient.Event::kind).as("시각 차례").containsExactly("집화처리", "배송출발", "배달완료");
        assertThat(t.events().toString()).doesNotContain("김기사").doesNotContain("010-1234-5678").doesNotContain("02-000-0000");
    }

    @Test
    void 중간_단계와_스캔_없음() {
        Map<String, Object> moving = new LinkedHashMap<>();
        moving.put("complete", false);
        moving.put("level", 3);
        moving.put("result", "Y");
        moving.put("trackingDetails", List.of(detail("2026-11-23 20:00:00", "간선상차", "대전 HUB", 3)));
        ParcelTrackingClient.Tracking t = SweetTrackerClient.parseTracking(200, moving);
        assertThat(t.outcome()).isEqualTo(Outcome.OK);
        assertThat(t.level()).isEqualTo(3);
        assertThat(t.completedAt()).isNull();

        Map<String, Object> blank = new LinkedHashMap<>();
        blank.put("complete", false);
        blank.put("level", 0);
        blank.put("result", "N");
        blank.put("trackingDetails", List.of());
        assertThat(SweetTrackerClient.parseTracking(200, blank).outcome()).isEqualTo(Outcome.NO_SCAN);
    }

    @Test
    void 오류는_본문_코드와_HTTP_상태_둘_다() {
        assertThat(SweetTrackerClient.parseTracking(200, Map.of("status", false, "code", "104", "msg", "운송장 번호 오류"))).satisfies(t -> {
            assertThat(t.outcome()).isEqualTo(Outcome.FAILED);
            assertThat(t.errorCode()).isEqualTo("104");
        });
        assertThat(SweetTrackerClient.parseTracking(401, Map.of("status", false, "code", "102", "msg", "만료"))).satisfies(t -> {
            assertThat(t.outcome()).isEqualTo(Outcome.KEY_ERROR);
            assertThat(t.errorCode()).isEqualTo("102");
        });
        assertThat(SweetTrackerClient.parseTracking(200, Map.of("status", "false", "code", 105)).outcome()).isEqualTo(Outcome.DAILY_LIMIT);
        assertThat(SweetTrackerClient.parseTracking(200, Map.of("status", false, "code", "101")).outcome()).isEqualTo(Outcome.KEY_ERROR);
        assertThat(SweetTrackerClient.parseTracking(200, Map.of("status", false, "code", "103")).outcome()).isEqualTo(Outcome.KEY_ERROR);
        assertThat(SweetTrackerClient.parseTracking(500, null)).satisfies(t -> {
            assertThat(t.outcome()).isEqualTo(Outcome.FAILED);
            assertThat(t.errorCode()).isEqualTo("HTTP_500");
        });
        assertThat(SweetTrackerClient.parseTracking(404, Map.of("status", false, "code", "404")).errorCode()).isEqualTo("404");
    }

    @Test
    void 이용권_남은_양() {
        ParcelTrackingClient.Usage u = SweetTrackerClient.parseUsage(200, Map.of("key", "k", "startDate", "2026-01-01", "endDate", "2026-12-31", "totalAmount", 10000,
                "leftAmount", 8123));
        assertThat(u.outcome()).isEqualTo(Outcome.OK);
        assertThat(u.leftAmount()).isEqualTo(8123L);
        assertThat(u.endDate()).isEqualTo("2026-12-31");
        assertThat(SweetTrackerClient.parseUsage(200, Map.of("status", false, "code", "102")).outcome()).isEqualTo(Outcome.KEY_ERROR);
    }

    @Test
    void 로그용_송장은_끝_4자리만() {
        assertThat(ParcelTrackingClient.masked("123456789012")).isEqualTo("******9012");
        assertThat(ParcelTrackingClient.masked("12")).isEqualTo("****");
    }
}
