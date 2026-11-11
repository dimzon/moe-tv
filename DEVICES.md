# Устройства и стенды — справочник по железу и управлению виртуалками

**Дата актуализации:** 2026-11-09  
**Хост:** Acer Aspire AL16-54P, Intel Core 5 120U, 31,7 ГБ RAM, Windows 11 Pro (build 26200)

Справочник проектно-нейтральный: только железо, стенды и управление виртуальными
машинами/контейнерами/эмулятором. Установка и запуск приложений через adb намеренно
не описаны.

---

## 1. Что есть

| # | Устройство | adb serial | Android | ABI | Экран / DPI | Роль |
|---|---|---|---|---|---|---|
| 1 | TV-бокс **Ugoos AM3** (Model-X3) | `192.168.1.22:5555` | 9 (API 28) | `armeabi-v7a` | 1920×1080 / 320 | реальное TV-устройство, эталон |
| 2 | **redroid-smartrutube** | `172.18.237.39:5556` | 13 (API 33) | `x86_64` | 1920×1080 / 320 | основной автоматизируемый стенд |
| 3 | **redroid** (старый) | `172.18.237.39:5555` | 13 (API 33) | `x86_64` | 1080×1920 / 480 | старый контейнер, свободен |
| 4 | **Android 7 emulator** (AVD `api24`) | `127.0.0.1:5559` | 7.0 (API 24) | `x86_64` | 1920×1080 / 320 | проверка API 24/25 |

Все четыре устройства одновременно видны в `adb devices` с Windows-хоста.

```text
List of devices attached
127.0.0.1:5559         device  sdk_phone_x86_64 / Android_SDK_built_for_x86_64 / generic_x86_64
172.18.237.39:5555     device  redroid_x86_64 / redroid13_x86_64
172.18.237.39:5556     device  redroid_x86_64 / redroid13_x86_64
192.168.1.22:5555      device  X3 / Model_X3 / X3
```

Имена `redroid-smartrutube` и каталог `redroid-smartrutube-data` — исторические;
это просто имена контейнера и тома.

---

## 2. Железо

### 2.1. Хост Windows

| Параметр | Значение |
|---|---|
| Модель | Acer Aspire AL16-54P |
| CPU | Intel Core 5 120U |
| RAM | 31,7 ГБ |
| ОС | Windows 11 Pro, build 26200 |
| adb | `37.0.1-15733141` (winget platform-tools) |
| scrcpy | `4.1`, `C:\APP\scrcpy\scrcpy.exe`; обёртка `C:\APP\tools\scrcpy.cmd` |
| Android SDK | `C:\Work\tools\androidsdk` (без пакета `emulator`) |
| JDK 17 | `C:\Work\tools\jdk17\jdk-17.0.20.1+1` |
| Hyper-V | включён; PowerShell-модуль legacy 2.0.0.0 |

Локальных AVD на Windows нет — Android 7-эмулятор живёт только внутри VM.

### 2.2. TV-бокс Ugoos AM3 — `192.168.1.22:5555`

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
| Сеть | LAN/Wi-Fi, статический `192.168.1.22`, adb по TCP |

Ограничения: 1080p SDR, только 32-битный ARM. Проверить 4K/HDR и arm64 на нём нельзя.
Бокс — эталон: без необходимости не перезагружать, не менять настройки, не чистить данные.

### 2.3. Hyper-V VM `hvm-ubuntu`

| Параметр | Значение |
|---|---|
| Поколение | Gen2, Secure Boot выключен, nested virtualization включён |
| CPU / RAM | 4 vCPU; динамическая память 2 ГБ → 8 ГБ |
| ОС | Ubuntu 24.04.5 LTS, kernel `6.8.0-147-generic` |
| Сеть | коммутатор «Default Switch», DHCP, подсеть `172.18.224.0/20` |
| Текущий IP | `172.18.237.39` (меняется) |
| SSH | `dev@172.18.237.39`, ключ `C:\Users\User\.ssh\hvm_key` |
| Docker | `docker.io` 29.1.3 + containerd, автозапуск |
| Android-модули | `binder_linux`, `kvm_intel`; `/dev/binderfs` через `/etc/fstab` |
| Диск VM | `C:\Users\User\hvm\hvm-ubuntu.vhdx` (+ `*.avhdx` контрольные точки) |
| Данные Android | `/home/dev/redroid-data`, `/home/dev/redroid-smartrutube-data` |
| Скрипты VM | `C:\Users\User\hvm\start-vm.cmd`, `stop-vm.cmd`, `setup-vm.ps1`, `attach-guest.sh` |

### 2.4. redroid-контейнеры (внутри VM)

| Параметр | redroid-smartrutube | redroid (старый) |
|---|---|---|
| Контейнер | `redroid-smartrutube` | `redroid` |
| Образ | `redroid/redroid:13.0.0-latest` | `redroid/redroid:13.0.0-latest` |
| Android | 13 (API 33) | 13 (API 33) |
| ABI | `x86_64` | `x86_64` |
| Экран / DPI | 1920×1080 / 320 | 1080×1920 / 480 |
| Порт хоста VM | `5556` → `5555` | `5555` → `5555` |
| Данные | `/home/dev/redroid-smartrutube-data` (~185 МБ) | `/home/dev/redroid-data` (~147 МБ) |
| Рестарт | `unless-stopped` | `unless-stopped` |
| Роль | основной автоматизируемый стенд | дополнительный свободный стенд |

Ограничения: x86_64, без Google Play и leanback-лаунчера; управление только через adb.
1080p SDR — 4K/HDR не проверить.

### 2.5. Android 7 emulator (API 24)

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

Стенд нужен для API 24/25. Приложения с `minSdk > 24` на нём не установятся
(`INSTALL_FAILED_OLDER_SDK`).

---

## 3. Архитектура

```text
Windows 11 (хост, Hyper-V)
├── 192.168.1.22:5555 ── LAN ── TV-бокс Ugoos AM3 (Android 9, armeabi-v7a)
│
├── Hyper-V VM "hvm-ubuntu" (Ubuntu 24.04, kernel 6.8, 4 vCPU, 2–8 ГБ RAM)
│   │   SSH: dev@172.18.237.39
│   │   Docker 29.x + containerd (autostart), /dev/kvm, /dev/binderfs
│   ├── redroid:13.0.0-latest  → 0.0.0.0:5556 → Android 13, 1920×1080, DPI 320
│   ├── redroid:13.0.0-latest  → 0.0.0.0:5555 → Android 13, 1080×1920, DPI 480
│   └── Android 7 emulator (AVD api24, headless)
│         emulator-5558 (console 5558 / adb 5559) → Windows 127.0.0.1:5559
│
└── SSH-туннели с хоста:
    -R 2080:127.0.0.1:2080  — прокси хоста внутри VM
    -L 5559:127.0.0.1:5559  — adb Android 7-эмулятора
```

---

## 4. Управление Hyper-V VM

Все команды Hyper-V выполняются в PowerShell **от администратора**. Без повышения прав
`Get-VM`, `Start-VM`, `Stop-VM`, `Get-VMNetworkAdapter` возвращают
«У вас нет разрешения на выполнение этой задачи».

### 4.1. Запуск и остановка

Двойной клик (UAC обязателен):

```text
C:\Users\User\hvm\start-vm.cmd   — запустить VM, подождать IP, записать в vm-ip.txt
C:\Users\User\hvm\stop-vm.cmd    — остановить VM
```

Вручную:

```powershell
Start-VM -Name hvm-ubuntu
Stop-VM  -Name hvm-ubuntu -Force

Get-VM -Name hvm-ubuntu | Select-Object Name, State, Generation, ProcessorCount, MemoryAssigned
```

> ⚠️ **Не запускать `run-setup.cmd`** — он может заменить диск на
> `hvm-ubuntu-fresh.vhdx`, если такой файл существует. Настроенный гость будет потерян.
> Для повседневного запуска — только `start-vm.cmd`.

### 4.2. Параметры и ресурсы

```powershell
# процессор и вложенная виртуализация
Get-VMProcessor -VMName hvm-ubuntu | Select-Object Count, ExposeVirtualizationExtensions

# память
Get-VMMemory -VMName hvm-ubuntu | Select-Object DynamicMemoryEnabled, StartupBytes, MinimumBytes, MaximumBytes

# прошивка
Get-VMFirmware -VMName hvm-ubuntu | Select-Object EnableSecureBoot

# диск
Get-VHD -Path "C:\Users\User\hvm\hvm-ubuntu.vhdx" | Select-Object Path, VhdType, Size, FileSize

# сетевой адаптер и IP
Get-VMNetworkAdapter -VMName hvm-ubuntu | Select-Object Name, SwitchName, IPAddresses
```

Модуль Hyper-V на хосте — legacy 2.0.0.0. Часть современных параметров отсутствует
или называется иначе; при сомнении сверяться с `Get-Command`/`Get-Help`.

### 4.3. IP гостя

IP выдаётся по DHCP и может измениться после перезапуска VM.

1. `C:\Users\User\hvm\vm-ip.txt` — последний известный IP (может быть пустым).
2. Сканирование порта 22 в `172.18.224.0/20`:

```powershell
$t = New-Object System.Collections.Concurrent.ConcurrentBag[string]
224..239 | % { $th=$_; 1..254 | % { $ip="172.18.$th.$_"; $c=New-Object Net.Sockets.TcpClient
  try { $a=$c.BeginConnect($ip,22,$null,$null); if($a.AsyncWaitHandle.WaitOne(400)){ $c.EndConnect($a); $t.Add($ip) } } catch {} finally { $c.Close() } } }
$t | Sort-Object
```

3. Либо `Get-NetNeighbor` по интерфейсу Default Switch — искать MAC `00:15:5d:…`.

### 4.4. Проверка гостя

```bash
ssh -i C:/Users/User/.ssh/hvm_key dev@172.18.237.39 \
  'uname -a; free -h; nproc; ls -l /dev/kvm /dev/binderfs; sudo docker ps'
```

Что должно быть в норме:

- `binder_linux` и `kvm_intel` загружены (`lsmod | grep -E 'binder|kvm'`);
- `/dev/kvm` доступен, `/dev/binderfs` смонтирован;
- `docker.service`, `containerd.service`, `ssh.service` в автозапуске;
- контейнеры в состоянии `Up`.

Полезное в госте:

```bash
sudo systemctl status docker --no-pager
sudo systemctl restart docker
journalctl -u docker -n 100 --no-pager
df -h /home/dev
sudo docker stats --no-stream
```

### 4.5. Контрольные точки и диск

Контрольные точки Hyper-V лежат рядом с диском как `hvm-ubuntu_<GUID>.avhdx`.

```powershell
Get-VMSnapshot -VMName hvm-ubuntu

# создать
Checkpoint-VM -Name hvm-ubuntu -SnapshotName before-change

# применить и удалить
Restore-VMSnapshot -VMName hvm-ubuntu -Name before-change -Confirm:$false
Remove-VMSnapshot -VMName hvm-ubuntu -Name before-change
```

> С контрольно-точечными `.avhdx` нужно быть осторожным: удаление/слияние необратимо.
> Перед операциями с диском VM лучше остановить.

### 4.6. Пересоздание и удаление VM

Пересоздание с нуля — `setup-vm.ps1` + `seed.iso`, подробности в
`C:\Users\User\hvm\android-redroid-runbook.md`. Скрипт меняет диск, поэтому
для повторного развёртывания, а не для повседневной работы.

Полное удаление (PowerShell от администратора):

```powershell
Stop-VM -Name hvm-ubuntu -Force -ErrorAction SilentlyContinue
Remove-VM -Name hvm-ubuntu -Force
Remove-VHD -Path "C:\Users\User\hvm\hvm-ubuntu.vhdx" -Force

Remove-Item -Recurse -Force "C:\Users\User\hvm"
Remove-Item -Force "C:\Users\User\.ssh\hvm_key", "C:\Users\User\.ssh\hvm_key.pub"
```

Если `Remove-VM` жалуется на подключённый диск:

```powershell
Get-VMHardDiskDrive -VMName hvm-ubuntu | Remove-VMHardDiskDrive
```

---

## 5. Управление redroid-контейнерами

Все команды — внутри VM по SSH:

```bash
ssh -i C:/Users/User/.ssh/hvm_key dev@172.18.237.39
```

### 5.1. Состояние

```bash
sudo docker ps -a --filter name=redroid
sudo docker inspect redroid-smartrutube --format \
  'Created={{.Created}} Restart={{.HostConfig.RestartPolicy.Name}} Cmd={{json .Config.Cmd}}'
sudo docker stats --no-stream
sudo docker logs --tail 100 redroid-smartrutube
```

### 5.2. Запуск / остановка / перезапуск

```bash
sudo docker stop redroid-smartrutube
sudo docker start redroid-smartrutube
sudo docker restart redroid-smartrutube
```

Оба контейнера имеют `restart unless-stopped` — после перезагрузки VM поднимаются сами.

### 5.3. Пересоздание

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

Старый контейнер:

```bash
sudo docker run -itd --restart unless-stopped --privileged \
  --name redroid \
  --device /dev/kvm:/dev/kvm \
  -v /dev/binderfs:/dev/binderfs \
  -v /home/dev/redroid-data:/data \
  -p 5555:5555 \
  redroid/redroid:13.0.0-latest \
  androidboot.redroid_width=1080 \
  androidboot.redroid_height=1920 \
  androidboot.redroid_dpi=480
```

### 5.4. Смена версии Android

Доступные теги `redroid/redroid`: `11.0.0-latest`, `12.0.0-latest`,
`13.0.0-latest` (текущий), `14.0.0-latest`. Есть также `erstt/redroid`
с ARM-транслятором Houdini.

```bash
sudo docker rm -f redroid-smartrutube
sudo docker pull redroid/redroid:14.0.0-latest
# запустить заново с новым тегом и теми же флагами
```

> ⚠️ Том `/data` не переносится между major-версиями Android. При переходе
> 13 → 14 (и вообще между major) очистить:
> `sudo rm -rf /home/dev/redroid-smartrutube-data/*`.
> Минорное обновление в рамках того же major данные сохраняет.

### 5.5. Данные и очистка

```bash
sudo du -sh /home/dev/redroid-data /home/dev/redroid-smartrutube-data

# полностью очистить данные стенда (необратимо)
sudo docker stop redroid-smartrutube
sudo rm -rf /home/dev/redroid-smartrutube-data/*
sudo docker start redroid-smartrutube
```

Удаление образа (освобождает ~2,87 ГБ):

```bash
sudo docker rmi redroid/redroid:13.0.0-latest
```

> `redroid` — это Android (bionic), `/bin/sh` внутри нет. Shell-команды в контейнере
> не работают, управление — только через adb.

---

## 6. Управление Android 7-эмулятором

Эмулятор запускается внутри VM и работает headless.

### 6.1. Запуск

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

Лог: `/tmp/emulator.log`.
Для ускорения повторных запусков можно убрать `-no-snapshot` и пользоваться снапшотами.

### 6.2. Остановка

```bash
ADB=~/android-sdk/platform-tools/adb
$ADB -s emulator-5558 emu kill

# если процесс завис
pkill -f qemu-system-x86_64-headless
```

### 6.3. Доступ с Windows

Эмулятор слушает adb на `127.0.0.1:5559` внутри VM. Проброс:

```bash
ssh -f -N -L 5559:127.0.0.1:5559 \
  -o ExitOnForwardFailure=yes \
  -o StrictHostKeyChecking=no \
  -i C:/Users/User/.ssh/hvm_key \
  dev@172.18.237.39

adb connect 127.0.0.1:5559
```

Правило портов: adb-порт = console-порт + 1
(`emulator-5554` → `5555`, `emulator-5558` → `5559`).
Serial может меняться — всегда проверять `adb devices`.

### 6.4. AVD и SDK-пакеты

```bash
# список AVD
~/android-sdk/emulator/emulator -list-avds

# создать заново
export ANDROID_SDK_ROOT=$HOME/android-sdk
echo no | ~/android-sdk/cmdline-tools/latest/bin/avdmanager create avd \
  -n api24 \
  -k "system-images;android-24;default;x86_64" \
  -d tv_1080p \
  --force

# удалить AVD
~/android-sdk/cmdline-tools/latest/bin/avdmanager delete avd -n api24
```

Для API 25: `system-images;android-25;default;x86_64` + отдельный AVD.

Проверка KVM:

```bash
~/android-sdk/emulator/emulator -accel-check
# KVM ... is installed and usable.
groups   # пользователь dev должен быть в группе kvm
```

Удаление system image / platform / emulator:

```bash
SDK=~/android-sdk/cmdline-tools/latest/bin/sdkmanager
$SDK --sdk_root=$HOME/android-sdk --uninstall "system-images;android-24;default;x86_64"
$SDK --sdk_root=$HOME/android-sdk --uninstall "platforms;android-24"
$SDK --sdk_root=$HOME/android-sdk --uninstall "emulator"
$SDK --sdk_root=$HOME/android-sdk --uninstall "platform-tools"
```

---

## 7. Подключение к стендам

```bash
# redroid-контейнеры
adb connect 172.18.237.39:5556
adb connect 172.18.237.39:5555
adb -s 172.18.237.39:5556 root      # после каждого нового connect
adb -s 172.18.237.39:5556 shell id  # uid=0(root)

# Android 7 emulator (после SSH-туннеля)
adb connect 127.0.0.1:5559

# TV-бокс
adb connect 192.168.1.22:5555

adb devices -l
```

Просмотр экрана:

```bash
scrcpy -s 192.168.1.22:5555    # TV-бокс
scrcpy -s 172.18.237.39:5555   # старый redroid
scrcpy -s 172.18.237.39:5556   # redroid-smartrutube
scrcpy -s 127.0.0.1:5559       # Android 7 emulator
```

> scrcpy может предупредить `adb reverse: more than one device/emulator` —
> он автоматически падает обратно на `adb forward`, зеркалирование работает.

---

## 8. Порты

| Порт | Где | Назначение |
|---|---|---|
| `22` | VM `172.18.237.39` | SSH в гостя |
| `2080` | Windows / VM | локальный HTTP-прокси для внешних сайтов |
| `5555` | VM | старый redroid (контейнер `redroid`) |
| `5555` | `192.168.1.22` | adb TV-бокса |
| `5556` | VM | redroid-smartrutube |
| `5558` | VM (loopback) | console-порт Android 7-эмулятора |
| `5559` | VM + Windows | adb Android 7-эмулятора (SSH-туннель) |

---

## 9. Сеть и прокси

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

## 10. Состояние на 09.11.2026

| Устройство | adb | Android | Состояние |
|---|---|---|---|
| Ugoos AM3 | `192.168.1.22:5555` | 9 | online |
| redroid-smartrutube | `172.18.237.39:5556` | 13 | online, контейнер Up 5 дней |
| redroid (старый) | `172.18.237.39:5555` | 13 | online, контейнер Up 6 дней |
| Android 7 emulator | `127.0.0.1:5559` | 7.0 | online |

VM: 4 vCPU, ~7,9 ГБ RAM (использовано ~5,5 ГБ), KVM и binderfs на месте.

---

## 11. Известные проблемы и ограничения

1. **Hyper-V-команды требуют администратора.** Без UAC — ошибка доступа.
   `start-vm.cmd` / `stop-vm.cmd` сами запрашивают повышение.

2. **IP гостя — DHCP.** `172.18.237.39` может измениться. `vm-ip.txt` может быть пустым —
   тогда сканировать подсеть или смотреть `Get-NetNeighbor`.

3. **`run-setup.cmd` опасен.** Может заменить диск на `hvm-ubuntu-fresh.vhdx`
   и потерять настроенного гостя.

4. **redroid в WSL2 невозможен.** В ядре WSL нет `CONFIG_ANDROID_BINDER_IPC` и модуля
   `binder_linux`; Docker Desktop и WSL делят одно ядро. Рабочий путь — только
   Hyper-V VM с обычным Ubuntu.

5. **Смена major-версии redroid требует очистки данных.** Том `/data` не переносится
   между Android 11/12/13/14.

6. **Serial эмулятора не фиксирован.** Может быть `emulator-5554`, `emulator-5558` и т.п.
   adb-порт = console-порт + 1.

7. **Android 7 emulator не подходит для приложений с minSdk > 24.**
   Установка завершится `INSTALL_FAILED_OLDER_SDK`.

8. **`adb root` нужен после каждого `adb connect`** для redroid. В Android-шелле
   `su` — toybox, `su -c` не работает.

9. **Память хоста — узкое место.** При большом числе IDE/браузеров VM может не
   стартовать с фиксированными 8 ГБ; поэтому память динамическая (2 → 8 ГБ).
   Эмулятор занимает ~1,5 ГБ RAM — если не нужен, лучше остановить.

10. **В старом redroid на 5555 остались данные прошлых запусков.** Очищать
    `/home/dev/redroid-data` только при необходимости — это необратимо.

11. **Прокси и RuTube.** Запросы к `rutube.ru` — только напрямую. Системный прокси
    `127.0.0.1:2080` искажает ответы live-каналов.

12. **Нет стендов для 4K/HDR, arm64 и API 25–27.** Бокс и redroid — 1080p SDR;
    реальных arm64-устройств нет.

---

## 12. Быстрая шпаргалка

```bash
# все устройства
adb devices -l

# Hyper-V VM (PowerShell от администратора)
Start-VM -Name hvm-ubuntu
Stop-VM  -Name hvm-ubuntu -Force
Get-VM -Name hvm-ubuntu | Select-Object Name, State, ProcessorCount, MemoryAssigned
Get-VMNetworkAdapter -VMName hvm-ubuntu | Select-Object -ExpandProperty IPAddresses

# гость
ssh -i C:/Users/User/.ssh/hvm_key dev@172.18.237.39
sudo docker ps -a --filter name=redroid
sudo docker restart redroid-smartrutube
sudo systemctl restart docker

# подключение
adb connect 172.18.237.39:5555
adb connect 172.18.237.39:5556
adb connect 127.0.0.1:5559
adb -s 172.18.237.39:5556 root

# эмулятор (внутри VM)
~/android-sdk/platform-tools/adb -s emulator-5558 emu kill
pkill -f qemu-system-x86_64-headless

# зеркало экрана
scrcpy -s 192.168.1.22:5555
scrcpy -s 172.18.237.39:5555
scrcpy -s 172.18.237.39:5556
scrcpy -s 127.0.0.1:5559
```

---

## 13. Ссылки

| Документ | Путь |
|---|---|
| Runbook VM/redroid | `C:\Users\User\hvm\android-redroid-runbook.md` |
| Подключение и смена версии redroid | `C:\Users\User\hvm\redroid-help-connect-versions.md` |
| Удаление стенда | `C:\Users\User\hvm\redroid-uninstall-cleanup.md` |
