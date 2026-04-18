FROM maven:3.9.6-eclipse-temurin-17

# Native libraries required by JavaFX's font and text pipeline under the
# Monocle headless glass platform. Without pango + freetype the GlyphLayout
# class cannot initialize, and any Stage.show() or Scene size computation
# throws UnsatisfiedLinkError ("no javafx_font_pango in java.library.path").
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libpango-1.0-0 \
        libpangoft2-1.0-0 \
        libfreetype6 \
        libfontconfig1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml ./
RUN mvn -B dependency:go-offline -q || true

COPY src ./src

CMD ["mvn", "-B", "verify"]
