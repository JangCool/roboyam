package com.community.zconfiguration.id;

import java.time.Clock;

/**
 * 시간순 64비트 ID 발급기 (Snowflake 계열).
 *
 * <pre>
 * 0 | 41비트 시각(ms, 2026-01-01 기준) | 10비트 서버 번호 | 12비트 순번
 * </pre>
 *
 * <p>시각이 앞에 있어 ID 순서가 곧 생성 순서다. 서버 번호({@code roboyam.node-id}, 환경 변수 {@code NODE_ID})는
 * 앱 서버마다 달라야 한다(같은 번호로 두 대를 띄우면 ID 가 겹친다). 한 서버에서 1ms 에 4096개까지 발급하고, 넘치면 다음 ms 를 기다린다.
 */
public class SnowflakeIdGenerator {

    /** 2026-01-01T00:00:00Z */
    static final long EPOCH_MS = 1_767_225_600_000L;

    private static final int NODE_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_NODE = (1L << NODE_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    /** 시계가 이만큼 넘게 되돌아가면 ID 순서를 지킬 수 없어 멈춘다 */
    private static final long MAX_CLOCK_DRIFT_MS = 5_000;

    private final long nodeId;
    private final Clock clock;
    private long lastTimestamp = -1;
    private long sequence = 0;

    public SnowflakeIdGenerator(int nodeId) {
        this(nodeId, Clock.systemUTC());
    }

    SnowflakeIdGenerator(int nodeId, Clock clock) {
        if (nodeId < 0 || nodeId > MAX_NODE) {
            throw new IllegalArgumentException("node-id 는 0~" + MAX_NODE + " 여야 한다: " + nodeId);
        }
        this.nodeId = nodeId;
        this.clock = clock;
    }

    public synchronized long nextId() {
        long now = clock.millis();
        if (now < lastTimestamp) {
            if (lastTimestamp - now > MAX_CLOCK_DRIFT_MS) {
                throw new IllegalStateException("시계가 " + (lastTimestamp - now) + "ms 되돌아가 ID 를 발급할 수 없다");
            }
            now = lastTimestamp; // 조금 되돌아간 것은 마지막 시각에 이어 붙여 순서를 지킨다
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH_MS) << (NODE_BITS + SEQUENCE_BITS)) | (nodeId << SEQUENCE_BITS) | sequence;
    }

    /**
     * 그 시각에 발급될 수 있는 가장 작은 ID — 시각 범위를 ID 범위로 바꿔 id 로 나눈 표(글 월 조각)를 가지치기할 때 쓴다.
     * DB 함수 {@code board_snowflake_floor}(R__batch_17)와 같은 식이다.
     */
    public static long floorIdAt(java.time.Instant at) {
        return Math.max(0L, at.toEpochMilli() - EPOCH_MS) << (NODE_BITS + SEQUENCE_BITS);
    }

    private long waitNextMillis(long last) {
        long now = clock.millis();
        while (now <= last) {
            Thread.onSpinWait();
            now = clock.millis();
        }
        return now;
    }
}
