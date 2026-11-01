package com.community.hotdeal.batch;

import com.community.batch.core.ChunkSource;
import java.util.List;
import java.util.function.BiFunction;

/** 핫딜 배치의 덩어리 대상 — 글 ID 키셋(처리하면 대상 조건이 거짓이 되므로 처음부터 다시 돌려도 안전하다 — 처리 쪽이 잠가 다시 본다) */
final class DealChunks {

    /** 실패 건의 대상 종류(번들 {@code batch.target.POST_DEAL}) */
    static final String TARGET = "POST_DEAL";

    private DealChunks() {
    }

    static ChunkSource<Long> byPostId(BiFunction<Long, Integer, List<Long>> next) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<Long> next(String afterKey, int size) {
                return next.apply(afterKey == null ? 0L : Long.parseLong(afterKey), size);
            }

            @Override
            public List<Long> byIds(List<String> targetIds) {
                return targetIds.stream().map(Long::valueOf).toList();
            }

            @Override
            public String keyOf(Long item) {
                return Long.toString(item);
            }

            @Override
            public String targetIdOf(Long item) {
                return Long.toString(item);
            }
        };
    }
}
