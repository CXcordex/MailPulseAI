#!/usr/bin/env python3
"""
get_refresh_token.py
────────────────────
One-time script to obtain a Gmail OAuth 2.0 refresh token.

Run ONCE to get your refresh token, then add it to .env as GOOGLE_REFRESH_TOKEN.
The refresh token never expires unless you revoke it in Google Account settings.

BEFORE running:
  1. In Google Cloud Console -> Credentials -> your OAuth client
  2. Add this redirect URI:  http://localhost:8090/
  3. Click Save, wait 1 minute

Requirements:
  pip install google-auth-oauthlib

Usage:
  python scripts/get_refresh_token.py
"""

import sys
import os

# Force UTF-8 output on Windows to avoid emoji encoding errors
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

try:
    from google_auth_oauthlib.flow import InstalledAppFlow
except ImportError:
    print("ERROR: google-auth-oauthlib not installed.")
    print("Run: pip install google-auth-oauthlib")
    sys.exit(1)

SCOPES = [
    "https://www.googleapis.com/auth/gmail.readonly",
    "https://www.googleapis.com/auth/gmail.modify",
    "https://www.googleapis.com/auth/gmail.send",
]

CLIENT_ID     = os.environ.get("GOOGLE_CLIENT_ID", "")
CLIENT_SECRET = os.environ.get("GOOGLE_CLIENT_SECRET", "")

if not CLIENT_ID or not CLIENT_SECRET:
    print("ERROR: Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables first.")
    sys.exit(1)

# Using "web" type to match the OAuth client created in Google Cloud Console.
# The redirect URI MUST be added in Google Cloud Console -> Credentials -> your client.
client_config = {
    "web": {
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
        "redirect_uris": ["http://localhost:8090/"],
    }
}

flow = InstalledAppFlow.from_client_config(client_config, scopes=SCOPES)
credentials = flow.run_local_server(port=8090, access_type="offline", prompt="consent")

print("")
print("SUCCESS: OAuth flow complete!")
print("")
print("GOOGLE_REFRESH_TOKEN=" + credentials.refresh_token)
print("")
print("Copy the token above and paste it into your .env file.")
