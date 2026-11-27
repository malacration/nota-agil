#!/usr/bin/env bash
set -Eeuo pipefail
cd /workspace/app

app_pid=''
build_pid=''

stop_app() {
    if [[ -n "$app_pid" ]]; then
        kill -TERM "$app_pid" 2>/dev/null || true
        wait "$app_pid" 2>/dev/null || true
        app_pid=''
    fi
}

shutdown() {
    trap - TERM INT
    if [[ -n "$build_pid" ]]; then
        kill -TERM "$build_pid" 2>/dev/null || true
        wait "$build_pid" 2>/dev/null || true
    fi
    stop_app
    exit 0
}
trap shutdown TERM INT

snapshot() {
    # Somente entradas: nao observa build, caches nem stubs gerados.
    # Inclui /config para recarregar tambem o properties externo de HMG.
    {
        find src/main /config -type f -print0
        printf '%s\0' build.gradle.kts settings.gradle.kts gradlew gradle/wrapper/gradle-wrapper.properties
    } | sort -z | xargs -0 sha256sum | sha256sum | cut -d ' ' -f 1
}

previous=''
while true; do
    current=$(snapshot)
    if [[ "$current" != "$previous" ]]; then
        previous="$current"
        echo '[dev] Alteracao detectada. Compilando o backend...'
        # Uma compilacao por vez. O servidor anterior atende ate o build concluir.
        bash gradlew bootJar --console=plain --max-workers=2 &
        build_pid=$!
        if wait "$build_pid"; then
            build_pid=''
            stop_app
            # O Gradle pode sobrescrever o build no proximo save.
            cp build/libs/nota.agil-0.0.1-SNAPSHOT.jar /tmp/nota-agil-dev.jar
            echo '[dev] Build concluido. Iniciando o backend atualizado...'
            java -jar /tmp/nota-agil-dev.jar "$@" &
            app_pid=$!
        else
            build_pid=''
            echo '[dev] Build falhou. Corrija e salve um arquivo para tentar novamente.' >&2
        fi
    fi
    if [[ -n "$app_pid" ]] && ! kill -0 "$app_pid" 2>/dev/null; then
        echo '[dev] Backend encerrou. Corrija e salve um arquivo ou reinicie o servico.' >&2
        app_pid=''
    fi
    sleep 1 &
    wait $!
done
