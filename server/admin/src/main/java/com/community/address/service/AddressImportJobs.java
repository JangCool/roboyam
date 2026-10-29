package com.community.address.service;

import com.community.address.entity.AddressImportPhase;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 이 서버에서 돌고 있는 적재의 실시간 진행(단계·줄 수) — 화면이 1.5초마다 묻는 값이라 DB 에 매번 쓰지 않고 메모리에 둔다. 단계 바뀜과 결과는 적재 줄
 * ({@code address_db_loads})에도 남으므로 서버가 다시 떠도 이력·상태는 남는다(진행 줄 수만 사라진다).
 */
@Component
public class AddressImportJobs {

    /** 한 적재의 진행 — 적재 스레드가 쓰고 요청 스레드가 읽는다 */
    public static final class Live {

        private volatile AddressImportPhase phase = AddressImportPhase.COUNTING;
        /** -1 = 아직 세는 중 */
        private final AtomicLong total = new AtomicLong(-1);
        private final AtomicLong processed = new AtomicLong();

        public AddressImportPhase phase() {
            return phase;
        }

        public long total() {
            return total.get();
        }

        public long processed() {
            return processed.get();
        }

        void phase(AddressImportPhase phase) {
            this.phase = phase;
        }

        /** 단계가 바뀌어 분모가 달라질 때(줄 수 → 붙일 줄 수) */
        void restart(long newTotal) {
            processed.set(0);
            total.set(newTotal);
        }

        void add(long rows) {
            processed.addAndGet(rows);
        }
    }

    private final Map<Long, Live> running = new ConcurrentHashMap<>();

    Live begin(long loadId) {
        Live live = new Live();
        running.put(loadId, live);
        return live;
    }

    public Optional<Live> live(long loadId) {
        return Optional.ofNullable(running.get(loadId));
    }

    void end(long loadId) {
        running.remove(loadId);
    }
}
