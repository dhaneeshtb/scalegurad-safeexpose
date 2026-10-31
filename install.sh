#!/bin/sh
# SafeExpose installer: downloads the prebuilt jar and puts a `safeexpose` command on your PATH. Needs Java 11+ and curl.
#   curl -fsSL https://raw.githubusercontent.com/dhaneeshtb/scalegurad-safeexpose/main/install.sh | sh
# Optional: SAFEEXPOSE_HOME (default ~/.safeexpose), SAFEEXPOSE_BIN (default ~/.local/bin), SAFEEXPOSE_URL (jar location).
set -eu

REPO="dhaneeshtb/scalegurad-safeexpose"
HOME_DIR="${SAFEEXPOSE_HOME:-$HOME/.safeexpose}"
BIN_DIR="${SAFEEXPOSE_BIN:-$HOME/.local/bin}"
URL="${SAFEEXPOSE_URL:-https://raw.githubusercontent.com/$REPO/main/dist/safeexpose.jar}"

command -v curl >/dev/null 2>&1 || { echo "safeexpose: curl is required" >&2; exit 1; }

java_help() {
  echo "  macOS:          brew install openjdk@17     (then follow brew's note to link it)"
  echo "  Debian/Ubuntu:  sudo apt install openjdk-17-jre"
  echo "  Fedora/RHEL:    sudo dnf install java-17-openjdk"
  echo "  Windows:        winget install EclipseAdoptium.Temurin.17.JRE"
  echo "  Anywhere:       https://adoptium.net"
}
java_major() { java -version 2>&1 | awk -F'[".]' '/version/ { print ($2 == "1") ? $3 : $2; exit }'; }

JAVA_OK=yes
if ! command -v java >/dev/null 2>&1; then
  JAVA_OK=no
  echo "WARNING: Java was not found. SafeExpose needs Java 11 or newer. Install it, for example:" >&2
  java_help >&2
elif [ "$(java_major)" -lt 11 ] 2>/dev/null; then
  JAVA_OK=no
  echo "WARNING: Java $(java_major) found, but SafeExpose needs Java 11 or newer. Install a newer one, for example:" >&2
  java_help >&2
fi

say() { printf '==> %s\n' "$*"; }

[ "$JAVA_OK" = yes ] && say "Java $(java_major) found"
mkdir -p "$HOME_DIR" "$BIN_DIR"
tmp="$HOME_DIR/safeexpose.jar.part"
say "Downloading $URL"
if [ -t 2 ]; then curl -fL --progress-bar "$URL" -o "$tmp"; else curl -fsSL "$URL" -o "$tmp"; fi   # a progress bar when run from a terminal
say "Downloaded $(wc -c < "$tmp" | tr -d ' ') bytes"

# verify the download when a checksum is published next to the jar
say "Verifying checksum"
if sum="$(curl -fsSL "$URL.sha256" 2>/dev/null)"; then
  want="$(printf '%s' "$sum" | awk '{print $1}')"
  if command -v sha256sum >/dev/null 2>&1; then got="$(sha256sum "$tmp" | awk '{print $1}')"; else got="$(shasum -a 256 "$tmp" | awk '{print $1}')"; fi
  [ "$want" = "$got" ] || { rm -f "$tmp"; echo "safeexpose: checksum mismatch, not installed" >&2; exit 1; }
  say "Checksum OK"
else
  say "No checksum published, skipping verification"
fi
mv "$tmp" "$HOME_DIR/safeexpose.jar"
say "Installing the safeexpose command"

cat > "$BIN_DIR/safeexpose" <<EOF
#!/bin/sh
if ! command -v java >/dev/null 2>&1; then
  echo "safeexpose: Java 11 or newer is required but was not found. Install it, for example:" >&2
  echo "  macOS: brew install openjdk@17   Debian/Ubuntu: sudo apt install openjdk-17-jre" >&2
  echo "  Fedora: sudo dnf install java-17-openjdk   Windows: winget install EclipseAdoptium.Temurin.17.JRE" >&2
  echo "  or https://adoptium.net" >&2
  exit 1
fi
exec java -jar "$HOME_DIR/safeexpose.jar" "\$@"
EOF
chmod +x "$BIN_DIR/safeexpose"

say "Installed: $BIN_DIR/safeexpose  (jar: $HOME_DIR/safeexpose.jar)"
case ":$PATH:" in
  *":$BIN_DIR:"*) ;;
  *) echo "Add it to your PATH:  export PATH=\"$BIN_DIR:\$PATH\"" ;;
esac
[ "$JAVA_OK" = yes ] && echo "Try:  safeexpose 8080" || echo "Installed, but install Java 11+ (see above) before running safeexpose."
