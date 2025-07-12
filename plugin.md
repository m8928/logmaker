# LogMaker 플러그인 개발 가이드

**버전:** 1.0
**최종 수정일:** 2025년 7월 12일

---

## 1. 개요

LogMaker는 플러그인 기반 아키텍처를 통해 기능을 확장할 수 있습니다. 이 문서는 LogMaker를 위한 새로운 플러그인을 개발하는 과정을 안내합니다. 플러그인은 크게 두 종류로 나뉩니다.

- **Maker 플러그인:** 로그 메시지를 구성하는 특정 데이터 조각을 생성합니다. (예: IP 주소, 날짜, 랜덤 숫자)
- **Sender 플러그인:** 생성된 로그 메시지를 특정 대상(시스템, 파일, 네트워크 등)으로 전송합니다.

이 가이드는 `default-plugin` 모듈의 예제를 기반으로 작성되었습니다.

---

## 2. 개발 환경 설정

플러그인 프로젝트는 Maven을 사용하여 구성하는 것을 권장합니다. `pom.xml` 파일에 다음과 같은 핵심 의존성을 설정해야 합니다.

### 2.1. 필수 의존성

가장 중요한 의존성은 `plugin-api`입니다. 이 API는 플러그인이 구현해야 할 핵심 인터페이스(`MakerPlugin`, `SenderPlugin` 등)를 포함하고 있습니다. 또한, 플러그인 시스템의 기반이 되는 PF4J 라이브러리도 필요합니다.

```xml
<dependencies>
    <!-- LogMaker Plugin API -->
    <dependency>
        <groupId>me.blueat.logmaker</groupId>
        <artifactId>plugin-api</artifactId>
        <version>2.0.1</version> <!-- 실제 버전에 맞게 수정 -->
        <scope>provided</scope>
    </dependency>

    <!-- PF4J (Plugin Framework) -->
    <dependency>
        <groupId>org.pf4j</groupId>
        <artifactId>pf4j</artifactId>
        <version>3.11.1</version> <!-- 실제 버전에 맞게 수정 -->
        <scope>provided</scope>
    </dependency>

    <!-- Spring Framework (필요시) -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
        <version>6.1.13</version> <!-- 실제 버전에 맞게 수정 -->
        <scope>provided</scope>
    </dependency>

    <!-- 기타 필요한 라이브러리 -->
    <!-- 예: Kafka 클라이언트, 정규식 생성기 등 -->

</dependencies>
```

**중요:** `plugin-api`, `pf4j`, `spring-context` 등 LogMaker 코어 시스템이 이미 가지고 있는 라이브러리는 `<scope>provided</scope>`로 설정해야 합니다. 이는 플러그인 패키징(`*.jar`) 시 해당 라이브러리가 포함되지 않도록 하여 클래스 충돌을 방지합니다.

### 2.2. Maven Assembly 플러그인 설정

플러그인을 배포 가능한 `jar` 파일로 패키징하고, `MANIFEST.MF` 파일에 플러그인 메타데이터를 포함시키기 위해 `maven-assembly-plugin`을 사용합니다.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-assembly-plugin</artifactId>
            <version>3.1.0</version>
            <configuration>
                <descriptorRefs>
                    <descriptorRef>jar-with-dependencies</descriptorRef>
                </descriptorRefs>
                <archive>
                    <manifestEntries>
                        <!-- PF4J가 인식하는 플러그인 메타데이터 -->
                        <Plugin-Id>my-awesome-plugin</Plugin-Id>
                        <Plugin-Version>1.0.0</Plugin-Version>
                        <Plugin-Provider>MyCompany</Plugin-Provider>
                        <Plugin-Class>com.mycompany.logmaker.plugins.MyPlugin</Plugin-Class>
                        <Plugin-Dependencies></Plugin-Dependencies> <!-- 의존하는 다른 플러그인 ID -->
                    </manifestEntries>
                </archive>
            </configuration>
            <executions>
                <execution>
                    <id>make-assembly</id>
                    <phase>package</phase>
                    <goals>
                        <goal>single</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

- **Plugin-Id:** 플러그인의 고유 ID입니다.
- **Plugin-Class:** 플러그인의 진입점(Entry Point) 클래스입니다. 이 클래스는 `org.pf4j.Plugin`을 상속해야 합니다.

---

## 3. Maker 플러그인 개발하기

Maker는 특정 규칙에 따라 데이터를 생성하는 객체입니다. 예를 들어, `IPMaker`는 IP 주소를 생성합니다.

### 3.1. `Maker` 추상 클래스 구현

먼저, `me.blueat.logmaker.plugin.api.maker.Maker` 추상 클래스를 상속받는 구체적인 Maker 클래스를 작성합니다.

**예시: `RandomNumberMaker.java`**
```java
package com.mycompany.logmaker.plugins.maker;

import me.blueat.logmaker.plugin.api.maker.Maker;
import java.util.Map;
import java.util.Random;

public class RandomNumberMaker extends Maker<Integer> {
    private final Random random = new Random();
    private int maxNumber = 100;

    @Override
    public Integer getData() {
        return random.nextInt(maxNumber);
    }

    @Override
    public String getMakerName() {
        return "randomNumber"; // UI에 표시될 이름
    }

    @Override
    public String getType() {
        return "my-makers"; // 이 Maker를 관리하는 MakerPlugin의 타입
    }

    @Override
    public void update(Map<String, Object> args) {
        if (args.containsKey("max")) {
            this.maxNumber = (Integer) args.get("max");
        }
    }

    // 기타 필수 추상 메소드 구현 (getArgs, getSize, getThread, isThread)
}
```

### 3.2. `MakerPlugin` 확장 클래스 구현

다음으로, `me.blueat.logmaker.plugin.api.maker.MakerPlugin`을 확장하여 위에서 만든 Maker를 관리하고 생성하는 팩토리 역할을 하는 클래스를 만듭니다. 이 클래스는 `@Extension` 어노테이션을 가져야 PF4J가 인식할 수 있습니다.

**예시: `MyMakerPluginFactory.java`**
```java
package com.mycompany.logmaker.plugins.maker;

import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;
import me.blueat.logmaker.plugin.api.maker.MakerPlugin;
import org.pf4j.Extension;

import java.util.HashMap;
import java.util.Map;

@Extension
public class MyMakerPluginFactory extends MakerPlugin {

    @Override
    public String getType() {
        return "my-makers"; // 이 플러그인 팩토리가 관리하는 Maker들의 타입
    }

    @Override
    public Maker getMaker(String name, Map<String, Object> args) throws ArgumentsNotValidException {
        // `checkArgs`를 통해 파라미터 유효성 검증
        checkArgs(getMakerArgsMap(), args);

        if ("randomNumber".equals(name)) {
            RandomNumberMaker maker = new RandomNumberMaker();
            maker.update(args); // 파라미터 적용
            return maker;
        }
        return null;
    }

    @Override
    public Map<String, MakerArgs> getMakerArgsMap() {
        // 이 플러그인이 제공하는 Maker들이 사용하는 파라미터 정의
        Map<String, MakerArgs> argsMap = new HashMap<>();
        argsMap.put("max", new MakerArgs(Integer.class, false, 100, "생성될 숫자의 최대값"));
        return argsMap;
    }
}
```

---

## 4. Sender 플러그인 개발하기

Sender는 생성된 로그 문자열을 특정 목적지로 보내는 역할을 합니다.

### 4.1. `Sender` 추상 클래스 구현

`me.blueat.logmaker.plugin.api.sender.Sender` 추상 클래스를 상속받아 실제 전송 로직을 구현합니다.

**예시: `FileSender.java`**
```java
package com.mycompany.logmaker.plugins.sender;

import me.blueat.logmaker.plugin.api.sender.Sender;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class FileSender extends Sender<String> {
    private PrintWriter writer;
    private String filePath = "/tmp/logmaker.log";

    public FileSender() {
        updateWriter();
    }

    @Override
    public void sendData(String data) {
        if (writer != null) {
            writer.println(data);
            writer.flush();
            increaseCount(); // 전송 카운트 증가
        }
    }

    @Override
    public String getSenderName() {
        return "file"; // UI에 표시될 이름
    }

    @Override
    public String getType() {
        return "my-senders";
    }

    @Override
    public void update(Map<String, Object> args) {
        if (args.containsKey("filePath")) {
            this.filePath = (String) args.get("filePath");
            updateWriter();
        }
    }

    private void updateWriter() {
        try {
            if (writer != null) writer.close();
            this.writer = new PrintWriter(new FileWriter(this.filePath, true));
        } catch (IOException e) {
            e.printStackTrace();
            this.writer = null;
        }
    }

    // 기타 필수 추상 메소드 구현
}
```

### 4.2. `SenderPlugin` 확장 클래스 구현

Maker와 마찬가지로, `@Extension` 어노테이션을 가진 `SenderPlugin` 확장 클래스를 만들어 Sender 객체를 생성하고 관리합니다.

**예시: `MySenderPluginFactory.java`**
```java
package com.mycompany.logmaker.plugins.sender;

import me.blueat.logmaker.plugin.api.exception.ArgumentsNotValidException;
import me.blueat.logmaker.plugin.api.sender.Sender;
import me.blueat.logmaker.plugin.api.sender.SenderArgs;
import me.blueat.logmaker.plugin.api.sender.SenderPlugin;
import org.pf4j.Extension;

import java.util.HashMap;
import java.util.Map;

@Extension
public class MySenderPluginFactory extends SenderPlugin {

    @Override
    public String getType() {
        return "my-senders";
    }

    @Override
    public Sender getSender(String name, Map<String, Object> args) throws ArgumentsNotValidException {
        checkArgs(getSenderArgsMap(), args);
        if ("file".equals(name)) {
            FileSender sender = new FileSender();
            sender.update(args);
            return sender;
        }
        return null;
    }

    @Override
    public Map<String, SenderArgs> getSenderArgsMap() {
        Map<String, SenderArgs> argsMap = new HashMap<>();
        argsMap.put("filePath", new SenderArgs(String.class, true, null, "로그를 저장할 파일 경로"));
        return argsMap;
    }
}
```

---

## 5. 플러그인 진입점(Entry Point) 클래스

마지막으로, `pom.xml`의 `Plugin-Class`에 지정한 메인 플러그인 클래스를 작성합니다. 이 클래스는 `org.pf4j.Plugin`을 상속해야 합니다.

```java
package com.mycompany.logmaker.plugins;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public class MyPlugin extends Plugin {
    public MyPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        System.out.println("MyAwesomePlugin.start() -> LogMaker에 플러그인을 등록합니다.");
    }

    @Override
    public void stop() {
        System.out.println("MyAwesomePlugin.stop() -> LogMaker에서 플러그인을 제거합니다.");
    }
}
```

## 6. 빌드 및 배포

1.  프로젝트 루트에서 `mvn clean package` 명령을 실행하여 플러그인 `jar` 파일을 빌드합니다.
2.  `target` 디렉토리에 생성된 `my-awesome-plugin-1.0.0.jar` 파일을 LogMaker의 플러그인 디렉토리(보통 `plugins`)에 복사합니다.
3.  LogMaker를 재시작하면 새로운 플러그인을 인식하고 UI에 해당 Maker/Sender가 표시됩니다.
