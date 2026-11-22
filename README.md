# Hub de Cobranza — Integración Java 21 con Apache Fineract 1.12.1 y Enfoque BIAN

Módulo externo desarrollado en **Java 21 + Spring Boot 3.4** que consulta préstamos y cronogramas en **Apache Fineract 1.12.1** mediante sus APIs públicas REST, generando y manteniendo órdenes locales de cobranza en su propia base de datos PostgreSQL bajo el estándar arquitectónico **BIAN**.

---

## 1. Arquitectura con Enfoque BIAN

El diseño separa la administración del contrato financiero (Core Bancario) de la gestión de partidas pendientes de cobro (Hub de Cobranza), alineándose con **BIAN Service Landscape v14.0** (https://bian.org):

| Service Domain (BIAN) | Área de Negocio BIAN | Componente en la Solución | Responsabilidad |
| :--- | :--- | :--- | :--- |
| **Consumer Loan** | *Operations & Execution → Loans & Deposits* | **Apache Fineract 1.12.1** | Administra el préstamo (`LOAN-DEMO-001`), el desembolso, el cronograma acumulativo (`CUMULATIVE`) y los saldos vivos de capital e interés. |
| **Open Item Management** | *Operations & Execution → Account Management* | **Collection Hub (Java 21)** | Extrae cuotas exigibles dentro del horizonte definido, genera lotes de extracción (`CollectionBatch`) y mantiene el estado de las órdenes de cobro (`CollectionOrder`) sin ejecutar movimientos contables. |
| **Party Reference Data Directory** | *Reference Data → Party* | **Apache Fineract 1.12.1** | Provee la identidad del cliente (`CLIENT-DEMO-001` — *Ana Demo Integración*). |
| **Payment Execution** | *Operations & Execution → Payments* | *Externo (Fuera de alcance)* | El Hub expone la orden exigible; la recaudación o transferencia de fondos ocurre en canales externos. |

---

## 2. Propuesta de Arquitectura Cloud en AWS

La siguiente arquitectura propone el despliegue productivo en **AWS** garantizando aislamiento de red, principio de mínimo privilegio (*Database-per-Service*) y comunicación cifrada.

*(Fuente editable en formato Mermaid; puede renderizarse directamente en GitHub o exportarse desde [Mermaid Live Editor](https://mermaid.live) / Draw.io).*

```mermaid
flowchart TB
    subgraph AWS["AWS Cloud (VPC - us-east-1)"]
        subgraph PublicSubnet["Subred Pública (ALB / NAT)"]
            ALB["Application Load Balancer\n(TLS 1.3 / AWS WAF)"]
        end

        subgraph PrivateAppSubnet["Subred Privada - Cómputo (ECS Fargate)"]
            HubApp["Hub de Cobranza\n(Java 21 + Spring Boot)\n[BIAN: Open Item Mgmt]"]
            FineractApp["Apache Fineract 1.12.1\n(Core Bancario)\n[BIAN: Consumer Loan]"]
        end

        subgraph PrivateDataSubnet["Subred Privada - Datos (Multi-AZ)"]
            HubDB[("Amazon RDS PostgreSQL\n(hub_cobranza_db)")]
            FineractDB[("Amazon RDS MariaDB\n(fineract_tenants / default)")]
        end

        Secrets["AWS Secrets Manager\n(Credenciales DB & API)"]
    end

    Client["Consumidor API / Operador"] -->|HTTPS REST + OAuth2/JWT| ALB
    ALB -->|HTTP :8080| HubApp
    HubApp -->|HTTPS REST :8443\n(AWS Cloud Map / Service Connect)| FineractApp
    HubApp -->|JDBC TLS :5432| HubDB
    FineractApp -->|JDBC TLS :3306| FineractDB
    HubApp -.->|IAM Task Role| Secrets
    FineractApp -.->|IAM Task Role| Secrets
```

### Justificación de Servicios AWS
* **Amazon ECS con AWS Fargate:** Ejecuta los microservicios en contenedores sin administrar servidores EC2, permitiendo escalar independientemente el Hub de Cobranza y el Core Fineract.
* **Amazon RDS (PostgreSQL y MariaDB Multi-AZ):** Aísla completamente la persistencia de cada dominio BIAN, impidiendo que el Hub acceda directamente a las tablas internas de Fineract.
* **Seguridad y Límites de Red:** Las instancias de cómputo y bases de datos residen en subredes privadas sin acceso directo desde Internet. Los *Security Groups* restringen el acceso al puerto `8443` de Fineract únicamente desde el servicio `collection-hub`.
* **AWS Secrets Manager:** Almacena y rota de forma segura las credenciales de acceso a las bases de datos y la autenticación del tenant en Fineract.

---

## 3. Decisiones Técnicas y Patrones de Diseño

* **Arquitectura Hexagonal (Ports & Adapters):** Aísla la lógica de sincronización (`SyncCollectionService`) de los detalles de transporte HTTP (`FineractApiClient`) y persistencia JPA.
* **Idempotencia por Clave Natural (`fineract_loan_id`, `installment_number`):** Cada ejecución crea un nuevo registro de auditoría `CollectionBatch`, pero actualiza de forma idempotente la `CollectionOrder` correspondiente a la cuota, reflejando transiciones de estado (`PENDING` → `PARTIALLY_PAID` → `PAID`).
* **Sin Motor de Intereses Duplicado:** El Hub consume directamente `principalOutstanding`, `interestOutstanding` y `totalOutstandingForPeriod` calculados por Fineract bajo la estrategia `mifos-standard-strategy`.
* **Migraciones Versionadas:** Esquema gestionado mediante **Flyway** (`V1__init_schema.sql`).

---

## 4. Requisitos Previos y Despliegue Local con Docker

### Requisitos
* Docker y Docker Compose v2+
* `curl` y `jq` (para ejecutar el script de inicialización y demostración)
* Java 21 y Maven 3.9+ *(opcional, solo si se desea ejecutar pruebas fuera de Docker)*

### Levantar el entorno
1. Clonar el repositorio y ubicarse en la raíz:
   ```bash
   git clone <URL_DEL_REPOSITORIO>
   cd collection-hub
   ```
2. Construir e iniciar los 4 contenedores (`fineract-db`, `fineract-server`, `hub-db`, `collection-hub`):
   ```bash
   docker compose up -d --build
   ```
3. Verificar que todos los servicios estén saludables (`Up`):
   ```bash
   docker compose ps
   ```

### Puertos y URLs de Acceso
* **API Hub de Cobranza:** `http://localhost:8080`
* **Documentación Swagger / OpenAPI:** `http://localhost:8080/swagger-ui.html`
* **API Apache Fineract 1.12.1:** `https://localhost:8443/fineract-provider/api/v1` (Credenciales: `mifos` / `password`, Tenant: `default`)
* **PostgreSQL (Hub):** `localhost:5433` (`hub_user` / `hub_pass`)
* **MariaDB (Fineract):** `localhost:3307` (`root` / `mysql`)

---

## 5. Preparación de Datos y Demostración del Flujo

### 1. Inicializar datos funcionales en Fineract
Ejecuta el script automatizado que configura la moneda `PEN`, los días hábiles, la fecha de negocio (`Business Date`), el cliente `CLIENT-DEMO-001`, el producto `DEMO-CUMULATIVE` y el préstamo desembolsado `LOAN-DEMO-001` por `S/ 12 000,00`:
```bash
chmod +x scripts/setup-fineract.sh
./scripts/setup-fineract.sh
```

### 2. Ejecutar los 3 pasos de la demostración

#### Paso 1: Sincronización inicial (Corte `2026-10-15`, Horizonte `31` días)
```bash
curl -s -X POST http://localhost:8080/api/v1/batches/sync \
  -H "Content-Type: application/json" \
  -d '{"loanIds": [1], "cutoffDate": "2026-10-15", "horizonDays": 31}' | jq
```
*Genera 2 órdenes `PENDING`: Cuota 1 (`S/ 1 120,00` = `1 000,00` capital + `120,00` interés) y Cuota 2 (`S/ 1 110,00` = `1 000,00` capital + `110,00` interés).*

#### Paso 2: Pago parcial de `S/ 500,00` en Fineract y nueva sincronización
```bash
# Registrar pago en Fineract
curl -s -k -X POST "https://localhost:8443/fineract-provider/api/v1/loans/1/transactions?command=repayment" \
  -u mifos:password -H "Fineract-Platform-TenantId: default" -H "Content-Type: application/json" \
  -d '{"transactionDate": "15 October 2026", "transactionAmount": 500.00, "dateFormat": "dd MMMM yyyy", "locale": "en"}' | jq

# Sincronizar Hub
curl -s -X POST http://localhost:8080/api/v1/batches/sync \
  -H "Content-Type: application/json" \
  -d '{"loanIds": [1], "cutoffDate": "2026-10-15", "horizonDays": 31}' | jq
```
*La Cuota 1 pasa a `PARTIALLY_PAID` con `620,00` de capital pendiente y `0,00` de interés.*

#### Paso 3: Pago del saldo restante (`S/ 620,00`) y nueva sincronización
```bash
# Registrar pago total de cuota 1 en Fineract
curl -s -k -X POST "https://localhost:8443/fineract-provider/api/v1/loans/1/transactions?command=repayment" \
  -u mifos:password -H "Fineract-Platform-TenantId: default" -H "Content-Type: application/json" \
  -d '{"transactionDate": "15 October 2026", "transactionAmount": 620.00, "dateFormat": "dd MMMM yyyy", "locale": "en"}' | jq

# Sincronizar Hub
curl -s -X POST http://localhost:8080/api/v1/batches/sync \
  -H "Content-Type: application/json" \
  -d '{"loanIds": [1], "cutoffDate": "2026-10-15", "horizonDays": 31}' | jq
```
*La Cuota 1 pasa a `PAID` (`0,00`) y la Cuota 2 permanece `PENDING` (`1 110,00`).*

### 3. Consultar órdenes filtradas
```bash
curl -s "http://localhost:8080/api/v1/collection-orders?loanId=1&dueDateTo=2026-11-15" | jq
```

---

## 6. Pruebas Automatizadas y Reinicio del Entorno

### Ejecutar pruebas unitarias y de integración
```bash
mvn test
```
*(O mediante Docker sin tener Maven instalado localmente)*:
```bash
docker run --rm -v "\$PWD":/app -w /app maven:3.9.9-eclipse-temurin-21-alpine mvn test
```

### Detener y limpiar completamente los datos
```bash
docker compose down -v --remove-orphans
```
# prestamype
