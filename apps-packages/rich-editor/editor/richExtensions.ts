import StarterKit from '@tiptap/starter-kit';
import { BackgroundColor, Color, TextStyle } from '@tiptap/extension-text-style';
import type { Extensions } from '@tiptap/vue-3';
import type { RichEditorLevel } from '#layers/rich-editor/components/RichEditor.types';

/**
 * 수준별 **서식 확장 한 벌** — community `layers/rich-editor/app/editor/richExtensions.ts` 를 로보얌 계약(공개 게시판 「에디터 화면」)에 맞게 줄였다.
 * 서버 정화가 허용하는 것만 만든다 — 확장을 켜지 않은 서식은 붙여넣어도 문서에 들어오지 않는다(그래서 서버에서 사라질 것이 에디터에 보이지 않는다).
 *  - `SIMPLE`: `p br strong em u s a ul ol li blockquote`
 *  - `ADVANCED`: SIMPLE + `h2 h3 hr code pre` + 팔레트 색 `span[style="color|background-color"]`(이미지·유튜브·올리기 자리 노드는 부르는 쪽이 이어 붙인다)
 * community 에 있던 글꼴·크기·줄 간격·정렬·형광펜·첨자·작업 목록·표는 계약에 없어 켜지 않는다.
 *
 * `editor/` 폴더 — TipTap 확장은 `@tiptap/vue-3`(→ `vue`)를 쓰므로 레이어의 순수 코어(`utils/`)에 두지 않는다. 명시 import(`#layers/rich-editor/editor/…`)로만 쓴다.
 * @param {RichEditorLevel} level - 에디터 수준.
 * @returns {Extensions} 서식 확장 목록(StarterKit 먼저).
 */
export function richFormatExtensions(level: RichEditorLevel): Extensions {
    const advanced = level === 'ADVANCED';
    const starter = StarterKit.configure({
        heading: advanced ? { levels: [2, 3] } : false,
        code: advanced ? {} : false,
        codeBlock: advanced ? {} : false,
        horizontalRule: advanced ? {} : false,
        link: {
            openOnClick: false,
            autolink: true,
            protocols: ['http', 'https', 'mailto'],
            HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: '_blank' },
        },
    });
    return advanced ? [starter, TextStyle, Color, BackgroundColor] : [starter];
}
