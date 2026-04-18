FROM maven:3.9.6-eclipse-temurin-17

# Native libraries required by JavaFX's font/text pipeline under the Monocle
# headless glass platform used by the test suite. Plus the tooling required
# to cross-build a Windows .exe from this Linux container:
#   - binutils-mingw-w64: launch4j needs mingw's windres/ld to produce the
#     PE32+ launcher stub.
#   - wget/unzip/zip: fetch the Windows JDK + JavaFX jmods, pack the artifact.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libpango-1.0-0 \
        libpangoft2-1.0-0 \
        libfreetype6 \
        libfontconfig1 \
        wget \
        unzip \
        zip \
        binutils-mingw-w64-x86-64 \
        binutils-mingw-w64-i686 \
    && rm -rf /var/lib/apt/lists/*

# --- Windows JDK 17 (Temurin) ---------------------------------------------
# Extracted just for its jmods/ directory — jlink can cross-target a Windows
# runtime image from a Linux host when the module path points at Windows
# jmods.
ENV WIN_JDK_URL=https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip
RUN mkdir -p /opt/win-jdk \
    && wget -q -O /tmp/win-jdk.zip "$WIN_JDK_URL" \
    && unzip -q /tmp/win-jdk.zip -d /opt/win-jdk \
    && rm /tmp/win-jdk.zip

# --- JavaFX 17 Windows jmods (Gluon) --------------------------------------
ENV JFX_URL=https://download2.gluonhq.com/openjfx/17.0.6/openjfx-17.0.6_windows-x64_bin-jmods.zip
RUN wget -q -O /tmp/jfx.zip "$JFX_URL" \
    && unzip -q /tmp/jfx.zip -d /opt/jfx-win \
    && rm /tmp/jfx.zip

# --- launch4j (builds Windows .exe stubs from Linux) ----------------------
ENV L4J_URL=https://downloads.sourceforge.net/project/launch4j/launch4j-3/3.50/launch4j-3.50-linux-x64.tgz
RUN wget -q -O /tmp/l4j.tgz "$L4J_URL" \
    && tar -xzf /tmp/l4j.tgz -C /opt \
    && rm /tmp/l4j.tgz

WORKDIR /app

COPY pom.xml ./
RUN mvn -B dependency:go-offline -q || true

COPY src ./src
COPY scripts/build-exe.sh /usr/local/bin/build-exe.sh
RUN chmod +x /usr/local/bin/build-exe.sh

CMD ["mvn", "-B", "verify"]
