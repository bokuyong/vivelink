# clodock v5

캡스톤 디자인용으로 `vivelink`를 재활용해 `clodock v5` 방향으로 바꾼 작업본이다.

현재 구조는 다음과 같다.

- `vibelink-sender-v3`
  - 이름만 남아 있지만 실제 역할은 `clodock Sender v5`
  - 음성을 듣고 명령 문장을 컨트롤 기기로 전송한다
  - 자동 기기 탐색을 지원한다
- `vibelink-controller-v3`
  - 이름만 남아 있지만 실제 역할은 `clodock Control v5`
  - 명령을 받아 자연어를 해석하고, 로컬 Android 기기에서 앱 실행 및 기본 제어를 수행한다
  - `PA03`, `Raspberry Pi 5`, `Radxa Cubie A7Z` 3개 변형으로 빌드된다

## v3 상태 진단

기존 `v3`는 아래 특징이 있었다.

- 음성 인식은 했지만 실제로는 어떤 문장을 말해도 고정된 토스 송금 명령만 전송했다
- 컨트롤 앱은 토스 자동화 전용이라 임의 앱 실행이나 범용 명령 해석 구조가 없었다
- 연결은 `Sender IP`, `Device IP`, `ADB over Wi‑Fi`에 강하게 의존했다
- 앱 이름과 UI, 리소스가 `VibeLink / ViveLink / v3` 상태로 남아 있었다
- 프로젝트 루트가 Git 저장소가 아니어서 변경 이력을 바로 관리할 수 없었다

## 이번에 바뀐 핵심

### 1. sender 앱 단순화

- 메인 UI를 한 화면으로 단순화했다
- 음성 명령 시작 버튼, 수동 텍스트 명령 전송, 자동 기기 탐색, 수동 IP 백업 입력만 남겼다
- 음성 결과를 그대로 컨트롤 앱으로 보내도록 바꿨다
- 앱 내부 이미지 리소스를 제거했다

### 2. controller 앱 범용화

- 토스 전용 메인 화면을 제거했다
- 명령 서버를 일반 명령 수신기로 바꿨다
- 접근성 서비스 기반으로 다음 동작을 수행할 수 있게 했다
  - 앱 실행
  - 홈
  - 뒤로
  - 최근 앱
  - 알림창
  - 빠른 설정
  - 텍스트 클릭
  - 텍스트 입력
  - 위/아래 스크롤

### 3. 기기 변형 추가

컨트롤 앱은 product flavor로 아래 3개 변형을 빌드한다.

- `pa03`
- `rpi5`
- `cubiea7z`

각 변형은 앱 이름과 프로필 문자열이 다르게 표시된다.

### 4. 자동 탐색 추가

- sender가 UDP 브로드캐스트로 컨트롤 기기를 찾는다
- controller는 탐색 요청에 자신의 이름, 프로필, IP, 명령 포트를 응답한다
- 자동 탐색 실패 시 sender에서 수동 IP 입력도 가능하다

## 가능한 것과 불가능한 것

### 현재 가능한 것

- 음성 문장을 받아 앱 실행 명령으로 해석
- 설치된 앱 이름을 찾아 실행
- 접근성 서비스가 켜져 있으면 홈/뒤로/최근 앱/알림/빠른 설정 실행
- 화면에 보이는 텍스트를 눌러 보기
- 포커스된 입력창에 텍스트 넣기
- 스크롤 제어

### 아직 불가능하거나 제한이 큰 것

- 모든 자연어를 100% 정확하게 이해하는 것
- 어떤 앱의 어떤 화면이든 완전 자동으로 처리하는 것
- OS 권한 없이 모든 민감 작업을 강제 실행하는 것
- 기기 전원이 꺼진 상태에서 앱이 항상 명령을 받는 것
- Miracast만으로 명령 전달, 앱 실행, 상태 확인까지 모두 대체하는 것

## Miracast에 대한 판단

요청하신 `IP 수동 입력 제거` 목적은 이해했지만, Miracast는 화면 전송 계열에 가깝고 명령 라우팅이나 앱 제어 채널을 대체하는 용도로는 맞지 않는다.

그래서 이번 버전에서는 Miracast로 바꾸지 않고 아래처럼 설계를 바꿨다.

- 같은 네트워크에서 자동 탐색
- 찾은 기기에 자연어 명령 전송
- 컨트롤 앱이 로컬 기기에서 직접 앱 실행과 접근성 제어 수행

## 지원 명령 예시

- `유튜브 열어줘`
- `설정 열어줘`
- `홈으로 가`
- `뒤로 가`
- `최근 앱 보여줘`
- `알림창 열어줘`
- `확인 눌러`
- `clodock 입력해`
- `아래로 스크롤해`

## 빌드 방법

Java와 Android SDK 경로를 지정해서 빌드했다.

### sender

```bash
cd vibelink-sender-v3
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=$HOME/Library/Android/sdk \
ANDROID_SDK_ROOT=$HOME/Library/Android/sdk \
PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH" \
./gradlew assembleDebug
```

### controller

```bash
cd vibelink-controller-v3
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=$HOME/Library/Android/sdk \
ANDROID_SDK_ROOT=$HOME/Library/Android/sdk \
PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH" \
./gradlew assemblePa03Debug assembleRpi5Debug assembleCubiea7zDebug
```

## 빌드 결과

검증 완료:

- `vibelink-sender-v3` `assembleDebug` 성공
- `vibelink-controller-v3` `assemblePa03Debug` 성공
- `vibelink-controller-v3` `assembleRpi5Debug` 성공
- `vibelink-controller-v3` `assembleCubiea7zDebug` 성공

## 남은 다음 단계

캡스톤 용도로 더 밀어붙이려면 다음이 필요하다.

- 명령 파서 고도화
- 앱 이름 별칭 사전 추가
- 위험 작업 전 확인 절차 추가
- 필요 시 기기별 좌표/화면 프로파일 보강
- 접근성 기반의 더 정교한 탐색과 제스처 추가
- 백그라운드 서비스화와 부팅 후 자동 시작

