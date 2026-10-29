package com.community.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.address.exception.AddressImportFailure;
import org.junit.jupiter.api.Test;

/** 주소 DB 원본 한 줄 읽기와 표기 규칙(「도로명주소한글활용가이드」·「영문활용가이드」 붙임1) — address-search-contract 「적재」 */
class AddressFileParserTest {

    @Test
    void 일반_건물은_동_이름을_참고항목으로_붙이고_검색_글자에_동_건물_우편번호를_넣는다() {
        AddressFileParser.RoadRow row = AddressFileParser.road(
                AddressSamples.road("11680101000000001", "서울특별시", "강남구", "역삼동", "", "테헤란로", 0, 152, 0, "06236", false, "", "강남파이낸스센터"), "f", 1);

        assertThat(row.roadAddress()).isEqualTo("서울특별시 강남구 테헤란로 152 (역삼동)");
        assertThat(row.buildingName()).isEqualTo("강남파이낸스센터");
        assertThat(row.eupmyeondong()).isEqualTo("역삼동");
        assertThat(row.zipcode()).isEqualTo("06236");
        assertThat(row.searchText()).isEqualTo("서울특별시 강남구 테헤란로 152 (역삼동) 역삼동 강남파이낸스센터 06236");
    }

    @Test
    void 공동주택은_동_이름과_건물_이름을_참고항목으로_시군구용_건물명을_먼저_쓴다() {
        AddressFileParser.RoadRow row = AddressFileParser.road(
                AddressSamples.road("m2", "서울특별시", "송파구", "잠실동", "", "올림픽로", 0, 99, 0, "05502", true, "대장건물명", "잠실엘스"), "f", 1);

        assertThat(row.roadAddress()).isEqualTo("서울특별시 송파구 올림픽로 99 (잠실동, 잠실엘스)");
        assertThat(row.buildingName()).isEqualTo("잠실엘스");

        AddressFileParser.RoadRow registerOnly = AddressFileParser.road(
                AddressSamples.road("m3", "서울특별시", "송파구", "잠실동", "", "올림픽로", 0, 99, 0, "05502", true, "대장건물명", ""), "f", 1);
        assertThat(registerOnly.buildingName()).isEqualTo("대장건물명");
    }

    @Test
    void 법정리가_있으면_읍면을_본문에_넣고_참고항목에_되풀이하지_않는다() {
        AddressFileParser.RoadRow row = AddressFileParser.road(
                AddressSamples.road("m4", "경기도", "양평군", "양평읍", "도곡리", "양평로", 0, 12, 3, "12550", false, "", ""), "f", 1);

        assertThat(row.roadAddress()).isEqualTo("경기도 양평군 양평읍 양평로 12-3");
        assertThat(row.eupmyeondong()).isEqualTo("양평읍 도곡리");

        AddressFileParser.RoadRow apartment = AddressFileParser.road(
                AddressSamples.road("m5", "경기도", "양평군", "양평읍", "도곡리", "양평로", 0, 12, 0, "12550", true, "", "양평아파트"), "f", 1);
        assertThat(apartment.roadAddress()).isEqualTo("경기도 양평군 양평읍 양평로 12 (양평아파트)");
    }

    @Test
    void 지하_공중_수상은_건물번호_앞에_붙이고_시군구가_없는_세종도_된다() {
        assertThat(AddressFileParser.road(AddressSamples.road("a", "서울특별시", "중구", "명동", "", "을지로", 1, 30, 0, "04533", false, "", ""), "f", 1)
                .roadAddress()).isEqualTo("서울특별시 중구 을지로 지하30 (명동)");
        assertThat(AddressFileParser.road(AddressSamples.road("b", "부산광역시", "해운대구", "우동", "", "마린시티2로", 3, 38, 0, "48120", false, "", ""), "f", 1)
                .roadAddress()).isEqualTo("부산광역시 해운대구 마린시티2로 수상38 (우동)");
        assertThat(AddressFileParser.road(AddressSamples.road("c", "세종특별자치시", "", "어진동", "", "한누리대로", 0, 2130, 0, "30151", false, "", ""), "f", 1)
                .roadAddress()).isEqualTo("세종특별자치시 한누리대로 2130 (어진동)");
    }

    @Test
    void 관리번호가_빈_줄은_건너뛰고_칸이_모자라면_파일_이름과_줄_번호로_멈춘다() {
        assertThat(AddressFileParser.road(AddressSamples.road(" ", "서울특별시", "강남구", "역삼동", "", "테헤란로", 0, 1, 0, "06236", false, "", ""), "f", 1)).isNull();
        assertThat(AddressFileParser.jibun(AddressSamples.jibun("", "역삼동", "", false, 1, 0), "f", 1)).isNull();
        assertThat(AddressFileParser.english(AddressSamples.english("", "Seoul", "", "", "", "Teheran-ro", 0, 1, 0), "f", 1)).isNull();

        assertThatThrownBy(() -> AddressFileParser.road("a|b|c", "rnaddrkor_seoul.txt", 42))
                .isInstanceOfSatisfying(AddressImportFailure.class, failure -> {
                    assertThat(failure.messageKey()).isEqualTo("address.import.badLine");
                    assertThat(failure.args()).containsExactly("rnaddrkor_seoul.txt", "42", "24", "3");
                });
        assertThatThrownBy(() -> AddressFileParser.jibun("a|b", "jibun_rnaddrkor_seoul.txt", 7)).isInstanceOf(AddressImportFailure.class);
        assertThatThrownBy(() -> AddressFileParser.english("a|b", "rneng_seoul.txt", 7)).isInstanceOf(AddressImportFailure.class);
    }

    @Test
    void 숫자_칸이_비었거나_글자면_0으로_본다() {
        String line = AddressSamples.road("m6", "서울특별시", "강남구", "역삼동", "", "테헤란로", 0, 7, 0, "06236", false, "", "").replace("|7|0|", "|7| |");
        assertThat(AddressFileParser.road(line, "f", 1).buildingNoSub()).isZero();
    }

    @Test
    void 관련지번_줄() {
        AddressFileParser.JibunRow row = AddressFileParser.jibun(AddressSamples.jibun("m1", "역삼동", "", true, 737, 12), "f", 1);
        assertThat(row.mgmtNo()).isEqualTo("m1");
        assertThat(row.beopjeongDong()).isEqualTo("역삼동");
        assertThat(row.mountain()).isTrue();
        assertThat(row.jibunMain()).isEqualTo(737);
        assertThat(row.jibunSub()).isEqualTo(12);
    }

    @Test
    void 영문은_건물번호와_도로명이_먼저_법정리가_있을_때만_읍면을_넣는다() {
        assertThat(AddressFileParser.english(AddressSamples.english("m1", "Seoul", "Gwangjin-gu", "Guui-dong", "", "Yeonghwasa-ro", 0, 87, 0), "f", 1)
                .roadAddressEn()).isEqualTo("87 Yeonghwasa-ro, Gwangjin-gu, Seoul");
        assertThat(AddressFileParser.english(AddressSamples.english("m2", "Gyeonggi-do", "Yangpyeong-gun", "Yangpyeong-eup", "Dogok-ri", "Yangpyeong-ro", 1, 12, 3),
                "f", 1).roadAddressEn()).isEqualTo("B12-3 Yangpyeong-ro, Yangpyeong-eup, Yangpyeong-gun, Gyeonggi-do");
        assertThat(AddressFileParser.english(AddressSamples.english("m3", "Sejong-si", "", "Eojin-dong", "", "Hannuri-daero", 3, 5, 0), "f", 1)
                .roadAddressEn()).isEqualTo("W5 Hannuri-daero, Sejong-si");
    }
}
