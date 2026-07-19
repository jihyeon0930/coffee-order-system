# 프로젝트 초기 환경 구성 학습 노트

# 이번 이슈에서 구현한 기능

이번 이슈에서는 커피 주문 시스템을 구현하기 위한 Spring Boot 프로젝트의 기본 뼈대를 구성했다.

- Java 21 기반 Spring Boot 프로젝트 생성
- Gradle Wrapper 구성
- Spring Web, Spring Data JPA, Validation, Redis, Kafka 의존성 추가
- MySQL, Redis, Kafka 로컬 실행을 위한 `docker-compose.yml` 작성
- `local`, `test` 프로필 설정 분리
- 공통 API 응답 객체 `ApiResponse<T>` 구성
- 공통 에러 코드 `ErrorCode`와 비즈니스 예외 `BusinessException` 구성
- 전역 예외 처리 `GlobalExceptionHandler` 구성
- 테스트 환경에서 H2를 사용하도록 분리

관련 커밋:

```text
ac6ad85 chore: rebuild coffee order system project
6640763 chore: align local database defaults
```

## 왜 필요한 기능인가

프로젝트 초기에 환경 구성을 제대로 잡아두면 이후 기능을 구현할 때 반복되는 결정을 줄일 수 있다. 특히 이번 과제는 메뉴 조회, 포인트 결제, 주문 저장, 인기 메뉴 집계, Redis 캐시, Kafka 이벤트까지 단계적으로 확장될 예정이므로 처음부터 다음 기준을 명확히 해두는 것이 중요했다.

- API 응답 형식을 일관되게 유지한다.
- 예외 처리 방식을 Controller마다 흩어놓지 않는다.
- 로컬 실행 환경과 테스트 환경을 분리한다.
- 외부 인프라가 없어도 기본 테스트는 통과해야 한다.
- Redis와 Kafka는 후반 기능에서 사용할 수 있도록 의존성과 구조를 미리 준비한다.

만약 초기 설정 없이 각 기능을 바로 구현하면, API 응답 형식이 제각각이 되거나 테스트가 로컬 MySQL, Redis, Kafka 상태에 의존할 수 있다. 그러면 기능보다 환경 문제에 시간을 더 쓰게 된다.

## 사용한 기술

| 기술 | 사용 이유 |
| --- | --- |
| Java 21 | 최신 LTS 버전으로 장기 지원과 안정성을 기대할 수 있다. |
| Spring Boot 3.3.5 | 웹 API, JPA, Validation, 테스트 구성을 빠르게 시작할 수 있다. |
| Gradle Wrapper | 로컬에 설치된 Gradle 버전에 의존하지 않고 같은 빌드 환경을 사용할 수 있다. |
| Spring Web | REST API Controller 구현을 위해 사용한다. |
| Spring Data JPA | 엔티티와 Repository 기반으로 DB 접근을 단순화한다. |
| Bean Validation | 요청 DTO의 기본 검증을 선언적으로 처리한다. |
| MySQL | 실제 로컬 개발 DB로 사용한다. |
| H2 | 테스트가 외부 DB에 의존하지 않도록 사용한다. |
| Redis | 인기 메뉴 캐시/집계 저장소로 사용할 예정이다. |
| Kafka | 주문 완료 이벤트 전달에 사용할 예정이다. |
| Docker Compose | MySQL, Redis, Kafka를 로컬에서 쉽게 실행하기 위해 사용한다. |

## 핵심 코드 설명

### Gradle 의존성

`build.gradle`에서는 이번 과제에 필요한 주요 기술을 한 번에 구성했다.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.kafka:spring-kafka'

runtimeOnly 'com.mysql:mysql-connector-j'
runtimeOnly 'com.h2database:h2'
```

설계상 Redis와 Kafka는 처음부터 핵심 로직에 넣지 않는다. 먼저 DB 기반 기능을 정확히 구현한 뒤, 인기 메뉴 조회 성능 개선과 주문 이벤트 전달 단계에서 사용한다. 하지만 프로젝트 구조와 의존성은 미리 준비해두면 나중에 확장할 때 큰 설정 변경 없이 이어갈 수 있다.

### 공통 응답 객체

```java
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "SUCCESS", "요청이 성공했습니다.", data);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
```

API 응답을 공통 구조로 맞추면 클라이언트가 성공/실패를 같은 방식으로 처리할 수 있다.

예를 들어 성공 응답은 항상 `success=true`, 실패 응답은 항상 `success=false`가 된다. HTTP 상태 코드만 보고 판단하는 것보다 비즈니스 에러 코드까지 함께 제공할 수 있어 디버깅과 프론트엔드 처리에 유리하다.

### 전역 예외 처리

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }
}
```

`@RestControllerAdvice`를 사용하면 Controller마다 `try-catch`를 작성하지 않아도 된다. Service에서 `BusinessException`을 던지면 전역 핸들러가 공통 실패 응답으로 변환한다.

이 구조를 선택한 이유는 예외 처리 책임을 Controller가 아니라 전역 예외 처리 계층에 두기 위해서다. Controller는 요청을 받고 응답을 반환하는 역할에 집중하고, 비즈니스 실패는 Service에서 판단한다.

### 프로필 분리

`application-local.yml`은 로컬 MySQL, Redis, Kafka를 사용한다.

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/coffee_order}
    username: ${DB_USERNAME:coffee}
    password: ${DB_PASSWORD:coffee}
```

`application-test.yml`은 H2를 사용한다.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:coffee_order_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
```

테스트가 외부 MySQL에 의존하면 Docker 실행 여부나 로컬 계정 상태에 따라 테스트가 실패할 수 있다. 그래서 테스트 프로필에서는 H2 인메모리 DB를 사용했다.

## 오늘 새롭게 배운 내용

### 1. 로컬 설정 기본값은 Docker Compose와 맞아야 한다

초기에는 `application-local.yml`의 DB 기본 계정이 `root`이고 비밀번호가 비어 있었다. 하지만 `docker-compose.yml`과 `.env.example`은 `coffee/coffee` 계정을 기준으로 되어 있었다. 그 결과 IntelliJ에서 앱을 실행하면 다음 오류가 발생했다.

```text
Access denied for user 'root'@'localhost' (using password: NO)
```

이 문제는 `application-local.yml`의 기본값을 Docker Compose 기준과 맞추어 해결했다.

```yaml
username: ${DB_USERNAME:coffee}
password: ${DB_PASSWORD:coffee}
```

환경변수를 사용하더라도 기본값이 로컬 인프라 설정과 맞지 않으면 실행 경험이 나빠진다는 점을 배웠다.

### 2. 테스트는 외부 인프라에 의존하지 않게 구성하는 것이 좋다

테스트는 H2를 사용하도록 분리했다. 이렇게 하면 Docker Desktop이 꺼져 있어도 단위/통합 테스트는 실행할 수 있다.

실무에서도 모든 테스트가 외부 인프라에 의존하면 CI가 불안정해진다. 외부 인프라가 꼭 필요한 테스트는 별도 프로필이나 Testcontainers로 분리하는 것이 좋다.

### 3. `@RestControllerAdvice`는 API 예외 응답 일관성을 만든다

전역 예외 처리를 사용하면 비즈니스 예외, 검증 실패, 예상하지 못한 서버 오류를 한 곳에서 공통 응답으로 변환할 수 있다. 이 방식은 API가 많아질수록 효과가 커진다.

## 어려웠던 점

### 1. 프로젝트 경로가 변경되면서 Gradle 캐시 권한 문제가 발생했다

프로젝트 위치가 Desktop 아래로 이동한 뒤 Gradle 실행 시 홈 디렉터리의 `.gradle` 락 파일 접근 권한 문제가 발생했다.

```text
Operation not permitted
```

이 문제는 프로젝트 내부에 Gradle 캐시를 두는 방식으로 해결했다.

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew test
```

그래서 `.gradle-home/`도 `.gitignore`에 추가했다.

### 2. IntelliJ 실행과 Gradle 테스트의 동작 환경이 달랐다

테스트는 `test` 프로필과 H2를 사용해서 통과했지만, IntelliJ에서 앱을 직접 실행하면 기본 프로필인 `local`이 적용되어 MySQL 접속을 시도했다. 그래서 테스트는 성공하지만 앱 실행은 실패하는 상황이 생겼다.

이 문제는 `local` 프로필의 DB 기본값을 Docker Compose와 맞추는 방식으로 해결했다.

## 어떻게 해결했는가

- `application-local.yml`의 DB 기본 계정을 `coffee/coffee`로 수정했다.
- `.gradle-home/`을 `.gitignore`에 추가했다.
- 테스트 프로필은 H2를 사용하도록 유지했다.
- 공통 응답과 전역 예외 처리를 먼저 구성해 이후 API 구현에서 같은 패턴을 재사용할 수 있게 했다.

검증 명령:

```bash
./gradlew test
```

결과:

```text
BUILD SUCCESSFUL
```

## 다음에 공부할 내용

- Spring Boot profile 우선순위
- `@ConfigurationProperties`를 이용한 설정 바인딩
- Testcontainers로 MySQL, Redis, Kafka 테스트 환경 구성하기
- `@RestControllerAdvice`에서 validation field error를 자세히 내려주는 방법
- Docker Compose의 Kafka KRaft 모드 구성

## 면접에서 나올 수 있는 질문

### Q1. 왜 API 응답을 공통 객체로 감쌌나요?

A. 클라이언트가 성공/실패 응답을 일관된 구조로 처리할 수 있게 하기 위해서입니다. HTTP 상태 코드와 별도로 애플리케이션 에러 코드를 제공하면 `MENU_NOT_FOUND`, `POINT_NOT_ENOUGH`처럼 실패 원인을 명확히 구분할 수 있습니다.

### Q2. `@RestControllerAdvice`를 사용하는 이유는 무엇인가요?

A. Controller마다 예외 처리를 반복하지 않고 전역에서 공통 응답으로 변환하기 위해 사용합니다. Controller는 요청/응답 흐름에 집중하고, 예외 변환 책임은 `GlobalExceptionHandler`가 담당하게 됩니다.

### Q3. 테스트에서 H2를 사용한 이유는 무엇인가요?

A. 기본 테스트가 로컬 MySQL 실행 여부에 의존하지 않게 하기 위해서입니다. 빠르고 독립적인 테스트가 가능하지만, MySQL과 SQL 문법이나 락 동작이 완전히 같지는 않으므로 동시성 테스트나 인덱스 성능 비교는 실제 MySQL 환경에서 별도 검증이 필요합니다.

### Q4. Docker Compose와 `application-local.yml`의 기본값이 왜 맞아야 하나요?

A. 환경변수를 따로 주지 않고 실행해도 로컬 인프라에 바로 연결될 수 있어야 개발 경험이 좋아집니다. Compose는 `coffee/coffee` 계정을 만들었는데 애플리케이션이 `root` 빈 비밀번호로 접속하면 실행 시 DB 인증 오류가 발생합니다.

### Q5. Redis와 Kafka 의존성을 미리 추가한 이유는 무엇인가요?

A. 이번 과제의 후반 요구사항에 Redis 인기 메뉴 캐시와 Kafka 주문 이벤트 연동이 포함되어 있기 때문입니다. 다만 핵심 기능은 먼저 DB 기반으로 정확하게 구현하고, Redis/Kafka는 성능 개선과 이벤트 확장 단계에서 점진적으로 적용합니다.

## 참고 자료

- Spring Boot Reference Documentation
- Spring Data JPA Reference Documentation
- Spring Framework Validation Documentation
- Docker Compose Documentation
- Gradle User Manual
