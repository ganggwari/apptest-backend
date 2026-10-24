import { noticeCategories, faqCategories } from '../services/backendCatalog'
export type Field = {
  key: string
  label: string
  type?:
    | 'text'
    | 'number'
    | 'textarea'
    | 'email'
    | 'date'
    | 'checkbox'
    | 'select'
    | 'multi'
    | 'lines'
  required?: boolean
  options?: [string, string][]
  source?: 'regions' | 'underpasses' | 'shifts' | 'cameras' | 'presets' | 'reasons'
}
export type Action = {
  label: string
  path: string
  method?: 'POST' | 'PUT' | 'DELETE' | 'GET'
  fields?: Field[]
  pdf?: boolean
  key?: string
}
export type Resource = {
  title: string
  path: string
  columns: [string, string][]
  filters?: Field[]
  parents?: Field[]
  fields?: Field[]
  detail?: boolean
  detailFromRow?: boolean
  create?: boolean
  edit?: boolean
  remove?: boolean
  actions?: Action[]
  public?: boolean
  owner?: boolean
  rowKey?: string
}
const text = (key: string, label: string, required = false): Field => ({ key, label, required })
const num = (key: string, label: string, required = false): Field => ({
  key,
  label,
  type: 'number',
  required,
})
const area = (key: string, label: string, required = false): Field => ({
  key,
  label,
  type: 'textarea',
  required,
})
const source = (key: string, label: string, value: Field['source'], required = false): Field => ({
  key,
  label,
  type: 'select',
  source: value,
  required,
})
const select = (key: string, label: string, options: [string, string][]): Field => ({
  key,
  label,
  type: 'select',
  options,
})
const region = source('regionId', '담당 지역', 'regions'),
  facility = source('underpassId', '지하차도', 'underpasses')
const period: Field[] = [
  { key: 'startDate', label: '시작일', type: 'date' },
  { key: 'endDate', label: '종료일', type: 'date' },
]
const risk: [string, string][] = [
  ['SAFE', '안전'],
  ['WARNING', '주의'],
  ['DANGER', '위험'],
]
const profileFields: Field[] = [
  text('name', '이름', true),
  text('phone', '연락처', true),
  { key: 'email', label: '이메일', type: 'email' },
  text('emergency', '비상 연락처'),
  text('zipcode', '우편번호'),
  text('roadAddress', '도로명 주소'),
  text('detailAddress', '상세 주소'),
]
const facilityFields: Field[] = [
  text('name', '시설명', true),
  source('regionId', '지역', 'regions', true),
  text('roadAddress', '도로명 주소', true),
  num('latitude', '위도', true),
  num('longitude', '경도', true),
  num('heightM', '높이(m)'),
  num('lengthM', '길이(m)'),
  num('widthM', '폭(m)'),
  num('lanes', '차로수'),
  select('safetyGrade', '안전등급', [
    ['A', 'A'],
    ['B', 'B'],
    ['C', 'C'],
    ['D', 'D'],
    ['E', 'E'],
    ['UNKNOWN', '미실시·불명'],
  ]),
  num('riverDistM', '하천 거리(m)'),
  num('seaDistM', '바다 거리(m)'),
  { key: 'earlyBlock', label: '선제 차단 대상', type: 'checkbox' },
  num('floodHistoryCount', '과거 침수 횟수'),
]
const cameraFields: Field[] = [
  select('cameraRole', '카메라 역할', [
    ['APPROACH_LPR', '접근 번호판 인식'],
    ['FINAL_LPR', '최종 번호판 인식'],
    ['CCTV', '일반 CCTV'],
  ]),
  text('streamUrl', '영상 주소', true),
  num('distanceM', '거리(m)'),
  num('latitude', '위도'),
  num('longitude', '경도'),
]
export const backendResources: Record<string, Resource> = {
  facilities: {
    title: '지하차도 관리',
    path: '/api/underpasses',
    public: true,
    columns: [
      ['name', '시설명'],
      ['regionName', '지역'],
      ['currentStatus', '통제 상태'],
      ['currentRiskLevel', '위험 단계'],
      ['riskScore', '위험 점수'],
      ['remainingCount', '잔류 차량'],
      ['pendingReviewCount', '판별 대기'],
    ],
    filters: [
      region,
      text('keyword', '시설명 검색'),
      select('status', '통제 상태', [
        ['NORMAL', '정상'],
        ['BLOCKED', '통제'],
      ]),
      select('sort', '정렬', [
        ['NAME', '이름순'],
        ['RISK_SCORE', '위험 점수순'],
        ['LATEST', '최근 등록순'],
      ]),
    ],
    fields: facilityFields,
    detail: true,
    create: true,
    edit: true,
    remove: true,
    actions: [
      {
        label: '통제 및 경찰 이관',
        path: '/api/control/underpasses/{id}/block',
        method: 'POST',
        fields: [source('presetId', '통제 사유 선택', 'reasons'), area('reason', '통제 사유')],
        pdf: true,
      },
      {
        label: '통제 해제 및 경찰 이관',
        path: '/api/control/underpasses/{id}/release',
        method: 'POST',
        fields: [source('presetId', '해제 사유 선택', 'reasons'), area('reason', '해제 사유')],
        pdf: true,
      },
      {
        label: '소방 이관',
        path: '/api/control/underpasses/{id}/fire-transfer',
        method: 'POST',
        fields: [
          num('eventId', '통제 이벤트 번호', true),
          { key: 'carNums', label: '잔류 차량 번호 (한 줄에 하나)', type: 'lines', required: true },
          area('memo', '전달 내용'),
        ],
        pdf: true,
      },
    ],
  },
  cameras: {
    title: '카메라 관리',
    path: '/api/underpasses/{underpassId}/cameras',
    parents: [{ ...facility, required: true }],
    columns: [
      ['id', '카메라 번호'],
      ['cameraRole', '역할'],
      ['streamUrl', '영상 주소'],
      ['distanceM', '거리(m)'],
    ],
    fields: cameraFields,
    create: true,
    edit: true,
    remove: true,
    detail: true,
  },
  thresholds: {
    title: '위험도 임계치 설정',
    path: '/api/underpasses/{underpassId}/thresholds',
    parents: [{ ...facility, required: true }],
    columns: [
      ['level', '단계'],
      ['riskScoreMin', '최소 위험 점수'],
      ['updatedAt', '수정 시각'],
    ],
    fields: [num('riskScoreMin', '최소 위험 점수', true)],
    edit: true,
    rowKey: 'level',
  },
  transits: {
    title: '차량 통과·잔류 기록',
    path: '/api/underpasses/transits',
    columns: [
      ['underpassName', '지하차도'],
      ['carNum', '차량 번호'],
      ['status', '상태'],
      ['entryAt', '진입 시각'],
      ['exitAt', '통과 시각'],
    ],
    filters: [
      region,
      facility,
      text('carNum', '차량 번호'),
      select('status', '상태', [
        ['REMAINING', '잔류'],
        ['EXITED', '정상 통과'],
        ['IN_TRANSIT', '진입 중'],
      ]),
      ...period,
    ],
    actions: [
      { label: '통과 완료 처리', path: '/api/underpasses/transits/{id}/complete', method: 'POST' },
    ],
  },
  events: {
    title: '시설 제어 이력',
    path: '/api/underpasses/{underpassId}/events',
    parents: [{ ...facility, required: true }],
    columns: [
      ['underpassName', '시설'],
      ['kind', '유형'],
      ['eventType', '이벤트'],
      ['reason', '사유'],
      ['displayContent', '전광판 문구'],
      ['createdAt', '발생 시각'],
    ],
    filters: [text('kind', '이력 종류'), ...period],
  },
  ocr: {
    title: 'OCR 인식 기록',
    path: '/api/plate-logs',
    columns: [
      ['underpassName', '시설'],
      ['detectedNum', '인식 번호'],
      ['imagePath', '인식 이미지'],
      ['correctedNum', '정정 번호'],
      ['confidence', '신뢰도'],
      ['recognitionStatus', '인식 상태'],
      ['reviewStatus', '판별 상태'],
      ['reviewDeadline', '판별 마감'],
      ['detectedAt', '인식 시각'],
    ],
    filters: [
      region,
      facility,
      text('carNum', '차량 번호'),
      select('reviewStatus', '판별 상태', [
        ['PENDING', '대기'],
        ['CONFIRMED', '확정'],
        ['AUTO_PROCESSED', '자동 처리'],
        ['NONE', '대상 아님'],
      ]),
      select('recognitionStatus', '인식 상태', [
        ['SUCCESS', '성공'],
        ['LOW_CONFIDENCE', '낮은 신뢰도'],
        ['FAIL', '실패'],
        ['UNMATCHED', '미매칭'],
      ]),
      ...period,
    ],
    detail: true,
    detailFromRow: true,
  },
  reviews: {
    title: '번호판 판별',
    path: '/api/plate-logs',
    columns: [
      ['underpassName', '시설'],
      ['detectedNum', '인식 번호'],
      ['imagePath', '인식 이미지'],
      ['correctedNum', '정정 번호'],
      ['confidence', '신뢰도'],
      ['recognitionStatus', '인식 상태'],
      ['reviewStatus', '판별 상태'],
      ['reviewDeadline', '판별 마감'],
      ['detectedAt', '인식 시각'],
    ],
    filters: [
      region,
      facility,
      text('carNum', '차량 번호'),
      select('reviewStatus', '판별 상태', [
        ['PENDING', '대기'],
        ['CONFIRMED', '확정'],
        ['AUTO_PROCESSED', '자동 처리'],
        ['NONE', '대상 아님'],
      ]),
      select('recognitionStatus', '인식 상태', [
        ['SUCCESS', '성공'],
        ['LOW_CONFIDENCE', '낮은 신뢰도'],
        ['FAIL', '실패'],
        ['UNMATCHED', '미매칭'],
      ]),
      ...period,
    ],
    actions: [
      {
        label: '번호판 정정·확정',
        path: '/api/plate-logs/{id}/review',
        method: 'PUT',
        fields: [text('correctedNum', '확정할 차량 번호', true)],
      },
    ],
  },
  transfers: {
    title: '경찰·소방 이관 문서',
    path: '/api/control/underpasses/{underpassId}/transfers',
    parents: [{ ...facility, required: true }],
    columns: [
      ['agency', '이관 기관'],
      ['managerName', '처리자'],
      ['carNums', '차량 번호'],
      ['memo', '전달 내용'],
      ['createdAt', '이관 시각'],
    ],
    actions: [
      { label: 'PDF 다시 받기', path: '/api/control/transfers/{id}/pdf', method: 'GET', pdf: true },
    ],
  },
  display: {
    title: '전광판 페이지 관리',
    path: '/api/display/equipments/{equipId}/pages',
    parents: [num('equipId', '전광판 설비 번호', true)],
    columns: [
      ['pageNo', '페이지'],
      ['content', '표시 문구'],
      ['updatedAt', '수정 시각'],
    ],
    fields: [source('presetId', '기본 문구 선택', 'presets'), area('content', '표시 문구')],
    create: true,
    edit: true,
    remove: true,
    rowKey: 'pageNo',
  },
  presets: {
    title: '전광판 문구 프리셋',
    path: '/api/display/presets',
    columns: [
      ['id', '번호'],
      ['category', '분류'],
      ['content', '문구'],
    ],
    filters: [
      select('category', '분류', [
        ['SAFE', '안전'],
        ['WATCH', '관심'],
        ['CAUTION', '주의'],
        ['BLOCK', '차단'],
        ['RELEASE', '해제'],
      ]),
    ],
  },
  reasons: {
    title: '통제 사유 프리셋',
    path: '/api/control/reason-presets',
    columns: [
      ['id', '번호'],
      ['type', '유형'],
      ['content', '통제 사유'],
    ],
    filters: [
      select('type', '유형', [
        ['BLOCK', '차단'],
        ['RELEASE', '해제'],
      ]),
    ],
  },
  regions: {
    title: '지역 관리',
    path: '/api/regions',
    public: true,
    columns: [
      ['id', '지역 번호'],
      ['name', '지역명'],
    ],
    fields: [text('name', '지역명', true)],
    create: true,
    edit: true,
    remove: true,
  },
  managers: {
    title: '관리자·담당 지역·근무조',
    path: '/api/managers',
    columns: [
      ['name', '이름'],
      ['loginId', '아이디'],
      ['role', '권한'],
      ['status', '상태'],
      ['regions', '담당 지역'],
      ['shiftName', '근무조'],
      ['phone', '연락처'],
    ],
    filters: [
      text('keyword', '이름·아이디'),
      region,
      source('shiftId', '근무조', 'shifts'),
      select('status', '상태', [
        ['PENDING', '승인 대기'],
        ['ACTIVE', '활성'],
        ['WITHDRAWN', '탈퇴'],
      ]),
      { key: 'includeWithdrawn', label: '탈퇴 관리자 포함', type: 'checkbox' },
    ],
    actions: [
      {
        label: '담당 지역 변경',
        path: '/api/managers/{id}/regions',
        method: 'PUT',
        fields: [
          {
            key: 'regionIds',
            label: '담당 지역',
            type: 'multi',
            source: 'regions',
            required: true,
          },
        ],
      },
      {
        label: '근무조 변경',
        path: '/api/managers/{id}/shift',
        method: 'PUT',
        fields: [source('shiftId', '근무조', 'shifts')],
      },
      { label: '탈퇴 처리', path: '/api/managers/{id}/withdraw', method: 'POST' },
    ],
  },
  shifts: {
    title: '근무조 조회',
    path: '/api/shifts',
    columns: [
      ['name', '근무조'],
      ['startTime', '시작'],
      ['endTime', '종료'],
    ],
  },
  customers: {
    title: '고객 관리',
    path: '/api/customers',
    columns: [
      ['name', '이름'],
      ['loginId', '아이디'],
      ['phone', '연락처'],
      ['email', '이메일'],
      ['carNums', '등록 차량'],
      ['withdrawn', '탈퇴 여부'],
    ],
    filters: [
      text('keyword', '통합 검색'),
      text('carNum', '차량 번호'),
      { key: 'includeWithdrawn', label: '탈퇴 고객 포함', type: 'checkbox' },
    ],
    fields: profileFields,
  },
  alerts: {
    title: '알림 발송 기록',
    path: '/api/alert-logs',
    columns: [
      ['underpassId', '시설 번호'],
      ['carNum', '차량 번호'],
      ['alertType', '알림 종류'],
      ['sentAt', '발송 시각'],
    ],
    filters: [
      region,
      facility,
      text('carNum', '차량 번호'),
      num('customerId', '고객 번호'),
      select('alertType', '알림 종류', [
        ['PREDICTIVE', '위험 예측'],
        ['FINAL', '최종 인식'],
      ]),
      ...period,
    ],
  },
  notices: {
    title: '공지사항 관리',
    path: '/api/notices',
    public: true,
    columns: [
      ['title', '제목'],
      ['important', '중요'],
      ['category', '분류'],
      ['authorName', '작성자'],
      ['createdAt', '작성일'],
      ['viewCount', '조회수'],
    ],
    filters: [text('keyword', '제목·내용'), select('category', '분류', noticeCategories)],
    fields: [
      text('title', '제목', true),
      area('content', '내용', true),
      select('category', '분류', noticeCategories),
      { key: 'important', label: '중요 공지', type: 'checkbox' },
      { key: 'sendPush', label: '고객 알림 발송', type: 'checkbox' },
    ],
    detail: true,
    create: true,
    edit: true,
    remove: true,
  },
  faqs: {
    title: 'FAQ 관리',
    path: '/api/faqs',
    public: true,
    columns: [
      ['question', '질문'],
      ['category', '분류'],
      ['authorName', '작성자'],
      ['createdAt', '작성일'],
      ['viewCount', '조회수'],
    ],
    filters: [text('keyword', '질문·답변'), select('category', '분류', faqCategories)],
    fields: [
      text('question', '질문', true),
      area('answer', '답변', true),
      select('category', '분류', faqCategories),
    ],
    detail: true,
    create: true,
    edit: true,
    remove: true,
  },
  inquiries: {
    title: '고객 문의 처리',
    path: '/api/inquiries',
    columns: [
      ['title', '제목'],
      ['customerName', '고객'],
      ['status', '처리 상태'],
      ['createdAt', '작성일'],
    ],
    filters: [
      text('keyword', '검색'),
      select('status', '처리 상태', [
        ['PENDING', '대기'],
        ['ANSWERED', '답변 완료'],
      ]),
      ...period,
    ],
    detail: true,
    actions: [
      {
        label: '답변 등록',
        path: '/api/inquiries/{id}/reply',
        method: 'POST',
        fields: [area('content', '답변 내용', true)],
      },
      {
        label: '답변 수정',
        path: '/api/inquiries/{id}/reply',
        method: 'PUT',
        fields: [area('content', '수정할 답변', true)],
      },
      { label: '답변 삭제', path: '/api/inquiries/{id}/reply', method: 'DELETE' },
    ],
  },
  cars: {
    title: '내 차량',
    path: '/api/customers/me/cars',
    owner: true,
    columns: [
      ['carNum', '차량 번호'],
      ['createdAt', '등록일'],
    ],
    fields: [text('carNum', '차량 번호', true)],
    create: true,
    edit: true,
    remove: true,
  },
  'my-inquiries': {
    title: '내 문의',
    path: '/api/customers/me/inquiries',
    owner: true,
    columns: [
      ['title', '제목'],
      ['status', '처리 상태'],
      ['createdAt', '작성일'],
    ],
    fields: [text('title', '제목', true), area('content', '문의 내용', true)],
    detail: true,
    create: true,
    edit: true,
    remove: true,
  },
  'my-alerts': {
    title: '내 알림',
    path: '/api/customers/me/alerts',
    owner: true,
    columns: [
      ['underpassName', '시설'],
      ['carNum', '차량 번호'],
      ['alertType', '알림 종류'],
      ['sentAt', '발송 시각'],
      ['messageText', '알림 내용'],
    ],
    filters: [...period],
  },
}
export const resourceRoutes: Record<string, string> = {
  '/fac-list': 'facilities',
  '/fac-new': 'facilities',
  '/verify': 'reviews',
  '/logs': 'events',
  '/notifications': 'alerts',
  '/cs-customers': 'customers',
  '/cs-inquiries': 'inquiries',
  '/cs-content': 'notices',
  '/cs-notice-edit': 'notices',
  '/my-cars': 'cars',
  '/my-inquiries': 'my-inquiries',
  '/my-inquiry-new': 'my-inquiries',
  '/my-alerts': 'my-alerts',
}
export const valueLabels: Record<string, string> = {
  RISK_LEVEL_UP: '위험도 상승',
  RISK_LEVEL_DOWN: '위험도 하락',
  NORMAL: '정상',
  BLOCKED: '통제',
  SAFE: '안전',
  WARNING: '주의',
  DANGER: '위험',
  PENDING: '대기',
  CONFIRMED: '확정',
  AUTO_PROCESSED: '자동 처리',
  NONE: '대상 아님',
  SUCCESS: '성공',
  LOW_CONFIDENCE: '낮은 신뢰도',
  FAIL: '실패',
  UNMATCHED: '미매칭',
  REMAINING: '잔류',
  PASSED: '통과',
  FINAL_LPR: '최종 인식',
  ENTRY: '입구',
  CCTV: '일반 CCTV',
  APPROACH_LPR: '접근 인식',
  IN_TRANSIT: '진입 중',
  EXITED: '정상 통과',
  PREDICTIVE: '위험 예측',
  FINAL: '최종 인식',
  REGIONAL_ADMIN: '지역 관리자',
  POLICE: '경찰',
  FIRE: '소방',
  ANSWERED: '답변 완료',
}
valueLabels.NORMAL = '정상'

backendResources.facilities!.actions!.push({
  label: '입출구 카메라 지정',
  path: '/api/underpasses/{id}/gate-cameras',
  method: 'PUT',
  fields: ['upEntryCameraId', 'upExitCameraId', 'downEntryCameraId', 'downExitCameraId'].map(
    (key, index) => ({
      key,
      label: ['상행 입구', '상행 출구', '하행 입구', '하행 출구'][index]!,
      type: 'select',
      source: 'cameras',
    }),
  ),
})
resourceRoutes['/c-notice'] = 'notices'
resourceRoutes['/info/faq'] = 'faqs'
resourceRoutes['/customer-app/notices'] = 'notices'
resourceRoutes['/customer-app/faq'] = 'faqs'

resourceRoutes['/fac-detail'] = 'facilities'
resourceRoutes['/handover-pdf'] = 'transfers'
resourceRoutes['/cs-inquiry-detail'] = 'inquiries'
resourceRoutes['/cs-customer-detail'] = 'customers'
resourceRoutes['/customer-app/account/cars'] = 'cars'
resourceRoutes['/customer-app/account/inquiries'] = 'my-inquiries'

Object.assign(valueLabels, { ACTIVE: '활성', WITHDRAWN: '탈퇴' })

resourceRoutes['/admin-app/screen-4'] = 'facilities'
resourceRoutes['/admin-app/screen-5'] = 'facilities'
resourceRoutes['/admin-app/screen-13'] = 'manager-notifications'

cameraFields[0]!.required = true

backendResources['risk-history'] = {
  title: '시설 위험도 관측 기록',
  path: '/api/underpasses/{underpassId}/risk-scores',
  parents: [{ ...facility, required: true }],
  columns: [
    ['riskScore', '위험 점수'],
    ['rainfallMm', '강우량(mm)'],
    ['predictedAt', '관측 시각'],
  ],
  filters: [num('hours', '조회 시간'), num('limit', '조회 개수')],
}

resourceRoutes['/customer-app/screen-12'] = 'my-alerts'

Object.assign(valueLabels, {
  WATCH: '관심',
  CAUTION: '주의',
  ALERT: '경계',
  SEVERE: '심각',
  BLOCK: '차단',
  RELEASE: '해제',
  ...Object.fromEntries([...noticeCategories, ...faqCategories]),
})

backendResources['manager-notifications'] = {
  title: '내 관리자 알림',
  path: '/api/managers/me/notifications',
  columns: [
    ['category', '분류'],
    ['title', '제목'],
    ['body', '내용'],
    ['read', '읽음'],
    ['createdAt', '수신 시각'],
  ],
  filters: [
    select('category', '분류', [
      ['RISK', '위험'],
      ['EQUIPMENT', '설비'],
      ['TASK', '업무'],
      ['SYSTEM', '시스템'],
    ]),
    { key: 'unreadOnly', label: '안 읽은 알림만', type: 'checkbox' },
  ],
  actions: [
    { label: '읽음 처리', path: '/api/managers/me/notifications/{id}/read', method: 'PUT' },
  ],
}
resourceRoutes['/admin-app/notifications'] = 'manager-notifications'
Object.assign(valueLabels, { RISK: '위험', EQUIPMENT: '설비', TASK: '업무', SYSTEM: '시스템' })
