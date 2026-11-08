#!/data/data/com.termux/files/usr/bin/bash
# Push DuongKeyboard từ bộ nhớ trong lên GitHub bằng Termux
# Cách chạy:  bash /storage/emulated/0/DuongKeyboard/push.sh
set -e

DIR="/storage/emulated/0/DuongKeyboard"
REPO="DuongKeyboard"

cd "$DIR"

# Bộ nhớ trong không hỗ trợ quyền file kiểu Linux -> tắt kiểm tra
git config --global --add safe.directory "$DIR"

if [ -z "$(git config --global user.name)" ]; then
  read -p "Tên GitHub của bạn: " NAME
  read -p "Email GitHub: " MAIL
  git config --global user.name "$NAME"
  git config --global user.email "$MAIL"
fi

if [ ! -d .git ]; then
  git init -b main
  git config core.fileMode false
fi

git add .
git commit -m "${1:-Cập nhật bàn phím DuongKB}" || echo "Không có gì mới để commit"

if ! git remote | grep -q origin; then
  if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
    gh repo create "$REPO" --public --source=. --remote=origin
  else
    read -p "Username GitHub: " GH_USER
    git remote add origin "https://github.com/$GH_USER/$REPO.git"
    echo ">> Nhớ tạo repo trống tên '$REPO' trên github.com trước."
    echo ">> Khi hỏi Password, dán Personal Access Token (không phải mật khẩu)."
  fi
fi

git push -u origin main
echo "Xong! Vào tab Actions trên GitHub để tải file APK."
