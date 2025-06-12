upgrade-db:
	docker compose -f .docker/dev-compose.yml run --rm flyway -url=jdbc:postgresql://ledgee-db:5432/ledgee -user=postgres -password=mysecretpassword migrate
.PHONY: upgrade-db

run-test:
	k6 run ./ledger-core-load-test/k6-test.js
.PHONY: run-test