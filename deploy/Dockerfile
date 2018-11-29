FROM 192.168.1.252/qyzh-v1/tomcat-85
ADD target/solo.war /tmp
RUN yum install -y unzip \
    && unzip -q /tmp/solo.war -d /usr/local/tomcat/webapps/ROOT
EXPOSE 8080
CMD ["./bin/catalina.sh", "run"]
