#!/bin/bash
# Foncit v2: genera el SDK (un solo .aar con Linphone dentro) y lo deja listo para subir a git.
# Uso:  ./publicar.sh            -> compila y prueba el repositorio Maven local
#       ./publicar.sh --tag      -> además crea y sube el tag con la versión (JitPack lo compila solo)
set -e
cd "$(dirname "$0")"

DESTINO=foncit-telefono/build/maven-foncit
VERSION=$(grep -m1 'version = "' foncit-telefono/build.gradle.kts | sed 's/.*version = "\(.*\)".*/\1/')

JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME

if git rev-parse "$VERSION" >/dev/null 2>&1; then
    echo "La versión $VERSION ya existe como tag. Sube el número de versión en foncit-telefono/build.gradle.kts."
    exit 1
fi

./gradlew :foncit-telefono:publishReleasePublicationToFoncitRepository --console=plain -q

echo "Listo: com.foncit:foncit-telefono:$VERSION generado en $DESTINO"
echo "Comprobación: el .aar debe traer Linphone dentro (libs/classes.jar y jni/)."
unzip -l "$DESTINO/com/foncit/foncit-telefono/$VERSION/foncit-telefono-$VERSION.aar" | grep -E "jni/|libs/" | head -8 || true

if [ "$1" = "--tag" ]; then
    git add -A
    git commit -m "Foncit Teléfono $VERSION" || true
    git push origin HEAD
    git tag "$VERSION"
    git push origin "$VERSION"
    echo "Tag $VERSION subido. Usa: implementation(\"com.github.Foncit.android-sdk:foncit-telefono:$VERSION\")"
fi
