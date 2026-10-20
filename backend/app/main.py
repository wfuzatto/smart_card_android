"""Experiment-only issuer service; separate from BIS production interfaces."""
import hmac
import os
import secrets
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, field_validator, model_validator

from . import wallet

load_dotenv()
app = FastAPI(title="Hotel NFC Wallet Lab API", version="0.1.0",
              description="Experimental passes only. No BIS key/door operations.")


def authenticate(x_lab_key: str | None = Header(default=None)):
    expected = os.getenv("LAB_API_KEY", "")
    if len(expected) < 20 or not x_lab_key or not hmac.compare_digest(expected, x_lab_key):
        raise HTTPException(401, "Lab API key missing or invalid")


def database():
    path = Path(os.getenv("DB_PATH", "./wallet_lab.sqlite3"))
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(str(path), timeout=15)
    db.row_factory = sqlite3.Row
    db.execute("""CREATE TABLE IF NOT EXISTS credentials (
        id TEXT PRIMARY KEY, hotel_name TEXT NOT NULL, guest_name TEXT NOT NULL,
        room_number TEXT NOT NULL, checkin TEXT NOT NULL, checkout TEXT NOT NULL,
        opaque_token TEXT NOT NULL UNIQUE, status TEXT NOT NULL,
        google_object_id TEXT, created_at TEXT NOT NULL)""")
    db.commit()
    return db


class NewCredential(BaseModel):
    hotel_name: str = Field(min_length=1, max_length=100)
    guest_name: str = Field(min_length=1, max_length=100)
    room_number: str = Field(min_length=1, max_length=20)
    checkin: datetime
    checkout: datetime

    @model_validator(mode="after")
    def validate_interval(self):
        if self.checkin.tzinfo is None or self.checkout.tzinfo is None:
            raise ValueError("Datetime needs UTC offset")
        if self.checkout <= self.checkin:
            raise ValueError("Checkout must follow checkin")
        return self


def present(row):
    d = dict(row)
    if d["status"] == "active" and datetime.fromisoformat(d["checkout"]) <= datetime.now(timezone.utc):
        d["status"] = "expired"
    return d


def get_one(identifier):
    db = database()
    try:
        row = db.execute("SELECT * FROM credentials WHERE id=?", (str(identifier),)).fetchone()
        if row is None:
            raise HTTPException(404, "Credential not found")
        return present(row)
    finally:
        db.close()


@app.get("/health")
def health():
    return {"ok": True, "wallet_configured": bool(os.getenv("WALLET_ISSUER_ID") and os.getenv("GOOGLE_APPLICATION_CREDENTIALS")),
            "note": "Configured does not mean issuer approved or external API verified"}


@app.post("/v1/credentials", dependencies=[Depends(authenticate)], status_code=201)
def create_credential(body: NewCredential):
    identifier = str(uuid.uuid4())
    token = secrets.token_urlsafe(32)
    db = database()
    try:
        db.execute("""INSERT INTO credentials
            (id, hotel_name, guest_name, room_number, checkin, checkout, opaque_token, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'active', ?)""",
            (identifier, body.hotel_name.strip(), body.guest_name.strip(), body.room_number.strip(),
             body.checkin.isoformat(), body.checkout.isoformat(), token, datetime.now(timezone.utc).isoformat()))
        db.commit()
    finally:
        db.close()
    return get_one(identifier)


@app.get("/v1/credentials/{identifier}", dependencies=[Depends(authenticate)])
def get_credential(identifier: uuid.UUID):
    return get_one(identifier)


@app.post("/v1/credentials/{identifier}/wallet", dependencies=[Depends(authenticate)])
def issue_wallet(identifier: uuid.UUID):
    record = get_one(identifier)
    if record["status"] != "active":
        raise HTTPException(409, "Only active credentials can be issued")
    try:
        result = wallet.provision(record)
    except wallet.WalletConfigurationError as exc:
        raise HTTPException(503, str(exc)) from exc
    except wallet.WalletApiError as exc:
        raise HTTPException(502, str(exc)) from exc
    except Exception as exc:
        # No fabricated success if external HTTP/Google auth failed.
        raise HTTPException(502, f"Google Wallet service unavailable: {type(exc).__name__}") from exc
    db = database()
    try:
        db.execute("UPDATE credentials SET google_object_id=? WHERE id=?",
                   (result["object_id"], str(identifier)))
        db.commit()
    finally:
        db.close()
    return result


class StateUpdate(BaseModel):
    status: str

    @field_validator("status")
    @classmethod
    def limited(cls, status):
        if status not in ("active", "expired", "revoked"):
            raise ValueError("Allowed: active, expired, revoked")
        return status


@app.patch("/v1/credentials/{identifier}", dependencies=[Depends(authenticate)])
def update_credential(identifier: uuid.UUID, body: StateUpdate):
    record = get_one(identifier)
    if body.status == "active" and datetime.fromisoformat(record["checkout"]) <= datetime.now(timezone.utc):
        raise HTTPException(409, "Cannot reactivate past checkout")
    if record["google_object_id"]:
        try:
            wallet.patch_state(record["google_object_id"], body.status)
        except wallet.WalletConfigurationError as exc:
            raise HTTPException(503, str(exc)) from exc
        except Exception as exc:
            raise HTTPException(502, f"Google Wallet update failed: {type(exc).__name__}") from exc
    db = database()
    try:
        db.execute("UPDATE credentials SET status=? WHERE id=?", (body.status, str(identifier)))
        db.commit()
    finally:
        db.close()
    return get_one(identifier)


@app.get("/v1/credentials/{identifier}/wallet-status", dependencies=[Depends(authenticate)])
def wallet_status(identifier: uuid.UUID):
    record = get_one(identifier)
    object_id = record["google_object_id"]
    if not object_id:
        return {"google_object_exists": False, "saved_to_user_wallet": None,
                "evidence": "Not issued"}
    try:
        remote = wallet.lookup(object_id)
    except Exception as exc:
        raise HTTPException(502, f"Google Wallet lookup failed: {type(exc).__name__}") from exc
    return {"google_object_exists": True, "object_id": remote.get("id"),
            "google_state": remote.get("state"), "has_users": remote.get("hasUsers"),
            "saved_to_user_wallet": None,
            "evidence": "REST object lookup: not proof that this user's Wallet saved it"}
