# BotHost Android

Ứng dụng Android quản lý và chạy bot Node.js/Python thông qua Termux `RUN_COMMAND` Intent.

## Chức năng V1

- Dashboard CPU, RAM, bộ nhớ và độ trễ mạng.
- Biểu đồ lịch sử 2 phút khi app đang mở.
- Upload project `.zip` bằng Android Storage Access Framework.
- Import project vào `~/bothost/<id>` trong Termux.
- Profile Node.js, Python hoặc lệnh tùy chỉnh.
- Cài thư viện, Start, Stop, Restart.
- Watchdog tự chạy lại bot sau 5 giây nếu bot crash.
- Log riêng tại `.bothost/bot.log`.
- Đọc và chỉnh sửa `.env`.
- Lưu danh sách bot trong SharedPreferences của ứng dụng.

## Yêu cầu

- Android 8.0 trở lên; luồng upload được tối ưu cho Android 10+.
- Termux bản chính thức từ F-Droid hoặc GitHub, không nên dùng bản Play Store bị lược bỏ tính năng.
- Termux phiên bản hỗ trợ `RUN_COMMAND`, nên dùng bản mới.

## Thiết lập Termux

Chạy một lần trong Termux:

```bash
mkdir -p ~/.termux
sed -i '/^allow-external-apps=/d' ~/.termux/termux.properties 2>/dev/null || true
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
termux-setup-storage
pkg update -y
pkg install -y nodejs python git unzip
```

Sau khi cài APK:

1. Mở **Cài đặt Android → Ứng dụng → BotHost Android → Quyền**.
2. Trong **Quyền bổ sung**, bật **Run commands in Termux environment**.
3. Tắt tối ưu pin cho Termux và BotHost Android.
4. Mở Termux ít nhất một lần trước khi gửi lệnh đầu tiên.

## Build bằng Android Studio

1. Mở thư mục project bằng Android Studio.
2. Chờ Gradle Sync.
3. Chọn **Build → Build APK(s)**.
4. APK debug ở `app/build/outputs/apk/debug/app-debug.apk`.

Project dùng:

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Gradle 8.10.2
- Compile/Target SDK 35
- Java 17

## Build bằng GitHub Actions

Push project lên GitHub. Workflow `.github/workflows/build-apk.yml` sẽ build `app-debug.apk` và cho tải ở mục **Actions → Artifacts**.

## Cấu trúc bot ZIP

Node.js:

```text
my-bot.zip
├── package.json
├── index.js
└── .env.example
```

Cấu hình gợi ý:

```text
Lệnh cài: npm install
Lệnh chạy: node index.js
```

Python:

```text
my-bot.zip
├── requirements.txt
├── bot.py
└── .env.example
```

Cấu hình gợi ý:

```text
Lệnh cài: python -m pip install -r requirements.txt
Lệnh chạy: python bot.py
```

Nếu ZIP chỉ chứa một thư mục gốc, app tự đưa nội dung thư mục đó lên thư mục project chính.

## Cơ chế chạy

BotHost gửi lệnh đến `com.termux.app.RunCommandService`. Khi Start, app tạo:

```text
.bothost/
├── runner.sh
├── start-command.sh
├── watchdog.pid
├── bot.log
└── last-exit-code
```

`runner.sh` chạy command của bot. Khi bật Auto Restart, bot được chạy lại sau 5 giây nếu process thoát.

## Giới hạn hiện tại

- Dashboard chỉ cập nhật khi BotHost đang mở; bot vẫn chạy trong Termux khi đóng panel.
- Trạng thái “Online” dựa trên PID watchdog, không kiểm tra bot đã đăng nhập Discord/Telegram thành công hay chưa.
- Android có thể kill Termux vì tiết kiệm pin hoặc giới hạn nền.
- V1 chưa có panel web truy cập từ máy khác, đăng nhập PIN, backup và quản lý file đầy đủ.
- Chưa kiểm thử trên mọi hãng Android; ROM Xiaomi/OPPO/Vivo thường cần cho phép tự khởi động và chạy nền thủ công.

## Bảo mật

- Không nhúng token bot vào source APK.
- `.env` được lưu trong thư mục riêng của Termux và đặt quyền `600` khi lưu từ app.
- Không chạy ZIP không tin cậy: lệnh cài và lệnh start có toàn quyền trong môi trường Termux của bạn.
