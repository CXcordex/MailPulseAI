# MailPulseAI 📬🤖

> **An AI-powered email assistant that classifies, summarises, and drafts replies to your inbox — then delivers smart WhatsApp notifications so you can approve or customise replies without ever opening your email client.**

![Dashboard Preview](assets/dashboard.png)

---

## ✨ Features

| Feature | Description |
|---|---|
| 📥 **Gmail Polling** | Fetches the last 24 hours of inbox messages every 60 seconds via Gmail OAuth |
| 🤖 **AI Classification** | Classifies every email into: `URGENT`, `IMPORTANT`, `CLIENT`, `NEWSLETTER`, `SPAM` |
| 📝 **AI Summarisation** | Generates a 2–3 bullet point summary for each email |
| ✍️ **AI Draft Reply** | Drafts a professional reply automatically (skipped for SPAM/NEWSLETTER) |
| 💬 **WhatsApp Alerts** | Sends a formatted WhatsApp notification for important/urgent/client emails via Twilio |
| 👍 **One-tap Approval** | Reply `YES` to send the AI draft, `EDIT: <text>` for a custom reply, or `IGNORE` to skip |
| 📊 **Live Dashboard** | Built-in web UI showing the inbox, AI analysis, category donut chart, and event log |
| 🔄 **Multi-provider AI Fallback** | Automatically rotates across Groq → NVIDIA NIM → OpenRouter → Gemini on rate limits |

---

## 🏗️ Architecture — Modular Monolith

This project is a **single deployable Spring Boot application** (monolith) with internally modular packages. All inter-module communication is via **Spring `ApplicationEvent`** — no Kafka, no Redis, no Eureka required.

```
Gmail API
    │
    ▼
GmailPollingService          (ingestion module — polls every 60s)
    │ publishes NewEmailEvent
    ▼
EmailProcessingListener      (ai module — @Async, processes with AI)
    │  ├─ AIEmailProcessorService.classify()
    │  ├─ AIEmailProcessorService.summarise()
    │  └─ AIEmailProcessorService.draftReply()
    │ saves to PostgreSQL → publishes EmailProcessedEvent
    ▼
EmailProcessedListener       (whatsapp module — @Async)
    │
    ▼
WhatsAppNotificationService  → Twilio API → Your WhatsApp
    │
    │ (user replies YES / EDIT / IGNORE via WhatsApp)
    ▼
WhatsAppWebhookController    → publishes ReplyApprovedEvent
    │
    ▼
ReplyApprovedListener        (outbound module — @Async)
    │
    ▼
OutboundMailService          → Gmail API (sends the reply)
```

### Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.3.5 (Java 21) |
| Database | PostgreSQL (via Spring Data JPA / Hibernate) |
| AI | Groq (llama-3.1-8b-instant) with fallback to NVIDIA NIM, OpenRouter, Google Gemini |
| Messaging | Gmail API v1 (Google OAuth 2.0 refresh token) |
| Notifications | Twilio WhatsApp API |
| Frontend | Vanilla HTML/CSS/JS (served as a Spring Boot static resource) |
| Deployment | Docker + Docker Compose / Render / AWS ECS |

---

## 🚀 Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Compose
- A Gmail account with OAuth credentials
- A Twilio account (free sandbox works)
- A free [Groq API key](https://console.groq.com)

### 1. Clone the repository

```bash
git clone https://github.com/CXcordex/MailPulseAI.git
cd MailPulseAI
```

### 2. Set up environment variables

```bash
cp .env.example .env
```

Edit `.env` and fill in your credentials:

```env
GROQ_API_KEY=gsk_...                        # Groq API key (primary AI)
GOOGLE_CLIENT_ID=...                         # Google OAuth client ID
GOOGLE_CLIENT_SECRET=...                     # Google OAuth client secret
GOOGLE_REFRESH_TOKEN=...                     # Gmail refresh token (see below)
TWILIO_ACCOUNT_SID=AC...                     # Twilio account SID
TWILIO_AUTH_TOKEN=...                        # Twilio auth token
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886   # Twilio sandbox number
WHATSAPP_TO=whatsapp:+91XXXXXXXXXX           # Your WhatsApp number
```

### 3. Get your Gmail Refresh Token

```bash
pip install google-auth-oauthlib
python scripts/get_refresh_token.py
```

Follow the OAuth browser flow. Paste the resulting refresh token into `.env`.

### 4. Run with Docker Compose

```bash
docker-compose up --build
```

This starts:
- **PostgreSQL** on port 5432
- **MailPulseAI app** on port 8080

Open the dashboard: **http://localhost:8080**

### 5. Configure Twilio Webhook

In your [Twilio Console](https://console.twilio.com) → WhatsApp Sandbox → "When a message comes in":

```
https://YOUR_DOMAIN/webhook/whatsapp
```

> For local development, use [ngrok](https://ngrok.com):
> ```bash
> ngrok http 8080
> # Then set the ngrok HTTPS URL as the Twilio webhook
> ```

---

## 📡 API Endpoints

The dashboard calls these REST endpoints (all prefixed `/api/ai/emails`):

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/ai/emails` | Paginated email list (newest first) |
| `GET` | `/api/ai/emails?category=URGENT` | Filter by category |
| `GET` | `/api/ai/emails/{id}` | Single email with full AI analysis |
| `GET` | `/api/ai/emails/stats` | Category counts for dashboard chart |
| `PATCH` | `/api/ai/emails/{id}/reply` | Edit the AI draft reply |

WhatsApp webhook:

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/webhook/whatsapp` | Receives Twilio WhatsApp replies |

---

## 🤖 AI Provider Fallback Chain

When the primary Groq key hits its rate limit, the system automatically tries:

1. **Groq** (`llama-3.1-8b-instant`) — primary from `GROQ_API_KEY`
2. **Groq** — secondary from `FALLBACK_GROQ_KEY_1` (optional)
3. **NVIDIA NIM** — from `NVIDIA_API_KEY_1` (optional)
4. **NVIDIA NIM** — from `NVIDIA_API_KEY_2` (optional)
5. **Groq** — tertiary from `FALLBACK_GROQ_KEY_2` (optional)
6. **OpenRouter** — from `OPENROUTER_API_KEY` (optional)
7. **Google Gemini Flash** — from `GEMINI_API_KEY` (optional)

All fallback keys are optional. Add them to `.env` for extra resilience.

---

## 📁 Project Structure

```
MailPulseAI/
├── mailpulseai-monolith/           # Single deployable Spring Boot app
│   ├── Dockerfile                  # Multi-stage Docker build (JDK build → JRE runtime)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/mailpulseai/monolith/
│       │   ├── MailPulseAIMonolithApplication.java   # Entry point
│       │   ├── ai/                 # AI classification, summarisation, draft reply
│       │   │   ├── AIEmailProcessorService.java      # Multi-provider AI client
│       │   │   ├── EmailDashboardController.java     # REST API for dashboard
│       │   │   ├── EmailProcessingListener.java      # Async NewEmailEvent handler
│       │   │   └── EmailProcessedEventService.java   # Persists AI results to DB
│       │   ├── config/
│       │   │   └── GmailConfig.java                  # Gmail OAuth client bean
│       │   ├── entity/
│       │   │   ├── EmailEntity.java                  # Raw ingested email (emails table)
│       │   │   └── ProcessedEmailEntity.java         # AI result (processed_emails table)
│       │   ├── event/              # Spring ApplicationEvent classes
│       │   │   ├── NewEmailEvent.java
│       │   │   ├── EmailProcessedEvent.java
│       │   │   └── ReplyApprovedEvent.java
│       │   ├── ingestion/
│       │   │   ├── GmailPollingService.java           # Scheduled Gmail inbox poller
│       │   │   └── EmailParserUtil.java               # MIME/Base64 email parser
│       │   ├── outbound/
│       │   │   ├── OutboundMailService.java           # Sends approved replies via Gmail
│       │   │   └── ReplyApprovedListener.java         # Async ReplyApprovedEvent handler
│       │   ├── repository/
│       │   │   ├── EmailRepository.java
│       │   │   └── ProcessedEmailRepository.java
│       │   └── whatsapp/
│       │       ├── WhatsAppNotificationService.java  # Twilio message sender
│       │       ├── WhatsAppWebhookController.java    # Handles YES/EDIT/IGNORE replies
│       │       └── EmailProcessedListener.java       # Async EmailProcessedEvent handler
│       └── resources/
│           ├── application.yml                       # All app configuration
│           └── static/index.html                     # Built-in dashboard UI
├── scripts/
│   └── get_refresh_token.py        # Helper to get Gmail refresh token
├── docker-compose.yml              # Postgres + App
├── .env.example                    # Environment variable template
└── README.md
```

---

## ☁️ Deployment on Render (Free Tier)

1. Push your code to GitHub (already done — this repo!)
2. Create a **new Web Service** on [Render](https://render.com)
3. Connect this GitHub repository
4. Set **Build Command**: `cd mailpulseai-monolith && mvn clean package -DskipTests`
5. Set **Start Command**: `java -jar mailpulseai-monolith/target/mailpulseai-monolith-1.0.0.jar`
6. Add a **PostgreSQL** database on Render and copy the internal connection string
7. Add all environment variables from `.env.example` in the Render dashboard
8. Set `SPRING_DATASOURCE_URL` to the Render Postgres internal URL

Or use the included Dockerfile for Docker-based deployment on Railway, Fly.io, or AWS ECS.

---

## 🔒 Security Notes

- `.env` is **git-ignored** — never commit real credentials
- The WhatsApp webhook validates the sender number against `WHATSAPP_TO` — only your number can trigger email sends
- Gmail OAuth uses a refresh token — no passwords are stored
- Processed email content is stored in PostgreSQL — ensure your DB is not publicly accessible

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 👤 Author

**Sayan Roy Chowdhury** — Final-year IT student & AI engineer  
GitHub: [@CXcordex](https://github.com/CXcordex)
