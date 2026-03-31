# LogMaker MCP Server

LogMaker의 모든 기능을 MCP(Model Context Protocol)를 통해 제어할 수 있는 서버입니다.
Claude Code, Claude Desktop, 또는 MCP 호환 클라이언트에서 사용 가능합니다.

## 설치

```bash
cd mcp-server
npm install
npm run build
```

## Claude Code에 등록

```bash
# 프로젝트 스코프로 등록
claude mcp add logmaker -- node /Users/m8928/logmaker/mcp-server/dist/index.js

# LogMaker가 다른 호스트에서 실행 중이면
claude mcp add logmaker -e LOGMAKER_URL=http://your-host:19999 -- node /Users/m8928/logmaker/mcp-server/dist/index.js
```

## Claude Desktop에 등록

`~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "logmaker": {
      "command": "node",
      "args": ["/Users/m8928/logmaker/mcp-server/dist/index.js"],
      "env": {
        "LOGMAKER_URL": "http://localhost:19999"
      }
    }
  }
}
```

## 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `LOGMAKER_URL` | `http://localhost:19999` | LogMaker 서버 주소 |

## 제공 도구 (23개)

### Dashboard
- `get_dashboard` — 대시보드 메트릭 조회 (maker/sender/log 수, EPS, CPU, 메모리)

### Maker 관리
- `list_makers` — 등록된 Maker 목록
- `create_maker` — Maker 생성
- `update_maker` — Maker 수정
- `delete_maker` — Maker 삭제

### Sender 관리
- `list_senders` — 등록된 Sender 목록
- `create_sender` — Sender 생성 (전송 제한 설정 가능)
- `update_sender` — Sender 수정
- `delete_sender` — Sender 삭제

### Log 관리
- `list_logs` — 등록된 Log 목록
- `create_log` — Log 생성 및 전송 시작
- `update_log` — Log 수정
- `delete_log` — Log 삭제 및 전송 중지
- `preview_log` — Log 포맷 미리보기

### Plugin 조회
- `list_plugins` — 설치된 플러그인 목록
- `list_plugin_makers` — 사용 가능한 Maker 타입 및 인자 스키마
- `list_plugin_senders` — 사용 가능한 Sender 타입 및 인자 스키마

### Scenario 관리
- `list_scenarios` — 시나리오 목록
- `create_scenario` — 시나리오 생성
- `update_scenario` — 시나리오 수정
- `delete_scenario` — 시나리오 삭제
- `start_scenario` — 시나리오 실행
- `stop_scenario` — 시나리오 중지

## 리소스

MCP 리소스를 통해 현재 상태를 조회할 수 있습니다:

- `logmaker://dashboard` — 대시보드 메트릭
- `logmaker://logs` — 모든 Log 정의 및 상태
- `logmaker://makers` — 모든 Maker
- `logmaker://senders` — 모든 Sender
- `logmaker://scenarios` — 모든 시나리오 및 실행 상태

## 프롬프트

자주 사용하는 워크플로를 프롬프트로 제공합니다:

- `setup_syslog_pipeline` — Syslog 파이프라인 구성 가이드 (Maker → Sender → Log)
- `setup_scenario` — 시나리오 생성 가이드

## 사용 예시

Claude Code에서:

```
> LogMaker에 IP Maker 하나 만들고, Debug Sender로 10 EPS 로그 생성해줘
> LogMaker 대시보드 상태 확인해줘
> 로그인 시나리오 만들어서 실행해줘
```
