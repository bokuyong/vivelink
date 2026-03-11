# VibeLink

두 대의 안드로이드 기기 간 화면 미러링 + 자동 제어로 토스 앱에서 아내에게 1만원 자동 송금하는 프로젝트.

---

## 구조

```
VibeLink/
├── vibelink-sender/       # 기기 A - 화면 캡처 및 스트리밍
└── vibelink-controller/   # 기기 B - 화면 수신, OCR 분석, 토스 자동화
```

## 파이프라인

```
[기기 A]                              [기기 B]
  토스 실행                              화면 표시
  MediaProjection 캡처  -WiFi->         Stream 수신
  ADB 입력 수신      <-ADB-WiFi-        Vision API OCR
                                        TossAutomation 실행
```

---

## 설정 방법

### 기기 A (vibelink-sender)

1. Android Studio에서 `vibelink-sender` 프로젝트 열기
2. 기기 A에 USB 디버깅 활성화
3. USB로 PC 연결 후 ADB over WiFi 설정:
   ```
   adb tcpip 5555
   ```
4. 앱 빌드 후 기기 A에 설치
5. 앱 실행 → 화면에 표시된 IP 주소 메모

### 기기 B (vibelink-controller)

1. `vibelink-controller/app/src/main/java/com/vibelink/controller/MainActivity.java` 열기
2. `VISION_API_KEY` 를 Google Cloud Console에서 발급한 Vision API 키로 교체:
   ```java
   private static final String VISION_API_KEY = "여기에_실제_API_키_입력";
   ```
3. 앱 빌드 후 기기 B에 설치
4. 앱 실행

### 기기 B 앱 사용 순서

1. **Sender IP**: 기기 A에서 확인한 IP 입력 (예: `192.168.0.10`)
2. **Device A IP**: 기기 A의 WiFi IP 입력 (ADB 용, 동일할 수 있음)
3. **연결** 버튼 탭 → 기기 A 화면이 미러링으로 표시됨
4. **토스 송금** 버튼 탭 → 자동화 시작

---

## 토스 자동화 플로우

```
1. 토스 앱 실행
2. "보내기" 버튼 탭 (OCR 탐지 → 실패 시 비율 좌표 폴백)
3. 받는 사람 검색창 탭 → "아내" 입력
4. 연락처 목록에서 "아내" 선택
5. 금액 입력창 탭 → "10000" 입력
6. "다음" 버튼 탭
7. "보내기" 최종 확인 버튼 탭
```

### 좌표 조정

기기 A의 해상도가 1080x2400이 아닌 경우, `TossAutomation.java` 생성자 호출부에서 수정:

```java
// MainActivity.java
tossAutomation = new TossAutomation(adbController, screenAnalyzer,
        실제_가로해상도, 실제_세로해상도);
```

### 수취인 이름 변경

`TossAutomation.java` 상단 상수 수정:

```java
private static final String RECIPIENT_NAME = "아내"; // 토스에 저장된 실제 이름
```

---

## Google Cloud Vision API 키 발급

1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 프로젝트 생성 또는 선택
3. Cloud Vision API 활성화
4. API 키 생성 (`API 및 서비스` → `사용자 인증 정보` → `API 키 만들기`)
5. `MainActivity.java`의 `VISION_API_KEY` 상수에 입력

---

## 주의사항

- 기기 A와 B는 동일한 WiFi 네트워크(5GHz 권장)에 연결되어 있어야 함
- USB 디버깅은 개발자 옵션에서 활성화 필요
- `adb tcpip 5555`는 최초 1회 USB 연결 상태에서 실행해야 하며, 재부팅 시 재설정 필요
- 토스 UI는 업데이트 시 변경될 수 있으므로, OCR 폴백 좌표를 주기적으로 재확인 권장
- 본 프로젝트는 본인 계정의 본인 아내에 대한 정기 송금 자동화 목적의 개인 데모임

---

## 개발 환경

- Android API 26+ (Oreo)
- JDK 17
- Gradle 8.4
- Android Gradle Plugin 8.2.0
