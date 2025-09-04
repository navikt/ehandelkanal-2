FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-11

COPY build/libs/*.jar app.jar
ENV JAVA_OPTS="-Dcom.ibm.msg.client.commonservices.log.status=OFF -XX:+UseStringDeduplication -XshowSettings:vm -XX:MaxRAMPercentage=75 -Dlogback.configurationFile=logback-remote.xml"
