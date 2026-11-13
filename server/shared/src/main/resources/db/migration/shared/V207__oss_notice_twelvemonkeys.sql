-- 오픈소스 라이선스 고지에 TwelveMonkeys ImageIO 를 더한다(docs/features/list-thumbnail-contract.md 「결정」 1 — 사용자 2026-11-13, oss-licenses-contract V181).
-- 서버 라이브러리는 자동 수집(scripts/collect-licenses.mjs)이 기본으로 싣지 않으므로(「고지 범위 줄이기」 2026-11-10 — --with-server 일 때만) 직접 추가 항목
-- (oss_notice_entries, 묶음 SERVER)으로 넣는다. 공개 /licenses 의 「서버」 칩은 이 항목이 있어 보인다. 운영자가 관리 화면에서 고치거나 숨기거나 지울 수 있다.
-- 같은 이름의 항목을 운영자가 이미 넣었으면 넣지 않는다. id 는 앱 Snowflake 와 같은 모양(서버 번호 1023 — V168·V183 과 같은 방식).
-- 라이선스 전문은 원본 저장소의 LICENSE.txt(BSD 3-Clause, 저작권 Harald Kuhr — jar 에는 라이선스 파일이 없고 POM 은 「The BSD License」와 이 주소를 가리킨다).
-- imageio-webp 가 끌어오는 imageio-core·imageio-metadata·common-lang·common-io·common-image 도 같은 프로젝트·같은 라이선스라 한 줄로 묶는다. 버전은 공개 API 에 나가지 않는다(관리자만 본다).
INSERT INTO oss_notice_entries (id, name, version, license, copyright, license_text, source_url, category, hidden, created_at, updated_at)
SELECT ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12) | 207,
       'TwelveMonkeys ImageIO',
       '3.15.3',
       'BSD-3-Clause',
       'Copyright (c) 2008-2020, Harald Kuhr' || chr(10) || 'All rights reserved.',
       'BSD 3-Clause License' || chr(10) || chr(10) ||
       'Copyright (c) 2008-2020, Harald Kuhr' || chr(10) ||
       'All rights reserved.' || chr(10) || chr(10) ||
       'Redistribution and use in source and binary forms, with or without' || chr(10) ||
       'modification, are permitted provided that the following conditions are met:' || chr(10) || chr(10) ||
       '* Redistributions of source code must retain the above copyright notice, this' || chr(10) ||
       '  list of conditions and the following disclaimer.' || chr(10) || chr(10) ||
       '* Redistributions in binary form must reproduce the above copyright notice,' || chr(10) ||
       '  this list of conditions and the following disclaimer in the documentation' || chr(10) ||
       '  and/or other materials provided with the distribution.' || chr(10) || chr(10) ||
       '* Neither the name of the copyright holder nor the names of its' || chr(10) ||
       '  contributors may be used to endorse or promote products derived from' || chr(10) ||
       '  this software without specific prior written permission.' || chr(10) || chr(10) ||
       'THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"' || chr(10) ||
       'AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE' || chr(10) ||
       'IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE' || chr(10) ||
       'DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE' || chr(10) ||
       'FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL' || chr(10) ||
       'DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR' || chr(10) ||
       'SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER' || chr(10) ||
       'CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,' || chr(10) ||
       'OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE' || chr(10) ||
       'OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.',
       'https://github.com/haraldk/TwelveMonkeys',
       'SERVER',
       false,
       now(),
       now()
 WHERE NOT EXISTS (SELECT 1 FROM oss_notice_entries WHERE lower(name) IN ('twelvemonkeys imageio', 'twelvemonkeys', 'imageio-webp'));
