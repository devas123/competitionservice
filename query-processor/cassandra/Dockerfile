FROM cassandra:latest

COPY schema.cql docker-entrypoint-initdb.d/

COPY docker-entrypoint.sh /docker-entrypoint.sh
COPY init_db.sh /init_db.sh

RUN ls -la *.sh; chmod +x *.sh; ls -la *.sh

ENTRYPOINT ["/init_db.sh"]

CMD ["cassandra", "-f"]