FROM phusion/baseimage

CMD ["/sbin/my_init"]

#install JDK
RUN apt update && apt install -y --no-install-recommends \
    default-jre \
    && apt clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

ADD competition-serv-service/target/competition-serv-service-1.0-SNAPSHOT.jar /app/application.jar