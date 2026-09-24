#!/usr/bin/env sh
set -e

VAULT_DIR=/var/run/secrets/nais.io/vault

if [ -d "$VAULT_DIR" ]; then
    for FILE in "$VAULT_DIR"/*.env; do
        [ -f "$FILE" ] || continue
        while IFS= read -r line || [ -n "$line" ]; do
            case "$line" in
                *=*) ;;
                *) continue ;;
            esac
            _key=${line%%=*}
            _val=$(echo "${line#*=}" | sed -e "s/^['\"]//" -e "s/['\"]$//")
            echo "- exporting $_key"
            export "$_key"="$_val"
        done < "$FILE"
    done
fi

if [ -r "${NAV_TRUSTSTORE_PATH}" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStore=${NAV_TRUSTSTORE_PATH} -Djavax.net.ssl.trustStorePassword=${NAV_TRUSTSTORE_PASSWORD}"
fi

exec java ${JAVA_OPTS} -jar /app.jar "$@"
