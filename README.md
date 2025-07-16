# LogMaker

LogMaker는 사용자가 웹 UI를 통해 직관적으로 로그 데이터의 구조를 정의하고, 원하는 대상으로 실시간으로 로그를 전송할 수 있게 해주는 유연한 로그 생성 및 전송 플랫폼입니다. 플러그인 기반 아키텍처를 채택하여 새로운 종류의 데이터 생성기(Maker)나 전송기(Sender)를 손쉽게 확장할 수 있습니다.

## 주요 기능

- **직관적인 로그 생성:** 사용자가 코딩 없이 UI를 통해 다양한 데이터 타입(IP, 날짜, 정규식 기반 문자열 등)을 조합하여 복잡한 로그 메시지 포맷을 정의할 수 있습니다.
- **유연한 확장성:** 개발자가 새로운 데이터 생성 방식(Maker)이나 전송 대상(Sender)을 플러그인 형태로 손쉽게 개발하고 시스템에 추가할 수 있는 환경을 제공합니다.
- **실시간 모니터링:** 생성되는 로그의 처리량(TPS)과 상태를 대시보드에서 실시간으로 확인하고 제어할 수 있습니다.
- **다양한 전송 대상 지원:** 주요 로그 수집 시스템(Kafka, Syslog 등)에 대한 기본 전송 플러그인을 제공하여 별도의 설정 없이 즉시 연동할 수 있습니다.

## 기술 스택

- **Backend:** Java, Spring Boot, PF4J
- **Frontend:** Vue.js, Vite, Pinia, Element Plus

## 아키텍처

LogMaker는 확장성을 핵심 가치로 설계되었습니다. 메인 `core` 애플리케이션이 있고, `plugin-api`를 통해 정의된 인터페이스를 구현하여 `default-plugin`과 같은 플러그인을 동적으로 로드합니다.

```mermaid
graph TD
    A[사용자] --> B{LogMaker UI (Vue.js)};
    B --> C{LogMaker Core (Spring Boot)};
    C -- 로드 --> D[Default Plugin];
    C -- 로드 --> E[Custom Plugin];
    D -- 확장 --> F(Makers / Senders);
    E -- 확장 --> F;
    C --> G(Kafka, Syslog 등 외부 시스템);
```

## API 문서

프로젝트의 API 문서는 SpringDoc을 통해 자동으로 생성됩니다. 애플리케이션을 실행한 후 다음 URL에서 확인할 수 있습니다.

- **Swagger UI:** [http://localhost:19999/swagger-ui.html](http://localhost:19999/swagger-ui.html)

## 시작하기

### 요구 사항

- Java 17 이상
- Maven 3.x 이상
- Node.js 18 이상

### 설치 및 실행

1.  **저장소 복제:**
    ```bash
    git clone https://github.com/blueat/logmaker.git
    cd logmaker
    ```

2.  **백엔드 빌드 및 실행:**
    ```bash
    ./mvnw clean install
    java -jar core/target/logmaker-core-2.0.1.jar
    ```

3.  **프론트엔드 빌드 및 실행:**
    ```bash
    cd ui
    npm install
    npm run dev
    ```

4.  웹 브라우저에서 `http://localhost:5173`으로 접속합니다.

## 플러그인 개발

자세한 내용은 [플러그인 개발 가이드](./plugin.md)를 참고하십시오.