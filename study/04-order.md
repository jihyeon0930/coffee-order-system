# 주문 생성 및 주문 내역 저장 학습 노트

## 이번 이슈에서 구현한 기능

- 주문과 주문 항목 모델링
- 서버 기준 주문 금액 계산과 포인트 결제
- 포인트 차감과 주문 저장을 하나의 트랜잭션으로 처리
- 주문 단건 조회와 회원별 주문 목록 조회
- 주문 완료 Spring 이벤트 발행
- 정상 주문, 결제 실패 롤백, 조회, 이벤트, HTTP API 테스트

## 왜 필요한 기능인가

주문은 메뉴 선택, 가격 계산, 결제, 이력 저장이라는 여러 도메인 작업을 하나의 사용자 행동으로 묶는다. 결제만 성공하고 주문이 저장되지 않거나, 주문은 저장됐지만 포인트가 차감되지 않으면 데이터 정합성이 깨진다. 따라서 이번 구현의 핵심은 단순 CRUD가 아니라 모든 변경을 하나의 트랜잭션 경계 안에서 성공하거나 실패하게 만드는 것이다.

과거 주문은 거래 당시의 사실이어야 한다. 메뉴 이름이나 가격이 나중에 바뀌더라도 이미 완료된 주문 금액과 표시 내용은 변하면 안 되므로 주문 항목에 메뉴 ID뿐 아니라 메뉴명과 단가를 함께 저장했다.

## 사용한 기술

| 기술 | 사용 목적 |
| --- | --- |
| Spring Data JPA | 주문과 주문 항목 영속화, 조회 |
| `@OneToMany`와 Cascade | 주문 애그리거트 단위로 항목 저장 |
| `@Transactional` | 포인트 차감과 주문 저장의 원자성 보장 |
| 비관적 락 | 동일 회원의 동시 포인트 결제 직렬화 |
| Spring Application Event | 주문 완료 후속 처리와 핵심 주문 흐름 분리 |
| Bean Validation | 빈 항목 목록과 0 이하 수량을 API 경계에서 차단 |
| `@EntityGraph` | 주문 조회 시 항목을 명시적으로 함께 로딩 |
| JUnit 5, AssertJ, MockMvc | 서비스 흐름과 HTTP 계약 검증 |

## 핵심 코드 설명

### 주문 금액은 서버에서 계산한다

```java
public void addItem(Menu menu, int quantity) {
    OrderItem item = OrderItem.from(menu, quantity);
    item.setOrder(this);
    items.add(item);
    totalAmount = Math.addExact(totalAmount, item.getLineAmount());
}
```

요청에는 메뉴 ID와 수량만 받는다. 가격은 DB에서 조회한 `Menu`를 기준으로 계산하므로 클라이언트가 가격을 조작할 수 없다. 항목 금액은 `단가 × 수량`, 총액은 모든 항목 금액의 합이며 `multiplyExact`, `addExact`로 정수 오버플로도 조용히 통과하지 않게 했다.

### 주문 항목에 가격 스냅샷을 저장한다

`OrderItem`은 `menuId`, `menuName`, `unitPrice`, `quantity`, `lineAmount`를 저장한다. `Menu` 엔티티를 직접 연관관계로 보관하고 조회할 때마다 현재 가격을 읽는 방식은 메뉴 변경이 과거 주문 표현에 영향을 준다. 스냅샷 방식은 데이터가 일부 중복되지만 주문 당시의 거래 사실을 안정적으로 보존한다.

### 트랜잭션 경계

```java
@Transactional
public OrderResponse create(OrderCreateRequest request) {
    // 메뉴 검증과 금액 계산
    pointService.use(request.memberId(), order.getTotalAmount());
    Order savedOrder = orderRepository.save(order);
    eventPublisher.publishEvent(new OrderCompletedEvent(...));
    return OrderResponse.from(savedOrder);
}
```

`OrderService.create()`가 전체 유스케이스의 트랜잭션 경계다. 이미 `@Transactional`인 `PointService.use()`는 기본 전파 속성 `REQUIRED`에 따라 주문 트랜잭션에 참여한다. 따라서 포인트 차감 뒤 주문 저장에서 unchecked 예외가 발생하면 두 변경이 함께 롤백된다.

포인트 차감을 주문 서비스가 직접 회원 엔티티에 수행하지 않고 기존 `PointService`를 재사용한 이유는 잔액 검증과 비관적 락 규칙을 한곳에 유지하기 위해서다. 주문 서비스는 흐름을 조율하고 각 도메인 규칙은 해당 객체와 서비스가 담당한다.

### 조회 모델

JPA 연관관계는 기본적으로 지연 로딩하고, 응답에 항목이 필요한 Repository 메서드에만 `@EntityGraph(attributePaths = "items")`를 적용했다. 모든 조회를 즉시 로딩으로 만들지 않으면서 트랜잭션 밖 직렬화 시 지연 로딩 예외와 주문별 추가 쿼리 문제를 피한다.

### 주문 완료 이벤트

이벤트에는 후속 처리에 필요한 주문 ID, 회원 ID, 총액만 넣었다. 엔티티 자체를 전달하지 않아 영속성 컨텍스트 생명주기와 지연 로딩에 결합되지 않는다. 이벤트 발행은 주문 핵심 흐름에서 랭킹, 알림 같은 후속 관심사를 분리하는 출발점이다.

## 오늘 새롭게 배운 내용

### 트랜잭션 스크립트와 애그리거트의 역할 차이

`OrderService`는 여러 저장소와 도메인을 연결하는 유스케이스 흐름을 담당하고, `Order`는 항목 추가와 금액 합산으로 자기 상태의 일관성을 지킨다. 모든 계산을 서비스에 두는 트랜잭션 스크립트 방식은 초기에 단순하지만 규칙이 늘면 서비스가 비대해진다. 반대로 엔티티가 Repository나 결제 서비스까지 직접 호출하게 만들면 인프라에 결합된다. 이번 구현은 도메인 내부 규칙은 엔티티에, 외부 협력 순서는 서비스에 두었다.

### 하나의 DB 트랜잭션이 보장하는 범위

현재 포인트와 주문은 같은 데이터베이스를 사용하므로 로컬 트랜잭션으로 원자성을 보장할 수 있다. 결제가 외부 PG이거나 주문 저장소가 별도 DB라면 `@Transactional` 하나로 묶을 수 없다. 그런 환경에서는 결제 취소 같은 보상 트랜잭션, Saga, 멱등 키가 필요하다.

### Spring 이벤트와 Outbox의 차이

`ApplicationEventPublisher`는 프로세스 내부 결합도를 낮추지만, DB 커밋과 외부 메시지 브로커 전송을 원자적으로 보장하지 않는다. 이벤트를 Kafka로 바로 보내면 DB 커밋 후 전송 실패 또는 전송 후 DB 롤백 같은 이중 쓰기 문제가 생길 수 있다.

Transactional Outbox는 주문과 Outbox 레코드를 같은 DB 트랜잭션으로 저장하고 별도 발행기가 브로커로 전달한다. 구현 복잡도와 운영 요소가 늘지만 재시도와 전달 신뢰성이 필요한 외부 이벤트에 더 적합하다. 이번 Issue는 Spring 이벤트 발행까지로 제한하고 Outbox는 후속 개선으로 남겼다.

### 가격 스냅샷과 정규화의 차이

메뉴 테이블만 참조하는 완전 정규화 구조는 중복이 적지만 과거 가격을 복원하려면 가격 이력 테이블과 유효 기간 조회가 필요하다. 주문 항목 스냅샷은 중복을 허용하는 대신 조회가 단순하고 거래 당시 값을 직접 보존한다. 주문 내역처럼 변경되면 안 되는 기록에는 의도적인 비정규화가 실용적이다.

## 어려웠던 점

- 포인트 차감과 주문 저장의 트랜잭션 경계를 어느 서비스에 둘지 결정해야 했다.
- 주문 응답에 항목을 포함하면서 지연 로딩과 불필요한 쿼리를 피해야 했다.
- 이벤트 발행 사실과 외부 시스템 전달 보장을 구분해야 했다.
- 테스트에서 JSON 숫자가 값 크기에 따라 `Integer` 또는 `Long`으로 역직렬화될 수 있어 특정 타입 캐스팅을 피해야 했다.

## 어떻게 해결했는가

- 전체 유스케이스를 아는 `OrderService.create()`를 가장 바깥 트랜잭션 경계로 정했다.
- 기존 `PointService.use()`의 비관적 락과 잔액 규칙을 그대로 재사용하고 같은 트랜잭션에 참여시켰다.
- 조회 전용 메서드에는 `readOnly = true`, 항목이 필요한 Repository 메서드에는 `@EntityGraph`를 적용했다.
- 포인트 부족 시 잔액, 주문 건수, 이벤트 부재를 함께 검증하고, 동기 완료 이벤트 리스너가 예외를 던지는 경우에는 이미 차감된 포인트와 저장된 주문이 모두 롤백되는지 확인했다.
- 컨트롤러 테스트의 주문 ID는 구체 타입 대신 `Number`로 읽고 `longValue()`로 변환했다.

## 다음에 공부할 내용

- `@TransactionalEventListener`의 `BEFORE_COMMIT`, `AFTER_COMMIT` 실행 시점
- Transactional Outbox와 CDC 기반 Kafka 발행
- 주문 요청 ID와 unique 제약을 이용한 멱등 주문 생성
- 결제와 주문 저장소가 분리됐을 때 Saga와 보상 트랜잭션
- 주문 상태 전이(`PENDING`, `COMPLETED`, `CANCELED`)와 취소 정책
- 다수 메뉴 재고 차감 시 교착을 피하는 일관된 잠금 순서
- 페이징과 DTO Projection을 이용한 대량 주문 목록 조회

## 참고 자료

- Spring Framework Transaction Management Documentation
- Spring Framework Application Events Documentation
- Spring Data JPA EntityGraph Documentation
- Jakarta Persistence Specification
- Martin Fowler, Event Sourcing / Data Transfer Object / Unit of Work 관련 글
- Microservices Patterns, Transactional Outbox Pattern
