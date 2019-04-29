FROM phusion/baseimage

CMD ["/sbin/my_init"]

#install JDK
RUN add-apt-repository ppa:webupd8team/java && apt-get update && apt-get install -y --no-install-recommends \
    oracle-java8-installer \
    && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

ADD competition-serv-service/target/competition-serv-service-1.0-SNAPSHOT.jar /app/application.jar