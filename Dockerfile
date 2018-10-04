FROM navikt/java:11

COPY build/install/ehandelkanal-2/bin/ehandelkanal-2 bin/app
COPY build/install/ehandelkanal-2/lib lib/
ENV JAVA_OPTS="-Dcom.ibm.msg.client.commonservices.log.status=OFF -XX:+UseStringDeduplication -XshowSettings:vm -Dlogback.configurationFile=logback-remote.xml"
