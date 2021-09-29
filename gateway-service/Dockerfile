FROM phusion/baseimage

CMD ["/sbin/my_init"]

#install JDK
RUN apt update && apt install -y --no-install-recommends \
    default-jre \
    && apt clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

COPY app.jar /app/
