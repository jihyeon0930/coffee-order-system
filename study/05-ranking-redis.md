# 이번 이슈에서 구현한 기능

완료된 주문을 모아 가장 많이 팔린 메뉴 10개를 조회하는 API를 만들었다.

- API: `GET /api/v1/menus/popular`
- 집계 기준: 완료 주문의 메뉴별 총 주문 수량
- 정렬: 총수량 내림차순 → 주문 횟수 내림차순 → 메뉴 ID 오름차순
- 캐시: Redis, 키 `popular-menus:v1:top:10`, TTL 5분
- 갱신: 주문 트랜잭션 커밋 후 기존 캐시 삭제
- 장애 대응: Redis를 사용할 수 없으면 DB 집계 결과 반환

## 전체 흐름 한눈에 보기

```text
조회 요청
  → Redis에 값이 있는가?
      → 있다: Redis 값 반환
      → 없다/Redis 장애: DB 집계 → Redis 저장 시도 → DB 결과 반환

주문 완료
  → 주문 트랜잭션 커밋 성공
  → 인기 메뉴 캐시 삭제
  → 다음 조회에서 DB로 최신 순위 재계산
```

## 왜 필요한 기능인가

인기 메뉴는 주문 행을 메뉴별로 모으고 수량을 더한 뒤 정렬해야 한다. 데이터가 많아질수록 단건 조회보다 일이 많다. 그런데 인기 메뉴 결과는 많은 사용자가 반복해서 조회한다. 매번 같은 계산을 DB에 시키는 대신 결과를 잠시 Redis에 저장하면 DB의 반복 작업을 줄일 수 있다.

DB는 원본 주문을 안전하게 보관하는 곳이고 Redis는 다시 계산할 수 있는 조회 결과를 잠시 보관하는 곳이다. 따라서 Redis 데이터가 사라져도 DB에서 다시 만들 수 있어야 한다.

## 인기 메뉴 집계란 무엇인가

집계는 여러 행을 의미 있는 묶음으로 만들고 합계나 개수를 구하는 작업이다. 이 프로젝트에서는 `orders` 한 행이 주문 하나이고 `order_items` 각 행이 주문에 들어간 메뉴 하나다.

한 주문에 아메리카노 2잔과 라테 1잔이 있으면 다음처럼 반영된다.

```text
아메리카노 총수량 +2, 주문 횟수 +1
라테       총수량 +1, 주문 횟수 +1
```

같은 주문에서 같은 메뉴를 두 줄로 보내는 것은 기존 주문 검증이 막는다. 그러므로 이 프로젝트에서 메뉴별 `COUNT`는 해당 메뉴가 포함된 주문 횟수와 같은 의미다.

집계 대상은 `COMPLETED` 주문뿐이다. `CANCELED` 주문은 실제 판매로 볼 수 없으므로 제외한다. 취소된 100잔 주문이 인기 순위에 들어가면 고객에게 잘못된 정보를 보여주게 된다.

조회 기간은 이번 Issue에서 요구하지 않았으므로 전체 기간 누적을 사용했다. 최근 7일 같은 기간 기능을 추가하면 유용하지만, 기간마다 캐시 키가 달라지고 삭제 범위도 넓어진다. 이는 별도 요구사항으로 다루는 편이 안전하다.

## 집계 쿼리 기초

### GROUP BY

`GROUP BY`는 같은 값을 가진 행을 한 묶음으로 만든다. `GROUP BY menu_id, menu_name`은 같은 메뉴의 주문 항목들을 하나의 그룹으로 만든다.

메뉴 이름도 함께 묶은 이유는 응답에 주문 당시 메뉴 이름을 보여주기 위해서다. `OrderItem`에는 주문 시점의 이름과 가격이 스냅샷으로 저장되어 있다. 메뉴가 나중에 수정되어도 과거 주문 기록은 바뀌지 않는다.

### COUNT와 SUM

`COUNT`는 행 개수를 센다. 이 쿼리의 `COUNT(o.id)`는 해당 메뉴가 몇 개 주문에 포함되었는지 센다.

`SUM`은 숫자를 더한다. `SUM(oi.quantity)`는 판매한 잔 수를 더한다. 인기 기준은 주문 횟수보다 실제 판매 수량이 더 자연스러워 `SUM(quantity)`를 첫 번째 기준으로 선택했다.

예를 들어 A 메뉴가 한 주문에서 10잔, B 메뉴가 5개 주문에서 각 1잔 팔렸다면 다음과 같다.

```text
A: 총수량 10, 주문 횟수 1
B: 총수량  5, 주문 횟수 5
```

이번 기준에서는 A가 더 높은 순위다.

### ORDER BY

`ORDER BY`는 결과 순서를 정한다. `DESC`는 큰 값부터, `ASC`는 작은 값부터 정렬한다.

1. 총수량 `DESC`
2. 총수량이 같으면 주문 횟수 `DESC`
3. 둘 다 같으면 메뉴 ID `ASC`

마지막 기준까지 둔 이유는 동점 결과가 요청마다 바뀌는 것을 막기 위해서다. 순서가 고정되어야 테스트와 캐시 결과도 예측 가능하다.

### 실제 프로젝트 쿼리 해석

```java
select oi.menuId as menuId,
       oi.menuName as menuName,
       sum(oi.quantity) as totalQuantity,
       count(o.id) as orderCount
from OrderItem oi
join oi.order o
where o.status = :status
group by oi.menuId, oi.menuName
order by sum(oi.quantity) desc, count(o.id) desc, oi.menuId asc
```

1. 주문 항목에서 시작한다.
2. 각 항목의 주문과 조인한다.
3. 상태가 `COMPLETED`인 주문만 남긴다.
4. 같은 메뉴를 묶는다.
5. 수량 합계와 주문 개수를 구한다.
6. 인기 기준으로 정렬한다.
7. `PageRequest.of(0, 10)`으로 상위 10개만 가져온다.

엔티티 전체를 메모리에 읽어 Java 반복문으로 계산하지 않는다. DB가 집계한 네 값만 projection으로 받는다. 그래서 불필요한 전체 테이블 객체 생성과 메뉴별 추가 조회, 즉 N+1 문제를 피한다.

## Redis란 무엇인가

Redis는 데이터를 메모리에 저장해 매우 빠르게 읽고 쓸 수 있는 저장소다. 이 프로젝트에서는 주문의 원본 저장소가 아니라 인기 메뉴 조회 결과의 임시 저장소로 쓴다.

메모리는 빠르지만 Redis 장애나 만료로 값이 없어질 수 있다. 그래서 “Redis 값은 없어져도 DB에서 다시 계산할 수 있다”는 원칙이 중요하다.

## 캐시를 적용하지 않았을 때의 흐름

```text
요청 1 → DB 조인/그룹/합계/정렬
요청 2 → DB 조인/그룹/합계/정렬
요청 3 → DB 조인/그룹/합계/정렬
```

결과가 같아도 매번 DB가 같은 계산을 한다.

## 캐시를 적용한 뒤의 흐름

```text
요청 1 → Redis에 없음 → DB 계산 → Redis 저장 → 반환
요청 2 → Redis 결과 반환
요청 3 → Redis 결과 반환
```

첫 요청 이후에는 TTL 만료나 캐시 삭제 전까지 DB 집계 횟수를 줄일 수 있다.

## 캐시 히트와 캐시 미스

캐시에서 값을 찾은 경우를 **캐시 히트(Cache Hit)**라고 한다. 히트이면 DB 집계 쿼리를 실행하지 않는다.

캐시에 값이 없는 경우를 **캐시 미스(Cache Miss)**라고 한다. 미스이면 DB에서 조회하고 그 결과를 캐시에 저장한다. 빈 목록도 정상 결과이므로 JSON `[]`로 저장된다. 그러면 주문이 하나도 없을 때도 매 요청마다 DB를 조회하지 않는다.

Redis 장애도 이 구현에서는 캐시 미스처럼 처리한다. 로그는 남기지만 DB 조회를 계속한다.

## Cache Aside 전략

Cache Aside는 애플리케이션이 캐시와 DB를 직접 순서대로 조회하는 방식이다.

```text
캐시 조회 → 없으면 DB 조회 → 캐시 저장
```

장점은 구조가 단순하고 Redis 장애가 원본 DB 쓰기에 영향을 주지 않도록 분리하기 쉽다는 것이다. 단점은 첫 요청이 느리고, DB와 캐시가 잠시 다를 수 있다는 것이다.

`@Cacheable` 하나에 모든 동작을 숨기지 않고 `PopularMenuCache` 인터페이스를 만들었다. 덕분에 캐시 키, JSON 직렬화, TTL, 장애 처리, 삭제 시점을 코드와 테스트에서 분명하게 확인할 수 있다.

## TTL이 필요한 이유

TTL(Time To Live)은 캐시가 살아 있는 시간이다. 이번 값은 5분이다.

TTL이 필요한 이유는 다음과 같다.

- 삭제 이벤트를 놓쳐도 오래된 결과가 영원히 남지 않는다.
- 더 이상 쓰지 않는 키가 Redis 메모리를 계속 차지하지 않는다.
- 일정 시간이 지나면 DB 원본으로 결과를 다시 검증한다.

TTL은 읽을 때마다 늘어나지 않는다. 저장하거나 다시 쓸 때 5분으로 설정되고, 그 뒤 5분이 지나면 만료된다.

## 캐시 무효화가 필요한 이유

캐시를 만든 직후 새 주문이 완료되면 캐시 값은 실제 DB보다 과거 값이다. TTL만 기다리면 최대 5분 동안 낡은 순위를 보여줄 수 있다.

캐시 무효화는 “이 값은 이제 오래되었으니 삭제하자”라는 뜻이다. 주문 완료 후 캐시를 삭제하면 다음 조회가 DB에서 최신 결과를 다시 계산한다.

## 이번 프로젝트에서 선택한 캐시 갱신 방식

선택한 방식은 **Cache Aside + 주문 완료 시 삭제 + TTL**이다.

- Write Through: 쓰기마다 DB와 캐시를 같이 갱신해 최신성이 좋지만 주문 경로가 캐시에 강하게 묶인다.
- Write Behind: 캐시에 먼저 쓰고 DB에 나중에 반영해 빠르지만 데이터 유실과 동기화 위험이 주문에 너무 크다.
- 주기적 갱신: 스케줄러가 단순 조회를 만들 수 있지만 갱신 간격 동안 오래된 값이 보이고 별도 작업이 필요하다.
- 주문 완료 시 직접 갱신: 즉시 최신이지만 동시 주문 때 수량과 순위를 안전하게 바꾸는 로직이 복잡하다.
- 주문 완료 시 삭제: 다음 조회 한 번이 DB를 사용하지만 구현이 단순하고 DB 원본과 맞추기 쉽다.

캐시 삭제는 `@TransactionalEventListener(phase = AFTER_COMMIT)`에서 실행한다. 주문 저장이 롤백되었다면 데이터는 변하지 않았으므로 캐시도 삭제할 필요가 없다. `@Async`는 적용하지 않았다. 삭제 한 번은 짧고, 비동기로 바꾸면 실행 실패 추적과 테스트가 복잡해진다. 캐시 구현 자체가 Redis 예외를 흡수하므로 삭제 실패가 성공한 주문 응답을 실패로 바꾸지 않는다.

## Redis 장애가 발생하면 어떻게 되는가

`RedisPopularMenuCache.get()`은 연결 또는 데이터 접근 예외가 발생하면 `Optional.empty()`를 반환한다. 서비스는 이를 캐시 미스로 보고 DB 집계 쿼리를 실행한다.

저장과 삭제가 실패해도 예외를 호출자에게 전파하지 않고 경고 로그를 남긴다. 따라서 Redis가 꺼져도 인기 메뉴 조회와 주문 완료는 동작한다. 다만 모든 인기 메뉴 요청이 DB로 가므로 DB 부하가 늘어난다. 운영 환경에서는 Redis 장애 알림과 DB 부하 모니터링이 필요하다.

JSON이 깨져 역직렬화에 실패한 경우도 미스로 처리해 DB에서 복구한다. 다음 저장이 성공하면 올바른 JSON으로 교체된다.

## 인덱스를 적용한 이유

인덱스는 책 뒤의 찾아보기와 비슷하다. 모든 페이지를 읽는 대신 원하는 행의 위치를 빨리 찾도록 돕는다.

추가한 인덱스는 다음과 같다.

```text
orders:      idx_orders_status_ordered_at(status, ordered_at)
order_items: idx_order_items_order_menu(order_id, menu_id)
```

첫 번째 인덱스는 완료 상태 주문을 찾는 조건을 돕는다. `ordered_at`은 현재 전체 기간 쿼리에서는 사용하지 않는다. 향후 `status = ? AND ordered_at >= ?` 기간 조건이 생길 때 상태 동등 조건 뒤에서 범위 검색을 돕도록 두 번째에 배치했다. 상태 값 종류가 적고 완료 주문 비율이 매우 높으면 MySQL이 전체 스캔을 더 싸다고 판단할 수도 있다. 인덱스가 있다고 반드시 선택되는 것은 아니다.

두 번째 인덱스는 `order_id`로 주문과 주문 항목을 연결할 때 사용하기 위한 것이다. `menu_id`는 같은 주문 내부의 메뉴 식별을 이어서 지원한다. MySQL/InnoDB는 외래 키 인덱스를 자동 생성할 수 있지만 명시적인 이름과 컬럼 구성을 엔티티에 남겼다.

복합 인덱스는 왼쪽부터 사용한다. `(status, ordered_at)`은 `status` 또는 `status + ordered_at` 조건에 유리하지만 `ordered_at`만 조회할 때는 앞부분을 건너뛰므로 효과가 제한될 수 있다. 이것이 컬럼 순서가 중요한 이유다.

인덱스를 무조건 많이 만들면 안 된다. 주문 INSERT, 상태 UPDATE, DELETE 때 관련 인덱스도 갱신해야 하므로 쓰기가 느려지고 저장 공간과 메모리를 더 쓴다.

실제 MySQL 데이터에서 적용 전후를 비교할 때는 같은 데이터와 조건으로 `EXPLAIN ANALYZE`를 실행하고 다음을 본다.

```sql
EXPLAIN ANALYZE
SELECT oi.menu_id, oi.menu_name,
       SUM(oi.quantity) AS total_quantity,
       COUNT(o.id) AS order_count
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status = 'COMPLETED'
GROUP BY oi.menu_id, oi.menu_name
ORDER BY total_quantity DESC, order_count DESC, oi.menu_id ASC
LIMIT 10;
```

- 실제 선택된 인덱스
- 읽은 행 수
- 실행 시간
- 임시 테이블과 정렬 사용 여부

이번 자동 테스트는 H2 인메모리 DB를 사용하므로 MySQL 운영 데이터 분포의 실행 계획 전후 수치를 정직하게 재현할 수 없다. 따라서 존재하지 않는 성능 수치를 만들지 않았고, 실제 MySQL에 충분한 샘플 데이터를 넣은 뒤 위 명령으로 측정하는 일을 보완 과제로 남겼다.

## 사용한 기술

- Spring Data JPA와 JPQL 집계
- interface projection
- Spring Data Redis의 `StringRedisTemplate`
- Jackson JSON 직렬화/역직렬화
- Spring `ApplicationEventPublisher`
- `@TransactionalEventListener(AFTER_COMMIT)`
- JUnit 5, AssertJ, Mockito, MockMvc, H2

## 핵심 코드 설명

### `PopularMenuRepository`

역할은 완료 주문의 메뉴별 수량과 횟수를 DB에서 한 번에 집계하는 것이다. 캐시 미스 때 호출된다. `@Query`는 메서드 이름만으로 표현하기 어려운 그룹 집계 JPQL을 직접 적는 애너테이션이다. 이 코드가 없으면 모든 주문 항목을 메모리에 읽어 직접 계산해야 해 데이터가 많을 때 메모리와 네트워크를 낭비한다.

초보자는 `COUNT`와 `SUM(quantity)`를 같은 것으로 오해하기 쉽다. 3잔을 한 번 주문하면 `COUNT`는 1, `SUM`은 3이다.

### `PopularMenuService`

역할은 캐시 우선 조회 흐름을 조정하는 것이다. 컨트롤러가 인기 메뉴 API 요청을 받을 때 호출된다. `@Transactional(readOnly = true)`는 DB 변경이 없는 조회 작업임을 나타낸다. 캐시가 있으면 repository를 부르지 않고, 없으면 DB 결과에 1부터 순위를 붙여 저장한다.

이 코드가 없으면 컨트롤러가 Redis와 DB 세부사항을 모두 알아야 한다. 초보자는 `readOnly`가 캐시 쓰기도 금지한다고 오해할 수 있지만, 이것은 주로 JPA DB 트랜잭션에 대한 힌트이며 별도 Redis 저장은 수행된다.

### `RedisPopularMenuCache`

역할은 Java 목록과 Redis 문자열 사이를 연결하는 것이다. 조회, 저장, 삭제 시 호출된다. `StringRedisTemplate`은 문자열 키와 값을 Redis에 읽고 쓰는 Spring 도구다. `ObjectMapper`는 Java record 목록을 JSON으로 바꾸고 다시 복원한다.

저장할 때 키, JSON, `Duration`을 한 번의 `set` 호출에 전달해 TTL을 함께 설정한다. 예외 처리 코드가 없으면 Redis가 꺼졌을 때 인기 메뉴 조회나 주문 완료까지 실패할 수 있다.

초보자는 예외를 무시하면 된다고 생각하기 쉽다. 여기서는 원본 데이터가 DB에 있고 캐시는 다시 만들 수 있기 때문에만 예외를 흡수한다. 결제 DB 오류까지 같은 방식으로 무시하면 절대 안 된다.

### `PopularMenuCacheInvalidator`

역할은 주문이 실제로 커밋된 뒤 오래된 캐시를 삭제하는 것이다. `@TransactionalEventListener`는 이벤트를 현재 트랜잭션 단계와 연결하고 `AFTER_COMMIT`은 성공적인 커밋 뒤에만 실행하게 한다.

이 코드가 없으면 새 주문 뒤에도 최대 TTL 동안 이전 순위가 보인다. 일반 `@EventListener`를 쓰면 주문이 나중에 롤백되더라도 캐시를 먼저 지울 수 있다.

### 엔티티의 `@Index`

`@Table(indexes = ...)`는 Hibernate가 스키마를 만들거나 갱신할 때 인덱스 정의를 전달한다. 이 코드가 없더라도 기능 결과는 맞지만 데이터가 많을 때 조인과 필터가 느려질 수 있다. 반대로 운영 DB에서는 자동 `ddl-auto=update`에만 의존하기보다 Flyway 같은 마이그레이션 도구로 검토·배포하는 것이 더 안전하다.

## 요청부터 응답까지의 전체 실행 순서

1. 클라이언트가 `GET /api/v1/menus/popular`을 호출한다.
2. `PopularMenuController`가 `PopularMenuService`를 호출한다.
3. 서비스가 `PopularMenuCache`를 통해 Redis 키를 조회한다.
4. 값이 있으면 캐시 데이터를 바로 응답한다. 이것이 캐시 히트다.
5. 값이 없거나 Redis 조회가 실패하면 repository의 DB 집계 쿼리를 실행한다. 이것이 캐시 미스 처리다.
6. DB는 완료 주문만 골라 메뉴별 수량과 주문 횟수를 계산하고 정렬한다.
7. 서비스는 최대 10개 결과에 1부터 순위를 붙인다.
8. Redis에 JSON으로 저장하면서 TTL 5분을 설정한다. Redis 저장 실패는 로그만 남긴다.
9. 클라이언트에게 결과를 반환한다.
10. 이후 새 주문이 완료되고 트랜잭션이 커밋되면 이벤트 리스너가 캐시를 삭제한다.
11. 다음 인기 메뉴 요청은 다시 DB에서 최신 결과를 계산한다.

## 오늘 새롭게 배운 내용

- 인기의 “횟수”와 “수량”은 다른 기준이다.
- DB가 잘하는 그룹 집계를 Java로 가져오기 전에 실행해야 한다.
- 캐시는 원본이 아니라 없어져도 다시 만들 수 있는 값이어야 한다.
- 캐시에는 TTL과 데이터 변경 시 무효화가 함께 필요하다.
- 외부 인프라 장애를 핵심 기능 장애와 분리하려면 경계를 인터페이스로 만들 수 있다.
- 이벤트는 발행 시점뿐 아니라 트랜잭션의 어느 단계에서 처리할지도 중요하다.
- 인덱스는 생성 자체보다 실제 실행 계획으로 선택 여부와 효과를 검증하는 것이 중요하다.

## 헷갈리기 쉬운 부분

- `COUNT`는 잔 수가 아니다. `SUM(quantity)`가 잔 수다.
- 캐시 미스는 오류가 아니라 정상적인 상황이다.
- Redis 장애 시 DB fallback은 DB 장애까지 해결한다는 뜻이 아니다.
- TTL이 있다고 항상 충분히 최신인 것은 아니다. 그래서 주문 후 삭제도 한다.
- 캐시를 먼저 삭제한 직후 동시에 여러 요청이 오면 모두 DB를 조회할 수 있다. 이를 캐시 스탬피드라고 하며 이번 소규모 범위에서는 별도 락을 추가하지 않았다.
- 인덱스가 존재해도 데이터 분포에 따라 DB 옵티마이저가 사용하지 않을 수 있다.
- `AFTER_COMMIT` 리스너는 실행 시점이 늦을 뿐 자동으로 비동기가 아니다. 이번 리스너는 동기 실행이다.

## 어려웠던 점

Redis가 없을 때도 테스트와 서비스가 정상이어야 했고, 주문이 롤백될 때 캐시를 잘못 삭제하지 않아야 했다. 또한 동률 순위가 매번 달라지지 않도록 명확한 정렬 기준이 필요했다.

## 어떻게 해결했는가

- `PopularMenuCache` 인터페이스로 Redis 세부 구현을 분리했다.
- Redis 데이터 접근/JSON 예외를 캐시 구현 안에서 처리했다.
- 서비스 단위 테스트에서는 가짜 캐시와 repository를 사용해 실제 Redis 없이 히트와 미스를 검증했다.
- repository 통합 테스트는 H2로 빈 결과, 수량 집계, 동률, 취소 제외를 검증했다.
- 캐시 테스트는 TTL 5분 전달과 장애 fallback을 검증했다.
- 이벤트 리스너는 `AFTER_COMMIT`으로 연결했다.

## 다른 구현 방법과 장단점

### Spring Cache의 `@Cacheable`

코드가 짧고 공통 캐시 기능을 쓰기 좋다. 하지만 처음 배우는 단계에서는 실제 키, 직렬화, 실패 처리, TTL과 무효화 흐름이 숨겨질 수 있다. 이번에는 학습과 장애 fallback을 명시하기 위해 직접 Cache Aside 흐름을 작성했다.

### Redis Sorted Set에 주문 때마다 점수 증가

조회는 매우 빠르다. 하지만 DB와 Redis 점수를 동시에 맞추는 문제, 취소 시 점수 감소, 재처리 중복, Redis 유실 후 복구가 복잡하다. Issue 범위를 넘어가므로 선택하지 않았다.

### 일정 시간마다 미리 계산

대규모 트래픽에서 읽기 지연을 일정하게 만들 수 있다. 하지만 스케줄러와 중복 실행 제어가 필요하고 갱신 사이에는 낡은 데이터가 보인다.

### 주문 완료 때 캐시 값을 직접 변경

다음 요청도 바로 최신 결과를 볼 수 있지만, 순위 전체 재정렬과 동시성 제어가 필요하다. 삭제 후 재계산이 더 단순하고 정확하다.

## 면접에서 나올 수 있는 질문과 답변

### 왜 Redis를 사용했나요?

인기 메뉴 집계는 여러 주문 항목을 그룹화하고 정렬하는 반복 비용이 큰 조회이기 때문이다. 결과를 5분간 재사용해 DB 집계 횟수를 줄였다.

### Cache Aside가 무엇인가요?

애플리케이션이 캐시를 먼저 조회하고, 없으면 DB에서 읽은 뒤 캐시에 저장하는 전략이다. 캐시 장애 시 DB로 대체하기 쉽다는 장점이 있다.

### 캐시 정합성은 어떻게 관리했나요?

주문 커밋 후 캐시를 삭제해 다음 조회가 DB에서 재계산하게 했고, 삭제 실패나 이벤트 누락에 대비해 TTL 5분도 설정했다.

### 왜 주문 이벤트를 AFTER_COMMIT에서 처리했나요?

주문이 롤백되면 인기 데이터는 바뀌지 않는다. 성공 커밋 뒤에만 삭제해야 불필요한 무효화를 막을 수 있다.

### Redis가 꺼지면 어떻게 되나요?

캐시 조회 실패를 미스로 취급해 DB 집계 결과를 반환한다. Redis 저장과 삭제 실패도 주문이나 조회 응답을 실패시키지 않는다. 대신 DB 부하가 증가하므로 모니터링이 필요하다.

### N+1 문제는 없나요?

집계 JPQL 한 번이 필요한 메뉴 ID, 이름, 수량, 횟수를 projection으로 반환한다. 메뉴마다 추가 쿼리를 실행하지 않는다.

### 복합 인덱스 순서가 왜 중요한가요?

B-tree 복합 인덱스는 왼쪽 접두 컬럼부터 검색에 활용한다. 상태 동등 조건을 먼저 두고 향후 기간 범위 컬럼을 뒤에 두었다.

### 인덱스가 항상 빨라지게 하나요?

아니다. 완료 주문 비율이 매우 높으면 전체 스캔이 더 쌀 수 있고, 그룹 정렬 비용은 남는다. 실제 데이터로 `EXPLAIN ANALYZE`를 확인해야 한다.

## 다음에 공부할 내용

- 실제 MySQL 대량 샘플 데이터에서 인덱스 전후 `EXPLAIN ANALYZE` 비교
- 최근 7일/30일 기간별 인기 순위와 캐시 키 설계
- 캐시 스탬피드 방지용 분산 락 또는 single-flight
- Redis Cluster 환경의 장애 처리와 관측 지표
- Micrometer로 캐시 hit/miss 비율 측정
- Flyway를 이용한 인덱스 스키마 마이그레이션
- 취소 API와 취소 커밋 후 캐시 무효화

## 참고 자료

- [Spring Data Redis - RedisTemplate](https://docs.spring.io/spring-data/redis/reference/redis/template.html)
- [Spring Data Redis - Redis Cache Expiration](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)
- [Redis 공식 명령 문서 - TTL](https://redis.io/docs/latest/commands/ttl/)
- [Spring Framework - Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [MySQL 8.4 Reference Manual - Optimization and Indexes](https://dev.mysql.com/doc/refman/8.4/en/optimization-indexes.html)
- [MySQL 8.4 Reference Manual - EXPLAIN Output](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html)
