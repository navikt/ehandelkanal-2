FROM eclipse-temurin:21-jre-jammy

COPY build/libs/*.jar app.jar
COPY --chmod=755 entrypoint.sh /entrypoint.sh
ENV JAVA_OPTS="-Dcom.ibm.msg.client.commonservices.log.status=OFF -XX:+UseStringDeduplication -XshowSettings:vm -XX:MaxRAMPercentage=75 -Dlogback.configurationFile=logback-remote.xml"

ENTRYPOINT ["/entrypoint.sh"]
