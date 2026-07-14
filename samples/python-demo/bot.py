import time
from datetime import datetime

print("BotHost Python demo đã khởi động", flush=True)
while True:
    print(datetime.now().isoformat(), "Bot vẫn hoạt động", flush=True)
    time.sleep(10)
