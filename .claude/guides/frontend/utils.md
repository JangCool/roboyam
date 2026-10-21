# Frontend Utils Guide (`utils.md`)

## 1. 역할 및 기본 원칙
* `utils/` 에는 상태(`useState`)·서버 요청(`$fetch`)·Nuxt 컴포저블이 없는 **순수 함수**만 둔다.
* 파일마다 클래스 하나를 `export default` 한다.

---

## 2. 주요 업무 및 규약
* 파일명은 `util-<이름>.ts`(kebab-case), 클래스는 `Util<이름>`.
* 메서드는 `static` 이고 `UtilClass.method()` 로 호출한다. 클래스 안에서 다른 정적 메서드를 부를 때도 `this` 가 아니라 클래스명을 쓴다.
* **Util 클래스는 항상 명시적 default import 한다**(`import UtilText from '~/utils/util-text'`, 레이어는 `#layers/<이름>/utils/util-xxx`). 자동 임포트에 기대면 런타임에서 깨진다.
* 화면 문구가 필요한 유틸은 `util-<업무>-messages.ts` 로 두되, 계산이 낀 문구만 두고 `t` 를 첫 인자로 받는다. 상수 문구는 i18n 키로 쓴다 → `i18n.md`.
* 한 유틸만 쓰는 타입은 그 파일 안에 둔다 → `composables.md` 타입 절.

---

## 3. 작성 예시
```ts
import UtilText from '~/utils/util-text'   // 사용하는 쪽: 명시적 default import

export default class UtilText {
    static isEmpty(value: string | null | undefined): boolean {
        return value == null || value === '';
    }
    static isNotEmpty(value: string | null | undefined): boolean {
        return !UtilText.isEmpty(value);   // this.isEmpty 금지
    }
}
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ `utils/` 안에서 `useState`·`useRoute`·`$fetch` 등 Nuxt 컴포저블·서버 요청 사용
2. ❌ 정적 메서드 간 호출에 `this` 사용
3. ❌ 한 파일에 클래스를 둘 이상 `export`
4. ❌ 자동 임포트에 기대고 Util 클래스를 import 하지 않기
