# Firebase 프로젝트 전환 안내

Spring FcmPushSender는 Firebase 프로젝트 ID를 서비스 계정 JSON에서 읽으므로 Java 코드 변경 없이 새 프로젝트로 전환할 수 있습니다.

Render Backend 환경변수:
- `PUF_FCM_ENABLED=true`
- `SECRET_FCM_CREDENTIALS_JSON` = 새 프로젝트 pufpr-ab135-7d2a5에서 발급한 **서비스 계정 JSON 전체** (비공개, 파일에 패키징하지 않음)
- `SECRET_FCM_WEB_API_KEY=AIzaSyC4tpG_t2OGKH8vQTIUHGJEM7lxR8NxiWI`
- `SECRET_FCM_WEB_PROJECT_ID=pufpr-ab135-7d2a5`
- `SECRET_FCM_WEB_SENDER_ID=950702292615`
- `SECRET_FCM_WEB_APP_ID=1:950702292615:web:ad273bbbb7d92a06cc8222`
- `SECRET_FCM_WEB_VAPID_KEY=BCIPCowZOT7Szt4tEXOsQzk8n4l595dVxChISYTf-4In15FI-9FHxc6hatBEfN-tKMWW6GzQ__Q5ywRUb4Sz4Cc`

서비스 계정 JSON은 Render 비밀 환경변수로만 설정하십시오. 업로드한 개인 키가 채팅에 노출되었으므로 해당 키를 폐기하고 새 비공개 키로 교체하는 것이 안전합니다.

기존 4KB SSE heartbeat 및 예지보전 백필 제거는 그대로 유지됩니다.
