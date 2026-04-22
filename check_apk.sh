#!/bin/bash
echo "=== Verificando APK ==="
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "APK encontrado localmente"
    unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "XHttpVpnService|MainActivity" | head -5
else
    echo "APK local não encontrado"
fi
