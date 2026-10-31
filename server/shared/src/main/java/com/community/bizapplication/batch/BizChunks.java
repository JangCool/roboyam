package com.community.bizapplication.batch;

import com.community.batch.core.ChunkSource;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/** 신청 건 배치의 덩어리 대상 — 신청 건 ID 키셋(처리하면 대상 조건이 거짓이 되므로 처음부터 다시 돌려도 안전하다) */
final class BizChunks {

    /** 실패 건의 대상 종류 */
    static final String TARGET = "BIZ_APPLICATION";

    private BizChunks() {
    }

    static ChunkSource<AppRow> byId(BiFunction<Long, Integer, List<AppRow>> next, Function<List<Long>, List<AppRow>> byIds) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<AppRow> next(String afterKey, int size) {
                return next.apply(afterKey == null ? 0L : Long.parseLong(afterKey), size);
            }

            @Override
            public List<AppRow> byIds(List<String> targetIds) {
                return byIds.apply(targetIds.stream().map(Long::valueOf).toList());
            }

            @Override
            public String keyOf(AppRow item) {
                return Long.toString(item.id());
            }

            @Override
            public String targetIdOf(AppRow item) {
                return Long.toString(item.id());
            }
        };
    }
}
