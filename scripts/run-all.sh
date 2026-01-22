#!/bin/bash
set -a
if [ -f .env ]; then
  . .env
fi
set +a
(cd services/api-gateway && mvn spring-boot:run) &
(cd services/payment-service && mvn spring-boot:run) &
(cd services/notification-service && mvn spring-boot:run) &
(cd services/order-service && mvn spring-boot:run) &
(cd services/admin-service && mvn spring-boot:run) &
wait
