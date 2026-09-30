#!/usr/bin/env bash
# Røyktest av image: JDK_JAVA_OPTIONS, truststore, Java-versjon og tidssone.
# Bruk: .github/smoke/smoke-test-image.sh <image>
set -euo pipefail

IMAGE="${1:?Bruk: $0 <image>}"
SMOKE_DIR="$(cd "$(dirname "$0")" && pwd)"
CLASSES_DIR="$(pwd)/build/smoke-test"

mkdir -p "$CLASSES_DIR"
javac --release 21 -d "$CLASSES_DIR" "$SMOKE_DIR/TzCheck.java"

failed=0
check() {
    local description="$1" pattern="$2" output="$3"
    if grep -qE "$pattern" <<< "$output"; then
        echo "OK:   $description"
    else
        echo "FEIL: $description (fant ikke '$pattern')"
        failed=1
    fi
}

properties="$(docker run --rm "$IMAGE" -XshowSettings:properties -version 2>&1)"
check "JDK_JAVA_OPTIONS blir lest" "Picked up JDK_JAVA_OPTIONS" "$properties"
check "truststore er Nais sin cacerts" "javax\.net\.ssl\.trustStore = /etc/ssl/certs/java/cacerts" "$properties"
check "Java 21" "java\.version = 21\." "$properties"

# user.timezone settes først når noe slår opp tidssonen, så vi spør JVM-en direkte.
timezone="$(docker run --rm -v "$CLASSES_DIR:/smoke:ro" "$IMAGE" -cp /smoke TzCheck 2>&1)"
check "tidssone er Europe/Oslo" "^zone=Europe/Oslo$" "$timezone"

if [ "$failed" -ne 0 ]; then
    echo "$properties"
    echo "$timezone"
    exit 1
fi
