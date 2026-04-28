# API Gateway 계획

## 목표

Spring Cloud Gateway 기반의 전용 `api-gateway` 서비스를 구축한다. 모든 외부 API 요청은 API Gateway를 통해서만 제공하며, 각 백엔드 서비스는 외부에 직접 노출하지 않는다.

Nginx는 제거 대상이 아니다. Nginx는 외부 HTTP 진입점, 정적 프론트엔드 배포, TLS 종료, 로드밸런싱 역할을 담당하고, Spring Cloud Gateway는 API 라우팅, JWT 검증, 서킷브레이커 같은 애플리케이션 게이트웨이 역할을 담당한다.

## 핵심 원칙

- 외부 클라이언트의 네트워크 진입점은 Nginx이다.
- 외부 API 요청의 애플리케이션 진입점은 `api-gateway`이다.
- `user-service` 같은 내부 서비스는 Docker 내부 네트워크에서만 접근한다.
- JWT 검증은 API Gateway에서 일괄 수행한다.
- 내부 서비스는 게이트웨이가 검증한 사용자 식별 정보를 신뢰한다.
- 서버 간 통신은 REST가 아니라 gRPC를 기본으로 사용한다.
- 서비스 장애 전파를 막기 위해 주요 라우트에 서킷브레이커를 적용한다.

## 기술 기준

- Java 21
- Spring Boot 3.5.13
- Spring Cloud 2025.0.x
- Spring Cloud Gateway
- Spring Security
- JJWT 또는 Spring Security OAuth2 Resource Server
- Resilience4j CircuitBreaker
- Actuator
- Nginx
- gRPC
- Docker Compose

## 전체 요청 흐름

외부 요청 흐름:

1. 클라이언트가 Nginx로 요청한다.
2. Nginx는 정적 프론트엔드 요청을 직접 처리한다.
3. Nginx는 `/api/**`, `/swagger-ui/**`, `/v3/api-docs/**` 같은 API 요청을 `api-gateway`로 전달한다.
4. `api-gateway`가 JWT 검증, 내부 헤더 정리, 서킷브레이커 처리를 수행한다.
5. `api-gateway`가 내부 서비스로 요청을 전달한다.

초기 구조:

```text
Client -> Nginx -> api-gateway -> user-service
                     |
                     +-> future services
```

서버 간 통신 구조:

```text
training-service -> user-service gRPC
report-service   -> user-service gRPC
other-service    -> user-service gRPC
```

## 서버 간 gRPC 통신 정책

API Gateway는 외부 클라이언트 요청을 내부 서비스로 라우팅하는 HTTP 계층이다. 백엔드 서비스가 다른 백엔드 서비스의 데이터를 조회하거나 명령을 호출할 때는 API Gateway를 거치지 않고 gRPC를 사용한다.

기본 원칙:

- 외부 클라이언트 요청: `Nginx -> api-gateway -> service`
- 서버 간 내부 요청: `service -> service gRPC`
- `api-gateway -> user-service` 요청은 HTTP/REST로 전달한다.
- API Gateway는 gRPC 프록시 역할을 1차 범위에 포함하지 않는다.
- gRPC 포트는 외부에 노출하지 않고 Docker 내부 네트워크에서만 사용한다.
- gRPC 계약은 `.proto` 파일로 관리한다.
- 내부 REST API인 `/internal/**`는 최종 구조에서 gRPC로 대체한다.

예상 gRPC 대상:

- 사용자 기본 정보 조회
- 사용자 장애 정보 조회
- 계정 상태 확인
- 다른 서비스에서 필요한 최소 사용자 컨텍스트 조회

보안 방향:

- gRPC 호출은 내부 네트워크에서만 허용한다.
- 필요 시 서비스 간 인증용 metadata를 추가한다.
- 외부 JWT를 gRPC 호출에 그대로 의존하지 않는다.
- Gateway가 검증한 사용자 컨텍스트가 필요한 경우 호출 서비스가 필요한 식별자만 gRPC 요청으로 전달한다.

## 라우팅 범위

1차 라우팅 대상은 `user-service`이다.

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/auth/reissue`
- `GET /api/users/me`
- `PATCH /api/users/me`
- `/swagger-ui/**`
- `/v3/api-docs/**`

외부에 노출하지 않을 경로:

- `/internal/**`
- 서비스별 Actuator 상세 엔드포인트
- 서비스 간 gRPC 엔드포인트

## 인증 정책

공개 API:

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/reissue`
- Swagger/OpenAPI 문서 경로는 로컬 개발 환경에서만 공개

인증 필요 API:

- `POST /api/auth/logout`
- `GET /api/users/me`
- `PATCH /api/users/me`
- 이후 추가되는 사용자 전용 API

JWT 검증 흐름:

1. Gateway가 `Authorization: Bearer <token>` 헤더를 확인한다.
2. 공개 API가 아니면 Access Token을 검증한다.
3. 토큰이 없거나 유효하지 않으면 Gateway가 직접 `401` 응답을 반환한다.
4. 검증 성공 시 Gateway가 내부 서비스로 사용자 식별 헤더를 전달한다.
5. 내부 서비스는 전달받은 식별 정보를 기준으로 요청을 처리한다.

내부 전달 헤더 후보:

- `X-User-Id`
- `X-User-Role`
- `X-Request-Id`

주의할 점:

- 외부 클라이언트가 임의로 보낸 `X-User-Id` 같은 내부 헤더는 Gateway에서 제거한 뒤 다시 설정한다.
- `user-service`의 기존 JWT 필터는 Gateway 전환 단계에서 비활성화하거나 내부 헤더 기반 인증으로 대체한다.
- 최종 구조에서는 내부 서비스가 외부 토큰을 직접 검증하지 않는다.
- 공개 API인 회원가입, 로그인, 토큰 재발급은 Gateway JWT 검증을 거치지 않고 라우팅한다.
- 로그아웃, 내 정보 조회, 내 정보 수정은 Gateway에서 Access Token을 검증한 뒤 라우팅한다.

## 서킷브레이커 정책

`user-service` 라우트에는 Resilience4j 기반 서킷브레이커를 적용한다.

초기 설정안:

- 실패율 기준: 50%
- 슬라이딩 윈도우 크기: 20
- 최소 호출 수: 10
- Open 상태 유지 시간: 10초
- Half-open 허용 호출 수: 5
- 느린 호출 기준: 2초
- 느린 호출 비율 기준: 50%

Fallback 응답:

- 인증/회원 API 장애 시 Gateway가 `503 SERVICE_UNAVAILABLE`을 반환한다.
- 응답 바디는 공통 에러 포맷으로 맞춘다.
- 장애 원인 상세는 외부에 노출하지 않는다.

예상 에러 코드:

- `USER_SERVICE_UNAVAILABLE`
- `GATEWAY_TIMEOUT`
- `INVALID_TOKEN`
- `EXPIRED_TOKEN`

## Docker Compose 방향

최종 노출 포트:

- `nginx`: 호스트 `80`, 추후 `443`
- `api-gateway`: Docker 내부 노출만 사용하거나 개발 편의를 위해 별도 포트 사용
- `user-service`: `expose`만 사용하고 `ports`는 사용하지 않음
- `mysql`: 로컬 개발 편의를 위해 `3307:3306` 유지 가능
- `redis`: 로컬 개발 편의를 위해 `6379:6379` 유지 가능

전환 계획:

1. `api-gateway` 서비스를 Docker Compose에 추가한다.
2. Nginx는 외부 `80` 포트를 유지한다.
3. Nginx의 API 라우팅 대상을 `user-service`에서 `api-gateway`로 변경한다.
4. `api-gateway`는 Docker 내부 네트워크 이름으로 `user-service:8080`에 접근한다.
5. `user-service`는 외부 포트를 열지 않고 내부 네트워크에서만 접근한다.

## 작업 계획

1. Spring Cloud Gateway 프로젝트 스캐폴딩
2. Spring Boot 3.5.13 및 Spring Cloud 2025.0.x 호환성 확정
3. Gateway 라우트 설정 추가
4. JWT 검증 필터 구현
5. 내부 전달 헤더 정리 필터 구현
6. Resilience4j 서킷브레이커와 fallback 라우트 구현
7. CORS 정책 추가
8. Actuator 헬스 체크 추가
9. Dockerfile 작성
10. `infra/docker-compose.yml`에 `api-gateway` 서비스 추가
11. Nginx API upstream을 `api-gateway`로 변경
12. `user-service`의 외부 직접 접근 차단 상태 확인
13. `user-service`의 Gateway 신뢰 방식 정리
14. gRPC 포트가 외부에 노출되지 않는지 확인
15. 회원가입, 로그인, 내 정보 조회 라우팅 테스트
16. `user-service` 중단 시 fallback 응답 테스트

## 테스트 계획

- 공개 API는 토큰 없이 Gateway를 통과해야 한다.
- 보호 API는 토큰 없이 호출하면 Gateway에서 `401`을 반환해야 한다.
- 보호 API는 유효한 Access Token이 있을 때 내부 서비스로 전달되어야 한다.
- 외부에서 전달한 `X-User-Id`는 무시되고 Gateway가 새로 설정해야 한다.
- `user-service` 장애 시 서킷브레이커 fallback이 동작해야 한다.
- `user-service`를 직접 호스트 포트로 접근할 수 없어야 한다.
- Nginx를 통해 들어온 API 요청만 `api-gateway`로 전달되어야 한다.
- gRPC 포트는 호스트에 직접 노출되지 않아야 한다.
- 서비스 간 사용자 조회는 REST `/internal/**`가 아니라 gRPC 계약으로 전환 가능해야 한다.

## 1차 완료 기준

- `api-gateway` 프로젝트가 생성되어 있다.
- Nginx가 외부 네트워크 진입점으로 동작한다.
- Gateway가 외부 API의 애플리케이션 진입점으로 동작한다.
- JWT 검증이 Gateway에서 수행된다.
- `user-service`는 Gateway를 통해서만 접근 가능하다.
- `user-service` 라우트에 서킷브레이커가 적용되어 있다.
- 서버 간 통신은 gRPC 기준으로 설계되어 있다.
- Docker Compose로 전체 로컬 실행이 가능하다.
- Swagger 또는 API 문서 접근 정책이 정리되어 있다.
