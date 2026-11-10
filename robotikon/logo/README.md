# 사이트 로고 생성

`docs/features/site-logo-variants-contract.md` 의 기본 로고 4장(한글·영문 × 밝은·어두운 바탕용)을 만든다.

```
npm install opentype.js@1.3.4 @fontsource/gaegu
STROKE=0.5 SUB=16 node build.mjs out
node render.mjs   # out/*.svg 를 preview.png 로 확인(../node_modules 의 @resvg/resvg-js 사용)
```

결과 `out/roboyam-logo-{ko,en}-{light,dark}.svg` 를 `apps/{admin,user}-web/public/brand/` 에 복사한다.
