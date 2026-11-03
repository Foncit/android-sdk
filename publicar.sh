#!/bin/bash
# Foncit v2: genera el repositorio Maven del SDK en foncit-telefono/build/maven-foncit (SDK de Foncit + Linphone integrado).
set -e
cd "$(dirname "$0")"
LINPHONE=5.5.39
DESTINO=foncit-telefono/build/maven-foncit
VERSIONES=${VERSIONES:-/Users/andres/Archivos/Descargas/PHP/FONCIT/SDK/FONCIT/ANDROID}

VERSION=$(grep -m1 'version = "' foncit-telefono/build.gradle.kts | sed 's/.*version = "\(.*\)".*/\1/')

JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME

./gradlew :foncit-telefono:publishReleasePublicationToFoncitRepository --console=plain -q

# Linphone embebido directamente desde repo-linphone/ local
CARPETA=$DESTINO/org/linphone/linphone-sdk-android
mkdir -p "$CARPETA"
cp -R repo-linphone/org/linphone/linphone-sdk-android/* "$CARPETA/"

if [ -d "$VERSIONES" ]; then
    mkdir -p "$VERSIONES"
    cp -R $DESTINO/com $DESTINO/org "$VERSIONES/"
    echo "Listo: versión $VERSION en $VERSIONES"
fi

echo "Listo: versión $VERSION generada en $DESTINO"
echo "Sube al servidor: com/foncit/telefono/$VERSION y org/linphone/linphone-sdk-android/$LINPHONE"
