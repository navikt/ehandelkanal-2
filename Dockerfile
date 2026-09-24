FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21

COPY build/libs/*.jar /app/app.jar

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-Dcom.ibm.msg.client.commonservices.log.status=OFF -XX:+UseStringDeduplication -XshowSettings:vm -XX:MaxRAMPercentage=75 -Dlogback.configurationFile=logback-remote.xml -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"

CMD ["-jar", "/app/app.jar"]

