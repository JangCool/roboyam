/**
 * 한국어 문구를 업무별 파일에서 모은다. **업무 파일을 늘리면 여기에 한 줄 더한다**(`import.meta.glob` 은 쓰지 않는다 — 서버 번들에서 500).
 * ⚠ **`en/index.ts` 와 같은 목록이어야 한다.** 한쪽에만 있는 업무는 그 언어에서 한국어(폴백)로 보인다.
 */
import richEditor from './richEditor';

export default {
    richEditor,
};
