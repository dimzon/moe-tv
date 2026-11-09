# Устройства и тестовые стенды

**Дата актуализации:** 2026-11-09  
**Хост:** Acer Aspire AL16-54P, Intel Core 5 120U, 31,7 ГБ RAM, Windows 11 Pro (build 26200)  
**Источники:** `C:\Work\SmartRuTube\docs\redroid-test-stand.md`,
`C:\Work\SmartRuTube\docs\android7-emulator-test-stand.md`,
`C:\Users\User\hvm\android-redroid-runbook.md`,
`C:\Users\User\hvm\redroid-help-connect-versions.md`.

---

## 1. Сводная таблица

| # | Устройство | adb serial | Android | ABI | Экран / DPI | Роль | Статус 09.11.2026 |
|---|---|---|---|---|---|---|---|
| 1 | TV-бокс **Ugoos AM3** (Model-X3) | `192.168.1.22:5555` | 9 (API 28) | `armeabi-v7a` | 1920×1080 / 320 | эталонный физический TV | online |
| 2 | **redroid-smartrutube** | `172.18.237.39:5556` | 13 (API 33) | `x86_64` | 1920×1080 / 320 | основной тестовый стенд | online |
| 3 | **redroid** (старый) | `172.18.237.39:5555` | 13 (API 33) | `x86_64` | 1080×1920 / 480 | старый контейнер, не трогать | online |
| 4 | **Android 7 emulator** (AVD `api24`) | `127.0.0.1:5559` | 7.0 (API 24) | `x86_64` | 1920×1080 / 320 | проверка API 24/25 | online |

Все четыре устройства одновременно видны в `adb devices` с Windows-хоста.

```text
List of devices attached
127.0.0.1:5559         device  sdk_phone_x86_64 / Android_SDK_built_for_x86_64 / generic_x86_64
172.18.237.39:5555     device  redroid_x86_64 / redroid13_x86_64
172.18.237.39:5556     device  redroid_x86_64 / redroid13_x86_64
192.168.1.22:5555      device  X3 / Model_X3 / X3
```

---

## 2. Архитектура стенда

```text
Windows 11 (хост, adb 37.0.1, scrcpy 4.1)
├── 192.168.1.22:5555 ── Wi-Fi/LAN ── TV-бокс Ugoos AM3 (Android 9, armeabi-v7a)
│
├── 172.18.237.39 ── Hyper-V VM "hvm-ubuntu" (Ubuntu 24.04, kernel 6.8, 4 vCPU, 2–8 ГБ RAM)
│   │   SSH: dev@172.18.237.39, ключ C:\Users\User\.ssh\hvm_key
│   │   Docker 29.x + containerd (autostart), /dev/kvm, /dev/binderfs
│   ├── redroid:13.0.0-latest  → 0.0.0.0:5556 → Android 13, 1920×1080, DPI 320
│   ├── redroid:13.0.0-latest  → 0.0.0.0:5555 → Android 13, 1080×1920, DPI 480
│   └── Android 7 emulator (AVD api24, headless)
│         emulator-5558 (console 5558 / adb 5559) → проброшен на Windows как 127.0.0.1:5559
│
└── SSH-туннели с хоста:
    -R 2080:127.0.0.1:2080  — прокси хоста доступен внутри VM
    -L 5559:127.0.0.1:5559  — adb Android 7-эмулятора
```

---

## 3. Хост Windows

| Компонент | Значение |
|---|---|
| adb | `37.0.1-15733141`, winget platform-tools |
| scrcpy | `4.1`, `C:\APP\scrcpy\scrcpy.exe`; обёртка `C:\APP\tools\scrcpy.cmd` |
| Android SDK (сборка) | `C:\Work\tools\androidsdk` (без пакета `emulator`) |
| JDK 17 | `C:\Work\tools\jdk17\jdk-17.0.20.1+1` |
| Hyper-V | включён; PowerShell-модуль legacy 2.0.0.0, команды требуют повышения прав |
| Прокси | `127.0.0.1:2080` — только для внешних сайтов |

Локальных AVD на Windows нет — Android 7-эмулятор живёт только внутри VM.

---

## 4. Hyper-V VM `hvm-ubuntu`

| Параметр | Значение |
|---|---|
| Поколение | Gen2, Secure Boot выключен, nested virtualization включён |
| CPU / RAM | 4 vCPU; динамическая память 2 ГБ → 8 ГБ |
| ОС | Ubuntu 24.04.5 LTS, kernel `6.8.0-147-generic` |
| Сеть | коммутатор «Default Switch», DHCP, подсеть `172.18.224.0/20` |
| Текущий IP | `172.18.237.39` (меняется) |
| SSH | `dev@172.18.237.39`, ключ `C:\Users\User\.ssh\hvm_key` |
| Docker | `docker.io` 29.1.3 + containerd, автозапуск |
| Android-модули | `binder_linux`, `kvm_intel`; `/dev/binderfs` монтируется через `/etc/fstab` |
| Диск VM | `C:\Users\User\hvm\hvm-ubuntu.vhdx` (+ `*.avhdx` контрольные точки) |
| Данные Android | `/home/dev/redroid-data`, `/home/dev/redroid-smartrutube-data` |
| Скрипты VM | `C:\Users\User\hvm\start-vm.cmd`, `stop-vm.cmd`, `setup-vm.ps1`, `attach-guest.sh` |

### 4.1. Запуск / остановка VM

Двойной клик (UAC обязателен):

```text
C:\Users\User\hvm\start-vm.cmd   — запустить VM, подождать IP, записать в vm-ip.txt
C:\Users\User\hvm\stop-vm.cmd    — остановить VM
```

Вручную из PowerShell **от администратора**:

```powershell
Start-VM -Name hvm-ubuntu
Stop-VM  -Name hvm-ubuntu -Force

Get-VM -Name hvm-ubuntu
Get-VMNetworkAdapter -VMName hvm-ubuntu | Select-Object -ExpandProperty IPAddresses
```

> ⚠️ Без повышения прав `Get-VM`/`Start-VM` возвращают
> «У вас нет разрешения на выполнение этой задачи».
>
> ⚠️ **Не запускать `run-setup.cmd`** — он может заменить диск на
> `hvm-ubuntu-fresh.vhdx`, если такой файл существует. Настроенный гость будет потерян.

### 4.2. Если IP гостя неизвестен

1. `C:\Users\User\hvm\vm-ip.txt` — последний известный IP (может быть пустым).
2. Сканирование порта 22 в `172.18.224.0/20`:

```powershell
$t = New-Object System.Collections.Concurrent.ConcurrentBag[string]
224..239 | % { $th=$_; 1..254 | % { $ip="172.18.$th.$_"; $c=New-Object Net.Sockets.TcpClient
  try { $a=$c.BeginConnect($ip,22,$null,$null); if($a.AsyncWaitHandle.WaitOne(400)){ $c.EndConnect($a); $t.Add($ip) } } catch {} finally { $c.Close() } } }
$t | Sort-Object
```

3. Либо `Get-NetNeighbor` по интерфейсу Default Switch — искать MAC `00:15:5d:…`.

### 4.3. Проверка гостя

```bash
ssh -i C:/Users/User/.ssh/hvm_key dev@172.18.237.39 \
  'uname -a; ls -l /dev/kvm /dev/binderfs; sudo docker ps'
```

---

## 5. Устройства подробно

### 5.1. TV-бокс Ugoos AM3 — `192.168.1.22:5555`

| Параметр | Значение |
|---|---|
| Build fingerprint | `Ugoos/AM3/AM3:9/PPR1.180610.011/20221007:userdebug/release-keys` |
| Модель / продукт | `Model-X3` / `X3` |
| Android | 9 (API 28) |
| ABI | `armeabi-v7a` |
| CPU | Amlogic, 4 ядра ARMv7 |
| RAM | ~3,97 ГБ (`MemTotal`) |
| Экран | 1920×1080, DPI 320 |
| Часовой пояс | `Europe/Moscow` |
| Serial | `1234567890` |
| Роль | эталонный физический TV-бокс; сборки проверяются в первую очередь здесь |

Установленное приложение на 09.11.2026: `com.github.dimzon.smartrutube.beta`
`1.0.629` — **release** (флаг `DEBUGGABLE` отсутствует), поэтому debug HTTP-сервер
на нём не поднимается.

```bash
# установка и запуск
adb -s 192.168.1.22:5555 install -r <path-to-apk>
adb -s 192.168.1.22:5555 shell am force-stop com.github.dimzon.smartrutube.beta
adb -s 192.168.1.22:5555 shell am start -n \
  com.github.dimzon.smartrutube.beta/com.github.dimzon.smartrutube.tv.ui.main.SplashActivity

# логи
adb -s 192.168.1.22:5555 logcat -c
adb -s 192.168.1.22:5555 logcat -d > logs.txt

# UI-дерево
adb -s 192.168.1.22:5555 shell uiautomator dump /sdcard/ui.xml
adb -s 192.168.1.22:5555 shell cat /sdcard/ui.xml

# экран
scrcpy -s 192.168.1.22:5555
```

> Бокс — эталон: без необходимости не перезагружать, не менять настройки,
> не удалять данные приложения.

---

### 5.2. redroid-smartrutube — `172.18.237.39:5556`

| Параметр | Значение |
|---|---|
| Контейнер | `redroid-smartrutube` |
| Образ | `redroid/redroid:13.0.0-latest` |
| Android | 13 (API 33), `redroid13_x86_64` |
| ABI | `x86_64` |
| Экран / DPI | 1920×1080 / 320 (задано в `Cmd` контейнера) |
| Порт | `5556` на хосте VM → `5555` в контейнере |
| Данные | `/home/dev/redroid-smartrutube-data` (~185 МБ) |
| Рестарт | `unless-stopped` |
| Создан | 2026-11-02 |
| Роль | основной стенд: быстрый install/логи/скриншоты, debug HTTP-сервер |

Установленное приложение на 09.11.2026: `1.0.628`, **debug** (`DEBUGGABLE`),
debug HTTP-сервер работает.

#### Подключение

```bash
adb connect 172.18.237.39:5556
adb -s 172.18.237.39:5556 root      # после каждого нового connect
adb -s 172.18.237.39:5556 shell id  # uid=0(root)
```

#### Управление контейнером

```bash
ssh -i C:/Users/User/.ssh/hvm_key dev@172.18.237.39

sudo docker ps --filter name=redroid
sudo docker stop redroid-smartrutube
sudo docker start redroid-smartrutube
```

Пересоздание (например, после смены разрешения):

```bash
sudo docker rm -f redroid-smartrutube
sudo docker run -itd --restart unless-stopped --privileged \
  --name redroid-smartrutube \
  --device /dev/kvm:/dev/kvm \
  -v /dev/binderfs:/dev/binderfs \
  -v /home/dev/redroid-smartrutube-data:/data \
  -p 5556:5555 \
  redroid/redroid:13.0.0-latest \
  androidboot.redroid_width=1920 \
  androidboot.redroid_height=1080 \
  androidboot.redroid_dpi=320
```

#### Debug HTTP-сервер

Поднимается автоматически только в **debug**-сборке (`DebugHttpServer`).
На redroid-smartrutube отвечает:

```bash
adb -s 172.18.237.39:5556 forward tcp:65065 tcp:65065
curl --noproxy '*' http://127.0.0.1:65065/status
```

Ожидаемый ответ:

```text
environment: redroid
virtual: true
debug_allowed: true
```

Порты: `65065`, `65066`, `65067` (fallback). Основные эндпоинты:

```text
/ui, /status, /logcat, /logcat/clear, /logfile, /video, /accounts,
/channelprefs[?channelId=&flags=], /account-mode,
/local, /local/add, /local/remove, /local/touch, /local/clear,
/local/feed, /local/feed/clear,
/history, /watchtime, /position,
/sections, /section, /feed, /feed/next,
/player, /player/state, /player/open, /player/play, /player/pause,
/player/seek, /player/controls, /player/overlay, /player/subtitles,
/player/formats, /player/hq, /player/speed, /player/volume,
/player/restart, /player/finish, /player/button,
/cache/card
```

Примеры:

```bash
curl --noproxy '*' http://127.0.0.1:65065/sections
curl --noproxy '*' 'http://127.0.0.1:65065/section?name=history'
curl --noproxy '*' 'http://127.0.0.1:65065/player/open?id=<videoId>'
curl --noproxy '*' http://127.0.0.1:65065/player/state
```

#### Перенос данных с бокса

На боксе приложение останавливается, чтобы не копировать открытые MapDB:

```bash
adb -s 192.168.1.22:5555 shell am force-stop com.github.dimzon.smartrutube.beta

adb -s 192.168.1.22:5555 exec-out \
  run-as com.github.dimzon.smartrutube.beta \
  tar -cf - shared_prefs files databases no_backup \
  > smartrutube-box-data.tar
```

На redroid:

```bash
adb -s 172.18.237.39:5556 push smartrutube-box-data.tar /data/local/tmp/
adb -s 172.18.237.39:5556 shell '
  am force-stop com.github.dimzon.smartrutube.beta
  rm -rf /data/data/com.github.dimzon.smartrutube.beta/{shared_prefs,files,databases,no_backup}
  tar -xf /data/local/tmp/smartrutube-box-data.tar \
    -C /data/data/com.github.dimzon.smartrutube.beta
  uid=$(stat -c %u /data/data/com.github.dimzon.smartrutube.beta)
  chown -R $uid:$uid /data/data/com.github.dimzon.smartrutube.beta
  restorecon -R /data/data/com.github.dimzon.smartrutube.beta
'
```

Не копировать `cache`, `code_cache`, `lib`.

---

### 5.3. Старый redroid — `172.18.237.39:5555`

| Параметр | Значение |
|---|---|
| Контейнер | `redroid` |
| Образ | `redroid/redroid:13.0.0-latest` |
| Android | 13 (API 33) |
| Экран / DPI | 1080×1920 / 480 |
| Данные | `/home/dev/redroid-data` (~147 МБ) |
| Порт | `5555` на хосте VM → `5555` в контейнере |

Сейчас в нём установлен SmartRuTube `1.0.594` (debug), но приложение не запущено;
на переднем плане работает постороннее `com.hd.videoboxalfa`.
Этот контейнер использовался раньше и оставлен как есть — **без необходимости не трогать**.

```bash
sudo docker stop redroid
sudo docker start redroid
```

---

### 5.4. Android 7 emulator (API 24) — `127.0.0.1:5559`

| Параметр | Значение |
|---|---|
| AVD | `api24` |
| System image | `system-images;android-24;default;x86_64` |
| Устройство | `tv_1080p` |
| Android | 7.0 (API 24), `sdk_phone_x86_64` |
| ABI | `x86_64` |
| Экран / DPI | 1920×1080 / 320 |
| CPU / RAM | 2 ядра / 2048 МБ |
| Режим | headless (`-no-window`), KVM |
| Где живёт | внутри VM `hvm-ubuntu`, serial `emulator-5558` |
| Доступ с Windows | SSH-туннель → `127.0.0.1:5559` |
| SDK в VM | `~/android-sdk` (cmdline-tools 13114758, adb 37.0.1, emulator 37.2.13) |

Установленное приложение на 09.11.2026: `1.0.618` — **release** (не debuggable),
поэтому debug HTTP-сервера на эмуляторе нет.

#### Запуск (внутри VM)

```bash
export ANDROID_SDK_ROOT=$HOME/android-sdk

nohup ~/android-sdk/emulator/emulator \
  -avd api24 \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -gpu swiftshader_indirect \
  -no-snapshot \
  -no-metrics \
  -memory 2048 \
  -cores 2 \
  > /tmp/emulator.log 2>&1 &

ADB=~/android-sdk/platform-tools/adb
$ADB devices
$ADB -s emulator-5558 shell getprop sys.boot_completed   # 1
$ADB -s emulator-5558 shell getprop ro.build.version.sdk # 24
```

#### Доступ с Windows

```bash
ssh -f -N -L 5559:127.0.0.1:5559 \
  -o ExitOnForwardFailure=yes \
  -o StrictHostKeyChecking=no \
  -i C:/Users/User/.ssh/hvm_key \
  dev@172.18.237.39

adb connect 127.0.0.1:5559
adb -s 127.0.0.1:5559 shell getprop ro.build.version.sdk  # 24
scrcpy -s 127.0.0.1:5559
```

Правило портов: adb-порт эмулятора = console-порт + 1
(`emulator-5554` → `5555`, `emulator-5558` → `5559`).
Фактический serial всегда смотреть в `adb devices`.

#### Остановка

```bash
ADB=~/android-sdk/platform-tools/adb
$ADB -s emulator-5558 emu kill
# если процесс завис:
pkill -f qemu-system-x86_64-headless
```

#### Пересоздание AVD

```bash
export ANDROID_SDK_ROOT=$HOME/android-sdk

echo no | ~/android-sdk/cmdline-tools/latest/bin/avdmanager create avd \
  -n api24 \
  -k "system-images;android-24;default;x86_64" \
  -d tv_1080p \
  --force
```

Для API 25 аналогично: `system-images;android-25;default;x86_64` + отдельный AVD.

> Стенд нужен именно для API 24/25. Для API 26+ он не показателен: например,
> `ThreadLocal.withInitial()` появился только в API 26, и на Android 7 падал
> `NoSuchMethodError`. Такие проблемы ловятся только здесь.

---

## 6. Порты

| Порт | Где | Назначение |
|---|---|---|
| `22` | VM `172.18.237.39` | SSH в гостя |
| `2080` | Windows / VM | локальный HTTP-прокси для внешних сайтов |
| `5555` | VM | старый redroid (контейнер `redroid`) |
| `5555` | `192.168.1.22` | adb TV-бокса |
| `5556` | VM | redroid-smartrutube |
| `5558` | VM (loopback) | console-порт Android 7-эмулятора |
| `5559` | VM + Windows | adb Android 7-эмулятора (SSH-туннель) |
| `65065`–`65067` | устройство | debug HTTP-сервер (только debug-сборки) |

---

## 7. Сеть и прокси

- `rutube.ru` и российские сайты — **напрямую, без прокси**:
  `curl --noproxy '*'`, `Invoke-WebRequest -NoProxy`.
- Остальные сайты (Google SDK, GitHub и т.п.) — через прокси `127.0.0.1:2080`.
- Внутри VM прокси хоста доступен через SSH reverse tunnel:

```bash
ssh -N -R 2080:127.0.0.1:2080 \
  -o ExitOnForwardFailure=yes \
  -o StrictHostKeyChecking=no \
  -i C:/Users/User/.ssh/hvm_key \
  dev@172.18.237.39
```

Проверка из VM:

```bash
curl -x http://127.0.0.1:2080 -sS -o /dev/null -w "%{http_code}\n" https://dl.google.com/
```

Системный прокси на Windows для запросов к RuTube лучше отключать: он даёт ложные
`blocking_rule` и пустые `live_streams` на live-каналах.

---

## 8. Типовые операции

### 8.1. Полный старт стенда

```bash
# 1. VM (UAC) — двойной клик
C:\Users\User\hvm\start-vm.cmd

# 2. дождаться IP и проверить гостя
ssh -i C:/Users/User/.ssh/hvm_key dev@172.18.237.39 \
  'sudo docker ps; ls -l /dev/kvm /dev/binderfs'

# 3. adb
adb connect 172.18.237.39:5556
adb connect 172.18.237.39:5555
adb -s 127.0.0.1:5559 shell getprop sys.boot_completed
adb devices -l
```

### 8.2. Установка APK

```bash
adb -s <serial> install -r <path-to-apk>
adb -s <serial> shell am force-stop com.github.dimzon.smartrutube.beta
adb -s <serial> shell am start -n \
  com.github.dimzon.smartrutube.beta/com.github.dimzon.smartrutube.tv.ui.main.SplashActivity
```

Пример имени APK SmartRuTube:
`smarttubetv/build/outputs/apk/stbeta/debug/SmartRuTube_beta_<version>_universal.apk`.
Актуальный файл — последний по времени в каталоге.

### 8.3. Логи и UI

```bash
adb -s <serial> logcat -c
# ... воспроизвести проблему ...
adb -s <serial> logcat -d > logs.txt
adb -s <serial> logcat -d | grep -E "VideoLoaderController|ExoPlayerController|TrackSelectorManager|ErrorFixerController"

adb -s <serial> shell uiautomator dump /sdcard/ui.xml
adb -s <serial> shell cat /sdcard/ui.xml
```

### 8.4. Скриншоты и управление

```bash
scrcpy -s 192.168.1.22:5555    # TV-бокс
scrcpy -s 172.18.237.39:5555   # старый redroid
scrcpy -s 172.18.237.39:5556   # redroid-smartrutube
scrcpy -s 127.0.0.1:5559       # Android 7 emulator

# навигация без scrcpy
adb -s <serial> shell input keyevent KEYCODE_DPAD_DOWN
adb -s <serial> shell input keyevent KEYCODE_DPAD_CENTER
```

> scrcpy может предупредить `adb reverse: more than one device/emulator` —
> он автоматически падает обратно на `adb forward`, зеркалирование работает.

---

## 9. Текущее состояние (проверено 09.11.2026)

| Устройство | adb | Android | Приложение | Передний план | Debug HTTP |
|---|---|---|---|---|---|
| Ugoos AM3 | `192.168.1.22:5555` | 9 | 1.0.629 release | `BrowseActivity` | нет (release) |
| redroid-smartrutube | `172.18.237.39:5556` | 13 | 1.0.628 debug | launcher | **работает**, `/status` OK |
| redroid (старый) | `172.18.237.39:5555` | 13 | 1.0.594 debug, не запущен | `com.hd.videoboxalfa` | нет (приложение не запущено) |
| Android 7 emulator | `127.0.0.1:5559` | 7.0 | 1.0.618 release | launcher | нет (release) |

Docker в госте: `redroid-smartrutube` Up 5 дней, `redroid` Up 6 дней.
VM: 4 vCPU, ~7,9 ГБ RAM (использовано ~5,5 ГБ), KVM и binderfs на месте.

---

## 10. Известные проблемы и ограничения

1. **Hyper-V-команды требуют администратора.** Без UAC `Get-VM`, `Start-VM`,
   `Stop-VM`, `Get-VMNetworkAdapter` возвращают ошибку доступа. Скрипты
   `start-vm.cmd` / `stop-vm.cmd` сами запрашивают повышение.

2. **IP гостя — DHCP.** `172.18.237.39` может измениться после перезапуска VM.
   `vm-ip.txt` может быть пустым — тогда сканировать подсеть или смотреть
   `Get-NetNeighbor`.

3. **`run-setup.cmd` опасен.** Он может заменить диск на `hvm-ubuntu-fresh.vhdx`
   и потерять настроенного гостя. Для повседневного запуска — только `start-vm.cmd`.

4. **redroid в WSL2 невозможен.** В ядре WSL нет `CONFIG_ANDROID_BINDER_IPC` и модуля
   `binder_linux`; Docker Desktop и WSL делят одно ядро. Рабочий путь — только
   Hyper-V VM с обычным Ubuntu (текущий стенд).

5. **Смена major-версии redroid требует очистки данных.** Том `/data` не переносится
   между Android 11/12/13/14. При смене major:
   `sudo rm -rf /home/dev/redroid-data/*` (или соответствующего каталога).
   Минорное обновление в рамках того же major данные сохраняет.

6. **Serial эмулятора не фиксирован.** Может быть `emulator-5554`, `emulator-5558` и т.п.
   adb-порт = console-порт + 1. Всегда проверять `adb devices`.

7. **Debug HTTP-сервер только в debug-сборках.** Release-сборки (бокс, эмулятор)
   его не поднимают, даже если устройство в allowlist. На redroid-smartrutube
   сервер работает, потому что там установлена debug-сборка.

8. **`adb root` нужен после каждого `adb connect`** для redroid. В Android-шелле
   `su` — toybox, `su -c` не работает; root доступен через `adb root`.

9. **Память хоста — узкое место.** При большом числе IDE/браузеров VM может не
   стартовать с фиксированными 8 ГБ. Поэтому используется динамическая память
   (2 → 8 ГБ), а эмулятор занимает ~1,5 ГБ RAM — если не нужен, лучше остановить.

10. **Старый redroid на 5555 занят посторонним приложением.** Не удалять контейнер
    и не чистить `/home/dev/redroid-data` без необходимости.

11. **Прокси и RuTube.** Запросы к `rutube.ru` — только напрямую. Системный прокси
    `127.0.0.1:2080` искажает ответы live-каналов.

---

## 11. Быстрая шпаргалка

```bash
# все устройства
adb devices -l

# VM
C:\Users\User\hvm\start-vm.cmd
C:\Users\User\hvm\stop-vm.cmd
ssh -i C:/Users/User/.ssh/hvm_key dev@172.18.237.39 'sudo docker ps'

# подключение
adb connect 172.18.237.39:5555
adb connect 172.18.237.39:5556
adb connect 127.0.0.1:5559

# root на redroid
adb -s 172.18.237.39:5556 root

# установка/запуск
adb -s <serial> install -r <apk>
adb -s <serial> shell am force-stop com.github.dimzon.smartrutube.beta
adb -s <serial> shell am start -n com.github.dimzon.smartrutube.beta/com.github.dimzon.smartrutube.tv.ui.main.SplashActivity

# логи
adb -s <serial> logcat -c && adb -s <serial> logcat -d > logs.txt

# UI
adb -s <serial> shell uiautomator dump /sdcard/ui.xml
adb -s <serial> shell cat /sdcard/ui.xml

# debug HTTP (только debug-сборка)
adb -s 172.18.237.39:5556 forward tcp:65065 tcp:65065
curl --noproxy '*' http://127.0.0.1:65065/status

# зеркало экрана
scrcpy -s 192.168.1.22:5555
scrcpy -s 172.18.237.39:5555
scrcpy -s 172.18.237.39:5556
scrcpy -s 127.0.0.1:5559
```

---

## 12. Ссылки на исходную документацию

| Документ | Путь |
|---|---|
| Redroid — основной стенд | `C:\Work\SmartRuTube\docs\redroid-test-stand.md` |
| Android 7 emulator | `C:\Work\SmartRuTube\docs\android7-emulator-test-stand.md` |
| Runbook VM/redroid | `C:\Users\User\hvm\android-redroid-runbook.md` |
| Подключение и смена версии redroid | `C:\Users\User\hvm\redroid-help-connect-versions.md` |
| Удаление стенда | `C:\Users\User\hvm\redroid-uninstall-cleanup.md` |
| Сборка и тестирование SmartRuTube | `C:\Work\SmartRuTube\docs\DEVELOPER.md` |
