upgrade-db:
	docker compose -f .docker/dev-compose.yml run --rm flyway -url=jdbc:postgresql://ledgee-db:5432/ledgee -user=postgres -password=mysecretpassword migrate
.PHONY: upgrade-db