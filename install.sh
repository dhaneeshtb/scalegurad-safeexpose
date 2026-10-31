#!/bin/sh
# SafeExpose installer: downloads the prebuilt jar and puts a `safeexpose` command on your PATH. Needs Java 11+ and curl.
#   curl -fsSL https://raw.githubusercontent.com/dhaneeshtb/scalegurad-safeexpose/main/install.sh | sh
# Optional: SAFEEXPOSE_HOME (default ~/.safeexpose), SAFEEXPOSE_BIN (default ~/.local/bin), SAFEEXPOSE_URL (jar location).
set -eu

REPO="dhaneeshtb/scalegurad-safeexpose"
HOME_DIR="${SAFEEXPOSE_HOME:-$HOME/.safeexpose}"
BIN_DIR="${SAFEEXPOSE_BIN:-$HOME/.local/bin}"
URL="${SAFEEXPOSE_URL:-https://raw.githubusercontent.com/$REPO/main/dist/safeexpose.jar}"

command -v java >/dev/null 2>&1 || { echo "safeexpose: Java 11 or newer is required (https://adoptium.net)" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "safeexpose: curl is required" >&2; exit 1; }

mkdir -p "$HOME_DIR" "$BIN_DIR"
tmp="$HOME_DIR/safeexpose.jar.part"
echo "Downloading SafeExpose..."
curl -fsSL "$URL" -o "$tmp"

# verify the download when a checksum is published next to the jar
if sum="$(curl -fsSL "$URL.sha256" 2>/dev/null)"; then
  want="$(printf '%s' "$sum" | awk '{print $1}')"
  if command -v sha256sum >/dev/null 2>&1; then got="$(sha256sum "$tmp" | awk '{print $1}')"; else got="$(shasum -a 256 "$tmp" | awk '{print $1}')"; fi
  [ "$want" = "$got" ] || { rm -f "$tmp"; echo "safeexpose: checksum mismatch, not installed" >&2; exit 1; }
fi
mv "$tmp" "$HOME_DIR/safeexpose.jar"

cat > "$BIN_DIR/safeexpose" <<EOF
#!/bin/sh
exec java -jar "$HOME_DIR/safeexpose.jar" "\$@"
EOF
chmod +x "$BIN_DIR/safeexpose"

echo "Installed: $BIN_DIR/safeexpose"
case ":$PATH:" in
  *":$BIN_DIR:"*) ;;
  *) echo "Add it to your PATH:  export PATH=\"$BIN_DIR:\$PATH\"" ;;
esac
echo "Try:  safeexpose 8080"
