package com.community.analytics.forget;

import com.community.analytics.dto.VisitForget;
import com.community.analytics.exception.VisitForgetBusyException;
import com.community.analytics.store.VisitForgetStore;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 방문 기록 삭제 요청(공개 {@code POST /api/visits/forget} — site-analytics-contract 「2단계 — 정보주체 요청」). 수집 스위치와 상관없이 늘 받는다.
 *
 * <ul>
 *   <li>방문자 ID 원본은 저장하지 않는다 — 방문 수집과 같은 {@link BlindIndex} 용도({@code analytics.visitor})의 해시만 요청 표에 둔다(원본·판정·지금 접속 표의 해시와 같은 값).</li>
 *   <li>지우기는 DB 크론(5분마다)이 한다 — 이 요청은 한 줄 넣기(또는 대기 중 요청 돌려주기)뿐이다. 처리한 뒤 크론이 해시를 비운다.</li>
 *   <li>해시 키가 없으면 503 {@code INDEX_KEY_MISSING}(방문 수집도 그때는 아무것도 모으지 않는다). UUID 가 아니면 400 {@code fields.visitorId}.</li>
 *   <li>대기 요청이 전체 상한({@link VisitForgetStore#MAX_PENDING})에 닿으면 503 {@code VISIT_FORGET_BUSY}(Retry-After 5분 — 크론 한 번).</li>
 * </ul>
 */
@Service
public class VisitForgetService {

    static final DataPurpose VISITOR = DataPurpose.of("analytics.visitor");
    static final long BUSY_RETRY_SECONDS = 300;

    private final VisitForgetStore store;
    private final BlindIndex index;
    private final SnowflakeIdGenerator ids;

    public VisitForgetService(VisitForgetStore store, BlindIndex index, SnowflakeIdGenerator ids) {
        this.store = store;
        this.index = index;
        this.ids = ids;
    }

    public VisitForget.Response submit(VisitForget.Request body) {
        UUID visitorId = uuid(body == null ? null : body.visitorId());
        if (visitorId == null) {
            throw ValidationException.of("visitorId", new MessageRef("analytics.forget.visitorId.invalid"));
        }
        index.requireConfigured();
        String hash = index.hash(VISITOR, visitorId.toString()); // 수집기(VisitCollector.VISITOR)와 같은 용도·같은 정규화(소문자 UUID)
        VisitForgetStore.Received r = store.submit(ids.nextId(), hash, Instant.now().truncatedTo(ChronoUnit.MICROS));
        if (r == null) {
            throw new VisitForgetBusyException(BUSY_RETRY_SECONDS);
        }
        return new VisitForget.Response(Long.toString(r.id()), r.receivedAt());
    }

    private static UUID uuid(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.strip();
        if (v.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(v.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
