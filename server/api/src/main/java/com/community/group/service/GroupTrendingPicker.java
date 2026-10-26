package com.community.group.service;

import com.community.group.entity.GroupEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * 「지금 뜨는 소모임」을 후보에서 고른다(community {@code GroupTrendingPicker} 그대로) — <b>활동·규모로 가중한 무작위, 중복 없이 {@code n}개</b>
 * (Efraimidis–Spirakis: 항목마다 {@code u^(1/가중치)} 를 매겨 큰 순서로). 가중치 = {@code 1 + ln(1 + 멤버 수) + 2 × 최근성}(최근성은 마지막 활동이 활동 기준 기간 안에서
 * 얼마나 가까운가, 0~1). 활동이 많고 큰 모임이 자주 뽑히되 작은 모임에도 기회가 간다. 난수는 인자로 받아 시험에서 고정할 수 있다.
 */
@Component
public class GroupTrendingPicker {

    /** 기본 난수로 고른다 */
    public List<GroupEntity> pick(List<GroupEntity> candidates, int n, int activeDays, Instant now) {
        return pick(candidates, n, activeDays, now, RandomGenerator.getDefault());
    }

    public List<GroupEntity> pick(List<GroupEntity> candidates, int n, int activeDays, Instant now, RandomGenerator random) {
        record Keyed(GroupEntity group, double key) {
        }
        double windowSeconds = Duration.ofDays(Math.max(1, activeDays)).toSeconds();
        List<Keyed> keyed = new ArrayList<>(candidates.size());
        for (GroupEntity g : candidates) {
            Instant last = g.getLastActivityAt() == null ? now.minus(Duration.ofDays(Math.max(1, activeDays))) : g.getLastActivityAt();
            double ageSeconds = Math.max(0, Duration.between(last, now).toSeconds());
            double recency = Math.max(0, 1 - ageSeconds / windowSeconds);
            double weight = 1 + Math.log(1 + g.getMemberCount()) + 2 * recency;
            double u = Math.max(Double.MIN_NORMAL, random.nextDouble());
            keyed.add(new Keyed(g, Math.pow(u, 1.0 / weight)));
        }
        keyed.sort(Comparator.comparingDouble(Keyed::key).reversed());
        return keyed.stream().limit(Math.max(0, n)).map(Keyed::group).toList();
    }
}
