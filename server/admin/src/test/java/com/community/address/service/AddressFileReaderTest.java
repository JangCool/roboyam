package com.community.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.address.exception.AddressImportFailure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 올린 zip 훑기 — 파일 종류 판정 · 덩어리 · 건수 · 멈추는 경우(address-search-contract 「적재」). 작은 표본 zip 을 시험이 만든다 */
class AddressFileReaderTest {

    @TempDir
    Path dir;

    /** 받은 덩어리를 모은다 */
    private static final class Collected implements AddressFileReader.Sink {
        final List<List<AddressFileParser.RoadRow>> roadBatches = new ArrayList<>();
        final List<AddressFileParser.JibunRow> jibuns = new ArrayList<>();
        final List<AddressFileParser.EnglishRow> englishes = new ArrayList<>();

        @Override
        public void roads(List<AddressFileParser.RoadRow> batch) {
            roadBatches.add(List.copyOf(batch));
        }

        @Override
        public void jibuns(List<AddressFileParser.JibunRow> batch) {
            jibuns.addAll(batch);
        }

        @Override
        public void englishes(List<AddressFileParser.EnglishRow> batch) {
            englishes.addAll(batch);
        }
    }

    private static String road(String mgmtNo, int main) {
        return AddressSamples.road(mgmtNo, "서울특별시", "강남구", "역삼동", "", "테헤란로", 0, main, 0, "06236", false, "", "");
    }

    @Test
    void 한글_zip_과_영문_zip_을_같이_읽고_모르는_항목과_빈_줄은_건너뛴다() throws IOException {
        Path korean = AddressSamples.zip(dir, "202610_도로명주소 한글_전체분.zip", AddressSamples.entries(
                "rnaddrkor_seoul.txt", List.of(road("m1", 1), "", road("m2", 2)),
                "jibun_rnaddrkor_seoul.txt", List.of(AddressSamples.jibun("m1", "역삼동", "", false, 737, 0)),
                "안내/도로명주소 활용가이드.pdf", List.of("not an address"),
                "readme.txt", List.of("not an address")), AddressSamples.MS949);
        Path english = AddressSamples.zip(dir, "202610_도로명주소 영문_전체분.zip", AddressSamples.entries(
                "rneng_seoul.txt", List.of(AddressSamples.english("m1", "Seoul", "Gangnam-gu", "Yeoksam-dong", "", "Teheran-ro", 0, 1, 0))), AddressSamples.MS949);
        List<AddressFileReader.Source> sources = List.of(new AddressFileReader.Source(korean, "ko.zip"), new AddressFileReader.Source(english, "en.zip"));

        assertThat(AddressFileReader.countLines(sources, AddressSamples.MS949)).isEqualTo(5); // 도로명 3줄(빈 줄 포함) + 지번 1 + 영문 1
        Collected sink = new Collected();
        AddressFileReader.Counts counts = AddressFileReader.read(sources, AddressSamples.MS949, sink);

        assertThat(counts).isEqualTo(new AddressFileReader.Counts(2, 1, 1));
        assertThat(sink.roadBatches).hasSize(1);
        assertThat(sink.roadBatches.getFirst()).extracting(AddressFileParser.RoadRow::mgmtNo).containsExactly("m1", "m2");
        assertThat(sink.roadBatches.getFirst().getFirst().roadAddress()).isEqualTo("서울특별시 강남구 테헤란로 1 (역삼동)"); // CP949 한글이 그대로
        assertThat(sink.jibuns).extracting(AddressFileParser.JibunRow::jibunMain).containsExactly(737);
        assertThat(sink.englishes).extracting(AddressFileParser.EnglishRow::roadAddressEn).containsExactly("1 Teheran-ro, Gangnam-gu, Seoul");
    }

    @Test
    void 도로명_줄은_정한_크기의_덩어리로_넘긴다() throws IOException {
        int lines = AddressFileReader.BATCH_SIZE * 2 + 5;
        List<String> rows = IntStream.range(0, lines).mapToObj(i -> road("m" + i, i + 1)).toList();
        Path zip = AddressSamples.zip(dir, "big.zip", AddressSamples.entries("rnaddrkor_busan.txt", rows), AddressSamples.MS949);

        Collected sink = new Collected();
        AddressFileReader.Counts counts = AddressFileReader.read(List.of(new AddressFileReader.Source(zip, "big.zip")), AddressSamples.MS949, sink);

        assertThat(counts.road()).isEqualTo(lines);
        assertThat(sink.roadBatches).extracting(List::size).containsExactly(AddressFileReader.BATCH_SIZE, AddressFileReader.BATCH_SIZE, 5);
    }

    @Test
    void 도로명_파일이_없으면_멈춘다() throws IOException {
        Path english = AddressSamples.zip(dir, "en.zip", AddressSamples.entries(
                "rneng_seoul.txt", List.of(AddressSamples.english("m1", "Seoul", "Gangnam-gu", "", "", "Teheran-ro", 0, 1, 0))), AddressSamples.MS949);

        assertThatThrownBy(() -> AddressFileReader.read(List.of(new AddressFileReader.Source(english, "en.zip")), AddressSamples.MS949, new Collected()))
                .isInstanceOfSatisfying(AddressImportFailure.class, failure -> assertThat(failure.messageKey()).isEqualTo("address.import.noRoadFile"));
    }

    @Test
    void zip_이_아니면_올린_파일_이름으로_멈춘다() throws IOException {
        Path notZip = Files.writeString(dir.resolve("fake.zip"), "this is not a zip");

        assertThatThrownBy(() -> AddressFileReader.read(List.of(new AddressFileReader.Source(notZip, "202610_한글.zip")), AddressSamples.MS949, new Collected()))
                .isInstanceOfSatisfying(AddressImportFailure.class, failure -> {
                    assertThat(failure.messageKey()).isEqualTo("address.import.badZip");
                    assertThat(failure.args()).containsExactly("202610_한글.zip");
                });
    }

    @Test
    void 고른_글자_집합으로_못_읽는_바이트는_깨진_글자로_넣지_않고_줄_번호와_멈춘다() throws IOException {
        // CP949 로 쓴 한글을 UTF-8 로 읽게 하면 틀린 바이트가 나온다
        Path zip = AddressSamples.zip(dir, "ko.zip", AddressSamples.entries("rnaddrkor_seoul.txt", List.of(road("m1", 1), road("m2", 2))), AddressSamples.MS949);
        List<AddressFileReader.Source> sources = List.of(new AddressFileReader.Source(zip, "ko.zip"));

        assertThatThrownBy(() -> AddressFileReader.read(sources, StandardCharsets.UTF_8, new Collected()))
                .isInstanceOfSatisfying(AddressImportFailure.class, failure -> {
                    assertThat(failure.messageKey()).isEqualTo("address.import.badCharset");
                    assertThat(failure.args()).containsExactly("rnaddrkor_seoul.txt", "1");
                });
        assertThatThrownBy(() -> AddressFileReader.countLines(sources, StandardCharsets.UTF_8))
                .isInstanceOfSatisfying(AddressImportFailure.class, failure -> assertThat(failure.messageKey()).isEqualTo("address.import.badCharset"));
    }

    @Test
    void UTF_8_로_만든_파일은_UTF_8_로_읽는다() throws IOException {
        Path zip = AddressSamples.zip(dir, "utf8.zip", AddressSamples.entries("rnaddrkor_seoul.txt", List.of(road("m1", 1))), StandardCharsets.UTF_8);
        Collected sink = new Collected();

        AddressFileReader.read(List.of(new AddressFileReader.Source(zip, "utf8.zip")), StandardCharsets.UTF_8, sink);

        assertThat(sink.roadBatches.getFirst().getFirst().sido()).isEqualTo("서울특별시");
    }

    @Test
    void 파일_종류는_이름_앞머리와_txt_로_가른다() {
        assertThat(AddressFileReader.kindOf("rnaddrkor_seoul.txt")).isEqualTo(AddressFileReader.Kind.ROAD);
        assertThat(AddressFileReader.kindOf("jibun_rnaddrkor_seoul.txt")).isEqualTo(AddressFileReader.Kind.JIBUN);
        assertThat(AddressFileReader.kindOf("RNENG_SEOUL.TXT")).isEqualTo(AddressFileReader.Kind.ENGLISH);
        assertThat(AddressFileReader.kindOf("rnaddrkor_seoul.pdf")).isNull();
        assertThat(AddressFileReader.kindOf("build_rnaddrkor_seoul.txt")).isNull();
    }
}
