package com.community.notification.repository.master;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 이벤트별 전달 도구(Slack · Mattermost)와 추가 수신 팀 — V41 의 {@code notification_event_channels}·{@code notification_event_teams}.
 *
 * <p>Native SQL(JDBC)인 이유: 이 표들은 (이벤트, 도구/팀) 짝만 든 얇은 연결 표라 엔티티를 두지 않고, 발행기({@code NotificationPublisher})가 업무 트랜잭션의
 * JDBC 세이브포인트 안에서 같은 방식으로 읽는다. 업무 트랜잭션(JPA)의 같은 연결에 참여하므로 <b>부르는 서비스가 트랜잭션을 열고</b>, 쓰기 전에 이벤트 행을 먼저 내보낸다(flush).
 */
@Repository
public class NotificationDeliveryRepository {

    private final JdbcTemplate jdbc;

    public NotificationDeliveryRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 이벤트마다 고른 Slack·Mattermost 도구 id (도구 만든 순서) */
    public Map<String, List<Long>> channelIdsByEvent() {
        return group("select event_code, channel_id from notification_event_channels order by event_code, channel_id");
    }

    /** 이벤트마다 고른 추가 수신 팀 id */
    public Map<String, List<Long>> teamIdsByEvent() {
        return group("select event_code, team_id from notification_event_teams order by event_code, team_id");
    }

    public List<Long> channelIdsOf(String eventCode) {
        return jdbc.queryForList("select channel_id from notification_event_channels where event_code = ? order by channel_id", Long.class, eventCode);
    }

    public List<Long> teamIdsOf(String eventCode) {
        return jdbc.queryForList("select team_id from notification_event_teams where event_code = ? order by team_id", Long.class, eventCode);
    }

    /** 이벤트의 도구 선택을 통째로 바꾼다 */
    public void replaceChannels(String eventCode, Collection<Long> channelIds) {
        jdbc.update("delete from notification_event_channels where event_code = ?", eventCode);
        insertAll("insert into notification_event_channels (event_code, channel_id) values (?, ?)", eventCode, channelIds);
    }

    /** 이벤트의 추가 수신 팀 선택을 통째로 바꾼다 */
    public void replaceTeams(String eventCode, Collection<Long> teamIds) {
        jdbc.update("delete from notification_event_teams where event_code = ?", eventCode);
        insertAll("insert into notification_event_teams (event_code, team_id) values (?, ?)", eventCode, teamIds);
    }

    /** 도구마다 그 도구를 고른 이벤트 수 */
    public Map<Long, Integer> eventCountByChannel() {
        return count("select channel_id, count(*) from notification_event_channels group by channel_id");
    }

    /** 팀마다 그 팀을 고른 이벤트 수 */
    public Map<Long, Integer> eventCountByTeam() {
        return count("select team_id, count(*) from notification_event_teams group by team_id");
    }

    /** 이메일 전달을 켜 둔 이벤트 수 */
    public int emailEventCount() {
        Integer count = jdbc.queryForObject("select count(*) from notification_events where email_enabled", Integer.class);
        return count == null ? 0 : count;
    }

    /** 브라우저 알림 전달을 켜 둔 이벤트 수 */
    public int browserEventCount() {
        Integer count = jdbc.queryForObject("select count(*) from notification_events where browser_enabled", Integer.class);
        return count == null ? 0 : count;
    }

    /** 이 이벤트에 고른 도구 중 지금 <b>켜져 있는</b> 것 — 발행할 때 Slack·Mattermost 로 한 번 게시할 곳 */
    public List<Long> enabledChannelIdsOf(String eventCode) {
        return jdbc.queryForList("select c.id from notification_event_channels ec join notification_channels c on c.id = ec.channel_id "
                + "where ec.event_code = ? and c.enabled order by c.id", Long.class, eventCode);
    }

    /** 이 이벤트에 고른 팀 중 <b>사용 중인</b> 팀의 팀원 운영자 id (중복 없이). 운영자의 활성 여부·보는 권한은 부르는 쪽이 거른다 */
    public Set<Long> enabledTeamMemberIdsOf(String eventCode) {
        return new LinkedHashSet<>(jdbc.queryForList("select distinct tm.operator_id from notification_event_teams et "
                + "join teams t on t.id = et.team_id and t.enabled join team_members tm on tm.team_id = t.id where et.event_code = ? order by tm.operator_id",
                Long.class, eventCode));
    }

    /**
     * 「팀에만 보내기」({@code default_recipients=false})인데 고른 팀이 하나도 남지 않은 이벤트를 기본 수신으로 되돌린다 — 팀을 지워 이벤트 선택에서 빠졌을 때(연쇄 삭제) 부른다.
     * 그대로 두면 저장된 값이 「팀 없이 팀에만」이 되어 그 이벤트의 다른 설정을 저장할 수 없다(400 {@code fields.teamIds}).
     *
     * @return 되돌린 이벤트 코드
     */
    public List<String> restoreDefaultRecipientsWithoutTeams() {
        return jdbc.queryForList("update notification_events e set default_recipients = true where not e.default_recipients "
                + "and not exists (select 1 from notification_event_teams et where et.event_code = e.code) returning e.code", String.class);
    }

    /** 기본 제공 도구(이메일 · 브라우저)가 켜져 있는가. 상태 행이 없으면 켜진 것이다 */
    public boolean builtinEnabled(String channelKey) {
        List<Boolean> rows = jdbc.queryForList("select enabled from notification_builtin_channels where channel_key = ?", Boolean.class, channelKey);
        return rows.isEmpty() || Boolean.TRUE.equals(rows.get(0));
    }

    private void insertAll(String sql, String eventCode, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<Object[]> rows = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            rows.add(new Object[] {eventCode, id});
        }
        jdbc.batchUpdate(sql, rows);
    }

    private Map<String, List<Long>> group(String sql) {
        Map<String, List<Long>> out = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            out.computeIfAbsent(rs.getString(1), key -> new ArrayList<>()).add(rs.getLong(2));
        });
        return out;
    }

    private Map<Long, Integer> count(String sql) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            out.put(rs.getLong(1), rs.getInt(2));
        });
        return out;
    }
}
