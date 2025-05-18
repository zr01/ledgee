FROM flyway/flyway:10-alpine

COPY --chown=flyway:nogroup .docker/db/flyway/sql /flyway/sql

ENTRYPOINT ["flyway/flyway", "-url=$MIGRATE_DB_URL", "-user=$MIGRATE_DB_USER", "-password=$MIGRATE_DB_PWD", "migrate"]