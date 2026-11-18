// 기념일 로고 전부 만들기 — 날마다 캐릭터 로고(build.mjs)와 글자 로고(build-wordmark.mjs)를 돌리고 바이트를 줄인다(slim.mjs).
// 쓰기: node build-all.mjs <출력 폴더>   → <출력 폴더>/<날>/irobit-{logo,wordmark}-<날>-{ko,en}-{light,dark}.svg
// 날 목록은 mascot.mjs 의 THEME_NAMES 가 원천이다(새 날을 더하면 여기도 따라온다).
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { THEME_NAMES } from './mascot.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const out = path.resolve(process.argv[2] ?? 'out-days')
const run = (script, args, env) => {
  const r = spawnSync(process.execPath, [path.join(here, script), ...args], { env: { ...process.env, ...env }, encoding: 'utf8' })
  if (r.status !== 0) throw new Error(`${script} ${env?.THEME ?? ''} 실패\n${r.stderr}`)
}
let count = 0, bytes = 0
for (const theme of Object.keys(THEME_NAMES)) {
  const dir = path.join(out, theme)
  const env = { THEME: theme, STROKE: process.env.STROKE ?? '0.5', SUB: process.env.SUB ?? '16' }
  run('build.mjs', [dir], env)
  run('build-wordmark.mjs', [dir], env)
  const files = fs.readdirSync(dir).filter((f) => f.endsWith('.svg')).map((f) => path.join(dir, f))
  run('slim.mjs', files, {})
  for (const f of files) { count++; bytes += fs.statSync(f).size }
  console.log(theme.padEnd(12), THEME_NAMES[theme])
}
console.log(`\n${Object.keys(THEME_NAMES).length}개 날 · 파일 ${count}개 · 합계 ${(bytes / 1024).toFixed(0)}KB`)
