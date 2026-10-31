#!/bin/sh
# Rebuild dist/safeexpose.jar (+ checksum) from the source. Commit and push dist/ to publish a new version;
# install.sh downloads it from the main branch.
set -eu
cd "$(dirname "$0")"
mvn -q clean package
mkdir -p dist
cp target/safeexpose.jar dist/safeexpose.jar
(cd dist && { shasum -a 256 safeexpose.jar 2>/dev/null || sha256sum safeexpose.jar; } > safeexpose.jar.sha256)
echo "Built dist/safeexpose.jar:"; cat dist/safeexpose.jar.sha256
echo "Now: git add dist && git commit -m 'release' && git push"
