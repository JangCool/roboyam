/**
 * 패키지에 라이선스 파일이 없을 때 대신 싣는 SPDX 표준 문구(저작권자 줄은 빼고 — 저작권 고지는 항목의 `copyright` 칸에 따로 싣는다).
 * Apache-2.0 처럼 저작권자 자리가 없는 긴 문구는 여기 두지 않고, 수집한 다른 패키지의 전문을 그대로 빌려 쓴다(`collect-licenses.mjs` 의 `standardText`).
 * 여기 없는 라이선스는 문구 없이 SPDX 안내 주소(`https://spdx.org/licenses/<id>.html`)만 싣는다.
 */
export const TEMPLATES = {
    MIT: `MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.`,
    ISC: `ISC License

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.`,
    '0BSD': `Zero-Clause BSD

Permission to use, copy, modify, and/or distribute this software for
any purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL
WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE
FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY
DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN
AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT
OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.`,
    'BSD-2-Clause': `BSD 2-Clause License

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.`,
    'BSD-3-Clause': `BSD 3-Clause License

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.`,
};

/**
 * Maven POM 의 라이선스 이름(자유 글) → SPDX 식별자. 비교 전에 소문자로 바꾸고 쉼표·줄표를 빼고 「version 2.0」「v. 2.0」「v 2.0」을 「v2.0」으로 맞춘다.
 * 맞는 것이 없으면 이름을 그대로 쓴다(지어내지 않는다).
 */
const POM_NAMES = [
    [/^(the )?apache( software)? licen[cs]e v?2(\.0)?$/, 'Apache-2.0'],
    [/^apache v?2(\.0)?$/, 'Apache-2.0'],
    [/^asl v?2(\.0)?$/, 'Apache-2.0'],
    [/^(the )?mit( licen[cs]e)?$/, 'MIT'],
    [/^mit 0$/, 'MIT-0'],
    [/^bouncy castle licen[cs]e$/, 'MIT'],
    [/^(the )?(new )?bsd( licen[cs]e)?$/, 'BSD-3-Clause'],
    [/^(the )?bsd 3 clause( licen[cs]e)?$/, 'BSD-3-Clause'],
    [/^bsd licen[cs]e 3$/, 'BSD-3-Clause'],
    [/^(the )?bsd 2 clause( licen[cs]e)?$/, 'BSD-2-Clause'],
    [/^eclipse distribution licen[cs]e v1\.0$/, 'BSD-3-Clause'],
    [/^edl v?1\.0$/, 'BSD-3-Clause'],
    [/^eclipse public licen[cs]e v2\.0$/, 'EPL-2.0'],
    [/^epl v?2\.0$/, 'EPL-2.0'],
    [/^eclipse public licen[cs]e v1\.0$/, 'EPL-1.0'],
    [/^gnu general public licen[cs]e v2 with the (gnu )?classpath exception$/, 'GPL-2.0-with-classpath-exception'],
    [/^gpl2 w\/ cpe$/, 'GPL-2.0-with-classpath-exception'],
    [/^gnu lesser general public licen[cs]e( v2\.1)?$/, 'LGPL-2.1'],
    [/^(cddl|common development and distribution licen[cs]e)( v?1\.[01])?$/, 'CDDL-1.0'],
    [/^public domain per creative commons cc0$/, 'CC0-1.0'],
    [/^cc0( v?1\.0)?$/, 'CC0-1.0'],
    [/^universal permissive licen[cs]e v1\.0$/, 'UPL-1.0'],
    [/^the postgresql licen[cs]e$/, 'PostgreSQL'],
];

function pomKey(raw) {
    return raw
        .toLowerCase()
        .replace(/[,\-]/g, ' ')
        .replace(/\b(version|ver\.?|v\.?)\s*(\d)/g, 'v$2')
        .replace(/\s+/g, ' ')
        .trim();
}

/** POM 라이선스 이름 → SPDX(모르면 원문). 이미 SPDX 꼴(Apache-2.0 · EPL-2.0 · LGPL-2.1-only 등 — 공백 없이 숫자를 품은 이름)이면 그대로 */
export function spdxFromPom(name) {
    const raw = (name ?? '').replace(/\s+/g, ' ').trim();
    if (!raw) return null;
    if (/^[A-Za-z0-9.+\-]+$/.test(raw) && /\d/.test(raw)) return raw;
    const key = pomKey(raw);
    for (const [pattern, id] of POM_NAMES) {
        if (pattern.test(key)) return id;
    }
    return raw;
}

/** 흔한 허용형(고지만 하면 되는) 라이선스 — 이 밖의 것은 보고에 「검토 권장」으로 따로 적는다 */
export const PERMISSIVE = new Set(['MIT', 'MIT-0', 'ISC', '0BSD', 'BSD-2-Clause', 'BSD-3-Clause', 'Apache-2.0', 'CC0-1.0', 'Unlicense', 'UPL-1.0', 'PostgreSQL', 'BlueOak-1.0.0', 'Python-2.0', 'Zlib']);

/** SPDX 식 `(A OR B)` 의 바깥 괄호를 벗긴다 */
export function stripParens(license) {
    const v = (license ?? '').trim();
    return v.startsWith('(') && v.endsWith(')') && !v.slice(1, -1).includes('(') ? v.slice(1, -1).trim() : v;
}

/** 허용형 라이선스만으로 고를 수 있는가(`A OR B` 중 하나라도 허용형이면 참, `AND` 는 모두) */
export function permissive(license) {
    const v = stripParens(license);
    if (/ AND /.test(v)) return v.split(/ AND /).every((part) => permissive(part));
    return v.split(/ OR /).some((part) => PERMISSIVE.has(part.trim()));
}

/**
 * 조건이 까다로운(복제 의무·소스 공개 등) 라이선스인가 — 경고로 따로 출력한다(빼거나 바꾸지는 않는다).
 * 여러 라이선스 중 고를 수 있는 것(`A OR B`)도 하나라도 걸리면 알린다(고를 수 있다고 함께 적는다).
 */
export function restrictive(license) {
    return /\b(A?GPL|LGPL|MPL|EPL|CDDL|EUPL|SSPL|OSL|CC-BY-SA|CPAL|RPL)/i.test(license ?? '');
}
