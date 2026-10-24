# 연계 사이트 로고 출처

확인·수집일: 2026-10-23

연계 사이트를 식별하고 공식 홈페이지로 연결하기 위한 로고입니다. 아래 파일은 각 기관·기업이 공식 웹사이트에서 제공하는 원본을 그대로 저장했습니다. 색상 변경, 배경 제거, 재생성, 비율 변형은 하지 않았습니다. 투명 배경은 원본에 포함되어 있습니다.

| 로컬 파일 | 기관·기업 | 원본 이미지 | 사용 위치 확인 |
| --- | --- | --- | --- |
| `its.png` | ITS 국가교통정보센터 | [공식 PNG](https://www.its.go.kr/css/images/common/logo.png) | [공식 홈페이지](https://www.its.go.kr/)의 헤더. [공식 CSS](https://www.its.go.kr/css/design/common.css)의 `#header .logo a`에서 참조합니다. 한국지능형교통체계협회(ITS Korea)의 로고와 구별했습니다. |
| `kma.png` | 기상청 | [공식 PNG](https://www.weather.go.kr/w/resources/images/logo_kma.png) | [공식 날씨누리](https://www.weather.go.kr/w/index.do)의 푸터 기관 표기. [공식 CSS](https://www.weather.go.kr/w/resources/css/styles.css)의 `#logo-kma`에서 참조합니다. |
| `straffic.svg` | 에스트래픽 STraffic | [공식 SVG](https://www.straffic.co.kr/rsc/en/images/main/logo.svg) | [공식 홈페이지](https://www.straffic.co.kr/en)의 헤더·푸터 `<img>`에서 참조합니다. 공식 흰색 로고 원본을 유지했습니다. |

## 화면 표시

- ITS와 기상청은 원래 로고의 색을 보존하고 밝은 중립색 받침 위에 표시합니다.
- 에스트래픽은 공식 흰색 로고가 보이도록 남색 받침을 사용합니다. 받침은 CSS이며 로고 이미지에 합성하지 않았습니다.
- `object-fit: contain`으로 원본 비율을 유지합니다. ITS의 155px 원본은 불필요하게 확대하지 않습니다.
- 외부 링크 주소, 새 창 열기, 안내 문구는 기존과 같습니다.
- 표시는 공식 사이트 연결 안내 목적이며, ROUTEL과의 제휴·보증 관계를 뜻하지 않습니다. 로고 권리는 각 기관·기업에 있습니다.

## 검증

`node tests/home-partners-polish.mjs`에서 320 / 390 / 768 / 1440px, 라이트·다크 테마를 확인합니다. 로고 로딩, 투명 알파 픽셀과 실제 도형 픽셀, 컨테이너 내부 표시, 공식 링크 유지, 버튼 잘림·가로 넘침을 검사합니다.
