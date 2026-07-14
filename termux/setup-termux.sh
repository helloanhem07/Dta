#!/data/data/com.termux/files/usr/bin/bash
set -e
mkdir -p "$HOME/.termux"
touch "$HOME/.termux/termux.properties"
sed -i '/^allow-external-apps=/d' "$HOME/.termux/termux.properties" || true
echo 'allow-external-apps=true' >> "$HOME/.termux/termux.properties"
termux-reload-settings || true
termux-setup-storage || true
pkg update -y
pkg install -y nodejs python git unzip
mkdir -p "$HOME/bothost"
echo "Hoàn tất. Hãy cấp quyền RUN_COMMAND cho BotHost Android trong Cài đặt Android."
