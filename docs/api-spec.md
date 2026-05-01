# Didgo API Specification

작성일: 2026-05-01  
기준 경로: API Gateway 경유 HTTP API

## 공통

Base URL:

```text
http://localhost
```

보호 API 요청 헤더:

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

`user-service` API는 응답 본문을 직접 반환한다. `training-service` API는 아래 공통 래퍼를 사용한다.

성공:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request."
  }
}
```

주요 enum:

```json
{
  "gender": ["MALE", "FEMALE"],
  "trainingType": ["SOCIAL", "SAFETY", "DOCUMENT", "FOCUS"],
  "socialJobType": ["OFFICE", "LABOR"],
  "socialSpeaker": ["USER", "AI"],
  "safetyCategory": ["COMMUTE_SAFETY", "WORKPLACE_SAFETY", "DAILY_SAFETY"],
  "sessionStatus": ["IN_PROGRESS", "COMPLETED", "FAILED"]
}
```

## Auth

### 회원가입

`POST /api/auth/signup`

Input:

```json
{
  "loginId": "user01",
  "password": "password1234",
  "name": "홍길동",
  "birthDate": "2000-01-01",
  "gender": "MALE",
  "email": "user@example.com",
  "disabilities": ["발달장애"],
  "desiredJob": "사무직"
}
```

Output:

```json
{
  "userId": 1,
  "message": "회원가입이 완료되었습니다."
}
```

### 로그인

`POST /api/auth/login`

Input:

```json
{
  "loginId": "user01",
  "password": "password1234",
  "rememberMe": true
}
```

Output:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "userId": 1,
    "loginId": "user01",
    "name": "홍길동",
    "email": "user@example.com",
    "disabilities": ["발달장애"],
    "desiredJob": "사무직"
  }
}
```

### 로그아웃

`POST /api/auth/logout`

Input:

```json
{}
```

Output:

```json
{
  "message": "로그아웃이 완료되었습니다."
}
```

### 토큰 재발급

`POST /api/auth/reissue`

Input:

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

Output:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

## User

### 내 정보 조회

`GET /api/users/me`

Input:

```json
{}
```

Output:

```json
{
  "userId": 1,
  "loginId": "user01",
  "name": "홍길동",
  "birthDate": "2000-01-01",
  "gender": "MALE",
  "email": "user@example.com",
  "disabilities": ["발달장애"],
  "desiredJob": "사무직",
  "accountStatus": "ACTIVE"
}
```

### 내 정보 수정

`PATCH /api/users/me`

Input:

```json
{
  "name": "홍길동",
  "gender": "MALE",
  "email": "new-user@example.com",
  "disabilities": ["발달장애", "청각장애"],
  "desiredJob": "사무직"
}
```

Output:

```json
{
  "message": "사용자 정보가 수정되었습니다."
}
```

## Training Progress

### 훈련 유형별 월간 진행 조회

`GET /api/trainings/progress?type=SOCIAL`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "trainingType": "SOCIAL",
    "level": 3,
    "periodStart": "2026-05-01T00:00:00",
    "periodEnd": "2026-06-01T00:00:00",
    "timezone": "Asia/Seoul",
    "completedCount": 3,
    "minRequiredCount": 3,
    "basis": "MONTHLY_COMPLETED_SUMMARIES",
    "reason": null,
    "metrics": {
      "averageScore": 82.3
    }
  },
  "error": null
}
```

### 홈 화면 진행 요약

`GET /api/trainings/progress/summary`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "periodStart": "2026-05-01T00:00:00",
    "periodEnd": "2026-06-01T00:00:00",
    "timezone": "Asia/Seoul",
    "items": [
      {
        "trainingType": "SOCIAL",
        "level": 3,
        "periodStart": "2026-05-01T00:00:00",
        "periodEnd": "2026-06-01T00:00:00",
        "timezone": "Asia/Seoul",
        "completedCount": 3,
        "minRequiredCount": 3,
        "basis": "MONTHLY_COMPLETED_SUMMARIES",
        "reason": null,
        "metrics": {
          "averageScore": 82.3
        }
      }
    ]
  },
  "error": null
}
```

### 훈련 기록 목록

`GET /api/trainings/sessions?type=SOCIAL&page=0&size=10`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "trainingType": "SOCIAL",
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "sessions": [
      {
        "sessionId": 10,
        "scenarioId": 1,
        "scenarioTitle": "직장 동료에게 도움 요청하기",
        "category": null,
        "score": 85,
        "feedbackSummary": "상황에 맞게 자연스럽게 대화했습니다.",
        "correctCount": null,
        "totalCount": null,
        "playedLevel": null,
        "accuracyRate": null,
        "wrongCount": null,
        "averageReactionMs": null,
        "completedAt": "2026-05-01T10:00:00"
      }
    ]
  },
  "error": null
}
```

## Social Training

### 직무 유형 선택

`POST /api/trainings/social/job-type`

Input:

```json
{
  "jobType": "OFFICE"
}
```

Output:

```json
{
  "success": true,
  "data": {
    "jobType": "OFFICE",
    "nextPage": "SCENARIO_SELECTION"
  },
  "error": null
}
```

### 사회성 시나리오 목록

`GET /api/trainings/social/scenarios?jobType=OFFICE`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": [
    {
      "scenarioId": 1,
      "title": "직장 동료에게 도움 요청하기",
      "difficulty": 1
    }
  ],
  "error": null
}
```

### 사회성 시나리오 상세

`GET /api/trainings/social/scenarios/{scenarioId}`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "scenarioId": 1,
    "jobType": "OFFICE",
    "title": "직장 동료에게 도움 요청하기",
    "backgroundText": "사무실에서 업무를 진행 중입니다.",
    "situationText": "동료에게 파일 정리를 도와달라고 요청해야 합니다.",
    "characterInfo": "동료는 바쁘지만 대화를 들어줄 수 있습니다.",
    "difficulty": 1
  },
  "error": null
}
```

### 사회성 세션 시작

`POST /api/trainings/social/sessions`

Input:

```json
{
  "jobType": "OFFICE",
  "scenarioId": 1
}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 10,
    "scenarioId": 1,
    "status": "IN_PROGRESS"
  },
  "error": null
}
```

### 사회성 음성 세션 준비

`POST /api/trainings/social/sessions/{sessionId}/voice/prepare`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 10,
    "scenarioId": 1,
    "connectionMode": "SERVER_RELAY",
    "realtime": {
      "wsUrl": "/ws/trainings/social/voice",
      "protocol": "json",
      "connectionToken": "voice-session-token",
      "expiresInSeconds": 300
    },
    "opening": {
      "script": "안녕하세요. 동료에게 도움을 요청하는 상황을 함께 연습해볼게요.",
      "audioUrl": "/static/opening/social/1/abcd1234.pcm",
      "audioAssetStatus": "READY"
    },
    "conversation": {
      "voice": "marin",
      "model": "gpt-realtime-mini",
      "instructionsVersion": "v1"
    }
  },
  "error": null
}
```

### 사회성 세션 완료

`POST /api/trainings/social/sessions/{sessionId}/complete`

Input:

```json
{
  "dialogLogs": [
    {
      "turnNo": 1,
      "speaker": "USER",
      "content": "지금 파일 정리를 도와주실 수 있나요?"
    },
    {
      "turnNo": 1,
      "speaker": "AI",
      "content": "네, 어떤 파일부터 정리하면 될까요?"
    }
  ],
  "voiceSummary": {
    "transcriptSource": "REALTIME",
    "audioSessionId": "audio-session-10",
    "durationSeconds": 120
  }
}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 10,
    "score": 85,
    "feedbackSummary": "요청 의도를 명확하게 전달했습니다.",
    "completed": true
  },
  "error": null
}
```

### 사회성 세션 상세

`GET /api/trainings/social/sessions/{sessionId}/detail`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 10,
    "score": 85,
    "scoreType": "AI_EVALUATION",
    "feedback": {
      "summary": "요청 의도를 명확하게 전달했습니다.",
      "detailText": "상대방이 이해하기 쉬운 표현을 사용했습니다."
    },
    "dialogLogs": [
      {
        "turnNo": 1,
        "speaker": "USER",
        "content": "지금 파일 정리를 도와주실 수 있나요?"
      }
    ]
  },
  "error": null
}
```

## Safety Training

### 안전 시나리오 목록

`GET /api/trainings/safety/scenarios?category=COMMUTE_SAFETY`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": [
    {
      "scenarioId": 1,
      "category": "COMMUTE_SAFETY",
      "title": "출근길 횡단보도 안전",
      "description": "출근 중 교통 상황에서 안전한 선택을 연습합니다."
    }
  ],
  "error": null
}
```

### 안전 세션 시작

`POST /api/trainings/safety/sessions`

Input:

```json
{
  "scenarioId": 1
}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 20,
    "scene": {
      "sceneId": 1,
      "screenInfo": "횡단보도 앞",
      "situationText": "신호가 깜빡이고 있습니다.",
      "questionText": "어떻게 행동해야 할까요?",
      "choices": [
        {
          "choiceId": 1,
          "text": "다음 신호를 기다린다."
        }
      ],
      "endScene": false
    }
  },
  "error": null
}
```

### 다음 안전 장면 진행

`POST /api/trainings/safety/sessions/{sessionId}/next-scene`

Input:

```json
{
  "sceneId": 1,
  "choiceId": 1
}
```

Output:

```json
{
  "success": true,
  "data": {
    "selectedResult": {
      "correct": true,
      "resultText": "안전하게 기다렸습니다.",
      "effectText": "사고 위험을 줄였습니다."
    },
    "nextScene": {
      "sceneId": 2,
      "screenInfo": "버스 정류장",
      "situationText": "버스가 도착했습니다.",
      "questionText": "어디에 서야 할까요?",
      "choices": [
        {
          "choiceId": 3,
          "text": "줄 뒤에 선다."
        }
      ],
      "endScene": true
    }
  },
  "error": null
}
```

### 안전 세션 완료

`POST /api/trainings/safety/sessions/{sessionId}/complete`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 20,
    "score": 80,
    "correctCount": 4,
    "totalCount": 5,
    "completed": true
  },
  "error": null
}
```

### 안전 세션 상세

`GET /api/trainings/safety/sessions/{sessionId}/detail`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 20,
    "score": 80,
    "choiceSummary": {
      "correctCount": 4,
      "totalCount": 5
    },
    "actions": [
      {
        "sceneId": 1,
        "situationText": "신호가 깜빡이고 있습니다.",
        "selectedChoice": "다음 신호를 기다린다.",
        "correct": true
      }
    ],
    "feedback": {
      "summary": "대부분의 안전 상황을 올바르게 판단했습니다.",
      "detailText": "신호와 주변 상황을 확인하는 선택이 좋았습니다."
    }
  },
  "error": null
}
```

## Focus Training

### 집중력 진행 조회

`GET /api/trainings/focus/progress`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "currentLevel": 2,
    "highestUnlockedLevel": 3,
    "lastPlayedLevel": 2,
    "lastAccuracyRate": 92.5,
    "lastAverageReactionMs": 820
  },
  "error": null
}
```

### 집중력 세션 시작

`POST /api/trainings/focus/sessions`

Input:

```json
{
  "level": 2
}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 40,
    "level": 2,
    "durationSeconds": 180,
    "commands": [
      {
        "commandId": 1001,
        "order": 1,
        "commandText": "파란색 위",
        "expectedAction": "BLUE_UP",
        "displayAtMs": 0
      }
    ]
  },
  "error": null
}
```

### 집중력 세션 완료

`POST /api/trainings/focus/sessions/{sessionId}/complete`

Input:

```json
{
  "reactions": [
    {
      "commandId": 1001,
      "userInput": "BLUE_UP",
      "reactionMs": 720
    }
  ]
}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 40,
    "score": 92,
    "accuracyRate": 92.5,
    "wrongCount": 1,
    "averageReactionMs": 820,
    "unlockedNextLevel": true,
    "currentLevel": 3,
    "highestUnlockedLevel": 3
  },
  "error": null
}
```

## Document Training

### 문서 이해 진행 조회

`GET /api/trainings/document/progress`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "currentLevel": 2,
    "highestUnlockedLevel": 3,
    "lastPlayedLevel": 2,
    "lastAccuracyRate": 80.0,
    "lastAverageReactionMs": null
  },
  "error": null
}
```

### 문서 이해 세션 시작

`POST /api/trainings/document/sessions`

Input:

```json
{
  "level": 1
}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 50,
    "questions": [
      {
        "questionId": 1,
        "title": "회의실 예약 안내",
        "documentText": "회의실 2번은 오후 3시에 사용할 수 있습니다.",
        "questionText": "오후 3시에 사용할 수 있는 회의실은 어디인가요?",
        "questionType": "MULTIPLE_CHOICE",
        "choices": [
          {
            "choiceId": 10,
            "choiceOrder": 1,
            "text": "회의실 2번"
          }
        ]
      }
    ]
  },
  "error": null
}
```

### 문서 이해 답변 제출

`POST /api/trainings/document/sessions/{sessionId}/answers`

Input:

```json
{
  "answers": [
    {
      "questionId": 1,
      "userAnswer": null,
      "choiceId": 10
    }
  ]
}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 50,
    "score": 100,
    "correctCount": 1,
    "totalCount": 1,
    "results": [
      {
        "questionId": 1,
        "correct": true,
        "correctAnswer": "회의실 2번",
        "explanation": "문서에 회의실 2번이라고 안내되어 있습니다."
      }
    ],
    "completed": true
  },
  "error": null
}
```

### 문서 이해 세션 상세

`GET /api/trainings/document/sessions/{sessionId}/detail`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "sessionId": 50,
    "score": 100,
    "answerSummary": {
      "correctCount": 1,
      "totalCount": 1
    },
    "answers": [
      {
        "questionId": 1,
        "questionText": "오후 3시에 사용할 수 있는 회의실은 어디인가요?",
        "userAnswer": "회의실 2번",
        "correctAnswer": "회의실 2번",
        "correct": true,
        "explanation": "문서에 회의실 2번이라고 안내되어 있습니다."
      }
    ]
  },
  "error": null
}
```

## Internal APIs

내부 API는 서비스 간 통신용이다. 외부 클라이언트에서 직접 호출하지 않는다.

필수 헤더:

```http
X-Internal-Api-Key: {internalApiKey}
```

### 내부 사용자 조회

`GET /internal/users/{userId}`

Input:

```json
{}
```

Output:

```json
{
  "userId": 1,
  "loginId": "user01",
  "name": "홍길동",
  "email": "user@example.com",
  "accountStatus": "ACTIVE",
  "disabilities": ["발달장애"],
  "desiredJob": "사무직"
}
```

### 내부 훈련 요약 조회

`GET /internal/trainings/users/{userId}/summary`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "socialRecentScore": 85,
    "safetyCorrectCount": 4,
    "safetyTotalCount": 5,
    "documentCorrectCount": 1,
    "documentTotalCount": 1,
    "focusCurrentLevel": 3
  },
  "error": null
}
```

### 내부 최신 훈련 결과 조회

`GET /internal/trainings/users/{userId}/latest-results`

Input:

```json
{}
```

Output:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "results": [
      {
        "sessionId": 10,
        "trainingType": "SOCIAL",
        "score": 85,
        "scoreType": "AI_EVALUATION",
        "completedAt": "2026-05-01T10:00:00"
      }
    ]
  },
  "error": null
}
```

## WebSocket: Social Voice

연결 전 `POST /api/trainings/social/sessions/{sessionId}/voice/prepare`로 `connectionToken`을 발급받는다.

Connect:

```text
WS /ws/trainings/social/voice?token={connectionToken}
```

Client event: `session.start`

```json
{
  "type": "session.start",
  "sessionId": 10
}
```

Server event: `session.ready`

```json
{
  "type": "session.ready",
  "sessionId": 10,
  "opening": {
    "script": "안녕하세요. 동료에게 도움을 요청하는 상황을 함께 연습해볼게요.",
    "audioUrl": "/static/opening/social/1/abcd1234.pcm"
  }
}
```

Client event: `audio.chunk`

```json
{
  "type": "audio.chunk",
  "sessionId": 10,
  "chunkBase64": "base64-pcm-audio",
  "mimeType": "audio/pcm",
  "sequence": 1
}
```

Client event: `audio.commit`

```json
{
  "type": "audio.commit",
  "sessionId": 10
}
```

Client event: `response.request`

```json
{
  "type": "response.request",
  "sessionId": 10
}
```

Server event: `audio.out`

```json
{
  "type": "audio.out",
  "sessionId": 10,
  "turnNo": 1,
  "chunkBase64": "base64-pcm-audio",
  "mimeType": "audio/pcm"
}
```

Server event: `transcript.partial`

```json
{
  "type": "transcript.partial",
  "sessionId": 10,
  "speaker": "AI",
  "turnNo": 1,
  "text": "네,"
}
```

Server event: `turn.complete`

```json
{
  "type": "turn.complete",
  "sessionId": 10,
  "turnNo": 1,
  "speaker": "AI",
  "finalText": "네, 어떤 파일부터 정리하면 될까요?"
}
```

Client event: `session.finish`

```json
{
  "type": "session.finish",
  "sessionId": 10
}
```

Server event: `session.completed`

```json
{
  "type": "session.completed",
  "sessionId": 10,
  "status": "COMPLETED"
}
```
