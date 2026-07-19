# 이번 이슈에서 구현한 기능

주문이 DB에 정상 저장된 뒤 `주문 완료`라는 사실을 Kafka에 JSON 메시지로 발행하고, Consumer가 메시지를 받아 인기 메뉴 캐시를 무효화하도록 구현했다.

- Topic: `coffee.order.completed.v1`
- Producer: `OrderCompletedEventProducer`
- Consumer: `OrderCompletedEventConsumer`
- Kafka Event DTO: `OrderCompletedKafkaEvent`
- Consumer Group: `ranking-order-completed-v1`
- 중복 기준: `eventId`
- 실패 정책: Consumer는 1초 간격으로 2회 재시도하고, 최종 실패를 로그로 남긴다.

Transactional Outbox Pattern과 영구적인 중복 처리 저장소는 Issue 범위에서 제외하고 개선안으로 남겼다.

## Kafka를 사용하는 이유

쉬운 설명: 주문 서비스가 랭킹 기능을 직접 부르지 않고, “주문이 끝났다”는 소식을 공용 게시판에 남긴다. 랭킹 기능은 자기 속도로 게시물을 읽는다.

실제 프로젝트: 주문 트랜잭션이 커밋되면 `OrderCompletedKafkaPublisher`가 Producer를 호출한다. Consumer는 Kafka에서 이벤트를 읽어 인기 메뉴 Redis 캐시를 비운다.

정확한 기술 용어: Producer와 Consumer를 시간적·구조적으로 분리하는 비동기 이벤트 기반 아키텍처다. Kafka는 이벤트를 분산 로그에 보존하고 Consumer Group별 offset으로 소비 위치를 관리한다.

## Kafka가 해결하는 문제

직접 호출은 주문 서비스가 랭킹 서비스의 클래스와 실패를 알아야 한다. 후속 작업이 늘면 주문 코드도 계속 바뀐다. Kafka를 사용하면 다음 문제를 줄일 수 있다.

- 생산자와 소비자의 강한 결합
- 후속 작업 때문에 주문 응답이 느려지는 문제
- Consumer가 잠시 중단됐을 때 이벤트를 잃는 문제
- 여러 Consumer가 같은 주문 사건을 독립적으로 활용하기 어려운 문제
- 실패한 이벤트의 재처리 위치를 직접 관리해야 하는 문제

단, DB 저장 성공과 Kafka 발행 성공 사이의 원자성 문제는 이번 구현만으로 해결되지 않는다. 실무에서는 Outbox Pattern을 검토한다.

## 기존 방식과 Kafka 방식 비교

| 구분 | 직접 Service 호출 | Kafka 이벤트 |
|---|---|---|
| 연결 | 주문 코드가 후속 서비스를 안다 | Topic 계약만 안다 |
| 처리 시점 | 보통 같은 요청 안에서 즉시 | 별도 Consumer가 비동기 처리 |
| 장애 영향 | 후속 작업 실패가 주문에 전파될 수 있음 | 주문 커밋 후 후속 작업을 재시도 가능 |
| 기록 | 직접 만들어야 함 | Kafka가 보존 기간 동안 저장 |
| 복잡도 | 낮음 | Broker, 직렬화, offset, 모니터링 필요 |

즉시 결과가 필요한 간단한 기능은 직접 호출이 더 적합하다. 여러 후속 작업, 서비스 간 전달, 재처리가 필요할 때 Kafka가 유리하다.

## Kafka 전체 구조

```text
Producer → Topic의 Partition → Kafka Broker → Consumer Group의 Consumer
                                         ↘ offset 저장 / lag 관찰
```

Kafka는 메시지를 받자마자 없애는 전달 통로라기보다 순서대로 이어 붙이는 사건 기록장에 가깝다. 정확히는 append-only distributed log 기반 이벤트 스트리밍 플랫폼이다.

## Producer

쉬운 설명: 메시지를 보내는 사람이다.

프로젝트에서는 `OrderCompletedEventProducer`가 주문 ID를 key로, `OrderCompletedKafkaEvent`를 value로 전송한다. 주문 ID를 key로 사용하면 partition이 여러 개가 되어도 같은 주문의 메시지가 같은 partition에 배치되어 순서를 유지하기 쉽다.

전송 API는 비동기 future를 반환한다. 성공 시 partition과 offset을, 실패 시 eventId와 orderId를 로그로 남긴다. Kafka가 꺼져 있어도 이미 커밋된 주문 결과를 되돌리지 않는다. 이 선택에는 이벤트 유실 가능성이 있으므로 Outbox가 다음 개선 과제다.

## Consumer

쉬운 설명: 메시지를 읽고 일을 하는 사람이다.

`OrderCompletedEventConsumer`는 `@KafkaListener`로 Topic을 구독한다. 새 eventId면 `PopularMenuCacheInvalidator.invalidate()`를 호출한다. 실패하면 처리 표시를 제거하고 예외를 다시 던져 재시도할 수 있게 한다.

## Topic

쉬운 설명: 같은 종류의 메시지를 모으는 이름 있는 우편함이다.

이번 Topic은 `coffee.order.completed.v1`이다. `KafkaConfig`의 `NewTopic` Bean이 1 partition, replication factor 1로 생성한다. 이름은 설정에서 가져오므로 환경 변수 `KAFKA_ORDER_COMPLETED_TOPIC`으로 바꿀 수 있다.

`v1`은 이벤트 계약 버전을 뜻한다. 호환되지 않는 스키마 변경이 필요하면 새 버전을 검토할 수 있다.

## Broker

쉬운 설명: 우편함과 메시지를 실제로 보관하는 Kafka 서버다.

로컬에서는 `docker-compose.yml`의 `coffee-kafka` 컨테이너가 Broker다. 현재 단일 Broker이므로 개발에는 간단하지만 서버 장애를 replica로 견디는 운영 구성은 아니다.

## Partition

쉬운 설명: 한 Topic 안의 여러 기록 줄이다.

각 partition 안에서는 offset 순서가 보장된다. partition을 늘리면 같은 Consumer Group의 여러 Consumer가 일을 나눌 수 있다. 전체 partition 사이의 전역 순서는 보장되지 않는다. 이번 과제는 학습 범위에 맞춰 1개를 사용한다.

## Offset

쉬운 설명: Consumer가 어디까지 읽었는지 나타내는 책갈피다.

각 메시지는 partition 안에서 0, 1, 2처럼 offset을 받는다. Consumer Group은 처리 위치를 Kafka에 커밋한다. `enable-auto-commit: false`로 자동 주기 커밋을 끄고 Spring Kafka listener container가 처리 결과에 맞춰 offset을 관리한다. 처리 예외가 발생하면 즉시 성공으로 보지 않고 error handler가 재시도한다. 설정된 재시도까지 모두 실패하면 현재 과제에서는 최종 실패를 로그로 남기고 recovered record의 offset을 커밋한다. 실무에서는 DLT로 보내 수동 복구하는 편이 안전하다.

## Consumer Group

쉬운 설명: 같은 일을 나누는 Consumer 팀이다.

`ranking-order-completed-v1` 그룹의 여러 인스턴스는 partition을 나눠 처리한다. 같은 그룹에서는 한 partition을 동시에 한 Consumer가 담당한다. 알림 기능이 같은 이벤트를 별도로 모두 읽고 싶다면 다른 group 이름을 사용해야 한다.

## 이벤트 기반 아키텍처

이벤트는 “주문이 완료되었다”처럼 이미 일어난 사실이다. 이벤트 기반 아키텍처는 생산자가 사실을 발행하고 관심 있는 소비자가 반응하게 구성한다.

명령은 “주문을 완료해라”, 이벤트는 “주문이 완료되었다”다. 의미 있는 이벤트 이름은 과거형 사실을 드러내야 한다. `OrderCompletedKafkaEvent`가 그 원칙을 따른다.

## 이번 프로젝트 이벤트 흐름

```text
사용자 주문

↓

OrderService

↓

주문 완료 Event 생성

↓

Kafka Producer

↓

Kafka Topic

↓

Kafka Broker

↓

Consumer

↓

후속 작업
(로그 저장 / 인기 메뉴 갱신 / 알림 등)
```

이번 구현의 실제 후속 작업은 인기 메뉴 캐시 무효화이며, 로그·알림은 확장 예시다.

## 요청부터 이벤트 처리까지 전체 순서

1. `OrderController`가 주문 요청을 받고 `OrderService.create()`를 호출한다. API 진입점이 없으면 외부 요청을 애플리케이션에 전달할 수 없다.
2. `OrderService`가 메뉴를 검증하고 포인트를 사용한 뒤 `OrderRepository`로 주문을 저장한다. 이 단계가 없으면 완료되지 않은 주문의 이벤트가 나갈 수 있다.
3. `OrderService`가 내부 `OrderCompletedEvent`를 발행한다. 주문 도메인 코드가 Kafka API를 직접 알지 않게 하는 내부 신호다. 없으면 Kafka 발행 시작점이 없다.
4. DB commit 성공 후 `OrderCompletedKafkaPublisher`가 실행된다. `AFTER_COMMIT`이 없으면 DB rollback된 주문 이벤트가 Kafka에 나갈 수 있다.
5. Publisher가 `OrderCompletedKafkaEvent.from()`으로 eventId와 completedAt을 포함한 전송 DTO를 만든다. Entity를 직접 보내면 영속성 구조와 외부 계약이 결합된다.
6. `OrderCompletedEventProducer`가 DTO를 JSON으로 직렬화해 Topic에 보낸다. Producer가 없으면 애플리케이션과 Kafka를 연결할 수 없다.
7. Kafka Broker가 `coffee.order.completed.v1` Topic의 partition에 메시지를 저장하고 offset을 부여한다. Broker/Topic이 없으면 메시지를 보존하거나 전달할 곳이 없다.
8. `OrderCompletedEventConsumer`가 그룹의 마지막 offset 다음 메시지를 읽는다. Consumer가 없으면 메시지는 쌓이지만 후속 작업은 실행되지 않는다.
9. `ProcessedOrderEventStore`가 eventId 중복 여부를 확인한다. 없으면 재전달된 이벤트가 후속 작업을 중복 실행할 수 있다.
10. `PopularMenuCacheInvalidator`가 Redis 인기 메뉴 캐시를 비운다. 없으면 주문 뒤에도 오래된 인기 메뉴 결과가 TTL까지 보일 수 있다.
11. 정상 반환하면 listener container가 소비 성공으로 판단하고 offset을 커밋한다. offset 관리가 없으면 재시작 때 처리 위치를 알 수 없다.

## Spring Boot에서 Kafka가 동작하는 과정

애플리케이션 시작 시 Spring Boot가 `spring.kafka` 설정으로 ProducerFactory, ConsumerFactory, KafkaTemplate, listener container를 자동 구성한다. `KafkaConfig`의 `NewTopic`을 KafkaAdmin이 Broker에 반영한다. `@KafkaListener` 메서드는 listener container가 polling한 record를 JSON 역직렬화한 뒤 호출한다. 메서드가 정상 종료되면 offset 처리가 진행되고, 예외가 나면 `DefaultErrorHandler`가 재시도한다.

## Event DTO 설명

`OrderCompletedKafkaEvent`는 Entity가 아니라 Kafka 전송 계약이다.

- `eventId`: 이벤트 한 건의 고유 ID. 중복 판단에 사용한다.
- `orderId`: 어떤 주문인지 식별한다. Kafka message key로도 사용한다.
- `memberId`: 주문 회원 식별자다.
- `totalAmount`: 주문 총액이다.
- `completedAt`: 이벤트가 만들어진 시각이다.

DTO와 Entity를 분리하면 DB 필드가 바뀌어도 Kafka 계약을 독립적으로 관리할 수 있고, 지연 로딩 객체나 불필요한 개인정보가 실수로 직렬화되는 것을 막기 쉽다.

## Producer 코드 설명

핵심 호출은 다음과 같다.

```java
kafkaTemplate.send(topicName, event.orderId().toString(), event)
        .whenComplete((result, exception) -> { /* 성공/실패 로그 */ });
```

Topic은 코드 문자열로 박지 않고 `${kafka.topic.order-completed}`에서 주입한다. `send`는 비동기이므로 완료 callback에서 실제 성공과 실패를 확인한다.

## Consumer 코드 설명

```java
@KafkaListener(
        topics = "${kafka.topic.order-completed}",
        groupId = "${kafka.consumer.order-completed-group}"
)
public void handle(OrderCompletedKafkaEvent event) {
    if (!processedEventStore.markIfNew(event.eventId())) {
        return;
    }
    try {
        cacheInvalidator.invalidate();
    } catch (RuntimeException exception) {
        processedEventStore.remove(event.eventId());
        throw exception;
    }
}
```

실패 때 eventId를 제거하는 이유는 같은 record가 재시도될 때 “이미 처리됨”으로 잘못 건너뛰지 않게 하기 위해서다.

## 직렬화와 역직렬화

객체는 네트워크로 그대로 보낼 수 없으므로 byte 배열로 바꿔야 한다. 객체를 JSON bytes로 바꾸는 과정이 직렬화, JSON bytes를 객체로 복원하는 과정이 역직렬화다.

Producer는 `JsonSerializer`, Consumer는 `JsonDeserializer`를 사용한다. 신뢰 package는 `com.jihyeon.coffeeorder.order.event`로 제한했다. 통합 테스트에서 실제 Producer가 보낸 JSON이 DTO로 복원되어 Consumer까지 도착하는지 검증한다.

이벤트 필드를 없애거나 타입을 바꾸면 오래된 Consumer와 호환이 깨질 수 있다. 실무에서는 Schema Registry, Avro/Protobuf, 호환성 정책도 검토한다.

## Kafka 장애가 발생하면?

- Broker 장애: Producer와 Consumer가 연결하지 못하고 client가 설정에 따라 재시도한다. 단일 Broker에서는 대체 replica가 없다.
- Consumer 장애: Broker의 메시지는 보존된다. 재기동 후 group의 마지막 committed offset 다음부터 읽는다.
- Producer 장애: 이번 구현은 실패를 로그로 남기며 이미 commit된 주문은 성공 상태를 유지한다. Outbox가 없으므로 발행 유실 가능성이 있다.
- 네트워크 장애: timeout이나 일시적 재시도가 발생하며, 응답 유실 때문에 중복 가능성도 생긴다.
- 처리 로직 장애: 1초 간격으로 2회 재시도한다. 최초 시도까지 합치면 최대 3번 처리 시도한다. 최종 실패는 로그로 남긴다. 실무에서는 DLT와 알림이 필요하다.

## 중복 소비는 왜 발생하는가

후속 작업은 성공했지만 offset commit 직전에 Consumer가 죽으면 Kafka는 성공 사실을 모른다. 재기동 후 같은 record를 다시 전달한다. Producer의 네트워크 재시도, Consumer rebalance 등도 중복 가능성을 만든다. 따라서 at-least-once 전달에서는 중복이 정상적으로 일어날 수 있다고 가정해야 한다.

## Idempotency란?

같은 요청을 여러 번 수행해도 한 번 수행한 것과 최종 결과가 같은 성질이다. 한국어로 멱등성이라고 한다. 단순히 “중복이 절대 오지 않는다”가 아니라 “중복이 와도 안전하게 처리한다”는 설계다.

## 이번 프로젝트에서 어떻게 처리했는가

`ProcessedOrderEventStore`가 thread-safe Set에 eventId를 저장한다. `add`가 처음일 때만 후속 작업을 실행한다. 처리 실패 시 ID를 제거해 Kafka 재시도를 허용한다.

이 방식은 학습용 단일 인스턴스에서 동작하지만 다음 한계가 있다.

- 애플리케이션 재시작 시 기록이 사라진다.
- 여러 인스턴스가 각자 메모리를 가지므로 전역 중복을 막지 못한다.
- 이벤트 수가 계속 늘면 메모리가 증가한다.

따라서 “이번 과제에서 기본 전략을 코드로 확인”하는 용도이며 운영 완성형이 아니다.

## 실무에서는 어떻게 구현하는가

- DB `processed_event` 테이블에 eventId를 unique key로 저장한다.
- Redis `SET key value NX`와 TTL로 최초 처리만 허용한다.
- 주문/집계 테이블에 마지막 처리 version을 두고 조건부 update한다.
- 자연스럽게 멱등인 연산으로 만든다. 캐시 삭제는 여러 번 해도 최종 상태가 같아 비교적 안전하다.
- 실패 record는 Dead Letter Topic으로 보내고 모니터링·재처리 도구를 둔다.
- DB 변경과 이벤트 발행 사이 유실 방지에는 Transactional Outbox + CDC 또는 outbox relay를 사용한다.

중복 체크 표시와 비즈니스 처리가 서로 다른 저장소라면 둘 사이 장애도 고려해야 한다.

## RabbitMQ와 Kafka 차이

RabbitMQ는 queue 중심 메시지 브로커로 routing과 작업 분배, 메시지별 ack에 강하다. 일반적으로 소비가 끝난 메시지는 queue에서 제거된다. Kafka는 partition log에 이벤트를 보존하고 Consumer Group별 offset으로 각자의 위치를 관리한다. 같은 기록을 여러 그룹이 독립적으로 재생하고 대용량 event stream을 처리하는 데 강하다.

작업 큐와 복잡한 routing이 핵심이면 RabbitMQ가 자연스러울 수 있고, 이벤트 보존·재생·대용량 스트리밍이 핵심이면 Kafka가 유리하다. 제품명만 보고 고르지 말고 전달 보장, 순서, 처리량, 운영 역량을 비교해야 한다.

## Spring Event와 Kafka 차이

| 구분 | Spring Event | Kafka |
|---|---|---|
| 범위 | 같은 JVM | 네트워크의 여러 서비스 |
| 설치 | 별도 Broker 불필요 | Kafka Broker 필요 |
| 보존/재처리 | 기본 제공 안 함 | 보존 기간과 offset 제공 |
| 장점 | 구현이 간단하고 객체 전달이 빠름 | 서비스 분리, 확장, 재처리, 여러 그룹 소비 |
| 단점 | 프로세스 종료 시 유실, 외부 서비스 전달 어려움 | 운영 및 장애·스키마·중복 설계 복잡 |
| 사용 시점 | 모놀리식 내부의 가벼운 알림 | 서비스 간 durable event 전달 |

이번 프로젝트는 Spring Event를 주문 도메인과 Kafka adapter 사이의 내부 신호로 사용하고, 실제 후속 작업 전달은 Kafka로 수행한다.

## 사용한 기술

- Spring Boot 3.3.5
- Spring for Apache Kafka
- `KafkaTemplate`
- `@KafkaListener`
- `JsonSerializer` / `JsonDeserializer`
- `KafkaAdmin` / `NewTopic`
- `DefaultErrorHandler` / `FixedBackOff`
- Embedded Kafka
- JUnit 5, AssertJ, Mockito
- Java 21 record, UUID, Instant

## 핵심 코드 설명

`@TransactionalEventListener(phase = AFTER_COMMIT)`은 주문 DB commit 뒤에만 Kafka 발행을 시작한다. `NewTopic`은 Topic 존재를 코드 기반 설정으로 보장한다. `enable-auto-commit: false`는 주기적인 Kafka client 자동 커밋을 끈다. `eventId`는 비즈니스 ID인 orderId와 달리 이벤트 한 건 자체를 식별한다.

## 오늘 새롭게 배운 내용

- Kafka는 단순 비동기 호출 도구가 아니라 보존 가능한 분산 로그다.
- 메시지 처리 성공과 offset commit 사이 장애 때문에 중복이 생길 수 있다.
- 재시도만 추가하면 중복 부작용이 생기므로 idempotency가 함께 필요하다.
- Entity와 Event DTO는 목적과 변경 주기가 다르므로 분리해야 한다.
- DB commit 후 발행만으로 DB-Kafka 원자성이 완성되지는 않는다.

## 헷갈리기 쉬운 부분

- Topic은 queue와 완전히 같지 않다. 여러 Consumer Group이 같은 기록을 각자 읽을 수 있다.
- offset은 메시지 ID가 아니라 partition 안의 위치다.
- partition 안의 순서만 보장되며 Topic 전체 순서는 기본 보장이 아니다.
- Consumer 인스턴스 수가 partition 수보다 많으면 남는 Consumer가 생긴다.
- JSON 변환 성공은 비즈니스 처리 성공과 다른 단계다.
- exactly-once라는 표현이 있어도 외부 DB/HTTP 부작용까지 자동으로 정확히 한 번이 되는 것은 아니다.

## 어려웠던 점

기존에는 Spring Event listener가 직접 캐시를 비웠다. Kafka Consumer도 같은 작업을 하면 주문 하나에 두 번 실행된다. 또한 실패한 이벤트를 처리 완료로 표시하면 재시도가 무시되는 문제가 있다.

## 어떻게 해결했는가

기존 `PopularMenuCacheInvalidator`는 작업 자체만 담당하게 하고 Kafka Consumer가 유일하게 호출하도록 경로를 정리했다. 중복 ID는 처리 전에 원자적으로 등록하고, 후속 작업 실패 시 제거해 다음 retry가 실제 작업을 다시 시도하도록 했다. Embedded Kafka 테스트로 설정만 확인하지 않고 실제 전송·수신까지 검증했다.

## 다른 구현 방법

- `OrderService`가 KafkaTemplate을 직접 호출: 간단하지만 도메인 서비스가 인프라에 결합되고 rollback 이벤트 발행 위험이 있다.
- Kafka transaction: Kafka 내부 consume-process-produce에는 유용하지만 일반 DB와의 원자성은 별도 문제다.
- Transactional Outbox: 주문과 outbox row를 같은 DB transaction에 저장하고 별도 relay가 Kafka에 발행한다. 발행 유실을 줄이는 실무 대안이다.
- DB/Redis idempotency store: 재시작과 다중 인스턴스에서도 중복을 막는다.
- DLT: 재시도 후 실패 record를 별도 Topic에 보관해 운영자가 복구한다.

## 면접 질문과 답변

**Q. Kafka를 왜 사용했나요?**

A. 주문 처리와 후속 랭킹 처리를 분리하고, Consumer 장애 중에도 이벤트를 보존하며 offset 기반 재처리를 가능하게 하기 위해 사용했습니다.

**Q. Consumer가 처리 후 offset commit 전에 죽으면 어떻게 되나요?**

A. 같은 메시지가 재전달될 수 있습니다. 그래서 eventId 기반 멱등 처리가 필요합니다.

**Q. 같은 key를 사용하는 이유는 무엇인가요?**

A. 같은 주문의 이벤트를 같은 partition으로 보내 해당 주문 단위의 순서를 유지하기 위해서입니다.

**Q. `enable-auto-commit=false`면 누가 commit하나요?**

A. Spring Kafka listener container가 listener 처리 결과와 error handler 정책에 따라 offset을 관리합니다.

**Q. Consumer가 꺼지면 메시지가 사라지나요?**

A. Kafka 보존 기간 안에서는 Broker에 남습니다. 같은 Consumer Group이 재기동하면 committed offset 다음부터 읽습니다.

**Q. 이번 구현의 가장 큰 한계는 무엇인가요?**

A. DB commit과 Kafka 발행 사이의 유실 가능성, 메모리 기반 중복 저장소, DLT 부재입니다. Outbox와 영구 idempotency 저장소가 개선 방향입니다.

**Q. Spring Event 대신 항상 Kafka가 좋은가요?**

A. 아닙니다. 같은 프로세스의 단순 알림은 Spring Event가 훨씬 간단합니다. 서비스 간 보존·재처리·확장이 필요할 때 Kafka 비용이 정당화됩니다.

## 다음에 공부할 내용

- Transactional Outbox Pattern과 CDC
- Dead Letter Topic과 실패 이벤트 재처리
- DB 또는 Redis 기반 영구 idempotency
- partition 확장, key 설계, rebalance
- producer `acks`, retry, idempotent producer
- consumer ack mode와 offset commit 시점
- Schema Registry와 Avro/Protobuf 호환성
- lag, 처리 실패율, DLT 크기 모니터링
- 다중 Broker replication과 leader election

## 참고 자료

- Apache Kafka Documentation: https://kafka.apache.org/documentation/
- Spring for Apache Kafka Reference: https://docs.spring.io/spring-kafka/reference/
- Spring Boot Kafka Support: https://docs.spring.io/spring-boot/reference/messaging/kafka.html
- Confluent Kafka Design: https://docs.confluent.io/kafka/design/index.html
