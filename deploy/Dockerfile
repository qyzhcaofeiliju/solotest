FROM 192.168.1.252/qyzh-v1/tomcate86
ADD target/solo.war /tmp
RUN unzip -q /tmp/solo.war -d /usr/local/tomcat/webapps/ROOT
EXPOSE 8080
CMD ["./bin/catalina.sh", "run"]
