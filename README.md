# Coffee Order System

커피숍 주문 시스템 개인과제를 새로 구성한 Spring Boot 프로젝트이다. 기존 작업 폴더는 수정하지 않고, `coffee-order-system-rebuild` 폴더에 깨끗한 구조로 다시 만들었다.

이번 프로젝트는 처음부터 모든 기능을 한 번에 구현하지 않고, DB 기반 정확성 확보 후 Redis/Kafka로 점진 개선하는 방향을 따른다.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Build | Gradle |
| Database | MySQL, H2 for test |
| Persistence | Spring Data JPA |
| Validation | Bean Validation |
| Cache/Ranking | Redis |
| Event Streaming | Kafka |
| Test | JUnit 5, Spring Boot Test |
| Local Infra | Docker Compose |

## 프로젝트 구조

```text
src/main/java/com/jihyeon/coffeeorder
├── global
│   ├── config
│   ├── exception
│   └── response
├── menu
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
├── member
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
├── order
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
├── ranking
│   ├── controller
│   ├── dto
│   ├── repository
│   └── service
└── event
```

## 로컬 실행 전제 조건

- Java 21
- Docker Desktop
- Git

## 환경변수 설정

`.env.example`을 참고해 로컬에서 `.env`를 만든다. 실제 비밀번호나 개인 계정 정보는 Git에 포함하지 않는다.

```bash
cp .env.example .env
```

주요 환경변수:

| 변수 | 설명 |
| --- | --- |
| `DB_URL` | MySQL JDBC URL |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `REDIS_HOST` | Redis host |
| `REDIS_PORT` | Redis port |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap server |

## Docker Compose 실행

```bash
docker compose up -d
```

포함 서비스:

- MySQL
- Redis
- Kafka

Docker Compose 설정 검증:

```bash
docker compose config
```

## 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 프로필은 `local`이다. 테스트는 `test` 프로필을 사용하며 외부 MySQL, Redis, Kafka에 의존하지 않도록 H2 기반으로 분리한다.

## 테스트 및 빌드

```bash
./gradlew clean test
./gradlew build
```

## 현재 구현 범위

- Spring Boot 프로젝트 초기 구성
- 프로필별 설정 분리
- Docker Compose 로컬 인프라 구성
- 공통 API 응답 `ApiResponse<T>`
- 공통 에러 코드 `ErrorCode`
- 비즈니스 예외 `BusinessException`
- 전역 예외 처리 `GlobalExceptionHandler`
- 메뉴 등록 API
- 메뉴 단건 조회 API
- 메뉴 목록 조회 API
- 메뉴 API 기본 테스트
- 사용자 생성 및 조회 API
- 포인트 충전 및 잔액 조회 API
- 포인트 차감과 잔액 부족 검증
- DB 비관적 락 기반 포인트 동시성 제어

## API

### 메뉴 등록

```http
POST /api/v1/menus
```

```json
{
  "name": "Americano",
  "price": 4500
}
```

잘못된 요청 값은 `INVALID_REQUEST`로 응답한다.

### 메뉴 단건 조회

```http
GET /api/v1/menus/{menuId}
```

메뉴가 존재하지 않으면 `MENU_NOT_FOUND`로 응답한다.

### 메뉴 목록 조회

```http
GET /api/v1/menus
```

메뉴가 없는 경우에도 예외가 아니라 빈 배열을 반환한다.

### 사용자 생성

```http
POST /api/v1/members
```

```json
{
  "name": "Jihyeon"
}
```

사용자는 포인트 잔액 0으로 생성된다.

### 포인트 충전 및 조회

```http
POST /api/v1/members/{memberId}/points/charge
GET /api/v1/members/{memberId}/points
```

```json
{
  "amount": 10000
}
```

충전 금액은 0보다 커야 한다. 포인트 차감은 향후 주문 서비스에서 `PointService.use()`를 호출하며,
동일 회원의 동시 차감 요청은 DB 비관적 락으로 순차 처리한다.

## 향후 구현 계획

1. 주문 생성과 포인트 결제 트랜잭션 연결
2. 주문 내역 기반 인기 메뉴 SQL 집계 구현
3. 인덱스 적용 전후 성능 비교
4. Redis ZSET 기반 인기 메뉴 캐시 적용
5. Spring Event와 `@Async`를 이용한 주문 후속 처리 분리
6. Kafka Producer/Consumer 기반 주문 이벤트 연동
7. k6 성능 테스트 작성

## 설계 메모

- Redis는 인기 메뉴 조회 성능 개선을 위한 캐시/집계 저장소로 사용한다.
- 주문 원본 데이터는 DB의 주문 테이블에 저장한다.
- Kafka는 주문 완료 이벤트 전달을 담당한다.
- Transactional Outbox Pattern은 이번 과제에서 구현하지 않고, Kafka 이벤트 신뢰성을 높이기 위한 추후 개선안으로 남긴다.
- 다중 서버 환경에서는 JVM Lock이나 `synchronized`가 아니라 DB 비관적 락으로 포인트 정합성을 보장한다.
