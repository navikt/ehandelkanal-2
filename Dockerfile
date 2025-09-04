FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-11
WORKDIR /app

COPY build/libs/*.jar /app/app.jar

COPY src/main/resources/logback-remote.xml /app/logback-remote.xml

ENV JAVA_TOOL_OPTIONS="-Dcom.ibm.msg.client.commonservices.log.status=OFF -XX:+UseStringDeduplication -XX:MaxRAMPercentage=75 -Dlogback.configurationFile=/app/logback-remote.xml"

ENTRYPOINT ["java","-jar","/app/app.jar"]
