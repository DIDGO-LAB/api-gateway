# Didgo API Gateway

`api-gateway`는 Didgo MSA의 외부 API 진입점이다.

이 서비스는 다음 책임을 가진다.

- 외부 요청 라우팅
- JWT Access Token 검증
- 공개 API와 보호 API 분기
- 내부 신뢰 헤더 재구성
- CircuitBreaker / fallback 처리
- CORS 처리

현재 외부 요청 흐름은 다음과 같다.

```text
Client -> Nginx -> api-gateway -> user-service | training-service
```

## 기술 스택

- Java 21
- Spring Boot 3.5.13
- Spring Cloud Gateway
- Spring Security
- Resilience4j
- Actuator
- Springdoc OpenAPI
- JJWT
- Docker

## 현재 라우팅 범위

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/auth/reissue`
- `GET /api/users/me`
- `PATCH /api/users/me`
- `/api/trainings/**`
- `WS /ws/trainings/social/voice`
- `/swagger-ui.html`, `/swagger-ui/**`
- `/user-service/v3/api-docs`
- `/training-service/v3/api-docs/**`

현재 라우팅 대상은 `user-service`와 `training-service`다.

## 인증 정책

공개 API:

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/reissue`
- Swagger / OpenAPI 문서 경로
- `WS /ws/trainings/social/voice`

인증 필요 API:

- `POST /api/auth/logout`
- `GET /api/users/me`
- `PATCH /api/users/me`
- `/api/trainings/**`

보호 API 요청은 Gateway에서 JWT를 검증한 뒤 `X-User-Id` 같은 내부 헤더로 변환해 하위 서비스에 전달한다.
사회성 음성 WebSocket은 브라우저가 일반 HTTP 인증 헤더를 넣기 어렵기 때문에 Gateway JWT 인증을 우회하고, `training-service`가 세션 준비 API에서 발급한 단기 `connectionToken`으로 검증한다.

## Swagger / OpenAPI

Gateway 기준 Swagger UI는 `/swagger-ui.html`에서 확인한다.

Swagger UI는 Gateway가 직접 제공하고, 하위 서비스 OpenAPI 문서는 다음 경로로 프록시한다.

- `User Service API`: `/user-service/v3/api-docs`
- `Training Service API`: `/training-service/v3/api-docs/external-training`
- `Training Service Internal API`: `/training-service/v3/api-docs/internal-training`

## 실행 방법

로컬에서 단독 테스트:

```bash
JAVA_HOME=/path/to/java21 ./mvnw test
```

애플리케이션 실행:

```bash
JWT_SECRET=your-secret \
JWT_ISSUER=user-service \
USER_SERVICE_URI=http://localhost:8080 \
TRAINING_SERVICE_URI=http://localhost:8081 \
TRAINING_SERVICE_WS_URI=ws://localhost:8081 \
./mvnw spring-boot:run
```

Docker 빌드:

```bash
docker build -t didgo-api-gateway:latest .
```

전체 연동 실행은 상위 인프라 레포의 `docker compose`를 사용한다.

## 주요 설정

- `JWT_SECRET`: JWT 서명 검증 키
- `JWT_ISSUER`: 허용할 토큰 issuer
- `USER_SERVICE_URI`: 라우팅 대상 user-service 주소
- `TRAINING_SERVICE_URI`: 라우팅 대상 training-service HTTP 주소
- `TRAINING_SERVICE_WS_URI`: 라우팅 대상 training-service WebSocket 주소
- `CORS_ALLOWED_ORIGINS`: 허용할 CORS origin 목록
- `SERVER_PORT`: Gateway 포트

## 테스트와 검증 범위

현재 확인된 범위:

- Spring Context 로딩
- Nginx -> api-gateway -> user-service 라우팅
- api-gateway -> training-service 라우팅
- 사회성 음성 WebSocket 인증 예외 및 내부 헤더 제거
- 공개 API 통과
- 보호 API 무토큰 차단
- 보호 API 유효 토큰 통과
- CORS preflight 허용

추가 보강이 필요한 범위:

- Gateway 인증 필터 단위 테스트
- 헤더 sanitizing 테스트
- CircuitBreaker / fallback 테스트
- 다중 서비스 라우팅 테스트

## 참고 문서

- [plan.md](./plan.md)
- [research.md](./research.md)
