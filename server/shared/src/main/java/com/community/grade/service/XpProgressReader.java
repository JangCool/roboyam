package com.community.grade.service;

import com.community.grade.entity.GradeSettingEntity;
import com.community.grade.repository.master.GradeSettingRepository;
import com.community.user.entity.ProfileEntity;
import org.springframework.stereotype.Component;

/**
 * 회원 레벨 진행도 읽기(grade-level-contract 4·5절) — 공개 프로필·로그인 정보({@code /api/me})·내 등급({@code /api/me/grade})·관리자 회원 상세가 같은 셈을 쓴다.
 * 저장된 경험치·레벨({@code profiles.xp}·{@code level})과 지금 레벨 곡선({@code grade_settings.xp_curve_base}·{@code xp_curve_exponent})으로 {@link XpCurve.Progress} 를 만든다.
 * 트랜잭션은 부르는 쪽 것을 쓴다(읽기 한 번 — 등급 설정 행).
 */
@Component
public class XpProgressReader {

    private final GradeSettingRepository settings;

    public XpProgressReader(GradeSettingRepository settings) {
        this.settings = settings;
    }

    /** 지금 레벨 곡선(기준값·지수 — 행이 없거나 범위 밖이면 기본값) */
    public XpCurve.Shape curve() {
        return settings.findById(GradeSettingEntity.ROW_ID).map(XpProgressReader::curveOf).orElse(XpCurve.Shape.DEFAULT);
    }

    /** 설정 행의 레벨 곡선(범위 밖이면 기본값) */
    public static XpCurve.Shape curveOf(GradeSettingEntity setting) {
        return XpCurve.Shape.of(setting.getXpCurveBase(), setting.getXpCurveExponent());
    }

    public XpCurve.Progress of(ProfileEntity profile) {
        return curve().progress(profile.getXp(), profile.getLevel());
    }
}
