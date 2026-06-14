# Android APK 배포

숲길동무 Android 배포는 Play Console용 AAB가 아니라 직접 배포용 signed APK를 기준으로 한다.

## 산출물

- 패키지 ID: `kr.forestmate.app`
- 빌드 도구: Bubblewrap TWA
- 최종 파일: `dist/forestmate-android-v1.0.0.apk`
- 체크섬: `dist/forestmate-android-v1.0.0.apk.sha256`

Bubblewrap는 내부적으로 AAB도 만들지만, `npm run build:apk`는 빌드 후 AAB를 삭제하고 APK만 `dist/`에 남긴다.

## 1. 최초 1회 준비

이 단계는 JDK 17+, Android SDK, 서명키 생성 때문에 대화형으로 진행한다.

```bash
cd "/Users/dong9733/Documents/LYT Kit 2/forest-mate/packaging/android"
npm install
npm run init:project
```

프롬프트에서는 기존 설정값을 기준으로 입력한다.

- Application ID: `kr.forestmate.app`
- Host: `forestmate.onrender.com`
- Name: `숲길동무 ForestMate`
- Launcher name: `숲길동무`
- Signing key alias: `forestmate`

생성된 `android-signing.keystore`와 비밀번호는 유출되면 안 된다. 이 repo의 `packaging/.gitignore`는 keystore 커밋을 막는다.

## 2. APK 빌드

서명키 비밀번호를 환경변수로 넣고 빌드한다.

```bash
cd "/Users/dong9733/Documents/LYT Kit 2/forest-mate/packaging/android"
export BUBBLEWRAP_KEYSTORE_PASSWORD="..."
export BUBBLEWRAP_KEY_PASSWORD="..."
npm run build:apk
```

완료 후 `dist/forestmate-android-v1.0.0.apk`만 배포한다.

## 3. Digital Asset Links

TWA에서 주소창을 숨기려면 APK 서명키 SHA-256을 웹 서버의 `/.well-known/assetlinks.json`에 넣어야 한다.

```bash
npm run fingerprint
```

출력된 SHA-256 값을 `/Users/dong9733/Documents/LYT Kit 2/forest-mate/app/.well-known/assetlinks.json`에 반영하고 다시 배포한다. 현재 v1.0.0 APK 서명 지문은 이미 반영되어 있다.

## 4. 배포 방식

APK는 Play Store가 아니라 직접 링크, GitHub Release, 또는 행사 제출 패키지로 배포한다. 사용자는 Android 설정에서 "알 수 없는 앱 설치"를 허용해야 설치할 수 있다.
