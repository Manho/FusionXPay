#!/bin/bash
(cd services/api-gateway && mvn spring-boot:run) &
(cd services/payment-service && mvn spring-boot:run) &
(cd services/notification-service && mvn spring-boot:run) &
(cd services/order-service && mvn spring-boot:run) &
wait
