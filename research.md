# api-gateway 분석 보고서

작성일: 2026-04-28

## 1. 개요

이 문서는 `/Users/byeok27/Documents/GitHub/Didgo/api-gateway` 프로젝트만을 별도로 깊게 분석한 결과다.

분석 기준:

- 현재 로컬 코드
- `plan.md`에 적힌 설계 의도
- 최근 실제 검증 결과

이 프로젝트의 핵심 역할은 외부 API 요청을 내부 서비스로 전달하기 전에 다음 책임을 선행하는 것이다.

- JWT Access Token 검증
- 공개/보호 API 구분
- 내부 신뢰 헤더 재구성
- CircuitBreaker와 fallback 적용
- CORS 처리
- 내부 서비스 직접 노출 차단 구조의 일부 구현

즉, 이 프로젝트는 단순 프록시가 아니라 인증과 장애 완충을 담당하는 애플리케이션 게이트웨이다.

## 2. 현재 프로젝트 상태 요약

현재 파일 수는 많지 않다. 실제 코드량도 작고, 1차 라우팅 대상을 `user-service` 하나로 제한한 최소 구현 상태다.

핵심 파일:

- `plan.md`: 설계 의도와 요구사항
- `pom.xml`: Spring Boot / Spring Cloud / 보안 의존성
- `src/main/resources/application.yml`: 라우트, CORS, actuator, circuitbreaker 설정
- `src/main/java/com/didgo/apigateway/security/GatewayAuthenticationFilter.java`: 핵심 인증 필터
- `src/main/java/com/didgo/apigateway/security/JwtTokenValidator.java`: JWT 검증기
- `src/main/java/com/didgo/apigateway/config/GatewayResilienceConfig.java`: TimeLimiter 적용
- `src/main/java/com/didgo/apigateway/fallback/FallbackController.java`: 장애 fallback 응답
- `Dockerfile`: 배포 이미지 정의

테스트 상태:

- 단일 `contextLoads()` 테스트 1개만 존재
- 최근 실행 결과는 `Tests run: 1, Failures: 0, Errors: 0`

결론적으로, 현재 상태는 "동작하는 최소 게이트웨이"에 가깝고, 아키텍처 방향은 맞지만 테스트와 운영 보강은 아직 부족하다.

## 3. 기술 스택 분석

`pom.xml` 기준 기술 구성:

- Java 21
- Spring Boot 3.5.13
- Spring Cloud 2025.0.2
- Spring Cloud Gateway WebFlux
- Spring Cloud CircuitBreaker Reactor Resilience4j
- Spring Security
- Spring Boot Actuator
- JJWT 0.12.7
- Reactor Test

선택 평가:

- Spring Cloud Gateway 선택은 현재 요구사항에 적합하다.
- Gateway 레벨에서 JWT를 직접 검증하기 위해 JJWT를 사용한 것도 이해 가능하다.
- 다만 장기적으로는 Spring Security OAuth2 Resource Server를 쓰는 편이 토큰 검증 정책 확장, claim 기반 권한 처리, 표준화 측면에서 더 낫다.

현재 방식의 장점:

- 구현이 단순하다.
- `user-service`와 같은 JWT 서명/issuer 계약을 빠르게 맞출 수 있다.
- 토큰 타입(`ACCESS`/`REFRESH`) 검증을 직접 제어할 수 있다.

현재 방식의 단점:

- 표준 Security Filter Chain과 별개로 커스텀 필터 로직이 커진다.
- 권한 모델이 복잡해질수록 수동 관리 비용이 증가한다.
- JJWT 예외 처리, claim 해석, 헤더 주입을 모두 직접 유지해야 한다.

## 4. 설계 의도와 실제 구현의 관계

`plan.md`의 핵심 방향은 다음과 같다.

- 외부 진입점은 Nginx
- API 애플리케이션 진입점은 `api-gateway`
- 내부 서비스는 Docker 내부 네트워크에만 노출
- JWT 검증은 Gateway 일괄 처리
- `api-gateway -> user-service`는 HTTP/REST
- 서버 간 통신은 장기적으로 gRPC
- CircuitBreaker 적용

현재 구현은 이 설계를 대부분 충족한다.

충족된 항목:

- 외부 요청은 Nginx가 받고, API는 Gateway로 전달된다.
- `user-service`는 호스트 포트 없이 내부 네트워크에서만 구동된다.
- JWT 검증은 Gateway에서 수행된다.
- Gateway는 `X-User-Id`를 내부 서비스에 전달한다.
- CircuitBreaker와 fallback이 설정되어 있다.
- CORS preflight가 Gateway에서 허용된다.

아직 1차 수준인 항목:

- 라우팅 대상이 `user-service` 하나뿐이다.
- gRPC는 설계만 있고 Gateway 자체에서 다룰 코드는 없다.
- 테스트 자동화는 거의 없다.
- 운영 정책과 개발 정책이 충분히 분리되어 있지 않다.

## 5. 요청 처리 흐름 분석

현재 외부 요청 흐름은 다음과 같다.

```text
Client
  -> Nginx
  -> api-gateway
  -> GatewayAuthenticationFilter
  -> Route + CircuitBreaker
  -> user-service
```

세부 단계:

1. 클라이언트가 `Authorization` 포함 API 요청을 보낸다.
2. Nginx가 해당 경로를 `api-gateway:8080`으로 프록시한다.
3. Gateway의 `GlobalFilter`가 요청을 가장 먼저 가로챈다.
4. 공개 API면 토큰 검증 없이 통과시킨다.
5. 보호 API면 `Bearer` 형식을 확인한다.
6. JWT 서명, issuer, token type을 검증한다.
7. 성공 시 내부용 헤더를 다시 구성한다.
8. 라우트 설정에 따라 `user-service`로 전달한다.
9. 장애나 timeout이 발생하면 fallback 컨트롤러가 `503`을 반환한다.

이 흐름은 설계상 맞다. 특히 외부 토큰을 내부 서비스가 직접 소비하지 않도록 `Authorization`을 제거하고 `X-User-Id`로 변환하는 점이 핵심이다.

## 6. 인증 필터 분석

핵심 구현은 [GatewayAuthenticationFilter.java](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/src/main/java/com/didgo/apigateway/security/GatewayAuthenticationFilter.java) 에 있다.

주요 동작:

- `GlobalFilter`, `Ordered.HIGHEST_PRECEDENCE`
- 클라이언트가 넣은 `X-User-Id`, `X-User-Role`, `X-Request-Id` 제거
- `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/reissue`는 공개
- `/swagger-ui*`, `/v3/api-docs*`는 공개
- `OPTIONS`는 preflight로 간주해 공개
- 보호 API는 `Authorization: Bearer` 필수
- 검증 성공 시:
  - `Authorization` 제거
  - `X-User-Id` 추가
  - `X-User-Role=USER` 추가
  - `X-Request-Id` 추가

좋은 점:

- 내부 헤더 위조 방지의 기본 형태가 들어가 있다.
- 공개 경로와 보호 경로 구분이 명확하다.
- Gateway에서 직접 JSON 에러 응답을 만든다.
- preflight OPTIONS 허용이 추가되어 브라우저 클라이언트와 맞는다.

제한 사항:

- 역할 모델이 현재 `USER` 하드코딩이다.
- 토큰에서 role/authority claim을 읽지 않는다.
- 공개 경로 목록이 코드 상수에 박혀 있어 라우트 확장 시 누락 위험이 있다.
- 내부 헤더는 제거하지만 내부 서비스가 추가 검증 없이 이를 신뢰하는 구조다.

운영상 의미:

- 현재 구조는 "외부 사용자가 내부 사용자 ID를 위조하는 것"은 막지만, "내부 네트워크에 있는 다른 서비스가 헤더를 위조하는 것"까지 막지는 못한다.

## 7. JWT 검증기 분석

[JwtTokenValidator.java](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/src/main/java/com/didgo/apigateway/security/JwtTokenValidator.java) 의 책임은 단순하다.

검증 요소:

- HMAC secret
- issuer 일치 여부
- claim `tokenType == ACCESS`
- subject를 `Long` userId로 파싱

에러 처리:

- 만료 토큰은 `EXPIRED_TOKEN`
- 그 외는 `INVALID_TOKEN`

장점:

- `Refresh Token`으로 보호 API 접근을 막는다.
- issuer가 다르면 거부한다.
- 구현이 짧고 추적이 쉽다.

리스크:

- secret rotation 고려가 없다.
- audience, not-before, clock skew 같은 세부 정책이 없다.
- token subject가 숫자가 아니면 `Long.parseLong()`에서 예외가 나고 결국 `INVALID_TOKEN`으로 처리된다. 동작상 문제는 없지만 의도적인 검증이라고 보기는 어렵다.

장기 개선 방향:

- key rotation 지원
- claim 기반 role / tenant / scope 해석
- 표준 Resource Server 전환 검토

## 8. SecurityConfig 분석

[SecurityConfig.java](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/src/main/java/com/didgo/apigateway/security/SecurityConfig.java) 는 Spring Security 자체로는 모든 요청을 `permitAll()` 한다.

즉, 보안의 실제 강제는 `SecurityWebFilterChain`이 아니라 `GlobalFilter`가 담당한다.

이 설계의 의미:

- Gateway는 Spring Security 인가 체계보다는 라우팅 전처리 필터로 인증을 처리한다.
- 구현은 단순하지만 보안 규칙이 Spring Security DSL에 표현되지 않으므로 추후 규칙이 늘어나면 가독성이 떨어질 수 있다.

현재 상태에서는 허용 가능하지만, 다음 상황이 오면 재검토가 필요하다.

- 관리자 API 추가
- 서비스별 권한 정책 추가
- Route별 세분화된 인증 정책 추가
- OAuth2/OIDC 연동

## 9. 라우팅 설정 분석

[application.yml](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/src/main/resources/application.yml) 기준 라우트는 2개다.

- `user-service-api`: `/api/**`
- `user-service-docs`: `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`

현재 라우트 구조의 장점:

- 단순하다.
- `user-service` 한 개를 대상으로 한 초기 구성에 적합하다.
- 문서 경로와 일반 API 경로를 분리해 향후 정책 분리 여지를 남겼다.

한계:

- 서비스가 늘어나면 `Path` 조건과 공개/보호 경로 조건이 분산된다.
- 공개 API 정책은 코드 필터에, 실제 전달 대상은 YAML에 흩어져 있다.
- `/api/**` 전체를 한 서비스로 보내는 구조라 multi-service API gateway로 확장될 때 재구성이 필요하다.

확장 시 예상 방향:

- `/api/users/**` -> `user-service`
- `/api/trainings/**` -> `training-service`
- `/api/reports/**` -> `report-service`
- 공통 인증 필터는 유지
- 서비스별 route metadata 또는 predicate 기반 공개 경로 정책 분리

## 10. CORS 분석

현재 CORS는 전역으로 다음 조건을 연다.

- Origin: `localhost`, `localhost:3000`, `localhost:5173`
- Methods: `GET, POST, PATCH, PUT, DELETE, OPTIONS`
- Headers: `*`
- Exposed Headers: `Authorization`, `X-Request-Id`
- Credentials: `true`

좋은 점:

- 프론트엔드 개발 서버와 바로 붙기 좋다.
- preflight OPTIONS가 실제 필터 레벨에서도 허용된다.

주의:

- `allow-credentials=true`와 넓은 Origin 사용은 운영 환경에서 더 엄격히 관리해야 한다.
- 프로덕션에서는 profile별로 origin whitelist를 분리하는 것이 맞다.

## 11. CircuitBreaker / Fallback 분석

현재 CircuitBreaker 이름은 `userServiceCircuitBreaker`다.

설정값:

- failure-rate-threshold: `50`
- sliding-window-size: `20`
- minimum-number-of-calls: `10`
- wait-duration-in-open-state: `10s`
- permitted-number-of-calls-in-half-open-state: `5`
- slow-call-duration-threshold: `2s`
- slow-call-rate-threshold: `50`
- timeLimiter timeout-duration: `3s`

추가 코드:

- [GatewayResilienceConfig.java](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/src/main/java/com/didgo/apigateway/config/GatewayResilienceConfig.java)
- [FallbackController.java](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/src/main/java/com/didgo/apigateway/fallback/FallbackController.java)

평가:

- 현재 값은 무난한 초기값이다.
- `user-service` 지연을 무한정 기다리지 않도록 `3s` 타임아웃을 걸어둔 점이 적절하다.
- fallback 응답을 Gateway가 직접 제공하므로 외부 클라이언트는 내부 장애 세부사항을 몰라도 된다.

한계:

- fallback은 현재 `user-service` 전체 장애를 하나의 에러 코드로만 표현한다.
- timeout, connection failure, open circuit를 구분하지 않는다.
- 관측성 측면에서 actuator 외에 structured logging이나 trace 연계가 아직 없다.

## 12. 애플리케이션 부트스트랩 분석

[ApiGatewayApplication.java](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/src/main/java/com/didgo/apigateway/ApiGatewayApplication.java) 는 `ReactiveUserDetailsServiceAutoConfiguration`을 제외한다.

의도:

- Spring Security 기본 사용자 자동 생성과 관련된 불필요한 비밀번호 로그를 제거한다.
- Gateway가 자체 사용자 저장소를 가지지 않는 구조를 코드 차원에서 명확히 한다.

이 판단은 맞다. Gateway는 사용자 DB나 인메모리 계정을 필요로 하지 않는다.

## 13. Dockerfile 분석

[Dockerfile](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/Dockerfile) 은 멀티스테이지 빌드다.

구성:

- build stage: `eclipse-temurin:21-jdk`
- Maven Wrapper로 `dependency:go-offline`
- `package`
- runtime stage: `eclipse-temurin:21-jre`

장점:

- 초기 `maven:` 베이스 이미지 pull 실패를 피하도록 단순화된 구성을 사용한다.
- runtime 이미지가 JRE 기반이라 빌드 이미지보다 가볍다.
- Maven Wrapper를 사용해 로컬/CI 버전 차이를 줄인다.

보완점:

- healthcheck가 없다.
- non-root user 실행이 아니다.
- layer caching을 더 세밀히 최적화할 수 있다.

## 14. 테스트 상태 분석

현재 테스트는 [ApiGatewayApplicationTests.java](/Users/byeok27/Documents/GitHub/Didgo/api-gateway/src/test/java/com/didgo/apigateway/ApiGatewayApplicationTests.java) 하나뿐이다.

내용:

- Spring Context가 뜨는지만 본다.
- 테스트용 JWT secret, issuer를 주입한다.

이 테스트가 보장하는 것:

- 설정 충돌 없이 애플리케이션 컨텍스트가 올라온다.
- 최소한 빈 등록은 된다.

이 테스트가 보장하지 못하는 것:

- 공개 API 판별
- 보호 API 401 차단
- Access Token과 Refresh Token 구분
- `Authorization` 제거와 `X-User-Id` 주입
- 클라이언트 헤더 sanitizing
- fallback 동작
- CORS preflight 동작
- route predicate 동작

현재 프로젝트에서 가장 큰 품질 공백은 여기다. 동작은 수동 검증으로 확인했지만, 회귀를 막을 자동 테스트가 거의 없다.

## 15. 최근 실제 검증 결과

최근 확인된 사실:

- `mvnw test` 통과
- Docker 이미지 빌드 성공
- Nginx -> api-gateway -> user-service 흐름 동작
- `POST /api/auth/signup` 성공
- `POST /api/auth/login` 성공
- 무토큰 `GET /api/users/me`는 `401 INVALID_TOKEN`
- 유효한 Access Token 포함 `GET /api/users/me`는 `200 OK`
- `OPTIONS /api/users/me` preflight는 `200 OK`

중간에 발견했던 문제:

- Nginx가 `Authorization` 헤더를 명시적으로 넘기지 않아 보호 API가 실패했다.
- 이를 `proxy_set_header Authorization $http_authorization;` 추가로 해결했다.

이 문제는 중요하다. Gateway 프로젝트 자체의 문제는 아니지만, 게이트웨이 구조에서는 외부 프록시와의 계약도 사실상 프로젝트 일부다.

## 16. 설계 대비 좋은 점

- 책임이 명확하다. Gateway는 토큰 검증과 라우팅에 집중한다.
- 내부 서비스에 외부 JWT를 직접 전달하지 않는다.
- 공개 API와 보호 API의 경계를 현재 요구사항 수준에서는 잘 반영했다.
- preflight와 Swagger 경로를 고려했다.
- Nginx를 유지한 채 애플리케이션 게이트웨이 계층을 추가한 구조가 합리적이다.
- 코드량이 적어 이해가 쉽고, 구조적 부채가 아직 커지지 않았다.

## 17. 설계 대비 아쉬운 점

- 인증 정책이 Spring Security DSL이 아니라 커스텀 GlobalFilter에 몰려 있다.
- 테스트가 사실상 없다.
- role/authority 모델이 없다.
- 다중 서비스 라우팅 확장 구조가 아직 없다.
- 운영/개발 프로파일 분리가 없다.
- 에러 메시지가 영어로 고정되어 있어 현재 전체 서비스의 한국어 응답과 일관되지 않는다.
- 내부 신뢰 헤더 위조에 대한 2차 방어가 없다.

## 18. 핵심 리스크

### 18.1 내부 신뢰 경계

현재 `user-service`는 `X-User-Id`를 신뢰한다. Gateway가 외부 헤더를 제거한 뒤 새로 넣는 구조라 외부 위조는 막지만, 내부 네트워크에서 임의 서비스가 직접 `user-service`를 두드리면 헤더 위조가 가능하다.

개선 방향:

- Gateway 전용 shared secret header
- HMAC 서명된 내부 헤더
- mTLS
- service mesh / network policy

### 18.2 테스트 부재

수동 검증이 통과했다고 해서 안정성이 확보된 것은 아니다. 특히 라우팅, 헤더 sanitizing, token type 검증은 회귀 가능성이 높다.

### 18.3 정책 분산

공개 API 목록은 `GatewayAuthenticationFilter`에 있고, 실제 라우트 목록은 `application.yml`에 있고, 외부 진입 라우팅은 Nginx에 있다. 서비스가 늘어나면 누락과 불일치 가능성이 커진다.

### 18.4 관측성 부족

Actuator는 열려 있지만 request logging, trace correlation, structured error logging, metrics tagging이 부족하다.

## 19. 우선순위별 다음 작업

### P0

- Gateway 인증 필터 단위/통합 테스트 추가
- 공개/보호 API 분기 테스트 추가
- `Authorization` 제거 및 `X-User-Id` 주입 테스트 추가
- Refresh Token으로 보호 API 접근 시 차단 테스트 추가

### P1

- 내부 헤더 신뢰 강화
- 에러 응답 언어와 포맷 정책 통일
- route/공개 경로 정책을 설정 기반으로 정리

### P1

- `user-service` 외 다른 서비스 라우팅을 고려한 path 구조 설계
- `/api/**` 단일 서비스 라우팅에서 서비스별 라우팅으로 전환 가능한 설계 초안 작성

### P2

- Spring Security Resource Server 전환 검토
- profile별 CORS / actuator / docs 노출 정책 분리
- Docker healthcheck, non-root 실행 추가

## 20. 결론

현재 `api-gateway`는 작은 코드베이스지만 역할은 분명하고, 지금 단계의 요구사항에는 맞는 구현이다. 특히 JWT 검증 중앙화, 내부 헤더 재구성, Nginx와의 계층 분리, CircuitBreaker 적용이라는 핵심 목표는 달성했다.

반면 이 프로젝트는 아직 "운영 준비가 된 게이트웨이"라기보다 "정상 동작이 검증된 1차 기반 게이트웨이"에 가깝다. 다음 단계의 핵심은 기능 추가가 아니라 품질 보강이다. 가장 먼저 해야 할 일은 테스트 자동화와 내부 신뢰 경계 강화다.

