# 제출 전 피드백 보완 기록

## 이번 이슈에서 구현한 기능

제출 전 피드백 네 가지를 실제 코드 기준으로 보완했다.

1. 전체 기간 인기 메뉴 TOP10을 최근 7일 완료 주문 TOP3로 변경
2. 포인트 적립·사용을 기록하는 `PointHistory` 추가
3. README를 현재 Entity, API, Redis, Kafka, 테스트 상태와 일치시킴
4. Redis ZSET과 JSON 결과 캐시를 비교하고 JSON 캐시 유지 결정

## 왜 필요한 기능인가

기존 인기 메뉴는 기간 조건이 없어 최근 트렌드를 나타내지 못했고, 포인트는 최종 잔액만 있어 변경 이유를 추적할 수 없었다. README에는 이미 구현된 Kafka가 향후 계획으로 남아 있는 등 코드와 설명도 어긋났다. 제출물은 동작뿐 아니라 요구사항의 정확성, 트랜잭션 정합성, 기술 선택 근거를 함께 설명할 수 있어야 한다.

## 기존 구현의 문제점

- `COMPLETED` 상태는 걸렀지만 전체 주문을 수량 기준으로 집계하고 TOP10을 반환했다.
- Redis key도 `popular-menus:v1:top:10`이어서 변경된 의미와 맞지 않았다.
- `Member.pointBalance` 변경 후 감사·추적 가능한 영속 이력이 없었다.
- README의 디렉토리, 이벤트 구현 상태, 인기 메뉴 기준이 실제 코드와 달랐다.
- Redis를 왜 JSON으로 쓰는지, ZSET이 최근 7일 조건에서 어떤 추가 설계를 요구하는지 설명이 부족했다.

## 인기 메뉴 최근 7일 TOP3 변경

`PopularMenuService`에서 조회 1회당 기준 시각을 한 번 구한다.

```java
LocalDateTime endAt = LocalDateTime.now();
LocalDateTime startAt = endAt.minusDays(7);
```

Repository에는 `status`, `startAt`, `endAt`, `Pageable`을 전달한다. 기간은 `orderedAt >= startAt AND orderedAt < endAt`으로 시작 포함·종료 미포함이다. DB가 `COMPLETED` 주문만 메뉴별로 묶어 `SUM(quantity)`를 계산한다. 정렬은 판매 수량 내림차순, 주문 횟수 내림차순, 메뉴 ID 오름차순이며 `PageRequest.of(0, 3)`으로 제한한다. 마지막 메뉴 ID 정렬 덕분에 완전 동률이어도 결과가 안정적이다.

기존 응답 DTO의 `rank`, `menuId`, `menuName`, `totalQuantity`, `orderCount`는 유지했다. 캐시 key는 의미와 버전을 드러내는 `popular-menus:v2:last-7-days:top:3`으로 바꿨다.

## PointHistory 설계 및 추가

이 프로젝트에는 별도 `Point` 엔티티가 없고 `Member`가 `pointBalance`를 가진다. 별도 Point 모델을 억지로 추가하면 기존 API와 락 대상을 크게 바꿔야 하므로, `PointHistory`가 현재 잔액 소유자인 `Member`를 `ManyToOne`으로 참조하게 했다.

저장 항목:

- `id`, `member`, `createdAt`
- `changeAmount`: 적립 양수, 사용 음수
- `type`: `EARN`, `USE`
- `description`
- `balanceAfter`: 변경 직후 잔액을 재구성·검증하기 위한 값

이력 생성 책임은 흐름과 설명을 아는 `PointService`에 두고, 잔액 검증·변경 책임은 기존처럼 `Member.charge/use`에 유지했다. 인덱스 `point_histories(member_id, created_at)`도 선언했다. 현재 이력 조회 API는 과제 범위에 포함하지 않았다.

## 포인트 변경과 이력 저장의 트랜잭션 범위

`PointService.charge/use`는 `@Transactional`이고 같은 회원을 `PESSIMISTIC_WRITE`로 조회한다. 도메인 잔액 변경이 먼저 성공한 뒤 이력을 저장한다. 포인트 부족이면 `member.use()`에서 예외가 발생해 이력 생성에 도달하지 않는다. 이력 저장 실패도 같은 트랜잭션을 롤백한다.

주문에서는 `OrderService.create()`의 트랜잭션에 `PointService.use()`가 참여한다. 따라서 포인트 잔액, USE 이력, 주문, 주문 항목은 함께 커밋된다. 동기 Spring event listener가 뒤에서 실패하는 기존 테스트에서도 모두 롤백되는 것을 이력 개수로 추가 검증했다. 비관적 락 로직은 변경하지 않았다.

## Redis 저장 방식 분석

현재 흐름은 Cache Aside다.

```text
조회 -> Redis JSON hit면 반환
     -> miss/장애/역직렬화 실패면 DB 최근 7일 집계
     -> TOP3 JSON 저장 시도(TTL 5분) -> 반환

주문 commit -> Kafka event -> Consumer -> cache key 삭제
```

Redis value는 `List<PopularMenuResponse>`를 Jackson으로 직렬화한 JSON 문자열이다. 저장과 동시에 TTL을 설정한다. 조회·저장·삭제의 Redis 예외는 경고 로그를 남기고 핵심 DB 기능에 전파하지 않는다.

## 최종 선택: JSON 캐시 유지

정확성의 기준을 DB에 두고 계산 결과 TOP3만 JSON으로 캐시하기로 했다.

- SQL이 상태와 정확한 시간 범위를 한 번에 처리한다.
- 결과가 최대 3개라 단일 key/value 조회와 JSON 직렬화 비용이 작다.
- Redis 데이터는 언제든 DB에서 재생성할 수 있다.
- 5분 TTL과 주문 이벤트 삭제로 오래된 결과의 수명을 제한한다.
- 과제 규모에서 날짜별 자료구조·보정 작업의 운영 복잡도를 추가하지 않는다.

이는 ZSET 구현을 피하기 위한 결정이 아니라 슬라이딩 윈도우 정확성, 장애 복구, 운영 복잡도를 비교한 결과다. 단, 7일 경계가 계속 이동하므로 주문이 없어도 TTL 동안 최대 5분 이전 기준의 결과가 보일 수 있다.

## 선택하지 않은 ZSET의 장단점

ZSET은 member별 score 증가가 원자적이고 TOP N 조회가 빠르며 실시간 순위에 적합하다. 하지만 누적 score 하나는 7일 전 주문 수량을 자동으로 빼지 못한다. 정확한 최근 7일을 만들려면 다음 중 하나가 필요하다.

- 날짜별 ZSET을 만들고 최근 7개를 합산
- 주문 항목을 timestamp score로 저장하고 만료 대상을 제거한 뒤 메뉴별 재집계
- 일별 집계 DB/Redis 구조와 주기적 재계산

이 방식들은 일자 경계와 애플리케이션 timezone, 지연·중복 Kafka event, 취소, 누락 event, DB와 Redis 보정 전략까지 다뤄야 한다. 트래픽 증가나 초단위 실시간 요구가 생기면 날짜별 집계 + ZSET 합산 구조를 다시 검토한다.

## 수정한 주요 파일

- `ranking/service/PopularMenuService.java`: 7일 기준 시각과 TOP3 제한
- `ranking/repository/PopularMenuRepository.java`: 상태·기간 집계 JPQL
- `ranking/cache/RedisPopularMenuCache.java`: 새 캐시 key
- `member/entity/PointHistory.java`, `PointHistoryType.java`: 이력 모델
- `member/repository/PointHistoryRepository.java`: 이력 저장/회원별 조회
- `member/service/PointService.java`: 적립·사용 이력 저장
- ranking/member/order 테스트: 기간·정렬·정합성 검증
- `README.md`: 실제 구현 기준 전체 개정
- 이 문서: 선택 근거와 검증 기록

## 추가하거나 수정한 테스트

- 최근 7일 주문 포함, 8일 전 주문 제외
- 판매 수량 내림차순과 최대 3건 제한
- `CANCELED` 주문 제외
- 수량 동률 시 주문 횟수, 다시 동률 시 메뉴 ID 정렬
- Service가 동일한 종료 시각에서 정확히 7일을 뺀 시작 시각과 size 3을 전달
- EARN 이력의 양수 금액과 변경 후 잔액
- USE 이력의 음수 금액, type, 변경 후 잔액
- 잔액 부족 시 USE 이력 미저장
- 주문 후반 실패 시 잔액과 USE 이력 동시 롤백
- 동시 사용 성공 건수만큼만 USE 이력 저장

## 실행한 검증 명령어와 결과

```bash
./gradlew test
./gradlew clean test
./gradlew build --console=plain
```

- 변경 전 `./gradlew test`: 성공
- 변경 후 `./gradlew test`: 성공 (`BUILD SUCCESSFUL`)
- 최종 clean test: 테스트 task 성공
- 최종 build: 성공 (`BUILD SUCCESSFUL in 1m 27s`)
- 테스트는 H2, Mockito, Embedded Kafka를 사용해 외부 MySQL/Redis/Kafka 없이 통과했다.
- 기존 `OrderCompletedEventProducerTest`에 unchecked operation 컴파일 경고가 있으나 이번 변경의 오류나 테스트 실패는 아니다.

## 오늘 새롭게 배운 내용

- “최근 7일”은 쿼리 안에서 `now()`를 여러 번 부르는 것보다 Service가 하나의 기준 시각을 만들어 전달해야 경계를 설명하고 테스트하기 쉽다.
- 기간 집계 캐시는 key 이름에도 기간과 결과 크기, 계약 버전을 드러내야 예전 캐시와 충돌하지 않는다.
- 잔액 테이블과 원장성 이력은 같은 트랜잭션에서 저장해야 실패 시 한쪽만 남는 문제를 막을 수 있다.
- ZSET의 빠른 순위 연산과 슬라이딩 윈도우의 정확성은 별개 문제다. 자료구조 선택에는 데이터 제거와 복구 과정까지 포함해야 한다.
- README에는 구현 장점뿐 아니라 Outbox 부재, JVM 멱등성 저장소, 측정하지 않은 성능 수치 같은 한계도 명시해야 신뢰할 수 있다.

## 어려웠던 점

기존 문서는 인기 메뉴 캐시 삭제를 Spring transactional listener가 직접 수행하는 것처럼 설명했지만, 현재 코드는 commit 후 Kafka에 발행하고 Consumer가 삭제한다. 또한 피드백은 Point 엔티티를 전제로 했지만 실제 모델에는 Point가 없었다.

## 어떻게 해결했는가

Entity, Service, event producer/consumer, 설정, 테스트를 먼저 추적했다. 문서의 명칭을 억지로 따르지 않고 현재 잔액 소유자인 Member와의 연관관계를 선택했다. Redis도 단순 비교가 아니라 최근 7일 데이터 제거 비용과 장애 복구 흐름을 기준으로 판단했다.

## 다음에 공부할 내용

- `Clock` Bean 주입으로 시간 기반 Service 테스트를 완전히 결정적으로 만드는 방법
- Flyway/Liquibase로 `point_histories` migration 관리
- Transactional Outbox와 DLT, 영구 processed-event 테이블
- 날짜별 판매 집계 테이블 또는 ZSET의 재계산·보정 전략
- MySQL 실데이터에서 `EXPLAIN ANALYZE`와 부하 테스트
- PointHistory 조회 API의 pagination과 접근 권한

## 참고 자료

- 프로젝트의 `README.md`
- `study/03-point.md`
- `study/05-ranking-redis.md`
- `study/06-kafka.md`
