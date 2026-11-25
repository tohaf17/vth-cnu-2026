# Valorant Tactical Hub — Monorepo Automation Makefile
GRADLE_VERSION := 9.9.1

# Keep Gradle's daemon, wrapper and caches inside this project on Windows too.
# This avoids permission conflicts with a locked global C:\\Users\\...\\.gradle directory.
export GRADLE_USER_HOME := $(CURDIR)/.gradle-user

.PHONY: setup-gradle init up-db down-db test build build-docker run-roster run-analytics run-rank dev-frontend dev-mock dev-api deploy-cloud deploy-frontend

# --- Підготовка проєкту та залежностей ---
setup-gradle:
	gradle wrapper --gradle-version $(GRADLE_VERSION) --distribution-type bin
	chmod +x gradlew

init:
	./gradlew classes testClasses
	npm --prefix frontend install
	npm --prefix open-api install

# --- Локальна розробка (Завдання після Лекції 2) ---
up-db:
	docker compose up -d

down-db:
	docker compose down

test:
	./gradlew test
	npm --prefix frontend run test

build:
	./gradlew clean build
	npm --prefix frontend run test

run-roster: up-db
	./gradlew :roster-service:run

run-analytics: up-db
	./gradlew :match-analytics-service:run

run-rank: up-db
	./gradlew :rank-calculator-service:run

dev-frontend:
	npm --prefix frontend run dev

dev-mock:
	npm --prefix frontend run dev:mock

dev-api:
	npm --prefix open-api run dev

# --- Збірка Docker-образів та деплой у Google Cloud Run + Neon ---
build-docker:
	@test -f .env.cloud || (echo "Помилка: створіть .env.cloud на основі .env.cloud.example" && exit 1)
	@set -a && . ./.env.cloud && set +a && ./gradlew jib

deploy-cloud: build-docker
	@set -a && . ./.env.cloud && set +a && \
	echo "1/3 Деплой rank-calculator-service у Cloud Run..." && \
	gcloud run deploy rank-calculator-service \
		--image $$GCP_REGION-docker.pkg.dev/$$GCP_PROJECT_ID/valorant-repo/rank-calculator-service:latest \
		--region $$GCP_REGION --platform managed --allow-unauthenticated \
		--set-env-vars="VTH_ENVIRONMENT=cloud" && \
	RANK_URL=$$(gcloud run services describe rank-calculator-service --region $$GCP_REGION --format='value(status.url)') && \
	echo "2/3 Деплой roster-service у Cloud Run (Neon roster_db)..." && \
	gcloud run deploy roster-service \
		--image $$GCP_REGION-docker.pkg.dev/$$GCP_PROJECT_ID/valorant-repo/roster-service:latest \
		--region $$GCP_REGION --platform managed --allow-unauthenticated \
		--set-env-vars="VTH_ENVIRONMENT=cloud,JDBC_DATABASE_URL=$$NEON_ROSTER_JDBC_URL,DB_USER=$$NEON_DB_USER,DB_PASSWORD=$$NEON_DB_PASSWORD" && \
	ROSTER_URL=$$(gcloud run services describe roster-service --region $$GCP_REGION --format='value(status.url)') && \
	echo "3/3 Деплой match-analytics-service у Cloud Run (Neon analytics_db)..." && \
	gcloud run deploy match-analytics-service \
		--image $$GCP_REGION-docker.pkg.dev/$$GCP_PROJECT_ID/valorant-repo/match-analytics-service:latest \
		--region $$GCP_REGION --platform managed --allow-unauthenticated \
		--set-env-vars="VTH_ENVIRONMENT=cloud,JDBC_DATABASE_URL=$$NEON_ANALYTICS_JDBC_URL,DB_USER=$$NEON_DB_USER,DB_PASSWORD=$$NEON_DB_PASSWORD,RANK_CALCULATOR_URL=$$RANK_URL,ROSTER_SERVICE_URL=$$ROSTER_URL" && \
	ANALYTICS_URL=$$(gcloud run services describe match-analytics-service --region $$GCP_REGION --format='value(status.url)') && \
	echo "Готово! URL сервісів для frontend/.env.cloud:" && \
	echo "VITE_ROSTER_SERVICE_URL=$$ROSTER_URL" && \
	echo "VITE_ANALYTICS_SERVICE_URL=$$ANALYTICS_URL"

deploy-frontend:
	npm --prefix frontend run deploy
