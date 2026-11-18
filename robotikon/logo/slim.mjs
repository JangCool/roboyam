// 로고 SVG 바이트 줄이기 — svgo 로 패스 좌표를 상대값·소수 한 자리로 줄이고 쓸모없는 속성을 뺀다.
// <view id="wordmark|symbol"> 는 바깥에서 주소(#symbol)로 쓰므로 id 를 지우지 않는다.
// 쓰기: node slim.mjs <svg 파일…>  (그 자리에서 고쳐 쓴다)
import fs from 'node:fs'
import { optimize } from 'svgo'

const config = {
  multipass: true,
  floatPrecision: 1,
  plugins: [
    {
      name: 'preset-default',
      params: {
        overrides: {
          cleanupIds: false,
          // 심볼의 scale(0.26) 같은 변환 값은 소수 한 자리로 깎이면 그림이 틀어진다
          convertTransform: false,
          cleanupNumericValues: { floatPrecision: 3 },
        },
      },
    },
  ],
}

for (const file of process.argv.slice(2)) {
  const before = fs.readFileSync(file, 'utf8')
  const after = optimize(before, { ...config, path: file }).data + '\n'
  fs.writeFileSync(file, after)
  console.log(file, before.length, '→', after.length)
}
