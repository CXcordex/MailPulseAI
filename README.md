<div align="center">
  <h1>📬 MailPulseAI</h1>
  <p><b>Your Personal AI-Powered Email & WhatsApp Assistant</b></p>
  <p><i>Classifies, summarises, and drafts replies to your inbox — then delivers smart WhatsApp notifications so you can approve or customise replies without ever opening your email client.</i></p>
</div>

---
<div align="center">
  <img src="./assets/dashboard.png" alt="MailPulseAI Dashboard" width="100%" />
  <br>
  <em>Live Analytics Dashboard displaying Inbox & AI Processing</em>
  <br><br>
  <img src="./assets/whatsapp-preview.jpg" alt="MailPulseAI WhatsApp Alerts" width="350" />
  <br>
  <em>WhatsApp Notification with One-Tap Approvals</em>
</div>
## ✨ Features

| Feature | Description |
| :--- | :--- |
| 📥 **Smart Gmail Polling** | Fetches unread emails from the last 24 hours every 60 seconds securely via OAuth 2.0. |
| 🤖 **AI Classification** | Auto-categorizes emails into: `URGENT`, `IMPORTANT`, `CLIENT`, `NEWSLETTER`, `SPAM`. |
| 📝 **Action-Oriented Summaries** | Generates 2–3 concise bullet points focusing on tasks, deadlines, and questions. |
| ✍️ **Auto-Drafted Replies** | Drafts a professional, context-aware reply automatically (intelligently skips SPAM/NEWSLETTERS). |
| 💬 **WhatsApp Alerts (Twilio)** | Sends formatted WhatsApp notifications for high-priority emails directly to your phone. |
| 👍 **One-Tap WhatsApp Approvals** | Reply `YES` to send the AI draft, `EDIT: <text>` for a custom reply, or `IGNORE` to skip. |
| 📊 **Live Analytics Dashboard** | Beautiful local web UI showing the inbox, category donut chart, and real-time processing logs. |
| 🔄 **Multi-Provider AI Fallback** | Seamlessly rotates between Groq → NVIDIA NIM → OpenRouter → Gemini when rate limits are hit. |
| ⏳ **Intelligent Rate Limiting** | Built-in smart cooldowns (e.g., pausing every 3 emails) to respect strict free-tier API limits during bulk ingestion. |

---

## 🏗️ Architecture: Event-Driven Modular Monolith

This project is built as a **single deployable Spring Boot application** with highly decoupled internal modules. It relies heavily on **Spring `ApplicationEvent`** for asynchronous messaging, eliminating the overhead of Kafka, Redis, or Eureka while maintaining a scalable event-driven architecture.

```mermaid
graph TD
    A[Gmail API] -->|Polls every 60s| B(GmailPollingService)
    B -->|NewEmailEvent| C(EmailProcessingListener)
    C -->|AI Analysis via Groq/Gemini| D[(PostgreSQL)]
    D -->|EmailProcessedEvent| E(EmailProcessedListener)
    E -->|Twilio API| F[Your WhatsApp]
    F -->|YES / EDIT / IGNORE| G(WhatsAppWebhookController)
    G -->|ReplyApprovedEvent| H(ReplyApprovedListener)
    H -->|Sends Email| A
```

### 🛠️ Tech Stack
- **Framework:** Spring Boot 3.3.5 (Java 21)
- **Database:** PostgreSQL (Spring Data JPA / Hibernate)
- **AI Providers:** Groq (`llama-3.1-8b-instant`), Google Gemini, NVIDIA NIM, OpenRouter
- **Messaging API:** Google Gmail API v1 (OAuth 2.0)
- **Notifications:** Twilio WhatsApp API
- **Frontend:** Vanilla HTML/CSS/JS (served as static resources)
- **Deployment:** Docker & Docker Compose

---

## 🚀 Quick Start (Local Development)

### Prerequisites
- Java 21 & Maven 3.9+
- Docker & Docker Compose
- Twilio Account (Free Sandbox works)
- Google Cloud Console Account (for Gmail API credentials)
- [Groq API Key](https://console.groq.com) (Free)

### 1. Clone & Configure
```bash
git clone https://github.com/CXcordex/MailPulseAI.git
cd MailPulseAI
cp .env.example .env
```
Fill in your credentials in `.env`.

### 2. Generate Gmail Refresh Token
Run the included Python script to perform the one-time Google OAuth flow:
```bash
pip install google-auth-oauthlib
python scripts/get_refresh_token.py
```
Paste the generated `GOOGLE_REFRESH_TOKEN` into your `.env` file.

### 3. Start the Application
```bash
docker-compose up --build -d
```
Your dashboard is now live at: **http://localhost:8080** 🎉

### 4. Configure Twilio Webhook (For WhatsApp Replies)
In your Twilio Console → WhatsApp Sandbox → "When a message comes in":
Set the webhook URL to: `https://YOUR_DOMAIN/webhook/whatsapp`
*(For local testing, use [ngrok](https://ngrok.com) to expose port 8080).*

---

## ☁️ Deployment Guide (Render - Free Tier)

Deploying this monolithic application to the cloud is incredibly simple. We recommend **Render** for a free and easy hosting solution.

### Step 1: Prepare the Database
1. Create an account on [Render.com](https://render.com).
2. Click **New** → **PostgreSQL**.
3. Name it `mailpulseai-db`, select the **Free** instance type, and click **Create**.
4. Once created, copy the **Internal Database URL** (e.g., `postgres://user:pass@host/dbname`).

### Step 2: Deploy the Web Application
1. Click **New** → **Web Service** on Render.
2. Select **"Build and deploy from a Git repository"** and connect your GitHub repo.
3. Configure the service:
   - **Language:** `Docker`
   - **Root Directory:** `mailpulseai-monolith`
   - **Instance Type:** Free

*(By setting the Root Directory, Render will automatically find and build the `Dockerfile` inside that folder).*

### Step 3: Configure Environment Variables
In the Render Web Service settings, go to **Environment Variables** and add everything from your `.env` file. 

**Crucially, add this database connection variable:**
- Key: `SPRING_DATASOURCE_URL`
- Value: `jdbc:<paste_your_render_internal_database_url_here>`
*(Ensure you replace `postgres://` with `postgresql://` in the JDBC URL).*

### Step 4: Deploy & Update Twilio
1. Click **Deploy**. Render will build and launch your application.
2. Once live, copy your `.onrender.com` URL.
3. Go back to your Twilio WhatsApp Sandbox and update the webhook URL to: `https://your-app.onrender.com/webhook/whatsapp`.

---

## 🔒 Security & Privacy
- **No Passwords Stored:** Uses Google OAuth 2.0 Refresh Tokens.
- **Webhook Authentication:** The application verifies incoming Twilio requests to ensure only your designated `WHATSAPP_TO` number can trigger email sends.
- **Local Database:** All AI analyses and emails are stored locally in your own Postgres database instance, not on third-party servers.

---

<div align="center">
  <p>Built with ❤️ by <a href="https://github.com/CXcordex">Sayan Roy Chowdhury</a></p>
  <p><i>Final-year IT Student & AI Engineer</i></p>
</div>
