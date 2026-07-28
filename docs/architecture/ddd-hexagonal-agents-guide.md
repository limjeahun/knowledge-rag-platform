# 순수 DDD + Hexagonal Architecture 재사용 지침서

이 문서는 순수 DDD 철학, Hexagonal Architecture 의존성 방향, 계층별 DTO 분리, Aggregate 캡슐화, Mapper 순수성, 코드 작성 습관을 다른 프로젝트의 `AGENTS.md`나 AI 프롬프트로 바로 사용할 수 있게 정리한 재사용 지침서다.

범위는 순수 DDD와 Hexagonal 코드 작성 방식이다. 이 문서의 목표는 “도메인 언어가 살아 있는 코드”, “외부 기술을 모르는 도메인”, “계층별 책임이 흔들리지 않는 소스 코드”를 어떤 방식으로 작성해야 하는지 명확히 전달하는 것이다.

## 사용 방법

1. 다른 프로젝트의 `AGENTS.md`에 `복사용 AGENTS.md / 프롬프트 템플릿` 섹션을 붙여 넣는다.
2. `{service}`, `{aggregate}`, `{domain_term}` placeholder를 실제 프로젝트 용어로 바꾼다.
3. 예시 코드는 그대로 복사하기보다, 같은 철학으로 프로젝트의 도메인 언어에 맞게 다시 작성한다.
4. 구현 후 ArchUnit 같은 구조 테스트와 도메인 단위 테스트로 규칙을 고정한다.

---

# Style Commerce 적용 가이드

이 섹션은 `plan/style-commerce-eda-plan.md`의 BE 기획을 이 DDD + Hexagonal 지침서에 적용한 프로젝트별 기준이다. 아래 내용은 `style-commerce-eda`를 구현하거나 리뷰할 때 범용 예시보다 우선해서 사용한다.

## 1. 프로젝트 모듈과 서비스 책임

MVP 모듈은 다음처럼 나눈다.

| 모듈 | 저장소 | 책임 |
| --- | --- | --- |
| `common-core` | 없음 | 공통 응답, 공통 예외, 테스트 fixture 최소화 |
| `common-events` | Avro schema | 공유 Kafka command/event/result 계약 |
| `member-service` | MariaDB | 회원, 주소록, 멤버십 등급, 포인트 |
| `catalog-service` | MariaDB + local file storage | 브랜드, 상품, 옵션, SKU, 상품 가격, 상품/브랜드 이미지 metadata |
| `cart-service` | Redis/MariaDB | 장바구니, 선택 옵션, 수량, 주문 전 상품 snapshot |
| `order-service` | MariaDB | 주문 Aggregate, 주문 SAGA 상태, 주문 조회 |
| `inventory-service` | MariaDB | SKU 재고, 재고 예약, 예약 해제 |
| `payment-service` | MariaDB | 결제 승인/취소 mock, 결제 이력 |
| `delivery-service` | MariaDB | 배송 생성/취소 mock, 배송 상태 |
| `display-service` | MongoDB | 홈 패널, 기획전, 브랜드 피드 read model |

Phase 2 모듈은 `promotion-service`, `notification-service`, `claim-service`, `review-service`, `recommendation-service`로 분리한다.

서비스 분리 기준은 기술 계층이 아니라 업무 능력이다. 예를 들어 상품 정보와 SKU 판매 상태는 `catalog-service`가 소유하지만, 실제 재고 수량과 예약 수량은 `inventory-service`가 소유한다. 주문은 상품/재고/결제/배송 DB를 직접 보지 않고, 주문 시점 snapshot과 Kafka result event로 상태를 수렴시킨다.

## 2. Style Commerce 패키지 구조

각 서비스는 다음 구조를 따른다.

```text
com.example.style.{service}/
├── application/
│   ├── dto/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   └── service/
├── config/
├── domain/
│   ├── event/
│   ├── model/
│   └── vo/
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   └── dto/
│   │   └── messaging/
│   │       └── consumer/
│   └── out/
│       ├── messaging/
│       ├── persistence/
│       └── storage/
└── {Service}Application.java
```

`adapter/out/storage`는 로컬 개발용 이미지 파일 저장 adapter를 위한 프로젝트 확장이다. Domain과 Application은 파일 시스템 경로, Multipart, ResourceHandler를 알지 않는다. Application은 `StoreProductImagePort`, `LoadProductImageResourcePort`, `DeleteProductImagePort` 같은 outbound port만 의존한다.

## 3. 서비스별 Aggregate와 VO

### 3.1 member-service

Aggregate:

| Aggregate | 책임 |
| --- | --- |
| `Member` | 회원 식별자, 이름, 이메일, 상태, 멤버십 등급 |
| `PointWallet` | 포인트 잔액, 적립/사용/취소 이력 |

VO:

| VO | 필드 |
| --- | --- |
| `MemberIdentity` | `memberId`, `memberName` |
| `Email` | `value` |
| `Address` | `receiverName`, `zipCode`, `address1`, `address2`, `phone` |
| `Point` | `amount` |

Application service는 web request에서 받은 primitive/simple command를 `MemberIdentity`, `Email`, `Address`, `Point`로 변환한 뒤 aggregate behavior를 호출한다.

### 3.2 catalog-service

Aggregate:

| Aggregate | 책임 |
| --- | --- |
| `Brand` | 브랜드명, 소개, 로고 이미지 metadata, 팔로우 가능 여부 |
| `Product` | 상품명, 브랜드 snapshot, 카테고리, 가격, 판매 상태, 상품 이미지 metadata |
| `Sku` | 색상/사이즈 조합, SKU 코드, 판매 가능 여부 |

VO:

| VO | 필드 |
| --- | --- |
| `ProductName` | `value` |
| `BrandSnapshot` | `brandId`, `brandName` |
| `Money` | `amount`, `currency` |
| `OptionValue` | `color`, `size` |
| `ProductImage` | `imageKey`, `imageUrl`, `sortOrder`, `altText`, `representative` |

`ProductImage`는 이미지 파일 자체가 아니라 이미지 metadata다. Domain은 `C:\...` 같은 로컬 절대 경로, Multipart, MIME 검증, 썸네일 생성 라이브러리를 알면 안 된다. 파일 검증과 저장은 application port와 storage adapter에서 처리한다.

주요 behavior:

- `Product.publish()`
- `Product.stopSelling()`
- `Product.changePrice(money)`
- `Product.addSku(optionValue, skuCode)`
- `Product.addImage(productImage)`
- `Product.changeRepresentativeImage(imageKey)`
- `Sku.markSoldOut()`
- `Sku.resumeSelling()`

### 3.3 cart-service

Aggregate:

| Aggregate | 책임 |
| --- | --- |
| `Cart` | 회원별 장바구니와 선택 라인 관리 |

VO:

| VO | 필드 |
| --- | --- |
| `CartLine` | `skuId`, `productId`, `productName`, `brandName`, `color`, `size`, `quantity`, `unitPrice`, `thumbnailUrl` |
| `SelectedCartLine` | 주문서로 넘길 선택 라인 snapshot |

장바구니는 가격, 상품명, 브랜드명, 옵션, 썸네일 URL snapshot을 가진다. 재고는 예약하지 않는다. 주문 생성 시 order-service가 command snapshot을 기준으로 금액과 주문 라인을 확정한다.

### 3.4 order-service

Aggregate:

| Aggregate | 책임 |
| --- | --- |
| `Order` | 주문 라인, 결제 금액, 배송지, 주문 상태 |
| `OrderSagaState` | `correlationId` 기준 참여 서비스 결과 추적 |

VO:

| VO | 필드 |
| --- | --- |
| `OrderLine` | `skuId`, `productId`, `brandName`, `productName`, `color`, `size`, `quantity`, `unitPrice`, `lineAmount`, `thumbnailUrl` |
| `ShippingAddress` | `receiverName`, `zipCode`, `address1`, `address2`, `phone` |
| `OrderAmount` | `productAmount`, `discountAmount`, `deliveryFee`, `totalAmount` |
| `PaymentSnapshot` | `method`, `requestedAmount` |

Order 상태:

```text
CREATED
STOCK_RESERVING
PAYMENT_AUTHORIZING
DELIVERY_PREPARING
CONFIRMED
CANCELING
CANCELED
FAILED
NEEDS_MANUAL_REVIEW
```

Order는 다른 서비스의 현재 상품명, 현재 가격, 현재 이미지 URL을 다시 조회하지 않는다. 주문 생성 command의 snapshot을 주문 이력으로 보존한다.

### 3.5 inventory-service

Aggregate:

| Aggregate | 책임 |
| --- | --- |
| `StockItem` | SKU별 실제 재고, 예약 재고, 판매 가능 재고 |
| `StockReservation` | 주문별 예약 내역과 예약 상태 |

주요 behavior:

- `StockItem.reserve(orderId, quantity)`
- `StockItem.release(orderId, quantity)`
- `StockItem.confirm(orderId, quantity)`
- `StockItem.increase(quantity)`

재고 부족은 `inventory-service`의 domain rule이다. `order-service`는 재고를 선판단하지 않고 `StockReservationResult`를 해석한다.

### 3.6 payment-service

Aggregate:

| Aggregate | 책임 |
| --- | --- |
| `Payment` | 주문별 결제 승인/취소 상태 |

MVP에서는 실제 PG를 연결하지 않고 mock adapter로 승인/실패를 제어한다. Domain은 mock 여부를 모른다. Application은 `AuthorizePaymentPort` 같은 outbound port를 호출하고, adapter가 성공/실패 fixture를 제공한다.

### 3.7 delivery-service

Aggregate:

| Aggregate | 책임 |
| --- | --- |
| `Shipment` | 주문별 배송지, 배송 라인, 운송장, 배송 상태 |

MVP에서는 실제 택배사를 연결하지 않고 mock adapter로 배송 생성/취소를 제어한다. 배송 상태가 `SHIPPING` 이후이면 고객 취소는 불가하고 Phase 2의 claim/return 흐름으로 넘긴다.

### 3.8 display-service

Document:

| Document | 책임 |
| --- | --- |
| `HomePanelDocument` | 홈 화면 섹션 구성 |
| `ExhibitionDocument` | 기획전과 연결 상품 |
| `BrandFeedDocument` | 브랜드 콘텐츠와 대표 상품 |
| `DisplayProductDocument` | 노출용 상품 snapshot |

Display document에는 상품 노출에 필요한 `thumbnailUrl`, `brandName`, `productName`, `salePrice`, `badges`, `available`을 snapshot으로 저장한다. 원본 상품 DB를 직접 조회하지 않는다.

## 4. 로컬 이미지 파일 운영 기획

이 프로젝트는 MVP에서 별도 이미지 파일 서버, S3, CDN, Nginx 정적 서버를 두지 않는다. 대신 `catalog-service`가 로컬 파일 저장소와 정적 리소스 매핑을 맡는다. 이 결정은 학습용 로컬 환경을 단순하게 만들기 위한 것이며, Domain/Application 순수성은 유지해야 한다.

### 4.1 저장 위치

로컬 개발 기본 경로:

```text
C:\workspace\style-commerce-eda\local-assets\
└── images\
    ├── products\
    │   └── {productId}\
    │       ├── {imageKey}-main.webp
    │       └── {imageKey}-detail.webp
    ├── brands\
    │   └── {brandId}\
    │       └── {imageKey}-logo.webp
    └── display\
        └── panels\
            └── {panelId}.webp
```

Git에는 실제 대용량 이미지를 넣지 않는다. 대신 다음 파일만 둔다.

```text
local-assets/
├── .gitkeep
└── README.md
```

샘플 이미지는 `dev-seed-assets` 작업이나 SQL/fixture와 함께 로컬에 생성하거나 복사한다.

### 4.2 설정 값

`catalog-service`는 다음 configuration properties를 가진다.

```yaml
style:
  assets:
    storage-root: C:/workspace/style-commerce-eda/local-assets
    public-base-url: http://localhost:8081/assets
    allowed-content-types:
      - image/jpeg
      - image/png
      - image/webp
    max-file-size-bytes: 5242880
```

`storage-root`는 로컬 물리 경로다. `public-base-url`은 FE가 접근할 HTTP URL의 기준이다. Domain에는 둘 다 전달하지 않는다.

### 4.3 Spring 정적 리소스 매핑

`catalog-service`의 `config`에 local profile 전용 `WebMvcConfigurer`를 둔다.

```java
@Configuration
@ConfigurationPropertiesScan
public class LocalAssetResourceConfig implements WebMvcConfigurer {
    private final AssetStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("file:" + properties.storageRoot().toAbsolutePath() + "/");
    }
}
```

이 설정은 기술 지원이므로 `config`에 둔다. 상품 이미지 저장 자체는 `adapter/out/storage`의 `LocalProductImageStorageAdapter`가 담당한다.

### 4.4 업로드 API

관리자 업로드 API는 catalog-service inbound web adapter에 둔다.

```http
POST /admin/v1/products/{productId}/images
Content-Type: multipart/form-data
```

Request part:

```text
file: image file
altText: string
sortOrder: number
representative: boolean
```

Response:

```json
{
  "imageKey": "prd-1001-01",
  "imageUrl": "http://localhost:8081/assets/images/products/1001/prd-1001-01-main.webp",
  "altText": "Technical Field Jacket black front",
  "sortOrder": 1,
  "representative": true
}
```

Controller는 Multipart를 받고 `UploadProductImageCommand`로 변환한다. Command는 primitive/simple field와 `MultipartFile`이 아니라 application이 읽을 수 있는 단순 입력을 가져야 한다. 파일 스트림 자체를 application으로 넘겨야 한다면 application port 전용 input type을 두고 domain으로 전달하지 않는다.

### 4.5 Application port

```java
public interface StoreProductImagePort {
    StoredImage store(ProductImageStorageCommand command);
}
```

```java
public record ProductImageStorageCommand(
    Long productId,
    String originalFileName,
    String contentType,
    long size,
    InputStreamSource content
) {
}
```

`InputStreamSource` 같은 기술 타입을 application DTO에 두는 것이 부담되면 adapter-local uploader가 먼저 임시 파일로 저장하고 application에는 저장 결과 metadata만 넘긴다. 원칙은 domain이 파일 I/O를 모르게 하는 것이다.

### 4.6 Persistence model

이미지 binary는 DB에 저장하지 않는다. DB에는 metadata만 저장한다.

```text
product_images
├── id
├── product_id
├── image_key
├── image_url
├── original_file_name
├── content_type
├── sort_order
├── alt_text
├── representative
├── created_at
└── updated_at
```

`image_url`은 FE와 read model이 사용할 공개 URL이다. `storage_path`를 저장해야 한다면 adapter persistence entity에만 두고 domain VO에는 노출하지 않는다.

### 4.7 API 응답과 snapshot 규칙

상품 상세 API는 다음처럼 이미지 metadata를 반환한다.

```json
{
  "productId": 1001,
  "productName": "Technical Field Jacket",
  "images": [
    {
      "imageUrl": "http://localhost:8081/assets/images/products/1001/prd-1001-01-main.webp",
      "altText": "black jacket front",
      "sortOrder": 1,
      "representative": true
    }
  ]
}
```

장바구니, 주문 라인, display read model에는 대표 이미지 URL을 snapshot으로 보관한다. 주문 이후 상품 이미지가 바뀌어도 주문 이력의 `thumbnailUrl`은 주문 당시 snapshot을 유지한다.

### 4.8 로컬 placeholder 전략

이미지 파일 서버와 실제 상품 이미지가 아직 없을 때는 세 단계로 처리한다.

1. FE 와이어프레임은 외부 이미지 URL을 사용하지 않고 CSS gradient placeholder를 사용한다.
2. Backend seed data는 `imageUrl`에 `/assets/images/placeholders/product-{n}.webp` 같은 로컬 URL을 넣는다.
3. `local-assets/images/placeholders`에 작은 placeholder 이미지를 두거나, placeholder 파일이 없으면 FE가 CSS fallback을 보여준다.

FE는 이미지 로딩 실패 시 다음 기준을 따른다.

- 상품 카드: 브랜드명과 상품명을 표시하고 회색 placeholder를 보여준다.
- 상품 상세: 대표 이미지가 없으면 local CSS placeholder를 보여준다.
- 주문/장바구니: snapshot thumbnail URL이 깨져도 주문 금액과 옵션 정보를 우선 표시한다.

### 4.9 금지 규칙

- Domain model에 `MultipartFile`, `File`, `Path`, `Resource`, `byte[]`를 넣지 않는다.
- `common-events` 메시지에 이미지 binary를 넣지 않는다.
- Kafka 이벤트에 로컬 절대 경로를 넣지 않는다.
- 다른 서비스가 `catalog-service`의 로컬 디스크 경로를 직접 읽지 않는다.
- 상품 이미지 때문에 `display-service`가 catalog DB를 직접 조회하지 않는다.
- 개발 편의를 이유로 `src/main/resources/static`에 업로드 파일을 저장하지 않는다. 빌드 산출물과 런타임 업로드 파일을 분리한다.

## 5. Style Commerce 구현 순서

1. root Gradle multi-module 구성
2. `common-core`, `common-events` 생성
3. `catalog-service`의 Brand/Product/Sku Aggregate와 상품 조회 API 구현
4. 로컬 이미지 저장소 설정, 이미지 metadata 모델, 관리자 이미지 업로드 API 구현
5. `inventory-service`의 StockItem/StockReservation 구현
6. `cart-service`의 Cart와 상품 snapshot 라인 구현
7. `order-service`의 Order/OrderSagaState 구현
8. `payment-service`, `delivery-service` mock 구현
9. display read model과 홈 패널 API 구현
10. ArchUnit, domain unit test, application service test로 경계 고정

---

# 복사용 AGENTS.md / 프롬프트 템플릿

아래 블록은 다른 프로젝트의 `AGENTS.md`에 그대로 붙여 넣을 수 있는 기준이다.

````markdown
# DDD + Hexagonal Architecture Instructions

## Core Philosophy

- Code must speak the domain language. Class names, method names, command names, and exception names should read like business rules.
- Domain code is the center. Frameworks, databases, HTTP, serialization, and UI contracts stay outside.
- The domain must be ignorant of the outside world. Domain models must not know controllers, repositories, entities, response DTOs, or framework annotations.
- Use Hexagonal Architecture to protect dependency direction. Outer layers depend on inner layers; inner layers do not depend on outer layers.
- Application services orchestrate use cases. They do not own business rules.
- Aggregate roots protect consistency. External code must not mutate aggregate state directly.
- DTO conversion must happen at layer boundaries. Each layer owns its own model.
- Code should expose intent. Prefer domain-language command methods over generic setters or procedural helper names.
- Keep abstraction levels consistent inside a method. One method should not mix business flow, primitive validation, persistence mapping, and formatting details.

## Layer Structure

Use this structure per bounded context or service:

```text
{service}/
├── application/
│   ├── dto/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   └── service/
├── config/
├── domain/
│   ├── model/
│   └── vo/
├── adapter/
│   ├── in/
│   │   └── web/
│   │       ├── request/
│   │       └── response/
│   └── out/
│       └── persistence/
│           ├── entity/
│           ├── mapper/
│           └── repository/
```

## Dependency Direction

| Layer | May Depend On | Must Not Depend On |
| --- | --- | --- |
| domain | Java standard library, service-local domain types | application, adapter, config, Spring, JPA, web DTOs, persistence entities |
| application | domain, application ports, application DTOs | adapter, config, web framework, persistence framework |
| adapter.in.web | application inbound ports, application DTOs, web request/response DTOs | adapter.out |
| adapter.out.persistence | application outbound ports, domain, persistence framework | adapter.in.web |
| config | technical bean wiring | business rules, use case decisions |

Spring annotations such as `@Service` and `@Transactional` may be used in application services if that is already the project convention. Do not let that open a path to adapter implementations.

## DTO Separation

- Web Request DTOs belong in `adapter/in/web/request`.
- Web Response DTOs belong in `adapter/in/web/response`.
- Application Command, Query, and Result records belong in `application/dto`.
- Domain Value Objects belong in `domain/vo`.
- Web Request DTOs may define `toCommand()` or `toQuery()`.
- Web Request DTOs must not create domain value objects.
- Application Command and Query records should carry primitive/simple use-case input for adapter-facing use cases.
- Application services create domain value objects just before invoking aggregate behavior.
- Application Result records may define `from(domain)` when useful.
- Web Response DTOs may define `from(result)` or intent-revealing factories.
- Application DTOs must not depend on web DTOs.
- Domain models must not define `toResponse()`, `toJpaEntity()`, or adapter-specific conversion methods.
- Persistence conversion belongs in persistence mappers only.

## Aggregate Rules

- Aggregate roots use private constructors.
- New creation and persistence restoration must be explicit and separate.
- Use factory methods such as `createRentalCard(...)`, `registerMember(...)`, or `enterBook(...)` for new aggregates.
- Use `reconstitute(...)` only for persistence restoration.
- State changes must happen through behavior methods on the aggregate root.
- Do not add public setters to aggregate roots.
- Do not expose mutable collections. Return defensive copies.
- Validate invariants before changing state.
- Keep business policies in domain policy objects, value objects, or domain enums.
- Domain methods should expose business intent: `rentItem(...)`, `returnItem(...)`, `makeAvailableRental(...)`, `usePoint(...)`, not `setStatus(...)` or `updateData(...)`.

## Aggregate Boundary Identification

- Do not start from database tables, screens, API payloads, or repository convenience. Start from commands, domain events, and invariants.
- An aggregate boundary is a transactional consistency boundary. State that must be validated and changed atomically belongs inside one aggregate.
- State that can be synchronized asynchronously, compensated later, or read as a snapshot belongs in another aggregate or a read model.
- One aggregate has one root. External code loads, references, and mutates the aggregate only through the root.
- Child entities belong inside the aggregate only when their lifecycle and invariants are controlled by the root.
- Separate aggregate roots should reference each other by ID or immutable snapshot fields, not by holding each other's object graphs.
- Keep aggregates small enough to protect real invariants without serializing unrelated business changes.
- Query shape is not aggregate shape. Build read DTOs or read models for screens and reports instead of widening aggregates for convenience.
- If a rule cannot be expressed and tested as a root behavior method, the boundary is probably unclear.

## Enum Rules

- Use an enum when the concept is a finite, stable business vocabulary in the bounded context.
- Business status and classification enums belong in `domain/model`.
- Business policy enums with values or behavior belong in a domain policy layer such as `domain/model/policy`.
- Prefer enum methods that answer domain questions, such as `canRent(...)`, `overdueDateFrom(...)`, or `calculate(...)`.
- Do not keep enum-like business concepts as strings in commands, services, or aggregates.
- Do not use enum for unbounded or user-managed reference data. Use an entity, value object, or lookup table instead.
- Do not use enum for simple scalar values such as IDs, names, dates, or amounts. Use value objects when those values need validation.
- Convert external strings to enums at an application or adapter boundary before invoking domain behavior.
- If persistence uses a different code from the domain enum name, keep the conversion in the persistence adapter or converter.
- Do not add technical behavior such as HTTP labels, JSON formatting, or persistence SQL to domain enums.

## Port and Adapter Rules

- Inbound ports in `application/port/in` are use case interfaces.
- Outbound ports in `application/port/out` represent repository, gateway, clock, ID generation, or other external capabilities needed by application services.
- Application services depend on outbound ports, not concrete adapters.
- Persistence adapters implement outbound ports.
- Persistence adapters may return `Optional<T>` for absence.
- Application services decide whether absence is valid. If absence violates the use case, the service throws an application/domain exception.
- Controllers should receive successful results or exceptions handled by global exception handling, not `Optional<T>`.

## Mapper Rules

- Mapper classes are pure translators between persistence shape and domain shape.
- Mappers do not call repositories.
- Mappers do not call application services.
- Mappers do not apply business rules.
- Mappers do not generate IDs or timestamps unless the persistence shape itself requires a technical default.
- Mapping from entity to domain should use `reconstitute(...)`.
- Mapping from domain to entity should read explicit domain accessors.

## Coding Style

- Prefer early return and guard clauses to reduce nested conditions.
- Prefer parameter object records for use-case inputs instead of long parameter lists.
- Keep abstraction levels consistent in one method.
- Private helper methods should reveal intent, not hide unrelated complexity.
- Use records for immutable DTOs and value objects.
- Avoid class-level constants in application services for domain policy values. Move policy to domain.
- Prefer small behavior methods with domain names over procedural methods with technical names.

## Functional Use Case Composition

- Use Stream, Functional Interface, Lambda, and Generics to remove repeated use-case skeletons only when the business purpose is genuinely the same and only the behavior varies.
- Treat the skeleton as the fixed purpose and the lambda as the varying behavior, like `map(Function)` fixes transformation and receives how to transform.
- Prefer `Consumer<? super A>` for in-place aggregate behavior, `Function<? super T, ? extends R>` for transformation, `Predicate<? super T>` for selection, and `UnaryOperator<T>` for same-type value transformation.
- Keep functional skeletons private or package-local inside the application service unless multiple aggregates clearly share the same pattern.
- A functional skeleton may load an aggregate through an outbound port, apply aggregate behavior, save through an outbound port, and publish pulled domain events when the project uses domain events.
- Do not pass Spring Data repositories or adapter implementations into application skeletons. Pass outbound ports or method references backed by outbound ports.
- Business invariants still belong in aggregate behavior or domain policies. A lambda must invoke domain behavior, not reimplement the rule in the application service.
- Keep lambdas short. If a lambda needs more than a few lines, extract a domain-named method and pass a method reference.
- If the skeleton needs flags, multiple unrelated function parameters, or branching by business purpose, stop generalizing and split the use cases.
- Use Stream for collection transformation, filtering, grouping, and batch classification. Do not use Stream pipelines that mutate external state.
- Use `record` and `sealed interface` for stable multi-branch results whose cases carry different data. Do not model those cases as strings, nullable fields, or generic maps.
- A generic `Result` or `Either` style type may be useful for internal composition, but do not confuse it with application `Result` DTOs. Prefer domain-specific result names at boundaries.

## Comment Policy

- Comments must explain business context, design intent, invariant rationale, or boundary decisions that code cannot express alone.
- Comments must not explain obvious code operations.
- Do not use comments to compensate for vague names. Rename classes, methods, variables, and commands first.
- Public aggregate behavior, value objects, policies, ports, and non-trivial mappers may use concise Javadoc when it documents business meaning or boundary contracts.
- Prefer a domain-language method name over an inline comment.
- Remove dead code instead of commenting it out.
- Temporary comments such as `TODO`, `FIXME`, or `HACK` must include an owner or issue reference when the project uses issue tracking; otherwise avoid them.
- Do not write comments that repeat method names, parameter names, or getter behavior.

## Code Smells

- Web Request creates domain VO directly.
- Application Command contains domain model or web DTO.
- Domain model has `toResponse()` or `toJpaEntity()`.
- Controller manually assembles nested response DTOs.
- Application service contains domain policy constants.
- Aggregate exposes mutable lists or setters.
- Persistence mapper performs validation or business decisions.
- Repository `Optional` leaks to controller response design.
- Method mixes high-level use case flow with low-level field mapping.
- Functional helper hides distinct business purposes behind generic parameters.
- Lambda passed into a use-case skeleton contains business rules that belong in the aggregate.
- Names are technical but not business-readable, such as `process`, `handleData`, `updateStatus`, `flag`.

## Anti-Pattern Review Rules

- A code smell is a symptom. Find the responsibility leak behind it before fixing names mechanically.
- Structural anti-patterns usually move business decisions away from the domain.
- Linguistic anti-patterns usually hide the business meaning behind technical verbs or generic nouns.
- Do not fix a domain-language problem with comments. Rename the code so the comment becomes unnecessary.
- If a method name needs "and" to explain it, it probably has more than one responsibility.
- If a service method reads like field manipulation, move the behavior into the aggregate.
- If an adapter method reads like business policy, move the decision into application/domain.
- If a mapper method needs an `if` for a business rule, the mapper is doing more than mapping.

## Validation

- Add architecture tests for dependency direction.
- Add pure unit tests for value objects, policies, and aggregate behavior.
- Add application service tests with mocked outbound ports.
- Add mapper tests for entity-domain round trips when mappings are non-trivial.
````

---

# 핵심 철학

## 1. 도메인의 외부 무지

도메인은 외부를 모른다. 도메인 모델은 HTTP, DB, JSON, ORM, 프레임워크, 화면 응답 형태를 알면 안 된다. 도메인은 오직 비즈니스 사실과 규칙만 표현한다.

나쁜 예:

```java
// domain/model/RentalCard.java
public RentalCardResponse toResponse() {
    return new RentalCardResponse(rentalCardNo, member.id(), rentStatus.name());
}
```

좋은 예:

```java
// application/dto/RentalCardResult.java
public record RentalCardResult(
    String rentalCardNo,
    String memberId,
    String memberName,
    RentStatus rentStatus,
    long lateFee
) {
    public static RentalCardResult from(RentalCard rentalCard) {
        return new RentalCardResult(
            rentalCard.rentalCardNo(),
            rentalCard.member().id(),
            rentalCard.member().name(),
            rentalCard.rentStatus(),
            rentalCard.lateFee().point()
        );
    }
}
```

도메인은 “대여카드 번호”, “회원”, “대여 가능 상태”, “연체료”를 알면 충분하다. HTTP 응답 모양은 Web Adapter의 언어다.

## 2. 헥사고널 의존성 방향

의존성은 항상 바깥에서 안쪽으로 흐른다.

```text
Web Adapter  ->  Inbound Port  ->  Application Service  ->  Domain
                                      |
                                      v
                                Outbound Port  <-  Persistence Adapter
```

핵심은 “Application이 Adapter를 호출하지 않는다”가 아니라 “Application은 Port만 알고, Adapter는 그 Port를 구현한다”이다.

나쁜 예:

```java
@Service
public class RentalCardService {
    private final RentalCardJpaRepository repository;
}
```

좋은 예:

```java
@Service
public class RentalCardService {
    private final LoadRentalCardPort loadRentalCardPort;
    private final SaveRentalCardPort saveRentalCardPort;
}
```

JPA Repository는 저장 기술의 언어다. Application Service는 “대여카드를 불러온다”, “대여카드를 저장한다”는 유스케이스 언어만 알아야 한다.

## 3. Ubiquitous Language

코드는 문서이고, 문서는 비즈니스 규칙이다. 도메인 전문가가 읽어도 의미를 추론할 수 있어야 한다.

| 피해야 할 이름 | 좋은 이름 | 이유 |
| --- | --- | --- |
| `process(...)` | `rentItem(...)` | 무엇을 처리하는지 드러난다. |
| `updateStatus(...)` | `makeAvailableRental(...)` | 상태 변경의 업무 의미가 드러난다. |
| `UserInfo` | `RentalMember` | 대여 문맥에서의 회원 snapshot임을 드러낸다. |
| `BookData` | `RentalItem` | 대여 행위에 필요한 도서 정보임을 드러낸다. |
| `amount` | `lateFee` 또는 `point` | 돈인지 포인트인지 정책 언어를 드러낸다. |
| `check(...)` | `canRent(...)` | 판단 기준이 대여 가능성임을 드러낸다. |

도메인 언어가 살아 있는 코드의 예:

```java
RentalCard rentalCard = loadRentalCard(member);
rentalCard.rentItem(item);
rentalCard.returnItem(item, returnDate);
rentalCard.makeAvailableRental(point);
```

이 코드는 구현 상세보다 비즈니스 흐름을 먼저 읽게 한다.

---

# 계층별 DTO 분리와 변환 규칙

## 1. Request DTO는 Command로만 변환한다

Request DTO는 HTTP 입력 모양이다. Validation과 JSON alias 같은 Web 관심사는 허용하지만, Domain VO를 만들면 안 된다.

좋은 예:

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RentItemRequest(
    @NotNull Long itemNo,
    @NotBlank String itemTitle,
    @NotBlank String userId,
    @NotBlank String userName
) {
    public RentItemCommand toCommand() {
        return new RentItemCommand(userId, userName, itemNo, itemTitle);
    }
}
```

나쁜 예:

```java
public record RentItemRequest(...) {
    public RentalItem toRentalItem() {
        return new RentalItem(itemNo, itemTitle);
    }
}
```

Web Request가 Domain VO를 만들면 Web Adapter가 도메인 생성 규칙을 알게 된다. Domain VO 생성은 Application Service가 담당한다.

## 2. Command는 Use Case의 Parameter Object다

Command는 controller 메서드 인자를 묶기 위한 DTO가 아니다. “이 유스케이스를 실행하기 위해 필요한 입력”을 이름 붙인 Parameter Object다.

```java
public record RentItemCommand(
    String userId,
    String userName,
    Long itemNo,
    String itemTitle
) {
}
```

나쁜 예:

```java
RentalCardResult rentItem(String userId, String userName, Long itemNo, String itemTitle);
```

좋은 예:

```java
RentalCardResult rentItem(RentItemCommand command);
```

Parameter Object의 장점:

- 유스케이스 입력이 하나의 이름을 가진다.
- 필드 추가 시 메서드 시그니처 변경 범위가 줄어든다.
- 테스트 fixture를 만들기 쉽다.
- Controller와 Application 사이 경계가 선명해진다.

프로젝트가 소유한 Application Service, Port, 내부 helper의 메서드 파라미터는 최대 2개로 제한한다. 3개 이상의 값이 필요하면 primitive를 그대로 나열하지 말고 `Command`, `Query`, `Criteria`, `Context`처럼 호출 의도를 드러내는 Parameter Object로 묶는다.

Kotlin 운영 코드는 top-level 타입 하나당 파일 하나를 사용하며 파일명은 타입명과 일치시킨다. DTO, Result, 예외, Port 보조 모델도 같은 규칙을 따른다. 타입을 선언하지 않는 순수 extension mapping 함수는 책임이 하나인 `*Mappings.kt` 파일에 함께 둘 수 있다.

## 3. Result는 Domain을 밖으로 내보내기 위한 Application 모델이다

Application Result는 HTTP Response가 아니다. 화면 문구, HTTP 상태, JSON naming은 몰라야 한다.

```java
import java.util.List;

public record RentalCardResult(
    String rentalCardNo,
    String userId,
    String userName,
    RentStatus rentStatus,
    long lateFee,
    List<RentItemResult> rentItems,
    List<ReturnItemResult> returnItems
) {
    public static RentalCardResult from(RentalCard rentalCard) {
        return new RentalCardResult(
            rentalCard.rentalCardNo(),
            rentalCard.member().id(),
            rentalCard.member().name(),
            rentalCard.rentStatus(),
            rentalCard.lateFee().point(),
            rentalCard.getRentItemList().stream().map(RentItemResult::from).toList(),
            rentalCard.getReturnItemList().stream().map(ReturnItemResult::from).toList()
        );
    }
}
```

## 4. Response DTO는 최종 HTTP 모양을 소유한다

Controller가 문자열과 중첩 DTO를 직접 조립하지 않게 한다.

```java
import java.util.List;

public record RentalResultResponse(
    String message,
    String rentalCardNo,
    String userId,
    String userName,
    RentStatus rentStatus,
    long lateFee,
    List<RentItemResponse> rentItems,
    List<ReturnItemResponse> returnItems
) {
    public static RentalResultResponse rentAccepted(RentalCardResult rentalCard) {
        return from("도서 대여 요청을 접수했습니다.", rentalCard);
    }

    public static RentalResultResponse returnAccepted(RentalCardResult rentalCard) {
        return from("도서 반납 요청을 접수했습니다.", rentalCard);
    }

    private static RentalResultResponse from(String message, RentalCardResult rentalCard) {
        return new RentalResultResponse(
            message,
            rentalCard.rentalCardNo(),
            rentalCard.userId(),
            rentalCard.userName(),
            rentalCard.rentStatus(),
            rentalCard.lateFee(),
            RentItemResponse.from(rentalCard.rentItems()),
            ReturnItemResponse.from(rentalCard.returnItems())
        );
    }
}
```

나쁜 Controller:

```java
return BaseResponse.accepted(
    new RentalResultResponse(
        "도서 대여 요청을 접수했습니다.",
        RentalCardResponse.from(result)
    )
);
```

좋은 Controller:

```java
return BaseResponse.accepted(
    RentalResultResponse.rentAccepted(rentItemUseCase.rentItem(request.toCommand()))
).toResponseEntity();
```

---

# Aggregate 설계

## 1. Aggregate 경계 식별

Aggregate를 나누는 기준은 테이블, 화면, API 응답, 패키지 크기가 아니다. Aggregate는 “하나의 업무 명령을 처리할 때 반드시 함께 보호되어야 하는 일관성 경계”다.

먼저 다음 질문으로 경계를 찾는다.

| 질문 | 같은 Aggregate에 둘 가능성이 높다 | 분리할 가능성이 높다 |
| --- | --- | --- |
| 한 명령 안에서 반드시 동시에 검증되고 변경되어야 하는가? | 같은 transaction 안에서 불변식을 지켜야 한다. | 이벤트, 보상, 재시도, 별도 use case로 늦게 맞춰도 된다. |
| 한 객체가 다른 객체 없이 의미 있게 존재할 수 있는가? | 부모 root가 없으면 lifecycle이 없다. | 독립적으로 생성, 조회, 변경, 삭제된다. |
| 외부 코드가 어떤 객체를 직접 찾아 상태를 바꿔야 하는가? | root behavior를 통해서만 바뀐다. | 그 객체 자체가 별도 root 후보이다. |
| 변경 빈도와 소유자가 같은가? | 같은 업무 흐름에서 함께 바뀐다. | 서로 다른 업무 흐름에서 독립적으로 자주 바뀐다. |
| 다른 bounded context나 service가 소유한 상태인가? | 아니다. 현재 context의 언어와 규칙이다. | ID나 snapshot으로 참조하고 메시지로 동기화한다. |

실무 절차는 다음 순서로 진행한다.

1. Use case의 명령을 도메인 언어로 나열한다. 예: `rentItem`, `returnItem`, `usePoint`, `makeUnavailableBook`.
2. 각 명령이 만들어 내는 도메인 사실을 적는다. 예: “도서가 대여되었다”, “회원 포인트가 차감되었다”, “도서가 대여 불가가 되었다”.
3. 명령마다 반드시 지켜야 하는 invariant를 적는다. 예: 대여 한도, 중복 대여 금지, 대여 정지 상태 금지, 포인트 잔액 부족 금지.
4. 같은 invariant를 검증하기 위해 동시에 읽고 써야 하는 상태만 하나의 aggregate 안에 묶는다.
5. lifecycle이 root에 종속된 객체는 child entity나 value object로 둔다. 독립 lifecycle을 가지면 별도 aggregate root로 분리한다.
6. 다른 aggregate의 상태가 필요하면 객체 그래프를 들고 있지 말고 ID 또는 immutable snapshot field로 참조한다.
7. 화면이나 조회 요구가 여러 aggregate를 함께 보여 달라고 해도 aggregate를 합치지 않는다. response DTO, query model, read model에서 조합한다.

도서 대여 예제 흐름을 기준으로 보면 `RentalCard`, `Member`, `Book`은 하나로 합치지 않는다.

- `RentalCard`는 “회원이 현재 무엇을 대여 중인지”, “대여 한도와 중복 대여를 지키는지”, “반납 후 연체료 때문에 대여 정지되는지”를 보호한다. 그래서 `RentItem`과 `ReturnItem`은 `RentalCard` 안의 child model이다.
- `Member`는 회원 식별, 권한, 포인트 잔액과 포인트 증감 규칙을 보호한다. 대여카드가 회원 객체 전체를 소유하지 않는다.
- `Book`은 도서 등록 정보와 대여 가능 상태를 보호한다. 대여카드가 도서 aggregate를 직접 들고 상태를 바꾸지 않는다.
- `BestBook`은 대여 이벤트로 유지되는 read model에 가깝다. 대여 업무의 강한 일관성을 보호하기 위해 `RentalCard` 안으로 들어가지 않는다.

따라서 대여 요청 하나가 `RentalCard`, `Member`, `Book`을 모두 바꿔야 한다고 해서 세 모델이 하나의 aggregate가 되는 것은 아니다. 서비스 경계가 다르고, 각 모델이 보호하는 invariant가 다르며, 전체 흐름은 Kafka command/event/result와 보상으로 맞춘다. Aggregate 경계는 “한 transaction으로 다 바꾸고 싶은 범위”가 아니라 “반드시 한 root가 책임져야 하는 도메인 불변식의 범위”다.

경계가 잘못 잡혔다는 신호는 다음과 같다.

- Aggregate가 다른 aggregate repository를 직접 호출해야 규칙을 지킬 수 있다.
- Root 밖에서 child list를 꺼내 `add`, `remove`, `setStatus`를 호출한다.
- 하나의 aggregate가 화면 응답을 편하게 만들기 위해 여러 root의 상태를 모두 들고 있다.
- 한 root의 변경 때문에 관련 없는 업무 변경까지 같은 lock이나 transaction에 묶인다.
- Application service가 여러 객체의 필드를 직접 비교하고 수정하면서 aggregate method는 단순 setter처럼만 남아 있다.
- Persistence FK 관계를 그대로 domain object graph로 옮겨 aggregate가 과도하게 커진다.

반대로 좋은 경계는 테스트로 드러난다. `RentalCard.rentItem(...)` 테스트는 대여 한도와 중복 대여 금지를 aggregate 단위로 검증할 수 있어야 한다. `Member.usePoint(...)` 테스트는 포인트 부족 규칙을 회원 aggregate 단위로 검증할 수 있어야 한다. 테스트가 여러 repository, mapper, Kafka message 없이는 불변식을 설명하지 못한다면 경계나 책임 배치가 흐려진 것이다.

## 2. Aggregate 캡슐화

Aggregate는 내부 상태를 보호한다. 외부 코드는 aggregate의 list를 꺼내 수정하거나 setter로 상태를 바꾸면 안 된다.

나쁜 예:

```java
rentalCard.getRentItemList().add(RentItem.createRentalItem(item));
rentalCard.setRentStatus(RentStatus.RENT_UNAVAILABLE);
```

좋은 예:

```java
rentalCard.rentItem(item);
rentalCard.overdueItem(item);
```

행위 메서드 안에서 검증과 상태 변경이 함께 일어난다.

## 3. 생성과 복원의 명시적 분리

새 aggregate 생성과 DB 복원은 의미가 다르다.

- `createRentalCard(...)`: 새 업무 상태를 만든다.
- `reconstitute(...)`: 저장된 상태를 그대로 복원한다.

```java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RentalCard {
    private final String rentalCardNo;
    private final RentalMember member;
    private RentStatus rentStatus;
    private LateFee lateFee;
    private final List<RentItem> rentItemList;
    private final List<ReturnItem> returnItemList;

    private RentalCard(
        String rentalCardNo,
        RentalMember member,
        RentStatus rentStatus,
        LateFee lateFee,
        List<RentItem> rentItemList,
        List<ReturnItem> returnItemList
    ) {
        this.rentalCardNo = rentalCardNo;
        this.member = member;
        this.rentStatus = rentStatus;
        this.lateFee = lateFee;
        this.rentItemList = new ArrayList<>(rentItemList);
        this.returnItemList = new ArrayList<>(returnItemList);
    }

    public static RentalCard createRentalCard(RentalMember creator) {
        return new RentalCard(
            RentalCardNo.createRentalCardNo().no(),
            creator,
            RentStatus.RENT_AVAILABLE,
            new LateFee(0),
            List.of(),
            List.of()
        );
    }

    public static RentalCard reconstitute(
        String rentalCardNo,
        RentalMember member,
        RentStatus rentStatus,
        LateFee lateFee,
        List<RentItem> rentItems,
        List<ReturnItem> returnItems
    ) {
        return new RentalCard(rentalCardNo, member, rentStatus, lateFee, rentItems, returnItems);
    }

    public void rentItem(RentalItem item) {
        if (rentStatus == RentStatus.RENT_UNAVAILABLE) {
            throw new IllegalArgumentException("대여 정지 상태에서는 도서를 대여할 수 없습니다.");
        }
        if (!RentalLimitPolicy.STANDARD.canRent(rentItemList.size())) {
            throw new IllegalArgumentException(
                "대여 중인 도서는 최대 " + RentalLimitPolicy.STANDARD.maxRentalCount() + "권까지 가능합니다."
            );
        }
        if (findRentItem(item) != null) {
            throw new IllegalArgumentException("이미 대여 중인 도서입니다.");
        }

        rentItemList.add(RentItem.createRentalItem(item));
    }

    public RentalCard returnItem(RentalItem item, LocalDate returnDate) {
        RentItem rentItem = requireRentItem(item);
        rentItemList.remove(rentItem);

        long latePoint = RentalLateFeePolicy.DAILY.calculate(rentItem.overdueDate(), returnDate);
        if (latePoint > 0) {
            lateFee = lateFee.addPoint(latePoint);
            rentStatus = RentStatus.RENT_UNAVAILABLE;
        }

        returnItemList.add(ReturnItem.createReturnItem(rentItem, returnDate));
        return this;
    }

    private RentItem requireRentItem(RentalItem item) {
        RentItem rentItem = findRentItem(item);
        if (rentItem == null) {
            throw new IllegalArgumentException("대여 중인 도서가 아닙니다.");
        }
        return rentItem;
    }

    private RentItem findRentItem(RentalItem item) {
        return rentItemList.stream()
            .filter(rentItem -> rentItem.isSameItem(item))
            .findFirst()
            .orElse(null);
    }

    public String rentalCardNo() {
        return rentalCardNo;
    }

    public RentalMember member() {
        return member;
    }

    public RentStatus rentStatus() {
        return rentStatus;
    }

    public LateFee lateFee() {
        return lateFee;
    }

    public List<RentItem> getRentItemList() {
        return List.copyOf(rentItemList);
    }

    public List<ReturnItem> getReturnItemList() {
        return List.copyOf(returnItemList);
    }
}
```

이 코드의 철학:

- 생성자는 `private`이다.
- 신규 생성은 `createRentalCard(...)`로만 가능하다.
- 저장소 복원은 `reconstitute(...)`로만 가능하다.
- 대여 한도, 중복 대여, 대여 정지 검증은 aggregate 내부에 있다.
- 내부 컬렉션은 `List.copyOf(...)`로만 노출한다.
- 외부 코드가 aggregate 상태를 임의로 만들거나 바꾸는 길을 막는다.

## 4. 도메인 정책의 응집

정책 값은 application service의 상수가 아니다. 정책은 도메인 언어이며, 도메인 계층에 응집되어야 한다.

나쁜 예:

```java
@Service
public class RentalCardService {
    private static final int MAX_RENTAL_COUNT = 5;
    private static final long DAILY_LATE_FEE = 10;
}
```

좋은 예:

```java
public enum RentalLimitPolicy {
    STANDARD(5);

    private final int maxRentalCount;

    RentalLimitPolicy(int maxRentalCount) {
        this.maxRentalCount = maxRentalCount;
    }

    public boolean canRent(int currentRentalCount) {
        return currentRentalCount < maxRentalCount;
    }

    public int maxRentalCount() {
        return maxRentalCount;
    }
}
```

```java
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum RentalLateFeePolicy {
    DAILY(10);

    private final long pointPerDay;

    RentalLateFeePolicy(long pointPerDay) {
        this.pointPerDay = pointPerDay;
    }

    public long calculate(LocalDate overdueDate, LocalDate returnDate) {
        long overdueDays = ChronoUnit.DAYS.between(overdueDate, returnDate);
        return Math.max(overdueDays, 0) * pointPerDay;
    }
}
```

정책이 도메인 안에 있으면 테스트도 도메인 언어로 작성된다.

```java
@Test
void standard_policy_allows_only_five_rental_items() {
    assertThat(RentalLimitPolicy.STANDARD.canRent(4)).isTrue();
    assertThat(RentalLimitPolicy.STANDARD.canRent(5)).isFalse();
}
```

## 5. Enum 사용 기준

Enum은 “문자열 상수를 보기 좋게 바꾼 것”이 아니다. DDD에서 enum은 bounded context 안에서 합의된 유한한 도메인 언어를 타입으로 고정하는 도구다.

이 지침서의 enum 예시는 크게 두 종류다.

| 종류 | 위치 | 예시 | 사용 기준 |
| --- | --- | --- | --- |
| 상태/분류 enum | `domain/model` | `RentStatus`, `BookStatus`, `UserRole`, `Classification`, `Location`, `Source` | 유한하고 안정적인 업무 상태나 분류 |
| 정책 enum | `domain/model/policy` | `RentalLimitPolicy`, `RentalPeriodPolicy`, `RentalPointPolicy`, `RentalLateFeePolicy` | 유한한 정책 variant가 값이나 계산 행위를 가질 때 |

### 상태/분류 enum

상태 enum은 aggregate의 현재 업무 상태를 드러낸다.

```java
/**
 * 대여카드가 도서를 빌릴 수 있는지 나타내는 상태입니다.
 */
public enum RentStatus {
    RENT_AVAILABLE,
    RENT_UNAVAILABLE
}
```

```java
/**
 * 도서가 대여 흐름에서 가질 수 있는 상태를 나타냅니다.
 */
public enum BookStatus {
    ENTERED,
    AVAILABLE,
    UNAVAILABLE
}
```

상태 enum을 쓰면 aggregate의 규칙이 문자열 비교가 아니라 도메인 상태 비교로 읽힌다.

```java
public void rentItem(RentalItem item) {
    if (rentStatus == RentStatus.RENT_UNAVAILABLE) {
        throw new IllegalArgumentException("대여 정지 상태에서는 도서를 대여할 수 없습니다.");
    }

    rentItemList.add(RentItem.createRentalItem(item));
}
```

나쁜 예:

```java
if ("RENT_UNAVAILABLE".equals(rentalCard.status())) {
    throw new IllegalArgumentException("대여 정지 상태에서는 도서를 대여할 수 없습니다.");
}
```

문자열은 오타와 잘못된 값이 컴파일 타임에 걸리지 않는다. `RentStatus`는 가능한 상태를 코드 안에서 제한한다.

### 정책 enum

정책 enum은 값만 들고 있는 상수 묶음보다 한 단계 더 나아간다. 정책 값과 그 값을 사용하는 판단 또는 계산을 함께 둔다.

```java
/**
 * 대여카드가 동시에 보유할 수 있는 대여 도서 수 정책입니다.
 */
public enum RentalLimitPolicy {
    STANDARD(5);

    private final int maxRentalCount;

    RentalLimitPolicy(int maxRentalCount) {
        this.maxRentalCount = maxRentalCount;
    }

    public boolean canRent(int currentRentalCount) {
        return currentRentalCount < maxRentalCount;
    }

    public int maxRentalCount() {
        return maxRentalCount;
    }
}
```

```java
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 반납 지연 일수에 따라 연체 포인트를 계산하는 정책입니다.
 */
public enum RentalLateFeePolicy {
    DAILY(10L);

    private final long pointPerDay;

    RentalLateFeePolicy(long pointPerDay) {
        this.pointPerDay = pointPerDay;
    }

    public long calculate(LocalDate overdueDate, LocalDate returnDate) {
        if (returnDate.isAfter(overdueDate)) {
            return ChronoUnit.DAYS.between(overdueDate, returnDate) * pointPerDay;
        }
        return 0;
    }
}
```

Aggregate는 정책의 내부 값을 직접 계산하지 않고 정책의 언어를 호출한다.

```java
if (!RentalLimitPolicy.STANDARD.canRent(rentItemList.size())) {
    throw new IllegalArgumentException(
        "대여 중인 도서는 최대 " + RentalLimitPolicy.STANDARD.maxRentalCount() + "권까지 가능합니다."
    );
}

long latePoint = RentalLateFeePolicy.DAILY.calculate(rentItem.overdueDate(), returnDate);
```

나쁜 예:

```java
private static final int MAX_RENTAL_COUNT = 5;
private static final long LATE_FEE_POINT_PER_DAY = 10L;

if (rentItemList.size() >= MAX_RENTAL_COUNT) {
    throw new IllegalArgumentException("대여 한도를 초과했습니다.");
}
```

상수는 정책의 이름과 행위를 흩뜨린다. 정책 enum은 “대여 한도 정책”, “연체료 정책”이라는 도메인 개념을 응집한다.

### Enum을 쓰면 안 되는 경우

Enum은 유한하고 안정적인 개념에만 쓴다. 값의 목록이 운영 중 자주 바뀌거나 사용자가 추가할 수 있으면 enum이 아니라 데이터로 모델링한다.

| 상황 | enum 적합 여부 | 더 나은 모델 |
| --- | --- | --- |
| 대여 가능/불가능 상태 | 적합 | `RentStatus` |
| 도서 입고/대여 가능/대여 불가능 상태 | 적합 | `BookStatus` |
| 회원 권한 ADMIN/USER | 적합 | `UserRole` |
| 표준 대여 기간 14일 | 적합, 정책이 고정이면 | `RentalPeriodPolicy.STANDARD` |
| 관리자가 화면에서 추가하는 도서 카테고리 | 부적합 | `Category` entity 또는 lookup table |
| 회원 ID, 도서명, 이메일 | 부적합 | value object |
| 금액, 포인트, 수량 | 단독 enum 부적합 | value object 또는 policy enum의 내부 값 |
| 환경별로 달라지는 timeout, page size | 부적합 | configuration properties |

### 외부 문자열을 enum으로 바꾸는 위치

외부 입력은 보통 문자열로 들어온다. Request DTO와 Command는 primitive/simple field를 유지하고, application service 또는 adapter boundary에서 도메인 enum으로 바꾼 뒤 aggregate에 전달한다.

```java
public BookResult enterBook(EnterBookCommand command) {
    Book book = Book.enterBook(
        command.title(),
        new BookDesc(command.description()),
        Classification.valueOf(command.classification()),
        Location.valueOf(command.location())
    );

    return BookResult.from(saveBookPort.save(book));
}
```

더 명확한 오류 메시지가 필요하면 enum에 명시적 factory를 둘 수 있다.

```java
public enum Classification {
    ARTS,
    COMPUTER,
    LITERATURE;

    public static Classification from(String name) {
        try {
            return Classification.valueOf(name);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("지원하지 않는 도서 분류입니다: " + name);
        }
    }
}
```

이때도 Request DTO가 `Classification`을 만들지 않는다. Request는 `EnterBookCommand`에 문자열을 넘기고, use case boundary에서 도메인 타입으로 변환한다.

### Domain enum에 넣으면 안 되는 것

Domain enum은 도메인 언어를 표현해야 한다. 기술 표현을 넣으면 안 된다.

나쁜 예:

```java
public enum BookStatus {
    AVAILABLE("available", 200, "대여 가능");

    private final String jsonValue;
    private final int httpStatus;
    private final String label;
}
```

좋은 예:

```java
public enum BookStatus {
    ENTERED,
    AVAILABLE,
    UNAVAILABLE
}
```

HTTP status, JSON label, 화면 문구는 Web Adapter 또는 Response DTO의 책임이다. DB 저장 코드가 enum 이름과 다르면 persistence converter에서 해결한다.

---

# Application Service 작성 철학

Application Service는 흐름을 조율한다. 비즈니스 규칙을 새로 만들지 않는다.

## 1. Service는 Command를 Domain VO로 바꾼다

```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RentalCardService implements RentItemUseCase {
    private final LoadRentalCardPort loadRentalCardPort;
    private final SaveRentalCardPort saveRentalCardPort;

    @Override
    public RentalCardResult rentItem(RentItemCommand command) {
        RentalMember member = rentalMember(command);
        RentalItem item = rentalItem(command);

        RentalCard rentalCard = loadRentalCardPort.loadRentalCard(member.id())
            .orElseGet(() -> RentalCard.createRentalCard(member));

        rentalCard.rentItem(item);

        return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
    }

    private RentalMember rentalMember(RentItemCommand command) {
        return new RentalMember(command.userId(), command.userName());
    }

    private RentalItem rentalItem(RentItemCommand command) {
        return new RentalItem(command.itemNo(), command.itemTitle());
    }
}
```

여기서 service의 책임은 명확하다.

- 입력 Command를 도메인 VO로 바꾼다.
- 필요한 aggregate를 로드하거나 생성한다.
- aggregate behavior를 호출한다.
- port를 통해 저장한다.
- Result로 반환한다.

## 2. Adapter는 Optional, Service는 예외

저장소 조회 결과가 없을 수 있다는 사실은 adapter/out persistence의 자연스러운 반환이다. 하지만 유스케이스 관점에서 “없음”이 허용되는지 여부는 application service가 결정한다.

Outbound Port:

```java
import java.util.Optional;

public interface LoadRentalCardPort {
    Optional<RentalCard> loadRentalCard(String userId);
}
```

Persistence Adapter:

```java
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RentalCardPersistenceAdapter implements LoadRentalCardPort {
    private final RentalCardJpaRepository repository;
    private final RentalCardPersistenceMapper mapper;

    @Override
    public Optional<RentalCard> loadRentalCard(String userId) {
        return repository.findByMemberId(userId).map(mapper::toDomain);
    }
}
```

Application Service:

```java
private RentalCard loadRequiredRentalCard(String userId) {
    return loadRentalCardPort.loadRentalCard(userId)
        .orElseThrow(() -> new IllegalArgumentException("대여카드가 없습니다."));
}
```

규칙:

- Adapter는 “조회 결과 없음”을 `Optional.empty()`로 표현해도 된다.
- Service는 use case 의미에 따라 `orElseGet(...)` 또는 `orElseThrow(...)`를 선택한다.
- Controller는 `Optional`을 직접 다루지 않는다.

## 3. Command 메서드의 의도 노출

Command 이름과 use case 메서드는 업무 행위를 드러내야 한다.

나쁜 예:

```java
public interface RentalCardUseCase {
    RentalCardResult update(RentalCardCommand command);
}
```

좋은 예:

```java
public interface RentItemUseCase {
    RentalCardResult rentItem(RentItemCommand command);
}

public interface ReturnItemUseCase {
    RentalCardResult returnItem(ReturnItemCommand command);
}

public interface ClearOverdueItemUseCase {
    RentalCardResult clearOverdue(ClearOverdueCommand command);
}
```

메서드명만 읽어도 비즈니스 흐름이 드러난다. 이것이 “코드가 곧 문서”라는 뜻이다.

## 4. Early Return으로 흐름 단순화

중첩 조건은 도메인 규칙을 숨긴다. Guard clause와 early return을 사용하면 성공 경로와 예외 경로가 분리된다.

나쁜 예:

```java
public Book makeUnavailable() {
    if (bookStatus != BookStatus.UNAVAILABLE) {
        this.bookStatus = BookStatus.UNAVAILABLE;
        return this;
    } else {
        throw new IllegalStateException("이미 대여 중인 도서입니다.");
    }
}
```

좋은 예:

```java
public Book makeUnavailable() {
    if (bookStatus == BookStatus.UNAVAILABLE) {
        throw new IllegalStateException("이미 대여 중인 도서입니다.");
    }

    this.bookStatus = BookStatus.UNAVAILABLE;
    return this;
}
```

Early return은 “예외 조건을 먼저 제거하고, 정상 흐름을 아래에 남긴다”는 코드 작성 방식이다.

## 5. 추상화 수준의 일관성

한 메서드 안에서 “유스케이스 흐름”과 “필드 하나하나 조립”이 섞이면 읽기 어렵다.

나쁜 예:

```java
public RentalCardResult rentItem(RentItemCommand command) {
    RentalMember member = new RentalMember(command.userId(), command.userName());
    RentalItem item = new RentalItem(command.itemNo(), command.itemTitle());
    RentalCard rentalCard = loadRentalCardPort.loadRentalCard(member.id())
        .orElseGet(() -> RentalCard.createRentalCard(member));
    rentalCard.rentItem(item);
    RentalCard saved = saveRentalCardPort.save(rentalCard);
    return new RentalCardResult(
        saved.rentalCardNo(),
        saved.member().id(),
        saved.member().name(),
        saved.rentStatus(),
        saved.lateFee().point(),
        saved.getRentItemList().stream().map(RentItemResult::from).toList(),
        saved.getReturnItemList().stream().map(ReturnItemResult::from).toList()
    );
}
```

좋은 예:

```java
public RentalCardResult rentItem(RentItemCommand command) {
    RentalMember member = rentalMember(command);
    RentalItem item = rentalItem(command);

    RentalCard rentalCard = loadOrCreateRentalCard(member);
    rentalCard.rentItem(item);

    return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
}

private RentalCard loadOrCreateRentalCard(RentalMember member) {
    return loadRentalCardPort.loadRentalCard(member.id())
        .orElseGet(() -> RentalCard.createRentalCard(member));
}
```

위 메서드는 한 수준의 언어로 읽힌다.

1. 회원과 도서를 만든다.
2. 대여카드를 가져오거나 만든다.
3. 도서를 대여한다.
4. 저장 후 결과를 반환한다.

## 6. 함수형 Java로 유스케이스 골격을 고정한다

Stream, Functional Interface, Lambda, Generic은 “짧게 쓰는 문법”이 아니라 반복되는 목적을 한 번만 작성하기 위한 설계 도구다.

`map`이 “변환한다”는 목적을 고정하고 “어떻게 변환할지”를 `Function`으로 받듯이, application service도 반복되는 유스케이스 골격을 고정하고 달라지는 행위만 함수로 주입할 수 있다.

판단 기준은 단순하다.

- 목적이 진짜 하나이고 행위만 다르면 함수 파라미터로 받는다.
- 목적 자체가 다르면 유스케이스 메서드를 나눈다.
- 비즈니스 규칙은 lambda 안에 새로 만들지 않고 aggregate behavior 또는 domain policy를 호출한다.
- 외부 기술 구현체는 넘기지 않는다. application service는 outbound port 또는 그 port의 method reference만 사용한다.

나쁜 예:

```java
public void blockMember(MemberId id) {
    Member member = loadMemberPort.loadMember(id)
        .orElseThrow(() -> new MemberNotFoundException(id));
    member.block();
    saveMemberPort.save(member);
}

public void changeTier(MemberId id, Tier tier) {
    Member member = loadMemberPort.loadMember(id)
        .orElseThrow(() -> new MemberNotFoundException(id));
    member.changeTier(tier);
    saveMemberPort.save(member);
}

public void resetPassword(MemberId id, Password password) {
    Member member = loadMemberPort.loadMember(id)
        .orElseThrow(() -> new MemberNotFoundException(id));
    member.resetPassword(password);
    saveMemberPort.save(member);
}
```

좋은 예:

```java
private Member modifyMember(MemberId id, Consumer<? super Member> behavior) {
    Member member = loadMemberPort.loadMember(id)
        .orElseThrow(() -> new MemberNotFoundException(id));

    behavior.accept(member);

    return saveMemberPort.save(member);
}

public MemberResult blockMember(BlockMemberCommand command) {
    return MemberResult.from(
        modifyMember(command.memberId(), Member::block)
    );
}

public MemberResult changeTier(ChangeTierCommand command) {
    return MemberResult.from(
        modifyMember(command.memberId(), member -> member.changeTier(command.tier()))
    );
}

public MemberResult resetPassword(ResetPasswordCommand command) {
    return MemberResult.from(
        modifyMember(command.memberId(), member -> member.resetPassword(command.password()))
    );
}
```

이 패턴의 이득은 메서드 개수를 줄이는 것이 아니다. “회원 aggregate를 찾아서 도메인 행위를 적용하고 저장한다”는 목적을 한 곳에 고정하는 것이다. 나중에 트랜잭션 범위, 예외 변환, 감사 로그, 도메인 이벤트 발행 같은 횡단 관심사가 생겨도 골격 한 곳에서만 다룬다.

조회처럼 “찾아서 변환한다”가 목적이면 `Function`을 사용한다.

```java
private <R> R getMember(MemberId id, Function<? super Member, ? extends R> mapper) {
    return loadMemberPort.loadMember(id)
        .map(mapper)
        .orElseThrow(() -> new MemberNotFoundException(id));
}

public MemberSummaryResult getSummary(MemberId id) {
    return getMember(id, MemberSummaryResult::from);
}

public MemberDetailResult getDetail(MemberId id) {
    return getMember(id, MemberDetailResult::from);
}
```

## 7. 제네릭 골격은 port 기반으로만 일반화한다

여러 aggregate가 정말 같은 패턴을 공유할 때만 `<A, ID>` 수준으로 올린다. 이때도 `CrudRepository`, JPA repository, Kafka producer 같은 adapter 구현체를 application service 골격에 넘기면 안 된다. Hexagonal 경계 안에서는 outbound port method reference를 넘긴다.

```java
private <A, ID> A modifyAggregate(
        ID id,
        Function<? super ID, Optional<A>> loader,
        Function<? super A, ? extends A> saver,
        Function<? super ID, ? extends RuntimeException> notFound,
        Consumer<? super A> behavior
) {
    A aggregate = loader.apply(id)
        .orElseThrow(() -> notFound.apply(id));

    behavior.accept(aggregate);

    return saver.apply(aggregate);
}
```

사용 예:

```java
public RentalCardResult returnItem(ReturnItemCommand command) {
    RentalCard saved = modifyAggregate(
        command.rentalCardNo(),
        loadRentalCardPort::loadRentalCard,
        saveRentalCardPort::save,
        RentalCardNotFoundException::new,
        rentalCard -> rentalCard.returnItem(rentalItem(command))
    );

    return RentalCardResult.from(saved);
}
```

프로젝트가 aggregate-collected domain event를 사용한다면 “저장 후 이벤트 발행”도 같은 골격에 고정할 수 있다. 이때도 event payload 생성은 aggregate가 상태 변경 후 기록한 service-local domain event를 사용하고, Kafka 메시지 변환은 messaging adapter가 담당한다.

```java
private RentalCard modifyRentalCard(
        RentalCardNo rentalCardNo,
        Consumer<? super RentalCard> behavior
) {
    RentalCard rentalCard = loadRentalCardPort.loadRentalCard(rentalCardNo)
        .orElseThrow(() -> new RentalCardNotFoundException(rentalCardNo));

    behavior.accept(rentalCard);

    var events = rentalCard.pullDomainEvents();
    RentalCard saved = saveRentalCardPort.save(rentalCard);
    publishRentalEventPort.publishAll(events);

    return saved;
}
```

이 골격은 “저장하면 도메인 이벤트를 발행한다”는 유스케이스 불변식을 application service에 응집한다. 새 유스케이스가 저장만 하고 이벤트 발행을 빠뜨리는 실수를 줄인다.

단, generic skeleton은 늦게 도입한다. aggregate가 한 종류뿐이거나, 일반화 때문에 호출부가 더 읽기 어려워지면 구체 타입 private helper가 더 낫다.

## 8. 정책 함수는 도메인 언어를 대체하지 않는다

정책이 값처럼 조합될 수 있으면 `Function`, `Predicate`, `UnaryOperator`로 표현할 수 있다. 그러나 application service의 lambda가 비즈니스 규칙을 새로 소유하면 안 된다.

나쁜 예:

```java
private Money applyDiscount(Order order, Function<Order, Money> discountPolicy) {
    return order.total().minus(discountPolicy.apply(order));
}

Function<Order, Money> vipPolicy = order -> order.total().percent(10);
```

위 코드는 할인 정책이 application service 주변의 익명 함수로 흩어진다. 고정된 도메인 정책이면 도메인 정책 객체나 enum으로 이름을 가져야 한다.

좋은 예:

```java
public enum DiscountPolicy {
    VIP {
        @Override
        public Money discountFor(Order order) {
            return order.total().percent(10);
        }
    },
    SEASON {
        @Override
        public Money discountFor(Order order) {
            return order.total().percent(5);
        }
    };

    public static DiscountPolicy from(String code) {
        return DiscountPolicy.valueOf(code);
    }

    public abstract Money discountFor(Order order);
}
```

Application service는 정책을 선택하고 aggregate 또는 domain service에 전달할 수 있다.

```java
public OrderResult applyDiscount(ApplyDiscountCommand command) {
    Order order = loadOrderPort.loadOrder(command.orderId())
        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
    DiscountPolicy policy = DiscountPolicy.from(command.discountPolicyCode());

    order.applyDiscount(policy);

    return OrderResult.from(saveOrderPort.save(order));
}
```

`Predicate`나 Specification은 조회 조건, 선택 조건, 필터 조합에 적합하다. 반드시 실패해야 하는 불변식은 `member.ensureCanRent(...)`, `rentalCard.rentItem(...)` 같은 aggregate behavior가 더 명확하다.

## 9. Stream은 컬렉션 흐름을 값으로 다룰 때 쓴다

Stream은 collection을 변환, 필터링, 그룹핑, 분류할 때 사용한다. 외부 리스트를 만들어 `forEach`에서 값을 추가하는 방식은 Stream의 장점을 버리는 코드다.

나쁜 예:

```java
List<RentItemResult> results = new ArrayList<>();

rentalCard.getRentItemList().stream()
    .filter(RentItem::isOverdue)
    .forEach(item -> results.add(RentItemResult.from(item)));
```

좋은 예:

```java
List<RentItemResult> results = rentalCard.getRentItemList().stream()
    .filter(RentItem::isOverdue)
    .map(RentItemResult::from)
    .toList();
```

여러 건을 처리하면서 멈추지 않고 성공/실패를 모아야 한다면 실패를 값으로 만든다. 이것은 “빠른 실패”가 아니라 batch 처리 관점이다.

```java
sealed interface Attempt<T, R> permits Attempt.Success, Attempt.Failure {
    T input();

    boolean isSuccess();

    record Success<T, R>(T input, R result) implements Attempt<T, R> {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    record Failure<T, R>(T input, Exception error) implements Attempt<T, R> {
        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    static <T, R> Attempt<T, R> of(
            T input,
            Function<? super T, ? extends R> action
    ) {
        try {
            return new Success<>(input, action.apply(input));
        } catch (Exception e) {
            return new Failure<>(input, e);
        }
    }
}
```

```java
record BatchResult<T, R>(
        List<Attempt<T, R>> succeeded,
        List<Attempt<T, R>> failed
) {
}

public <T, R> BatchResult<T, R> processEach(
        List<T> items,
        Function<? super T, ? extends R> action
) {
    Map<Boolean, List<Attempt<T, R>>> partitioned = items.stream()
        .map(item -> Attempt.of(item, action))
        .collect(Collectors.partitioningBy(Attempt::isSuccess));

    return new BatchResult<>(
        partitioned.getOrDefault(true, List.of()),
        partitioned.getOrDefault(false, List.of())
    );
}
```

이런 helper는 notification, migration, batch command처럼 “각 입력을 독립적으로 시도하고 결과를 분류한다”는 목적이 분명할 때만 사용한다. 단일 aggregate 변경 유스케이스에서 실패를 삼키면 정합성 문제가 숨을 수 있다.

## 10. Result/Either와 ADT는 분기 누락을 줄이는 도구다

선형 흐름에서는 `Result<T, E>` 또는 `Either` 스타일이 유용할 수 있다. 성공 값은 `map`, 실패 가능 다음 단계는 `flatMap`으로 이어 붙인다.

```java
findRentalCard(command.rentalCardNo())
    .flatMap(rentalCard -> rentalCard.tryReturn(rentalItem(command)))
    .map(RentalCardResult::from);
```

다만 application boundary에서 모든 결과를 `Result<Object, Error>` 같은 범용 이름으로 덮으면 도메인 언어가 사라진다. 외부에 드러나는 결과는 `RentalCardResult`, `ReturnRejected`, `MemberBlocked`처럼 use case 언어를 우선한다.

결과나 상태가 여러 갈래이고, 갈래마다 데이터가 다르면 `sealed interface`와 `record`로 ADT를 표현한다.

```java
sealed interface RentalDecision permits RentalAccepted, OutOfStock, MemberBlocked {
}

record RentalAccepted(RentalCardNo rentalCardNo, DueDate dueDate) implements RentalDecision {
}

record OutOfStock(ItemNo itemNo) implements RentalDecision {
}

record MemberBlocked(MemberId memberId, String reason) implements RentalDecision {
}
```

사용처는 Java 21 switch pattern matching으로 가능한 케이스를 모두 처리한다.

```java
String message = switch (decision) {
    case RentalAccepted accepted -> "대여 완료: " + accepted.dueDate();
    case OutOfStock outOfStock -> "재고 없음: " + outOfStock.itemNo();
    case MemberBlocked blocked -> "회원 차단: " + blocked.reason();
};
```

나중에 `RentalLimitExceeded` 케이스가 추가되면 이 switch는 컴파일 단계에서 처리 누락을 드러낸다. 보상 흐름, 승인/거절 결과, 상태 전이 결과처럼 누락이 위험한 곳에서 유용하다.

선택 기준:

| 상황 | 도구 |
| --- | --- |
| 목적 하나, 행위만 다름 | 함수 주입 |
| 결과/상태가 여러 갈래이고 케이스별 데이터가 다름 | `sealed interface` + `record` |
| 행위가 타입의 본연한 책임 | 다형성 |
| 일부 케이스만 관심 있음 | `instanceof` pattern matching 또는 명시적 guard |
| 컬렉션 변환/분류 | Stream + Collector |
| 실패 가능 선형 흐름 | `Result.flatMap` 스타일 |

## 11. 함수형 추상화의 중단선

함수형 스타일은 가독성을 높일 때만 사용한다. 다음 신호가 보이면 일반화를 멈춘다.

| 신호 | 의미 | 대응 |
| --- | --- | --- |
| lambda가 길다 | 이름이 필요한 도메인 행위다 | private method 또는 aggregate behavior로 추출 |
| 함수 파라미터가 많다 | 호출부가 골격보다 복잡하다 | 구체 유스케이스 메서드 유지 |
| skeleton 내부에 business `if`가 늘어난다 | 목적이 하나가 아니다 | 유스케이스를 나눈다 |
| generic 타입 이름만 있고 도메인 언어가 사라진다 | 재사용이 의미를 압도한다 | 구체 타입과 도메인 이름을 복원 |
| lambda가 aggregate 필드를 직접 판단한다 | 비즈니스 규칙 위치가 잘못됐다 | aggregate method/domain policy로 이동 |

재사용성과 가독성이 충돌하면 가독성이 이긴다. 이 가이드의 목적은 함수형 기법을 많이 쓰는 것이 아니라, 같은 목적을 한 번만 쓰고 달라지는 행위만 안전하게 주입하는 것이다.

---

# Mapper의 순수성

Mapper는 번역기다. 판단자가 아니다.

## 좋은 Persistence Mapper

```java
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RentalCardPersistenceMapper {
    public RentalCardJpaEntity toJpaEntity(RentalCard rentalCard) {
        List<RentItemJpaEmbeddable> rentItems = rentalCard.getRentItemList().stream()
            .map(this::toRentItemJpa)
            .toList();
        List<ReturnItemJpaEmbeddable> returnItems = rentalCard.getReturnItemList().stream()
            .map(this::toReturnItemJpa)
            .toList();

        return new RentalCardJpaEntity(
            rentalCard.rentalCardNo(),
            rentalCard.member().id(),
            rentalCard.member().name(),
            rentalCard.rentStatus(),
            rentalCard.lateFee().point(),
            rentItems,
            returnItems
        );
    }

    public RentalCard toDomain(RentalCardJpaEntity entity) {
        return RentalCard.reconstitute(
            entity.getRentalCardNo(),
            new RentalMember(entity.getMemberId(), entity.getMemberName()),
            entity.getRentStatus(),
            new LateFee(entity.getLateFeePoint()),
            entity.getRentItems().stream().map(this::toRentItemDomain).toList(),
            entity.getReturnItems().stream().map(this::toReturnItemDomain).toList()
        );
    }

    private RentItemJpaEmbeddable toRentItemJpa(RentItem rentItem) {
        return new RentItemJpaEmbeddable(
            rentItem.item().no(),
            rentItem.item().title(),
            rentItem.rentDate(),
            rentItem.overdue(),
            rentItem.overdueDate()
        );
    }

    private ReturnItemJpaEmbeddable toReturnItemJpa(ReturnItem returnItem) {
        RentItem rentItem = returnItem.item();
        return new ReturnItemJpaEmbeddable(
            rentItem.item().no(),
            rentItem.item().title(),
            rentItem.rentDate(),
            rentItem.overdue(),
            rentItem.overdueDate(),
            returnItem.returnDate()
        );
    }

    private RentItem toRentItemDomain(RentItemJpaEmbeddable entity) {
        return new RentItem(
            new RentalItem(entity.getItemNo(), entity.getItemTitle()),
            entity.getRentDate(),
            entity.isOverdue(),
            entity.getOverdueDate()
        );
    }

    private ReturnItem toReturnItemDomain(ReturnItemJpaEmbeddable entity) {
        RentItem rentItem = new RentItem(
            new RentalItem(entity.getItemNo(), entity.getItemTitle()),
            entity.getRentDate(),
            entity.isOverdue(),
            entity.getOverdueDate()
        );
        return new ReturnItem(rentItem, entity.getReturnDate());
    }
}
```

나쁜 Mapper:

```java
@Component
public class RentalCardPersistenceMapper {
    private final RentalCardJpaRepository repository;

    public RentalCard toDomain(RentalCardJpaEntity entity) {
        if (repository.existsByMemberId(entity.getMemberId())) {
            // business decision
        }
        return ...
    }
}
```

Mapper 순수성 규칙:

- repository 호출 금지
- service 호출 금지
- 비즈니스 검증 금지
- 상태 전이 금지
- HTTP 응답 변환 금지
- entity-domain 필드 번역만 수행

---

# Controller 작성 철학

Controller는 얇다. HTTP를 application use case로 연결하는 adapter다.

```java
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rental-cards")
@RequiredArgsConstructor
public class RentalCardController {
    private final RentItemUseCase rentItemUseCase;

    @PostMapping("/rent")
    public ResponseEntity<BaseResponse<RentalResultResponse>> rent(
        @Valid @RequestBody RentItemRequest request
    ) {
        return BaseResponse.accepted(
            RentalResultResponse.rentAccepted(
                rentItemUseCase.rentItem(request.toCommand())
            )
        ).toResponseEntity();
    }
}
```

Controller가 해야 할 일:

- Request validation
- `request.toCommand()`
- Inbound port 호출
- Response DTO factory 호출
- Response wrapper 반환

Controller가 하지 말아야 할 일:

- aggregate 직접 생성
- repository 직접 호출
- domain VO 직접 생성
- response DTO 중첩 수동 조립
- 비즈니스 조건 분기

---

# 코드 스멜과 교정 예시

| 코드 스멜 | 왜 문제인가 | 교정 방향 |
| --- | --- | --- |
| Request DTO의 `toDomainVo()` | Web이 Domain 생성 규칙을 알게 된다. | `toCommand()`로 primitive/simple 값만 전달 |
| Command의 `fromRequest()` | Application이 Web Adapter를 알게 된다. | Request가 `toCommand()`를 가진다. |
| Domain의 `toResponse()` | Domain이 HTTP 응답 모양을 알게 된다. | Application Result와 Web Response로 분리 |
| Domain의 `toJpaEntity()` | Domain이 persistence 기술을 알게 된다. | Persistence Mapper로 이동 |
| Application Service 상수 `MAX_RENTAL_COUNT` | 도메인 정책이 orchestration 계층에 흩어진다. | Domain Policy enum/object로 이동 |
| 문자열 상태값 `"AVAILABLE"` | 유한한 도메인 상태가 타입으로 보호되지 않는다. | `BookStatus.AVAILABLE` 같은 domain enum 사용 |
| 사용자 관리형 카테고리를 enum으로 고정 | 운영 데이터 변경 때마다 배포가 필요해진다. | Entity 또는 lookup table로 모델링 |
| Domain enum에 HTTP label/JSON value 포함 | 도메인이 presentation 형식을 알게 된다. | Response DTO나 adapter-local mapper로 이동 |
| `setStatus(...)` | 비즈니스 의도가 사라진다. | `makeUnavailable()`, `makeAvailableRental()` |
| public mutable list getter | aggregate 외부에서 상태가 바뀐다. | `List.copyOf(...)` 반환과 behavior method 제공 |
| Controller에서 `Optional` 처리 | 부재 처리 정책이 presentation에 새어 나간다. | Service에서 예외 또는 생성 정책 결정 |
| Mapper가 repository 호출 | 번역 책임과 조회 책임이 섞인다. | Adapter가 repository를 호출하고 mapper는 변환만 수행 |
| 소유한 메서드에 파라미터 3개 이상 나열 | 호출 의도가 흐려지고 변경 때마다 호출부가 함께 깨진다. | 최대 2개로 제한하고 `Command`/`Query`/`Criteria` 같은 Parameter Object 사용 |
| Kotlin 파일 하나에 여러 top-level 타입 선언 | 탐색, 변경 영향 파악, 타입 단위 이동이 어려워진다. | top-level 타입당 파일 하나를 사용하고 파일명을 타입명과 일치 |
| `process(...)`, `handle(...)` 남발 | 코드가 업무 언어를 잃는다. | 유스케이스 이름으로 메서드명 변경 |

## 스멜 1: Primitive Obsession

나쁜 예:

```java
public void rentItem(String userId, String userName, Long itemNo, String itemTitle) {
    ...
}
```

좋은 예:

```java
public void rentItem(RentalMember member, RentalItem item) {
    ...
}
```

Application boundary에서는 `RentItemCommand`로 받고, Domain boundary에서는 `RentalMember`, `RentalItem`으로 바꾼다.

## 스멜 1-1: Long Parameter List

나쁜 예:

```java
List<GraphEntity> searchEntities(String text, String type, int limit);
```

좋은 예:

```java
List<GraphEntity> searchEntities(GraphEntitySearchCriteria criteria);
```

두 값까지는 각각의 의미가 명확하면 그대로 전달할 수 있다. 세 값부터는 단순히 인자 수를 줄이기 위한 묶음이 아니라, 호출의 업무 의미를 이름으로 표현하는 Parameter Object를 사용한다.

## 스멜 2: Anemic Domain Model

나쁜 예:

```java
if (rentalCard.getRentItemList().size() >= 5) {
    throw new IllegalArgumentException("대여 한도를 초과했습니다.");
}
rentalCard.getRentItemList().add(RentItem.createRentalItem(item));
```

좋은 예:

```java
rentalCard.rentItem(item);
```

규칙은 aggregate 안으로 들어간다.

## 스멜 3: 추상화 수준 혼합

나쁜 예:

```java
public RentalCardResult rentItem(RentItemCommand command) {
    RentalMember member = new RentalMember(command.userId(), command.userName());
    RentalItem item = new RentalItem(command.itemNo(), command.itemTitle());
    RentalCard rentalCard = loadRentalCardPort.loadRentalCard(member.id())
        .orElseGet(() -> RentalCard.createRentalCard(member));
    rentalCard.rentItem(item);
    RentalCard saved = saveRentalCardPort.save(rentalCard);
    return new RentalCardResult(...many fields...);
}
```

좋은 예:

```java
public RentalCardResult rentItem(RentItemCommand command) {
    RentalMember member = rentalMember(command);
    RentalItem item = rentalItem(command);

    RentalCard rentalCard = loadOrCreateRentalCard(member);
    rentalCard.rentItem(item);

    return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
}
```

## 코드 스멜을 보는 관점

코드 스멜은 단순 취향 문제가 아니다. 대부분의 스멜은 다음 셋 중 하나가 깨졌다는 신호다.

| 깨진 기준 | 겉으로 보이는 스멜 | 근본 문제 |
| --- | --- | --- |
| 책임의 위치 | Controller가 분기하고 Service가 정책 상수를 가진다. | 비즈니스 판단이 도메인 밖으로 새어 나갔다. |
| 경계의 언어 | Request가 Domain VO를 만들고 Domain이 Response를 만든다. | 계층별 모델이 분리되지 않았다. |
| 도메인 언어 | `process`, `updateData`, `setStatus`가 늘어난다. | 코드가 업무 행위 대신 기술 조작을 말한다. |

리팩터링은 “코드를 예쁘게 만드는 일”이 아니라, 잘못된 책임과 언어를 제자리로 돌려놓는 일이다.

## 구조적 안티패턴 1: Transaction Script Service

Application Service가 모든 비즈니스 판단과 상태 변경을 직접 수행하면 도메인 모델은 데이터 덩어리가 된다.

나쁜 예:

```java
@Service
@Transactional
public class RentalCardService {
    private static final int MAX_RENTAL_COUNT = 5;

    public RentalCardResult rentItem(RentItemCommand command) {
        RentalCard rentalCard = loadRentalCardPort.loadRentalCard(command.userId())
            .orElseGet(() -> RentalCard.createRentalCard(
                new RentalMember(command.userId(), command.userName())
            ));

        if (rentalCard.rentStatus() == RentStatus.RENT_UNAVAILABLE) {
            throw new IllegalArgumentException("대여 정지 상태에서는 도서를 대여할 수 없습니다.");
        }
        if (rentalCard.getRentItemList().size() >= MAX_RENTAL_COUNT) {
            throw new IllegalArgumentException("대여 한도를 초과했습니다.");
        }

        rentalCard.getRentItemList().add(
            RentItem.createRentalItem(new RentalItem(command.itemNo(), command.itemTitle()))
        );

        return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
    }
}
```

문제:

- 대여 가능 여부, 대여 한도, 대여 항목 추가가 service에 있다.
- aggregate가 자신의 불변식을 보호하지 못한다.
- `MAX_RENTAL_COUNT`가 도메인 정책인데 application service 상수로 존재한다.
- 컬렉션을 외부에서 직접 수정한다.

좋은 예:

```java
@Service
@Transactional
@RequiredArgsConstructor
public class RentalCardService implements RentItemUseCase {
    private final LoadRentalCardPort loadRentalCardPort;
    private final SaveRentalCardPort saveRentalCardPort;

    @Override
    public RentalCardResult rentItem(RentItemCommand command) {
        RentalMember member = rentalMember(command);
        RentalItem item = rentalItem(command);

        RentalCard rentalCard = loadOrCreateRentalCard(member);
        rentalCard.rentItem(item);

        return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
    }

    private RentalCard loadOrCreateRentalCard(RentalMember member) {
        return loadRentalCardPort.loadRentalCard(member.id())
            .orElseGet(() -> RentalCard.createRentalCard(member));
    }

    private RentalMember rentalMember(RentItemCommand command) {
        return new RentalMember(command.userId(), command.userName());
    }

    private RentalItem rentalItem(RentItemCommand command) {
        return new RentalItem(command.itemNo(), command.itemTitle());
    }
}
```

도메인 규칙은 aggregate로 이동한다.

```java
public void rentItem(RentalItem item) {
    if (rentStatus == RentStatus.RENT_UNAVAILABLE) {
        throw new IllegalArgumentException("대여 정지 상태에서는 도서를 대여할 수 없습니다.");
    }
    if (!RentalLimitPolicy.STANDARD.canRent(rentItemList.size())) {
        throw new IllegalArgumentException(
            "대여 중인 도서는 최대 " + RentalLimitPolicy.STANDARD.maxRentalCount() + "권까지 가능합니다."
        );
    }
    if (findRentItem(item) != null) {
        throw new IllegalArgumentException("이미 대여 중인 도서입니다.");
    }

    rentItemList.add(RentItem.createRentalItem(item));
}
```

## 구조적 안티패턴 2: God Service

하나의 service가 생성, 조회, 변환, 검증, 상태 변경, 응답 조립까지 모두 처리하면 변경 이유가 너무 많아진다.

나쁜 예:

```java
@Service
public class RentalService {
    public RentalResultResponse rent(RentItemRequest request) {
        RentalMember member = request.toRentalMember();
        RentalItem item = request.toRentalItem();
        RentalCard card = repository.findByMemberId(member.id())
            .map(mapper::toDomain)
            .orElse(RentalCard.createRentalCard(member));

        card.rentItem(item);

        RentalCardJpaEntity entity = mapper.toJpaEntity(card);
        RentalCard saved = mapper.toDomain(repository.save(entity));

        return RentalResultResponse.rentAccepted(RentalCardResult.from(saved));
    }
}
```

문제:

- Request DTO, repository, mapper, response DTO가 한 service에 모두 들어왔다.
- inbound adapter, application, outbound adapter 책임이 섞였다.
- 테스트가 어려워지고 변경 이유가 많아진다.

좋은 예:

```java
@RestController
@RequiredArgsConstructor
public class RentalCardController {
    private final RentItemUseCase rentItemUseCase;

    @PostMapping("/rent")
    public ResponseEntity<BaseResponse<RentalResultResponse>> rent(
        @Valid @RequestBody RentItemRequest request
    ) {
        return BaseResponse.accepted(
            RentalResultResponse.rentAccepted(rentItemUseCase.rentItem(request.toCommand()))
        ).toResponseEntity();
    }
}
```

```java
@Service
@Transactional
@RequiredArgsConstructor
public class RentalCardService implements RentItemUseCase {
    private final LoadRentalCardPort loadRentalCardPort;
    private final SaveRentalCardPort saveRentalCardPort;

    @Override
    public RentalCardResult rentItem(RentItemCommand command) {
        RentalMember member = new RentalMember(command.userId(), command.userName());
        RentalItem item = new RentalItem(command.itemNo(), command.itemTitle());

        RentalCard rentalCard = loadRentalCardPort.loadRentalCard(member.id())
            .orElseGet(() -> RentalCard.createRentalCard(member));
        rentalCard.rentItem(item);

        return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
    }
}
```

```java
@Repository
@RequiredArgsConstructor
public class RentalCardPersistenceAdapter implements LoadRentalCardPort, SaveRentalCardPort {
    private final RentalCardJpaRepository repository;
    private final RentalCardPersistenceMapper mapper;

    @Override
    public Optional<RentalCard> loadRentalCard(String userId) {
        return repository.findByMemberId(userId).map(mapper::toDomain);
    }

    @Override
    public RentalCard save(RentalCard rentalCard) {
        return mapper.toDomain(repository.save(mapper.toJpaEntity(rentalCard)));
    }
}
```

## 계층 경계 안티패턴 1: DTO 터널링

DTO 터널링은 한 계층의 DTO가 다른 계층을 관통해서 계속 전달되는 현상이다.

나쁜 예:

```java
@RestController
public class RentalCardController {
    private final RentalCardService rentalCardService;

    @PostMapping("/rent")
    public RentalResultResponse rent(@RequestBody RentItemRequest request) {
        return rentalCardService.rent(request);
    }
}
```

```java
@Service
public class RentalCardService {
    public RentalResultResponse rent(RentItemRequest request) {
        RentalItem item = new RentalItem(request.itemNo(), request.itemTitle());
        RentalCard rentalCard = ...
        rentalCard.rentItem(item);
        return RentalResultResponse.rentAccepted(...);
    }
}
```

문제:

- Application Service가 Web Request와 Web Response를 안다.
- HTTP 입력 형식 변경이 use case 변경으로 번진다.
- 테스트가 Web DTO에 묶인다.

좋은 예:

```java
public record RentItemRequest(
    Long itemNo,
    String itemTitle,
    String userId,
    String userName
) {
    public RentItemCommand toCommand() {
        return new RentItemCommand(userId, userName, itemNo, itemTitle);
    }
}
```

```java
public interface RentItemUseCase {
    RentalCardResult rentItem(RentItemCommand command);
}
```

```java
public record RentalResultResponse(...) {
    public static RentalResultResponse rentAccepted(RentalCardResult rentalCard) {
        return from("도서 대여 요청을 접수했습니다.", rentalCard);
    }
}
```

## 계층 경계 안티패턴 2: Mapper가 판단한다

Mapper는 판단자가 아니라 번역기다. Mapper가 도메인 규칙을 판단하면 저장소 변환과 비즈니스 규칙이 결합된다.

나쁜 예:

```java
@Component
public class RentalCardPersistenceMapper {
    public RentalCard toDomain(RentalCardJpaEntity entity) {
        if (entity.getLateFeePoint() > 0 && entity.getRentItems().isEmpty()) {
            entity.setRentStatus(RentStatus.RENT_AVAILABLE);
        }

        return RentalCard.reconstitute(
            entity.getRentalCardNo(),
            new RentalMember(entity.getMemberId(), entity.getMemberName()),
            entity.getRentStatus(),
            new LateFee(entity.getLateFeePoint()),
            toRentItems(entity),
            toReturnItems(entity)
        );
    }
}
```

좋은 예:

```java
@Component
public class RentalCardPersistenceMapper {
    public RentalCard toDomain(RentalCardJpaEntity entity) {
        return RentalCard.reconstitute(
            entity.getRentalCardNo(),
            new RentalMember(entity.getMemberId(), entity.getMemberName()),
            entity.getRentStatus(),
            new LateFee(entity.getLateFeePoint()),
            toRentItems(entity),
            toReturnItems(entity)
        );
    }
}
```

상태를 바꿔야 하는 업무 행위는 aggregate method로 표현한다.

```java
public long makeAvailableRental(long point) {
    if (!rentItemList.isEmpty()) {
        throw new IllegalArgumentException("모든 도서를 반납해야 정지해제할 수 있습니다.");
    }
    if (lateFee.point() != point) {
        throw new IllegalArgumentException("입력 포인트가 현재 연체료와 일치하지 않습니다.");
    }

    lateFee = lateFee.removePoint(point);
    if (lateFee.point() == 0) {
        rentStatus = RentStatus.RENT_AVAILABLE;
    }
    return point;
}
```

## 도메인 모델 안티패턴 1: Setter Driven Domain

Setter 중심 모델은 도메인 행위를 없애고 상태 조작만 남긴다.

나쁜 예:

```java
public class Book {
    private BookStatus bookStatus;

    public void setBookStatus(BookStatus bookStatus) {
        this.bookStatus = bookStatus;
    }
}
```

```java
book.setBookStatus(BookStatus.UNAVAILABLE);
```

좋은 예:

```java
public class Book {
    private BookStatus bookStatus;

    public Book makeUnavailable() {
        if (bookStatus == BookStatus.UNAVAILABLE) {
            throw new IllegalStateException("이미 대여 중인 도서입니다.");
        }
        this.bookStatus = BookStatus.UNAVAILABLE;
        return this;
    }

    public Book makeAvailable() {
        this.bookStatus = BookStatus.AVAILABLE;
        return this;
    }
}
```

```java
book.makeUnavailable();
```

`setBookStatus`는 어떤 이유로 상태가 바뀌는지 말하지 않는다. `makeUnavailable`은 도서가 대여 불가능 상태가 되는 업무 행위를 말한다.

## 도메인 모델 안티패턴 2: 생성과 복원의 혼합

신규 생성과 저장소 복원을 하나의 public constructor로 처리하면 잘못된 상태를 쉽게 만들 수 있다.

나쁜 예:

```java
public class Member {
    public Member(
        Long memberNo,
        MemberIdentity idName,
        PassWord password,
        Email email,
        List<Authority> authorities,
        Point point
    ) {
        this.memberNo = memberNo;
        this.idName = idName;
        this.password = password;
        this.email = email;
        this.authorities = authorities;
        this.point = point;
    }
}
```

이 생성자는 신규 회원인데 `memberNo`가 있거나, 권한이 비어 있거나, 초기 포인트가 임의 값인 객체를 만들 수 있다.

좋은 예:

```java
public class Member {
    private Member(
        Long memberNo,
        MemberIdentity idName,
        PassWord password,
        Email email,
        List<Authority> authorities,
        Point point
    ) {
        this.memberNo = memberNo;
        this.idName = idName;
        this.password = password;
        this.email = email;
        this.authorities = new ArrayList<>(authorities);
        this.point = point;
    }

    public static Member registerMember(MemberIdentity idName, PassWord password, Email email) {
        Member member = new Member(null, idName, password, email, new ArrayList<>(), new Point(0));
        member.addAuthority(Authority.create(UserRole.USER));
        return member;
    }

    public static Member reconstitute(
        Long memberNo,
        MemberIdentity idName,
        PassWord password,
        Email email,
        List<Authority> authorities,
        Point point
    ) {
        return new Member(memberNo, idName, password, email, authorities, point);
    }
}
```

`registerMember`와 `reconstitute`는 같은 객체를 만들지만 의도가 다르다. 의도가 다르면 메서드도 달라야 한다.

## 언어적 안티패턴 1: Generic Verb

`process`, `handle`, `execute`, `update`, `manage`는 대부분 도메인 언어를 숨긴다. 이런 이름은 “무엇을 하는지”가 아니라 “코드가 뭔가 한다”는 사실만 말한다.

나쁜 예:

```java
public interface RentalUseCase {
    RentalCardResult process(RentalCommand command);
}
```

```java
public class RentalCardService implements RentalUseCase {
    public RentalCardResult process(RentalCommand command) {
        if (command.type().equals("RENT")) {
            ...
        }
        if (command.type().equals("RETURN")) {
            ...
        }
        return ...
    }
}
```

좋은 예:

```java
public interface RentItemUseCase {
    RentalCardResult rentItem(RentItemCommand command);
}

public interface ReturnItemUseCase {
    RentalCardResult returnItem(ReturnItemCommand command);
}
```

```java
public class RentalCardService implements RentItemUseCase, ReturnItemUseCase {
    @Override
    public RentalCardResult rentItem(RentItemCommand command) {
        ...
    }

    @Override
    public RentalCardResult returnItem(ReturnItemCommand command) {
        ...
    }
}
```

좋은 이름은 if/switch를 줄인다. 이름이 갈라지면 유스케이스도 갈라진다.

## 언어적 안티패턴 2: Data Suffix

`Info`, `Data`, `Dto`, `Object`, `Manager` 같은 이름은 도메인 의미를 흐리게 만든다. DTO 접미사는 계층 모델에서 필요할 수 있지만, 도메인 모델에서는 피해야 한다.

나쁜 예:

```java
public record UserInfo(String id, String name) {
}

public record BookData(Long no, String title) {
}
```

좋은 예:

```java
public record RentalMember(String id, String name) {
}

public record RentalItem(Long no, String title) {
}
```

`RentalMember`는 대여 문맥에서 필요한 회원 snapshot이다. `UserInfo`는 어느 문맥의 어떤 정보인지 말하지 않는다.

## 언어적 안티패턴 3: Boolean Flag Method

boolean flag는 호출부의 의미를 숨긴다.

나쁜 예:

```java
public RentalCardResult changeRentalStatus(ChangeRentalStatusCommand command, boolean available) {
    RentalCard rentalCard = loadRequiredRentalCard(command.userId());
    if (available) {
        rentalCard.makeAvailableRental(command.point());
    } else {
        rentalCard.overdueItem(new RentalItem(command.itemNo(), command.itemTitle()));
    }
    return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
}
```

호출부만 보면 의미를 알기 어렵다.

```java
changeRentalStatus(command, true);
changeRentalStatus(command, false);
```

좋은 예:

```java
public RentalCardResult clearOverdue(ClearOverdueCommand command) {
    RentalCard rentalCard = loadRequiredRentalCard(command.userId());
    rentalCard.makeAvailableRental(command.point());
    return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
}

public RentalCardResult overdueItem(OverdueItemCommand command) {
    RentalCard rentalCard = loadRequiredRentalCard(command.userId());
    rentalCard.overdueItem(new RentalItem(command.itemNo(), command.itemTitle()));
    return RentalCardResult.from(saveRentalCardPort.save(rentalCard));
}
```

서로 다른 업무 행위는 서로 다른 command와 method로 분리한다.

## 언어적 안티패턴 4: Status Update Language

`updateStatus`는 상태 변경 이유를 지운다. DDD에서는 상태 변경 자체보다 “왜 바뀌는가”가 중요하다.

나쁜 예:

```java
public void updateStatus(BookStatus status) {
    this.bookStatus = status;
}
```

좋은 예:

```java
public Book makeAvailable() {
    this.bookStatus = BookStatus.AVAILABLE;
    return this;
}

public Book makeUnavailable() {
    if (bookStatus == BookStatus.UNAVAILABLE) {
        throw new IllegalStateException("이미 대여 중인 도서입니다.");
    }
    this.bookStatus = BookStatus.UNAVAILABLE;
    return this;
}
```

상태 이름은 enum이 표현하고, 상태 변경 이유는 method가 표현한다.

## 언어적 안티패턴 5: Technical Name in Domain

도메인에 기술 이름이 들어오면 도메인 언어가 밀려난다.

나쁜 예:

```java
public class RentalCard {
    private String userPk;
    private String rowStatus;
    private List<RentItemJpaEmbeddable> itemEntities;
}
```

좋은 예:

```java
public class RentalCard {
    private final RentalMember member;
    private RentStatus rentStatus;
    private final List<RentItem> rentItemList;
}
```

`userPk`, `rowStatus`, `Entity`는 저장 기술의 언어다. 도메인은 `member`, `rentStatus`, `rentItemList`처럼 업무 언어로 말해야 한다.

## 안티패턴 리뷰 질문

리뷰할 때 다음 질문을 순서대로 던진다.

1. 이 코드는 도메인 전문가가 읽어도 업무 행위로 이해할 수 있는가?
2. 이 이름은 기술 조작이 아니라 비즈니스 의도를 드러내는가?
3. 이 책임은 현재 계층에 있어야 하는가?
4. 이 로직이 domain 밖에 있다면, domain 밖에 있어야 하는 이유가 명확한가?
5. 이 DTO가 자기 계층 밖으로 새어 나가고 있지 않은가?
6. 이 mapper가 변환 말고 판단을 하고 있지 않은가?
7. 이 enum은 유한하고 안정적인 도메인 언어인가, 아니면 운영 데이터인가?
8. 이 method는 하나의 추상화 수준으로 읽히는가?
9. 이 주석은 코드 이름을 잘 지으면 사라질 수 있는가?
10. 이 변경을 테스트하면 도메인 규칙 테스트로 표현되는가, 프레임워크 테스트로만 표현되는가?

---

# 주석 정책

주석은 코드를 대신 설명하는 도구가 아니다. DDD 코드에서 가장 좋은 문서는 도메인 언어로 작성된 클래스명, 메서드명, 값 객체명, 정책명이다. 주석은 코드 이름과 구조만으로는 충분히 드러나지 않는 비즈니스 맥락, 설계 의도, 불변식의 이유, 계층 경계의 계약을 보완할 때만 사용한다.

## 1. 주석보다 이름을 먼저 고친다

나쁜 예:

```java
// 도서를 대여한다.
public RentalCardResult process(RentItemCommand command) {
    ...
}
```

좋은 예:

```java
public RentalCardResult rentItem(RentItemCommand command) {
    ...
}
```

주석이 메서드명을 번역하고 있다면 주석이 필요한 것이 아니라 메서드명이 잘못된 것이다.

## 2. 뻔한 구현 설명은 쓰지 않는다

나쁜 예:

```java
public List<RentItem> getRentItemList() {
    // rentItemList를 복사해서 반환한다.
    return List.copyOf(rentItemList);
}
```

좋은 예:

```java
public List<RentItem> getRentItemList() {
    return List.copyOf(rentItemList);
}
```

`List.copyOf(...)`는 이미 구현 의도를 충분히 드러낸다. 이런 주석은 유지보수 비용만 늘린다.

## 3. 불변식의 이유는 주석으로 남길 수 있다

코드가 “무엇을 하는지”는 이름과 구조가 말해야 한다. 하지만 “왜 이 제약이 필요한지”는 도메인 맥락이 필요할 수 있다.

좋은 예:

```java
public long makeAvailableRental(long point) {
    if (!rentItemList.isEmpty()) {
        throw new IllegalArgumentException("모든 도서를 반납해야 정지해제할 수 있습니다.");
    }
    if (lateFee.point() != point) {
        throw new IllegalArgumentException("입력 포인트가 현재 연체료와 일치하지 않습니다.");
    }

    lateFee = lateFee.removePoint(point);
    if (lateFee.point() == 0) {
        rentStatus = RentStatus.RENT_AVAILABLE;
    }
    return point;
}
```

이 경우 코드와 예외 메시지만으로 규칙이 충분히 드러난다. 별도 주석이 없어도 된다.

도메인 맥락이 더 필요한 경우에는 짧게 이유를 남긴다.

```java
public long makeAvailableRental(long point) {
    // 연체 해제는 남은 대여 도서가 없을 때만 가능하다. 대여 중인 도서가 있으면 즉시 재연체 상태가 될 수 있다.
    if (!rentItemList.isEmpty()) {
        throw new IllegalArgumentException("모든 도서를 반납해야 정지해제할 수 있습니다.");
    }
    ...
}
```

이 주석은 `if` 문을 설명하지 않는다. 왜 이 규칙이 존재하는지 설명한다.

## 4. Aggregate behavior에는 비즈니스 계약 Javadoc을 허용한다

Aggregate의 public behavior는 외부에서 호출하는 도메인 계약이다. Javadoc은 구현 설명이 아니라 비즈니스 계약을 기록해야 한다.

좋은 예:

```java
/**
 * 도서를 대여 목록에 추가합니다.
 *
 * <p>대여 정지 상태, 대여 한도 초과, 같은 도서의 중복 대여는 허용하지 않습니다.
 *
 * @param item 대여할 도서 snapshot.
 */
public void rentItem(RentalItem item) {
    if (rentStatus == RentStatus.RENT_UNAVAILABLE) {
        throw new IllegalArgumentException("대여 정지 상태에서는 도서를 대여할 수 없습니다.");
    }
    if (!RentalLimitPolicy.STANDARD.canRent(rentItemList.size())) {
        throw new IllegalArgumentException(
            "대여 중인 도서는 최대 " + RentalLimitPolicy.STANDARD.maxRentalCount() + "권까지 가능합니다."
        );
    }
    if (findRentItem(item) != null) {
        throw new IllegalArgumentException("이미 대여 중인 도서입니다.");
    }

    rentItemList.add(RentItem.createRentalItem(item));
}
```

나쁜 예:

```java
/**
 * rentItemList에 item을 add 한다.
 */
public void rentItem(RentalItem item) {
    rentItemList.add(RentItem.createRentalItem(item));
}
```

Javadoc은 구현 줄거리가 아니라 외부 호출자가 알아야 하는 도메인 계약을 적는다.

## 5. Port에는 boundary contract를 남긴다

Port는 application과 adapter가 만나는 계약이다. 무엇을 반환하고, absence를 어떻게 표현하는지 정도는 주석으로 남길 수 있다.

좋은 예:

```java
/**
 * 회원 ID로 대여카드 도메인 모델을 조회합니다.
 */
public interface LoadRentalCardPort {
    /**
     * @param userId 대여카드 소유자를 식별하는 회원 ID.
     * @return 회원 ID에 해당하는 대여카드. 저장소에 없으면 {@link Optional#empty()}.
     */
    Optional<RentalCard> loadRentalCard(String userId);
}
```

이 주석은 중요한 경계 계약을 설명한다. Adapter는 absence를 `Optional.empty()`로 표현하고, Service가 유스케이스 의미에 따라 생성 또는 예외를 결정한다.

## 6. Mapper 주석은 변환 경계만 설명한다

Mapper는 번역기다. 주석도 번역 경계를 설명해야지 비즈니스 판단을 설명하면 안 된다.

좋은 예:

```java
/**
 * JPA 엔티티 그래프를 대여카드 도메인 모델로 복원합니다.
 *
 * <p>저장소 복원이므로 신규 생성 factory가 아니라 {@code reconstitute(...)}를 사용합니다.
 */
public RentalCard toDomain(RentalCardJpaEntity entity) {
    return RentalCard.reconstitute(
        entity.getRentalCardNo(),
        new RentalMember(entity.getMemberId(), entity.getMemberName()),
        entity.getRentStatus(),
        new LateFee(entity.getLateFeePoint()),
        entity.getRentItems().stream().map(this::toRentItemDomain).toList(),
        entity.getReturnItems().stream().map(this::toReturnItemDomain).toList()
    );
}
```

나쁜 예:

```java
/**
 * 연체료가 있으면 대여 정지 상태로 바꾼다.
 */
public RentalCard toDomain(RentalCardJpaEntity entity) {
    if (entity.getLateFeePoint() > 0) {
        entity.setRentStatus(RentStatus.RENT_UNAVAILABLE);
    }
    ...
}
```

이 주석은 mapper가 비즈니스 판단을 하고 있다는 경고다. 판단은 aggregate나 application service로 이동해야 한다.

## 7. TODO, FIXME, HACK 정책

임시 주석은 기술 부채를 숨기기 쉽다. 남긴다면 추적 가능해야 한다.

피해야 할 예:

```java
// TODO 나중에 수정
private RentalCard load(String userId) {
    ...
}
```

허용 가능한 예:

```java
// TODO ISSUE-123: 회원 탈퇴 정책 확정 후 탈퇴 회원 대여카드 조회 규칙을 분리한다.
private RentalCard load(String userId) {
    ...
}
```

프로젝트가 이슈 트래커를 쓰지 않는다면 TODO를 남기는 대신 작은 리팩터링으로 즉시 정리하거나, 문서의 잔여 리스크에 기록한다.

## 8. 주석 처리된 죽은 코드는 남기지 않는다

나쁜 예:

```java
public Book makeUnavailable() {
    // this.bookStatus = BookStatus.RENTED;
    this.bookStatus = BookStatus.UNAVAILABLE;
    return this;
}
```

좋은 예:

```java
public Book makeUnavailable() {
    this.bookStatus = BookStatus.UNAVAILABLE;
    return this;
}
```

이력은 Git이 담당한다. 소스 파일은 현재 설계만 담아야 한다.

## 9. 주석 리뷰 기준

주석을 추가하기 전에 다음 질문을 던진다.

1. 이 주석은 코드가 “무엇을 하는지”를 반복하는가?
2. 이름을 바꾸면 이 주석이 사라질 수 있는가?
3. 이 주석은 비즈니스 이유, 불변식의 근거, 계층 경계 계약을 설명하는가?
4. 이 주석이 오래되어도 컴파일러나 테스트가 깨지지 않는 종류의 정보인가?
5. TODO라면 추적 가능한 이슈나 만료 조건이 있는가?
6. 주석이 실제 코드와 달라질 가능성이 높은가?
7. 주석 대신 테스트 이름으로 더 정확히 표현할 수 있는가?

정책의 결론은 단순하다. 코드는 도메인 언어로 읽히게 만들고, 주석은 코드가 담기 어려운 맥락만 짧고 정확하게 남긴다.

---

# Architecture Test 예시

문서는 쉽게 낡는다. 의존성 방향은 테스트로 고정한다.
테스트 대상 루트 지정은 각 프로젝트의 테스트 설정에 맞춘다.

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(importOptions = DoNotIncludeTests.class)
class HexagonalArchitectureTest {
    private static final String[] DOMAIN_FORBIDDEN_DEPENDENCIES = {
        "org.springframework..",
        "jakarta.persistence..",
        "javax.persistence..",
        "..application..",
        "..adapter..",
        "..config.."
    };

    private static final String[] APPLICATION_FORBIDDEN_DEPENDENCIES = {
        "..adapter..",
        "..config..",
        "org.springframework.web..",
        "jakarta.persistence..",
        "javax.persistence..",
        "org.springframework.data.."
    };

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers_or_frameworks = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(DOMAIN_FORBIDDEN_DEPENDENCIES)
        .because("domain code must stay pure and independent from application, adapters, config, and frameworks");

    @ArchTest
    static final ArchRule application_must_not_depend_on_adapters_config_or_technical_frameworks = noClasses()
        .that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAnyPackage(APPLICATION_FORBIDDEN_DEPENDENCIES)
        .because("application code should orchestrate use cases through ports");

    @ArchTest
    static final ArchRule inbound_adapters_must_not_depend_on_outbound_adapters = noClasses()
        .that().resideInAPackage("..adapter.in..")
        .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
        .because("inbound and outbound adapters should meet only through application ports");

    @ArchTest
    static final ArchRule web_requests_must_not_create_domain_inputs = noClasses()
        .that().haveSimpleNameEndingWith("Request")
        .and().resideInAnyPackage("..adapter.in.web..")
        .should().dependOnClassesThat().resideInAnyPackage("..domain..")
        .because("web requests should convert to primitive/simple application commands");

    @ArchTest
    static final ArchRule application_commands_must_not_use_domain_types = noClasses()
        .that().haveSimpleNameEndingWith("Command")
        .and().resideInAPackage("..application.dto..")
        .should().dependOnClassesThat().resideInAnyPackage("..domain..")
        .because("adapter-facing commands should carry primitive/simple use-case input");
}
```

---

# 적용 체크리스트

## DDD 철학

- [ ] 코드 이름이 도메인 전문가의 언어로 읽히는가?
- [ ] 메서드명이 기술 행위가 아니라 업무 행위를 드러내는가?
- [ ] 도메인 규칙이 application service, controller, mapper에 흩어져 있지 않은가?
- [ ] 문서에 적힌 비즈니스 규칙이 코드의 이름과 구조로도 드러나는가?

## Hexagonal

- [ ] Domain이 application/adapter/config/framework에 의존하지 않는가?
- [ ] Application이 adapter 구현체에 의존하지 않는가?
- [ ] Inbound adapter와 outbound adapter가 서로 직접 의존하지 않는가?
- [ ] Application service가 outbound port만 바라보는가?

## DTO

- [ ] Request DTO는 `toCommand()` 또는 `toQuery()`만 제공하는가?
- [ ] Request DTO가 domain VO를 만들지 않는가?
- [ ] Command/Query가 primitive/simple field를 사용하는가?
- [ ] Result가 domain을 application boundary 밖으로 옮기는 역할만 하는가?
- [ ] Response DTO가 최종 HTTP shape와 메시지를 소유하는가?

## Aggregate

- [ ] Aggregate 경계를 테이블, 화면, API 응답 모양이 아니라 command, domain event, invariant 기준으로 나누었는가?
- [ ] 하나의 aggregate 안에 묶인 상태가 같은 transaction에서 반드시 함께 검증되고 변경되어야 하는가?
- [ ] 다른 aggregate는 객체 그래프가 아니라 ID 또는 immutable snapshot field로 참조하는가?
- [ ] 조회 편의를 위해 여러 aggregate root를 하나의 aggregate로 합치지 않았는가?
- [ ] child model의 lifecycle과 상태 변경이 root에 의해 통제되는가?
- [ ] 생성자 접근이 제한되어 있는가?
- [ ] 신규 생성과 복원이 factory로 분리되어 있는가?
- [ ] setter 없이 behavior method로 상태를 변경하는가?
- [ ] invariant 검증 후 상태를 변경하는가?
- [ ] 내부 컬렉션을 방어적 복사로 노출하는가?
- [ ] 정책 값이 domain policy/value object/enum에 응집되어 있는가?
- [ ] 유한하고 안정적인 도메인 상태/분류가 string이 아니라 domain enum으로 표현되는가?
- [ ] enum에 HTTP, JSON, DB 코드 같은 외부 표현이 섞이지 않았는가?
- [ ] 운영 중 변경 가능한 목록을 enum으로 고정하지 않았는가?

## Application Service

- [ ] Command를 domain VO로 변환하는 위치가 service인가?
- [ ] Service가 repository 구현체를 직접 알지 않는가?
- [ ] Adapter의 `Optional`을 service가 use case 의미에 맞게 처리하는가?
- [ ] 정상 흐름이 중첩 조건에 묻히지 않도록 guard clause를 쓰는가?
- [ ] 한 메서드 안의 추상화 수준이 일관적인가?
- [ ] 목적이 같은 반복 유스케이스는 private functional skeleton으로 묶고, 달라지는 aggregate behavior만 `Consumer`/`Function`으로 주입했는가?
- [ ] functional skeleton이 adapter 구현체가 아니라 outbound port method reference만 사용하는가?
- [ ] lambda 안에 aggregate/domain policy로 옮겨야 할 비즈니스 규칙이 숨어 있지 않은가?
- [ ] 프로젝트가 소유한 Application Service, Port, 내부 helper의 파라미터가 최대 2개인가?
- [ ] 3개 이상의 입력은 `Command`, `Query`, `Criteria`, `Context` 같은 Parameter Object로 묶었는가?
- [ ] multi-branch result가 nullable field/string/map이 아니라 필요한 경우 `sealed interface` + `record`로 표현되는가?

## Mapper

- [ ] Mapper가 순수 변환만 수행하는가?
- [ ] Mapper가 repository/service를 호출하지 않는가?
- [ ] Entity -> Domain 변환이 `reconstitute(...)`를 사용하는가?
- [ ] Domain -> Entity 변환이 명시적 accessor를 사용하는가?

## Code Smell / Anti-pattern

- [ ] `process`, `handle`, `updateData`, `updateStatus` 같은 generic verb가 도메인 행위를 숨기지 않는가?
- [ ] `Info`, `Data`, `Object`, `Manager` 같은 이름이 도메인 의미를 흐리지 않는가?
- [ ] boolean flag로 서로 다른 업무 행위를 한 메서드에 합치지 않았는가?
- [ ] service가 transaction script처럼 모든 검증과 상태 변경을 직접 처리하지 않는가?
- [ ] 하나의 service가 request, repository, mapper, response를 모두 아는 God Service가 되지 않았는가?
- [ ] 운영 Kotlin 파일이 top-level 타입 하나만 가지며 파일명이 타입명과 일치하는가?
- [ ] Web DTO가 application/domain 내부로 터널링되지 않는가?
- [ ] Mapper가 판단하지 않고 번역만 하는가?
- [ ] 주석으로 설명해야만 이해되는 이름을 도메인 언어로 고칠 수 없는가?

## Comment

- [ ] 주석이 obvious implementation을 반복하지 않는가?
- [ ] 주석이 이름 부족을 보완하는 용도로 쓰이지 않았는가?
- [ ] Javadoc이 구현 설명이 아니라 도메인 계약, 불변식, boundary contract를 설명하는가?
- [ ] TODO/FIXME/HACK가 추적 가능한 이슈나 제거 조건 없이 남아 있지 않은가?
- [ ] 주석 처리된 죽은 코드가 남아 있지 않은가?

---

# AI에게 요청할 때 쓰는 짧은 프롬프트

```text
대상 프로젝트는 순수 DDD + Hexagonal Architecture를 따른다.

반드시 다음 철학과 규칙으로 구현하라.
- 도메인 언어가 코드에 살아 있어야 한다. process/updateData 같은 기술적 이름을 피하고 업무 행위를 메서드명으로 드러내라.
- domain은 외부를 모른다. Spring, JPA, Web DTO, Persistence Entity, Adapter, Config에 의존하지 않는다.
- application은 use case를 조율한다. 비즈니스 규칙은 aggregate, value object, domain policy에 둔다.
- adapter.in.web은 request validation, request.toCommand/toQuery, use case 호출, response factory 호출만 담당한다.
- adapter.out.persistence는 outbound port를 구현하고 mapper를 통해 entity-domain 변환만 수행한다.
- Request DTO는 domain VO를 만들지 않고 primitive/simple field로 Command를 만든다.
- Application Command/Query는 Parameter Object이며, adapter-facing 입력은 primitive/simple field로 유지한다.
- 프로젝트가 소유한 Application Service, Port, 내부 helper의 파라미터는 최대 2개로 제한하고, 3개 이상의 입력은 업무 의미가 드러나는 Parameter Object로 묶어라.
- Kotlin 운영 코드는 top-level 타입 하나당 파일 하나를 사용하고 파일명을 타입명과 일치시켜라. 순수 mapping 함수만 타입 선언 없는 `*Mappings.kt` 파일에 함께 둘 수 있다.
- Application Service에서 Command를 domain VO로 변환하고 aggregate behavior를 호출한다.
- 목적이 같은 application service 흐름은 private functional skeleton으로 고정하고, 달라지는 행위만 `Consumer`, `Function`, `Predicate`로 주입하라.
- 함수형 골격은 outbound port method reference와 aggregate behavior 호출만 조합해야 하며, adapter 구현체나 비즈니스 규칙을 lambda 안에 숨기지 마라.
- Stream은 컬렉션 변환/필터/그룹핑/분류에 사용하고, 외부 상태를 변경하는 `forEach` 파이프라인은 피하라.
- 결과/상태가 여러 갈래이고 케이스별 데이터가 다르면 `sealed interface` + `record`로 표현하고 switch pattern matching으로 누락을 컴파일 단계에서 드러내라.
- Aggregate 경계는 command, domain event, invariant를 기준으로 식별하고, 테이블/화면/API 응답 모양으로 나누지 않는다.
- Aggregate root는 private constructor, create/reconstitute 분리, behavior method, invariant 검증, defensive collection exposure를 지킨다.
- 유한하고 안정적인 도메인 상태/분류는 domain enum으로 표현하고, 값과 행위를 가진 고정 정책은 domain policy enum/object로 응집하라.
- enum은 도메인 언어만 담아야 한다. HTTP label, JSON value, DB code 같은 외부 표현은 adapter나 response DTO에 둔다.
- Mapper는 순수 번역기다. repository/service/business rule을 넣지 않는다.
- Adapter는 Optional을 반환할 수 있지만, Service가 use case 의미에 따라 예외 또는 생성 정책을 결정한다.
- Early return과 guard clause로 흐름을 단순화하라.
- 한 메서드 안에서 추상화 수준을 섞지 말고, private helper 이름도 도메인 의도를 드러내게 작성하라.
- 주석은 코드가 표현하지 못하는 비즈니스 맥락, 불변식의 이유, boundary contract만 설명하게 하라. 이름 부족을 주석으로 때우지 말고 이름을 고쳐라.

구현 후 관련 architecture test, domain unit test, application service test를 실행하고 결과를 보고하라.
```

---

# 프로젝트별 치환 가이드

| Placeholder | 치환 예시 |
| --- | --- |
| `{service}` | `rental`, `member`, `book`, `order`, `payment` |
| `{aggregate}` | `RentalCard`, `Member`, `Book`, `Order`, `Payment` |
| `{domain_term}` | `rentItem`, `returnItem`, `usePoint`, `approvePayment` |
| `Command` | 유스케이스 입력 이름: `RentItemCommand`, `ApproveOrderCommand` |
| `Result` | 유스케이스 출력 이름: `RentalCardResult`, `OrderResult` |
| `Policy` | 도메인 정책 이름: `RentalLimitPolicy`, `PaymentApprovalPolicy` |

규칙은 그대로 두고 도메인 언어만 바꾼다. 좋은 DDD 코드는 프레임워크 패턴을 먼저 보여주지 않는다. 비즈니스 규칙과 도메인 언어를 먼저 보여준다.
