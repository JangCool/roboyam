# 사이트 로고 생성

`docs/features/site-logo-variants-contract.md`·`site-logo-library-contract.md` 의 로고를 만든다. 한 벌은 4장(한글·영문 × 밝은·어두운 바탕용)이다.

- 캐릭터 로고 `irobit-logo-*.svg`: 마스코트 + 개구체 글자(`build.mjs`)
- 글자 로고 `irobit-wordmark-*.svg`: 마스코트 없이 Gothic A1 800 + Space Grotesk 700, i 의 점은 네모(`build-wordmark.mjs`)
- 기념일 로고: 위 두 벌에 `THEME=<날>` 을 주면 그날의 그림과 이름·날짜가 붙는다. 그림·이름·날짜의 원천은 `mascot.mjs`

```
npm install opentype.js@1.3.4 @fontsource/gaegu @fontsource/gothic-a1 @fontsource/space-grotesk svgo
STROKE=0.5 SUB=16 node build.mjs out      # 평소 캐릭터 로고
node build-wordmark.mjs out               # 평소 글자 로고
node slim.mjs out/*.svg                   # 바이트 줄이기(그 자리에서 고쳐 씀)
node build-all.mjs out-days               # 기념일 전부(날마다 두 벌, slim 까지)
node render.mjs                           # out/*.svg 를 preview.png 로 확인(../node_modules 의 @resvg/resvg-js 사용)
```

글꼴 폴더가 다른 곳에 있으면 `LOGO_MODULES=<node_modules 경로>` 로 알려 준다.

## 넣는 곳
- 평소 두 벌: `apps/{admin,user}-web/public/brand/`, 서버 기본 제공 묶음은 shared 마이그레이션(V249)에 실려 있다 — 그림을 바꾸면 새 마이그레이션으로 고친다.
- 기념일 확정본: `design/brand/holiday-logos/<날>/`.

## 다시 뽑을 일이 드문 재료(결과는 `mascot.mjs` 에 상수로 들어 있다)
- `korea-map.mjs`: 한반도 윤곽(Natural Earth 1:10m). `npm install world-atlas@2 topojson-client@3`
- `brush-text.mjs`: 붓글씨 글자를 패스로(나눔손글씨 붓). `npm install @fontsource/nanum-brush-script`

## 그릴 때 지킬 것
- 해에서 사방으로 뻗는 빛살 무늬, 붉은 동그라미만 있는 표현은 쓰지 않는다.
- 태극기는 건·감·리·곤을 제대로, 깃면 안에 그린다.
- 올린 SVG 는 서버가 검사하므로 필터(흐림) 같은 요소는 쓰지 않는다.
- 실제 헤더 크기(높이 28~32px)에서 어떻게 보이는지 꼭 확인한다 — 크게 볼 때만 보이는 디테일이 많다.
