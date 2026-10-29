package com.community.address.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 시험용 작은 주소 DB 표본 — 행정안전부 레이아웃(도로명 24칸 · 관련지번 14칸 · 영문 17칸)대로 만든 가짜 줄. 실제 원본 데이터는 크기가 커서 저장소에 두지 않는다.
 */
final class AddressSamples {

    static final Charset MS949 = Charset.forName("MS949");

    private AddressSamples() {
    }

    /** 도로명주소 한 줄(쓰는 칸만 채우고 나머지는 빈 칸) */
    static String road(String mgmtNo, String sido, String sigungu, String dong, String ri, String roadName, int underground, int main, int sub, String zipcode,
                       boolean apartment, String registerName, String sigunguBuildingName) {
        String[] c = new String[AddressFileParser.ROAD_COLUMNS];
        Arrays.fill(c, "");
        c[0] = mgmtNo;
        c[2] = sido;
        c[3] = sigungu;
        c[4] = dong;
        c[5] = ri;
        c[10] = roadName;
        c[11] = Integer.toString(underground);
        c[12] = Integer.toString(main);
        c[13] = Integer.toString(sub);
        c[16] = zipcode;
        c[19] = apartment ? "1" : "0";
        c[21] = registerName;
        c[22] = sigunguBuildingName;
        return String.join("|", c);
    }

    static String jibun(String mgmtNo, String dong, String ri, boolean mountain, int main, int sub) {
        String[] c = new String[AddressFileParser.JIBUN_COLUMNS];
        Arrays.fill(c, "");
        c[0] = mgmtNo;
        c[4] = dong;
        c[5] = ri;
        c[6] = mountain ? "1" : "0";
        c[7] = Integer.toString(main);
        c[8] = Integer.toString(sub);
        return String.join("|", c);
    }

    static String english(String mgmtNo, String sido, String sigungu, String eupmyeondong, String ri, String roadName, int underground, int main, int sub) {
        String[] c = new String[AddressFileParser.ENGLISH_COLUMNS];
        Arrays.fill(c, "");
        c[0] = mgmtNo;
        c[2] = sido;
        c[3] = sigungu;
        c[4] = eupmyeondong;
        c[5] = ri;
        c[7] = roadName;
        c[8] = Integer.toString(underground);
        c[9] = Integer.toString(main);
        c[10] = Integer.toString(sub);
        return String.join("|", c);
    }

    /** 이름 → 줄 목록을 CRLF 로 이어 zip 하나로 쓴다 */
    static Path zip(Path dir, String name, Map<String, List<String>> entries, Charset charset) throws IOException {
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out, charset)) {
            for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(String.join("\r\n", entry.getValue()).concat("\r\n").getBytes(charset));
                zip.closeEntry();
            }
        }
        return file;
    }

    static Map<String, List<String>> entries(Object... nameThenLines) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < nameThenLines.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) nameThenLines[i + 1];
            map.put((String) nameThenLines[i], lines);
        }
        return map;
    }
}
