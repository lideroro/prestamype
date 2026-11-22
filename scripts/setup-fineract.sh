#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://localhost:8443/fineract-provider/api/v1"
AUTH="mifos:password"
TENANT="default"

api_call() {
  local method=$1
  local endpoint=$2
  local data=${3:-}

  if [ -n "$data" ]; then
    curl -s -k -X "$method" "${BASE_URL}${endpoint}" \
      -u "$AUTH" \
      -H "Fineract-Platform-TenantId: $TENANT" \
      -H "Content-Type: application/json" \
      -d "$data"
  else
    curl -s -k -X "$method" "${BASE_URL}${endpoint}" \
      -u "$AUTH" \
      -H "Fineract-Platform-TenantId: $TENANT"
  fi
}

echo "1. Habilitando moneda PEN..."
api_call PUT "/currencies" '{"currencies": ["PEN", "USD"]}' > /dev/null

echo "2. Configurando días hábiles para conservar fechas exactas (sin desplazar vencimientos)..."
api_call PUT "/workingdays" '{
  "recurrence": "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR,SA,SU",
  "repaymentRescheduleType": 1,
  "extendTermForDailyRepayments": false
}' > /dev/null

echo "3. Habilitando Business Date en configuraciones globales..."
CONFIG_ID=$(api_call GET "/configurations" | jq -r '.globalConfiguration[] | select(.name=="enable-business-date") | .id')
api_call PUT "/configurations/${CONFIG_ID}" '{"enabled": true}' > /dev/null

echo "4. Estableciendo fecha de negocio inicial: 15 de septiembre de 2026..."
api_call POST "/businessdate" '{
  "type": "BUSINESS_DATE",
  "date": "15 September 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}' > /dev/null

echo "5. Creando cliente ficticio: Ana Demo Integración (CLIENT-DEMO-001)..."
CLIENT_RES=$(api_call POST "/clients" '{
  "officeId": 1,
  "legalFormId": 1,
  "firstname": "Ana Demo",
  "lastname": "Integración",
  "externalId": "CLIENT-DEMO-001",
  "active": true,
  "activationDate": "01 September 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}')
CLIENT_ID=$(echo "$CLIENT_RES" | jq -r '.clientId')
echo "   -> Cliente creado con ID: $CLIENT_ID"

echo "6. Creando producto de préstamo: DEMO-CUMULATIVE..."
PRODUCT_RES=$(api_call POST "/loanproducts" '{
  "name": "DEMO-CUMULATIVE",
  "shortName": "DCUM",
  "currencyCode": "PEN",
  "digitsAfterDecimal": 2,
  "inMultiplesOf": 0,
  "principal": 12000.00,
  "minPrincipal": 1000.00,
  "maxPrincipal": 50000.00,
  "numberOfRepayments": 12,
  "minNumberOfRepayments": 1,
  "maxNumberOfRepayments": 36,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 1.00,
  "minInterestRatePerPeriod": 0.00,
  "maxInterestRatePerPeriod": 10.00,
  "interestRateFrequencyType": 2,
  "amortizationType": 0,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "loanScheduleType": "CUMULATIVE",
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "daysInMonthType": 30,
  "daysInYearType": 360,
  "isInterestRecalculationEnabled": false,
  "accountingRule": 1,
  "locale": "en",
  "dateFormat": "dd MMMM yyyy"
}')
PRODUCT_ID=$(echo "$PRODUCT_RES" | jq -r '.resourceId')
echo "   -> Producto creado con ID: $PRODUCT_ID"

echo "7. Creando solicitud de préstamo LOAN-DEMO-001 (15 de septiembre de 2026)..."
LOAN_RES=$(api_call POST "/loans" "{
  \"clientId\": ${CLIENT_ID},
  \"productId\": ${PRODUCT_ID},
  \"externalId\": \"LOAN-DEMO-001\",
  \"loanType\": \"individual\",
  \"principal\": 12000.00,
  \"loanTermFrequency\": 12,
  \"loanTermFrequencyType\": 2,
  \"numberOfRepayments\": 12,
  \"repaymentEvery\": 1,
  \"repaymentFrequencyType\": 2,
  \"interestRatePerPeriod\": 1.00,
  \"amortizationType\": 0,
  \"interestType\": 0,
  \"interestCalculationPeriodType\": 1,
  \"transactionProcessingStrategyCode\": \"mifos-standard-strategy\",
  \"expectedDisbursementDate\": \"15 September 2026\",
  \"submittedOnDate\": \"15 September 2026\",
  \"repaymentsStartingFromDate\": \"15 October 2026\",
  \"interestChargedFromDate\": \"15 September 2026\",
  \"locale\": \"en\",
  \"dateFormat\": \"dd MMMM yyyy\"
}")
LOAN_ID=$(echo "$LOAN_RES" | jq -r '.loanId')
echo "   -> Préstamo creado con ID: $LOAN_ID"

echo "8. Aprobando y desembolsando préstamo..."
api_call POST "/loans/${LOAN_ID}?command=approve" '{
  "approvedOnDate": "15 September 2026",
  "approvedLoanAmount": 12000.00,
  "locale": "en",
  "dateFormat": "dd MMMM yyyy"
}' > /dev/null

api_call POST "/loans/${LOAN_ID}?command=disburse" '{
  "actualDisbursementDate": "15 September 2026",
  "transactionAmount": 12000.00,
  "locale": "en",
  "dateFormat": "dd MMMM yyyy"
}' > /dev/null

echo "9. Avanzando fecha de negocio al 15 de octubre de 2026 (fecha de corte)..."
api_call POST "/businessdate" '{
  "type": "BUSINESS_DATE",
  "date": "15 October 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}' > /dev/null

echo "============================================================"
echo "Configuración finalizada exitosamente."
echo "Client ID: $CLIENT_ID | Product ID: $PRODUCT_ID | Loan ID: $LOAN_ID"
echo "============================================================"

