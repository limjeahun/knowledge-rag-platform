FROM eclipse-temurin:21-jre-alpine

ARG JENA_VERSION=6.1.0
ARG FUSEKI_SHA1=b59760331e53f662a7a5ce0481bdf3e58611ee94
ARG FUSEKI_JAR=jena-fuseki-server-${JENA_VERSION}.jar

RUN apk add --no-cache curl \
    && mkdir -p /opt/fuseki /fuseki/databases \
    && curl --fail --location --silent --show-error \
        "https://repo1.maven.org/maven2/org/apache/jena/jena-fuseki-server/${JENA_VERSION}/${FUSEKI_JAR}" \
        --output "/opt/fuseki/${FUSEKI_JAR}" \
    && echo "${FUSEKI_SHA1}  /opt/fuseki/${FUSEKI_JAR}" | sha1sum -c \
    && addgroup -g 1000 fuseki \
    && adduser -D -H -u 1000 -G fuseki fuseki \
    && chown -R fuseki:fuseki /opt/fuseki /fuseki

COPY --chown=fuseki:fuseki entrypoint.sh /opt/fuseki/entrypoint.sh
RUN chmod 755 /opt/fuseki/entrypoint.sh

USER fuseki
WORKDIR /fuseki

ENV FUSEKI_JAR=/opt/fuseki/${FUSEKI_JAR}
ENV JAVA_OPTIONS="-Xms256m -Xmx1024m"

EXPOSE 3030

ENTRYPOINT ["/opt/fuseki/entrypoint.sh"]

