# MailPulseAI Deployment Guide

This guide covers how to deploy the **MailPulseAI** microservices stack to **Render.com**, as well as details on why a VPS is recommended for microservice architectures.

---

## ⚠️ CRITICAL WARNING: Render Free Tier Limitations

Before deploying to Render's free tier, please review these architectural constraints:

1. **The 750 Free Hours Limit**: 
   Render provides **750 free instance hours per month** shared across *all* free services in your account.
   - MailPulseAI contains **6 distinct microservices** (Eureka, Gateway, Ingestion, AI Processing, WhatsApp, Outbound) plus Redis and PostgreSQL.
   - If you run 6 free services simultaneously:  
     $$\text{6 services} \times 24\text{ hours/day} = 144\text{ hours/day}$$
   - Your monthly quota of 750 hours will be completely exhausted in **approx. 5 days** ($750 / 144 \approx 5.2$ days). After 5 days, all services will be suspended until the next month.
2. **RAM Constraints (512 MB Limit)**:
   Spring Boot microservices generally require 300MB - 512MB RAM each to compile and run. Running 6 of them on free tiers will frequently hit Out-Of-Memory (OOM) errors unless heavily tuned.
3. **Inactivity Sleep (15-Min Spin-Down)**:
   If a service receives no public traffic for 15 minutes, Render spins it down. When spun down, the **Email Ingestion Service** will stop polling your Gmail inbox.

---

## 🛠️ Step-by-Step Render Deployment (If Proceeding)

To run the application on Render, you must replace the local Docker-based database, cache, and message broker with Cloud providers.

### Step 1: Create Cloud Middleware Services

#### 1. Cloud PostgreSQL (On Render)
1. Go to your **Render Dashboard** ➔ **New +** ➔ **PostgreSQL**.
2. Name: `mailpulseai-db`.
3. Database Name: `mailpulseai`, User: `mailpulseai`.
4. Click **Create Database**.
5. Save the **Internal Database URL** (for other Render services) and **External Database URL** (for local testing).

#### 2. Cloud Redis (On Render)
1. Go to **Render Dashboard** ➔ **New +** ➔ **Redis**.
2. Name: `mailpulseai-redis`.
3. Plan: **Free**.
4. Click **Create Redis**.
5. Save the **Internal Redis Connection String**.

#### 3. Cloud Serverless Kafka (Upstash)
Render does not offer a free Kafka broker. You must use **Upstash** (Free: 10,000 messages/day):
1. Sign up at [upstash.com](https://upstash.com).
2. Click **Create Cluster** ➔ name it `mailpulseai-kafka` ➔ Choose **Free Tier**.
3. Create the following topics in your Upstash Console:
   - `new-email`
   - `email-processed`
   - `reply-approved`
4. Copy the **Bootstrap Server URL** and your **SASL Username & Password** from the credentials section.

---

### Step 2: Configure Application Yaml Files for Upstash Kafka

For cloud deployment, each service's `application.yml` needs to support SASL authentication for Upstash. Add this environment mapping variable under the `spring.kafka` config in your service configurations if you deploy them:

```yaml
spring:
  kafka:
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-256
      sasl.jaas.config: org.apache.kafka.common.security.scram.ScramLoginModule required username="${KAFKA_USERNAME}" password="${KAFKA_PASSWORD}";
```

---

### Step 3: Deploy Microservices on Render

You must create a **Web Service** on Render for each of the 6 components:

1. Click **New +** ➔ **Web Service**.
2. Select your GitHub repository `CXcordex/MailPulseAI`.
3. Configure each service with the settings below:

| Service | Root Directory | Build Command | Start Command | Env Variables Required |
|---|---|---|---|---|
| **eureka-server** | `eureka-server` | (Autodetected Dockerfile) | (Autodetected Dockerfile) | *None* |
| **api-gateway** | `api-gateway` | (Autodetected Dockerfile) | (Autodetected Dockerfile) | `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` |
| **email-ingestion-service** | `email-ingestion-service` | (Autodetected Dockerfile) | (Autodetected Dockerfile) | (See Env block below) |
| **ai-processing-service** | `ai-processing-service` | (Autodetected Dockerfile) | (Autodetected Dockerfile) | (See Env block below) |
| **whatsapp-messaging-service** | `whatsapp-messaging-service` | (Autodetected Dockerfile) | (Autodetected Dockerfile) | (See Env block below) |
| **outbound-mail-service** | `outbound-mail-service` | (Autodetected Dockerfile) | (Autodetected Dockerfile) | (See Env block below) |

---

### Step 4: Environment Variables to Set on Render

Under the **Environment** tab of your Render services, configure:

```env
# Database & Cache (Use the Internal URL/Hosts provided by Render)
SPRING_DATASOURCE_URL=jdbc:postgresql://<RENDER_POSTGRES_INTERNAL_HOST>:5432/mailpulseai
SPRING_DATASOURCE_USERNAME=mailpulseai
SPRING_DATASOURCE_PASSWORD=<RENDER_POSTGRES_PASSWORD>
SPRING_REDIS_HOST=<RENDER_REDIS_INTERNAL_HOST>
SPRING_REDIS_PORT=6379

# Service Registry
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/

# Upstash Kafka Credentials
KAFKA_BOOTSTRAP_SERVERS=<UPSTASH_BOOTSTRAP_SERVER>
KAFKA_USERNAME=<UPSTASH_SASL_USERNAME>
KAFKA_PASSWORD=<UPSTASH_SASL_PASSWORD>

# AI API Keys
GROQ_API_KEY=gsk_...

# Google Gmail OAuth
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret
GOOGLE_REFRESH_TOKEN=your-refresh-token

# Twilio WhatsApp
TWILIO_ACCOUNT_SID=AC...
TWILIO_AUTH_TOKEN=your-token
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
WHATSAPP_TO=whatsapp:+91XXXXXXXXXX
```

---

## 🚀 Recommended Alternative: VPS Deployment (Oracle Free Tier)

Instead of managing 6 separate Render services and exhausting free hours in 5 days, a **Virtual Private Server (VPS)** is highly recommended. 

### Why VPS?
- **100% Free Forever**: Oracle Cloud Free Tier provides 1 VM instance with **24 GB RAM and 4 OCPUs** for free.
- **Single-Command Setup**: You run the entire stack (including Kafka, Postgres, and Redis) inside the VM using `docker compose up -d` in under 2 minutes.
- **Continuous Execution**: Services never spin down, ensuring your email poller runs 24/7.

### Basic VPS Setup Steps:
1. Log into your VPS via SSH:
   ```bash
   ssh ubuntu@your-vps-ip
   ```
2. Install Docker & Git:
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-plugin git
   ```
3. Clone your repository:
   ```bash
   git clone https://github.com/CXcordex/MailPulseAI.git
   cd MailPulseAI
   ```
4. Create the `.env` file and insert all configurations.
5. Deploy:
   ```bash
   docker compose up --build -d
   ```
