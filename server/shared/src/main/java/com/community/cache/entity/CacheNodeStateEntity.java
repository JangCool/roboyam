package com.community.cache.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 서버(노드)별 · 캐시별 상태 보고 — shared V175 {@code cache_node_states}. 쓰기는 {@code CacheStateStore#report}(한 번에 덮어쓰기 — ON CONFLICT), 읽기는 프로젝션.
 */
@Entity
@Table(name = "cache_node_states")
@IdClass(CacheNodeStateEntity.Key.class)
public class CacheNodeStateEntity {

    @Id
    private String nodeId;
    @Id
    private String cacheKey;

    private String role;
    private long appliedGeneration;
    private boolean appliedEnabled;
    private Instant lastClearedAt;
    private Instant lastCheckedAt;
    private long entryCount;
    private long hitCount;
    private long missCount;
    private Instant reportedAt;

    protected CacheNodeStateEntity() {
    }

    public static class Key implements Serializable {

        private String nodeId;
        private String cacheKey;

        public Key() {
        }

        public Key(String nodeId, String cacheKey) {
            this.nodeId = nodeId;
            this.cacheKey = cacheKey;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(nodeId, k.nodeId) && Objects.equals(cacheKey, k.cacheKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId, cacheKey);
        }
    }
}
