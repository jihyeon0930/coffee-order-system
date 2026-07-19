# 메뉴 등록 및 조회 기능 학습 노트

# 이번 이슈에서 구현한 기능

이번 이슈에서는 커피 메뉴 도메인의 기본 API를 구현했다.

- 메뉴 등록 API
- 메뉴 단건 조회 API
- 메뉴 목록 조회 API
- 메뉴가 없을 때 `MENU_NOT_FOUND` 예외 처리
- 요청값 검증 실패 시 `INVALID_REQUEST` 응답 처리
- Controller, Service, Repository 테스트 작성

관련 커밋:

```text
04f9772 feat: complete menu registration and lookup
```

## 왜 필요한 기능인가

커피 주문 시스템에서 메뉴는 주문의 기준 데이터다. 사용자는 메뉴 목록을 보고 주문할 메뉴를 선택하고, 주문 서비스는 메뉴 가격을 기준으로 결제 금액을 계산한다. 따라서 메뉴 기능은 이후 포인트 결제, 주문 생성, 인기 메뉴 집계의 기반이 된다.

메뉴 조회 기능이 없다면 주문 API에서 `menuId`를 검증할 수 없고, 메뉴 가격이 없으므로 결제 금액도 계산할 수 없다. 그래서 실제 주문 기능에 들어가기 전에 메뉴 도메인을 먼저 구현했다.

이번 단계에서는 개인과제의 첫 기능 구현 범위에 맞게 복잡한 관리자 기능까지 만들지 않고, 기본적인 등록/단건 조회/목록 조회에 집중했다.

## 사용한 기술

| 기술 | 사용 위치 | 이유 |
| --- | --- | --- |
| `@RestController` | `MenuController` | HTTP 요청을 JSON API로 처리하기 위해 사용 |
| `@RequestMapping` | `/api/v1/menus` | 메뉴 API의 공통 URL prefix 지정 |
| `@PostMapping` | 메뉴 등록 | 리소스 생성 요청 처리 |
| `@GetMapping` | 메뉴 조회 | 리소스 조회 요청 처리 |
| `@Valid` | 요청 DTO 검증 | Controller 진입 시점에 잘못된 요청 차단 |
| Spring Data JPA | `MenuRepository` | 기본 CRUD와 쿼리 메서드 사용 |
| `@Transactional` | `MenuService` | 쓰기 작업과 읽기 작업의 트랜잭션 경계 설정 |
| H2 | 테스트 DB | 외부 MySQL 없이 테스트 실행 |
| MockMvc | Controller 테스트 | HTTP 요청/응답 형태 검증 |
| AssertJ | Service/Repository 테스트 | 객체 상태와 예외 검증 |

## 핵심 코드 설명

### 1. 메뉴 엔티티

```java
@Entity
@Table(name = "menus")
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuStatus status;
}
```

`Menu`는 주문 가능한 상품 정보를 나타낸다. 메뉴명, 가격, 판매 상태를 가진다.

`MenuStatus`는 enum으로 분리했다.

```java
public enum MenuStatus {
    ON_SALE,
    SOLD_OUT
}
```

상태값을 문자열로 저장하기 위해 `@Enumerated(EnumType.STRING)`을 사용했다. `ORDINAL`로 저장하면 enum 순서가 바뀔 때 데이터 의미가 깨질 수 있다. 실무에서는 enum을 DB에 저장할 때 보통 `STRING` 방식을 선호한다.

### 2. 요청 DTO와 Bean Validation

```java
public record MenuCreateRequest(
        @NotBlank String name,
        @Positive long price
) {
}
```

메뉴 등록 요청은 메뉴명과 가격을 받는다.

- `@NotBlank`: 메뉴명이 비어 있거나 공백만 들어오는 것을 막는다.
- `@Positive`: 가격이 0 이하인 것을 막는다.

이 검증은 Controller의 `@Valid`와 함께 동작한다.

```java
public ResponseEntity<ApiResponse<MenuResponse>> create(
        @Valid @RequestBody MenuCreateRequest request
) {
    ...
}
```

검증 실패 시 `MethodArgumentNotValidException`이 발생하고, `GlobalExceptionHandler`에서 `INVALID_REQUEST` 응답으로 변환한다.

### 3. Repository 쿼리 메서드

```java
public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findAllByStatusOrderByIdAsc(MenuStatus status);
}
```

Spring Data JPA의 쿼리 메서드를 사용했다. 메서드 이름만으로 다음 조건을 표현한다.

- 특정 상태의 메뉴만 조회
- ID 오름차순 정렬

직접 JPQL을 작성하지 않아도 간단한 조회 조건은 쿼리 메서드로 충분하다. 반대로 조건이 복잡해지거나 성능 튜닝이 필요하면 JPQL, Querydsl, Native Query 등을 고려할 수 있다.

### 4. Service 계층

```java
@Transactional
public MenuResponse create(MenuCreateRequest request) {
    Menu menu = menuRepository.save(new Menu(request.name(), request.price()));
    return MenuResponse.from(menu);
}
```

메뉴 등록은 DB에 데이터를 저장하는 쓰기 작업이므로 일반 `@Transactional`을 사용했다.

```java
@Transactional(readOnly = true)
public MenuListResponse findOnSaleMenus() {
    List<MenuResponse> menus = menuRepository.findAllByStatusOrderByIdAsc(MenuStatus.ON_SALE)
            .stream()
            .map(MenuResponse::from)
            .toList();
    return new MenuListResponse(menus);
}
```

조회 기능은 `@Transactional(readOnly = true)`를 사용했다. 읽기 전용 트랜잭션은 변경 감지 비용을 줄이고, 의도를 명확히 표현할 수 있다.

```java
@Transactional(readOnly = true)
public MenuResponse findById(Long menuId) {
    Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));
    return MenuResponse.from(menu);
}
```

단건 조회에서 메뉴가 없으면 `BusinessException`을 던진다. Controller가 직접 예외 응답을 만들지 않고, 전역 예외 처리에서 공통 응답으로 변환한다.

### 5. Controller 계층

```java
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    @PostMapping
    public ResponseEntity<ApiResponse<MenuResponse>> create(...) {
        ...
    }

    @GetMapping
    public ApiResponse<MenuListResponse> findMenus() {
        ...
    }

    @GetMapping("/{menuId}")
    public ApiResponse<MenuResponse> findMenu(@PathVariable Long menuId) {
        ...
    }
}
```

Controller는 HTTP 요청을 받고 Service를 호출한 뒤 공통 응답으로 감싸 반환한다. 비즈니스 판단은 Service가 담당한다.

메뉴 등록은 새 리소스를 생성하므로 `201 Created`를 반환한다.

```java
return ResponseEntity
        .created(URI.create("/api/v1/menus/" + response.menuId()))
        .body(ApiResponse.success(response));
```

조회 API는 정상 조회이므로 `200 OK`를 반환한다.

## 오늘 새롭게 배운 내용

### 1. 빈 목록은 예외가 아니다

메뉴 목록 조회에서 메뉴가 하나도 없는 경우 `MENU_NOT_FOUND` 예외를 던지지 않고 빈 배열을 반환했다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "menus": []
  }
}
```

목록 조회 결과가 0건인 것은 요청 실패가 아니라 정상 조회 결과다. 반면 단건 조회에서 특정 `menuId`가 없으면 클라이언트가 요청한 리소스가 존재하지 않는 것이므로 `404 Not Found`가 적절하다.

### 2. Controller 테스트와 Service 테스트의 목적은 다르다

Controller 테스트는 HTTP 관점에서 상태 코드와 JSON 응답을 검증한다.

```java
mockMvc.perform(get("/api/v1/menus/{menuId}", 999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
```

Service 테스트는 비즈니스 로직과 예외 발생을 검증한다.

```java
assertThatThrownBy(() -> menuService.findById(999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MENU_NOT_FOUND);
```

Repository 테스트는 DB 조회 메서드가 원하는 조건과 정렬을 만족하는지 검증한다.

### 3. DTO를 사용하면 API 응답 구조를 엔티티와 분리할 수 있다

API 응답에서 엔티티를 그대로 반환하지 않고 `MenuResponse`를 사용했다.

```java
public record MenuResponse(
        Long menuId,
        String name,
        long price
) {
    public static MenuResponse from(Menu menu) {
        return new MenuResponse(menu.getId(), menu.getName(), menu.getPrice());
    }
}
```

엔티티를 그대로 반환하면 내부 필드가 API에 노출될 수 있고, 엔티티 구조 변경이 API 변경으로 이어질 수 있다. DTO를 사용하면 API 계약을 더 안정적으로 유지할 수 있다.

## 어려웠던 점

### 1. 단건 조회와 목록 조회의 예외 정책을 구분해야 했다

처음에는 메뉴가 없을 때 모두 예외로 처리해야 하는지 고민할 수 있다. 하지만 단건 조회와 목록 조회는 의미가 다르다.

- `GET /api/v1/menus/{menuId}`: 특정 리소스를 요청했는데 없으므로 `404`
- `GET /api/v1/menus`: 조건에 맞는 결과가 없을 뿐이므로 `200`과 빈 배열

이 차이를 명확히 정리하는 것이 중요했다.

### 2. 테스트 데이터가 서로 영향을 주지 않게 해야 했다

Controller/Service 테스트에서 같은 H2 DB를 사용하므로 테스트 간 데이터가 남아 있으면 결과가 흔들릴 수 있다. 그래서 `@BeforeEach`에서 `menuRepository.deleteAll()`을 호출해 테스트 데이터를 초기화했다.

```java
@BeforeEach
void setUp() {
    menuRepository.deleteAll();
}
```

실무에서는 테스트 격리를 위해 트랜잭션 롤백, SQL 초기화, 테스트 픽스처 전략 등을 함께 고려한다.

## 어떻게 해결했는가

- 단건 조회 API `GET /api/v1/menus/{menuId}`를 추가했다.
- Service에서 `findById` 실패 시 `BusinessException(ErrorCode.MENU_NOT_FOUND)`를 던지도록 했다.
- `GlobalExceptionHandler`가 `MENU_NOT_FOUND`를 공통 실패 응답으로 변환하도록 기존 구조를 재사용했다.
- 목록 조회는 결과가 없더라도 빈 배열을 반환하도록 유지했다.
- Controller, Service, Repository 테스트를 각각 추가해 계층별 책임을 검증했다.

검증 명령:

```bash
./gradlew test
```

결과:

```text
BUILD SUCCESSFUL
```

## 다음에 공부할 내용

- 관리자 API와 사용자 API를 분리하는 기준
- 메뉴 가격 변경 시 주문 시점 가격을 보존하는 방법
- Soft Delete와 판매 상태 관리 전략
- `@WebMvcTest`와 `@SpringBootTest`의 차이
- 테스트 픽스처 관리 방법
- 목록 조회에 페이징을 적용하는 방법

## 면접에서 나올 수 있는 질문

### Q1. 왜 엔티티를 API 응답으로 바로 반환하지 않았나요?

A. 엔티티는 DB 모델이고 API 응답은 외부 계약입니다. 엔티티를 그대로 반환하면 내부 필드가 노출되거나 엔티티 변경이 API 변경으로 이어질 수 있습니다. DTO를 사용하면 API 응답 구조를 안정적으로 관리할 수 있습니다.

### Q2. 메뉴 목록이 비어 있을 때 왜 404가 아니라 200을 반환하나요?

A. 목록 조회에서 결과가 0건인 것은 요청한 경로가 잘못된 것이 아니라 정상적인 조회 결과입니다. 그래서 `200 OK`와 빈 배열을 반환하는 것이 자연스럽습니다. 반면 단건 조회에서 특정 ID가 없으면 해당 리소스가 없으므로 `404 Not Found`를 반환합니다.

### Q3. `@Transactional(readOnly = true)`를 조회 메서드에 붙인 이유는 무엇인가요?

A. 조회 전용 트랜잭션임을 명확히 표현하고, JPA에서 불필요한 변경 감지 비용을 줄일 수 있기 때문입니다. 또한 Service 메서드의 의도를 코드에서 바로 파악할 수 있습니다.

### Q4. Spring Data JPA 쿼리 메서드의 장단점은 무엇인가요?

A. 간단한 조건 조회는 메서드 이름만으로 빠르게 구현할 수 있어 생산성이 좋습니다. 하지만 조건이 복잡해지면 메서드 이름이 길어지고 가독성이 떨어질 수 있습니다. 복잡한 쿼리는 JPQL, Querydsl, Native Query 등을 고려하는 것이 좋습니다.

### Q5. Controller 테스트, Service 테스트, Repository 테스트를 각각 작성한 이유는 무엇인가요?

A. 각 계층의 책임이 다르기 때문입니다. Controller 테스트는 HTTP 요청/응답, Service 테스트는 비즈니스 로직과 예외, Repository 테스트는 DB 조회 조건과 정렬을 검증합니다. 계층별 테스트를 작성하면 문제가 발생했을 때 원인을 더 쉽게 찾을 수 있습니다.

### Q6. 메뉴 등록 API에서 왜 `201 Created`를 반환하나요?

A. 새로운 메뉴 리소스가 생성되었기 때문입니다. HTTP 상태 코드 의미상 리소스 생성 성공에는 `201 Created`가 적절하고, `Location` 헤더에 생성된 리소스 URI를 담을 수도 있습니다.

## 참고 자료

- Spring Web MVC Documentation
- Spring Data JPA Reference Documentation
- Bean Validation Documentation
- JPA Entity Mapping Documentation
- Spring Boot Testing Documentation
