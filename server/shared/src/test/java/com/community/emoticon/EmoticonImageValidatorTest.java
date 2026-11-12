package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.emoticon.entity.EmoticonImageFormat;
import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.exception.EmoticonImageInvalidException;
import com.community.emoticon.service.EmoticonImageInspector;
import com.community.emoticon.service.EmoticonImageValidator;
import com.community.media.config.MediaProperties;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageGate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 로보티콘 그림 검사(emoticon-contract S1 — 계약 가정 7 · user-web {@code UtilEmoticonSpec} 의 이유 코드와 차례). DB 없이 검사기만.
 * 바이트로 형식을 가르고(확장자 없음), 200×200 · 2MB(대표 PNG 1MB) · GIF 24프레임 · 무한 반복(작가만 막음) · 압축 폭탄(프레임이 화면 밖) · 저장 바이트·지문.
 */
class EmoticonImageValidatorTest {

    private final EmoticonImageValidator validator = new EmoticonImageValidator(new ImageCodec(new ImageGate(new MediaProperties(2, 10, 24))));

    private String reason(byte[] data, EmoticonImageKind kind, boolean loopRequired) {
        try {
            validator.check(data, kind, loopRequired, "file");
        } catch (EmoticonImageInvalidException e) {
            assertThat(e.getErrorCode()).isEqualTo("VALIDATION");
            assertThat(e.getFields()).containsOnlyKeys("file");
            assertThat(e.getFields().get("file").key()).isEqualTo("emoticon.image.reason");
            assertThat(e.getFields().get("file").args()).containsExactly(e.reason());
            return e.reason();
        }
        return null;
    }

    @Test
    void 아이로빗콘_시드_그림은_그대로_받고_지문은_SHA256_이다() {
        byte[] gif = TestImages.robotikon("robotikon-m/19.gif");
        EmoticonImageValidator.Checked checked = validator.check(gif, EmoticonImageKind.EMOTICON, true, "file");
        assertThat(checked.format()).isEqualTo(EmoticonImageFormat.GIF);
        assertThat(checked.width()).isEqualTo(200);
        assertThat(checked.frames()).isEqualTo(24);
        assertThat(checked.loopForever()).isTrue();
        assertThat(checked.data()).isEqualTo(gif);
        // V142 시드의 지문과 같다(그 파일에서 잰 값)
        assertThat(checked.contentHash()).isEqualTo("753ee3db31d1be83f0242e6c4ebcdd3e15f9de5422a98c1d15ccf1807f30de87");
        EmoticonImageValidator.Checked cover = validator.check(TestImages.robotikon("robotikon-f/cover.png"), EmoticonImageKind.COVER, true, "cover");
        assertThat(cover.format()).isEqualTo(EmoticonImageFormat.PNG);
        assertThat(cover.frames()).isEqualTo(1);
    }

    @Test
    void 형식은_바이트로_가른다_GIF_PNG_가_아니면_TYPE_대표가_PNG_가_아니면_COVER_TYPE() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 0x10, 'J', 'F', 'I', 'F'};
        assertThat(reason(jpeg, EmoticonImageKind.EMOTICON, true)).isEqualTo("TYPE");
        assertThat(reason("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(), EmoticonImageKind.EMOTICON, true)).isEqualTo("TYPE");
        assertThat(reason(new byte[0], EmoticonImageKind.EMOTICON, true)).isEqualTo("TYPE");
        assertThat(reason(TestImages.animated("c"), EmoticonImageKind.COVER, true)).isEqualTo("COVER_TYPE");
        // 머리만 GIF 인 깨진 파일 — 끝 표식이 없다
        byte[] cut = Arrays.copyOf(TestImages.animated("cut"), 40);
        assertThat(reason(cut, EmoticonImageKind.EMOTICON, true)).isEqualTo("TYPE");
    }

    @Test
    void 크기는_정확히_200x200() {
        assertThat(reason(TestImages.gif().size(220, 220).bytes(), EmoticonImageKind.EMOTICON, true)).isEqualTo("DIMENSION");
        assertThat(reason(TestImages.png(199, 200, 0x123456, 0), EmoticonImageKind.EMOTICON, true)).isEqualTo("DIMENSION");
        assertThat(reason(TestImages.png(220, 220, 0x123456, 0), EmoticonImageKind.COVER, true)).isEqualTo("DIMENSION");
        assertThat(reason(TestImages.png(200, 200, 0x123456, 0), EmoticonImageKind.EMOTICON, true)).isNull();
    }

    @Test
    void 용량은_한_개_2MB_대표_1MB_권장_300KB_는_받고_표시만() {
        assertThat(reason(TestImages.gif().trailing(2 * 1024 * 1024).bytes(), EmoticonImageKind.EMOTICON, true)).isEqualTo("TOO_LARGE");
        assertThat(reason(TestImages.png(200, 200, 0x1, 1024 * 1024), EmoticonImageKind.COVER, true)).isEqualTo("TOO_LARGE");
        EmoticonImageValidator.Checked heavy = validator.check(TestImages.gif().padding(400 * 1024).bytes(), EmoticonImageKind.EMOTICON, true, "file");
        assertThat(heavy.heavy()).isTrue();
        assertThat(validator.check(TestImages.animated("light"), EmoticonImageKind.EMOTICON, true, "file").heavy()).isFalse();
    }

    @Test
    void GIF_는_24프레임까지_무한_반복은_작가만_막고_운영자는_받아_둔다() {
        assertThat(reason(TestImages.gif().frames(24).bytes(), EmoticonImageKind.EMOTICON, true)).isNull();
        assertThat(reason(TestImages.gif().frames(25).bytes(), EmoticonImageKind.EMOTICON, true)).isEqualTo("TOO_MANY_FRAMES");
        byte[] once = TestImages.gif().frames(3).loop(null).bytes();
        assertThat(EmoticonImageInspector.inspect(once).loopForever()).isFalse();
        assertThat(reason(once, EmoticonImageKind.EMOTICON, true)).isEqualTo("NOT_LOOP");
        EmoticonImageValidator.Checked operator = validator.check(once, EmoticonImageKind.EMOTICON, false, "file");
        assertThat(operator.loopForever()).isFalse();
        // 반복 횟수가 정해진(0 이 아닌) GIF 도 무한 반복이 아니다 · 한 프레임 GIF 는 반복을 보지 않는다
        assertThat(reason(TestImages.gif().frames(2).loop(3).bytes(), EmoticonImageKind.EMOTICON, true)).isEqualTo("NOT_LOOP");
        assertThat(reason(TestImages.gif().frames(1).loop(null).bytes(), EmoticonImageKind.EMOTICON, true)).isNull();
    }

    @Test
    void 차례는_형식_크기_용량_프레임_반복_첫_문제가_이유다() {
        // 220×220 + 25프레임 + 한 번만 재생 — 크기가 먼저
        assertThat(reason(TestImages.gif().size(220, 220).frames(25).loop(null).bytes(), EmoticonImageKind.EMOTICON, true)).isEqualTo("DIMENSION");
        // 2MB 넘음 + 25프레임 — 용량이 먼저
        assertThat(reason(TestImages.gif().frames(25).trailing(2 * 1024 * 1024).bytes(), EmoticonImageKind.EMOTICON, true)).isEqualTo("TOO_LARGE");
        // 25프레임 + 한 번만 재생 — 프레임이 먼저
        assertThat(reason(TestImages.gif().frames(25).loop(null).bytes(), EmoticonImageKind.EMOTICON, true)).isEqualTo("TOO_MANY_FRAMES");
    }

    @Test
    void 압축_폭탄_논리_화면_밖으로_크게_적은_프레임은_디코딩하기_전에_TYPE() {
        byte[] bomb = TestImages.gif().frame(60_000, 60_000).bytes();
        assertThat(EmoticonImageInspector.inspect(bomb)).isNull();
        assertThat(reason(bomb, EmoticonImageKind.EMOTICON, true)).isEqualTo("TYPE");
    }

    @Test
    void 저장할_바이트_GIF_는_끝_표식_뒤를_떼고_PNG_는_다시_인코딩한다_지문은_저장할_바이트의_것() {
        byte[] clean = TestImages.animated("trail");
        byte[] trailing = Arrays.copyOf(clean, clean.length + 500);
        EmoticonImageValidator.Checked gif = validator.check(trailing, EmoticonImageKind.EMOTICON, true, "file");
        assertThat(gif.data()).isEqualTo(clean);
        assertThat(gif.contentHash()).isEqualTo(EmoticonImageValidator.sha256(clean));
        byte[] png = TestImages.png(200, 200, 0xabcdef, 300);
        EmoticonImageValidator.Checked cover = validator.check(png, EmoticonImageKind.COVER, true, "cover");
        assertThat(EmoticonImageInspector.inspect(cover.data()).format()).isEqualTo(EmoticonImageFormat.PNG);
        assertThat(cover.data().length).isLessThan(png.length); // 뒤에 붙은 300바이트가 없다
        assertThat(cover.contentHash()).isEqualTo(EmoticonImageValidator.sha256(cover.data()));
    }

    @Test
    void 오류_칸은_부르는_쪽이_정한다_대표는_cover() {
        assertThatThrownBy(() -> validator.check(TestImages.animated("x"), EmoticonImageKind.COVER, false, "cover")).isInstanceOfSatisfying(EmoticonImageInvalidException.class,
                e -> {
                    assertThat(e.getFields()).containsOnlyKeys("cover");
                    assertThat(e.reason()).isEqualTo("COVER_TYPE");
                });
    }
}
