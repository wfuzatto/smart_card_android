import os
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from app.main import app

KEY = "very-long-test-key-should-remain-only-in-tests"
client = TestClient(app)
headers = {"X-Lab-Key": KEY}


@pytest.fixture(autouse=True)
def temporary_settings(monkeypatch, tmp_path):
    monkeypatch.setenv("LAB_API_KEY", KEY)
    monkeypatch.setenv("DB_PATH", str(tmp_path / "test.sqlite3"))
    monkeypatch.delenv("WALLET_ISSUER_ID", raising=False)
    monkeypatch.delenv("GOOGLE_APPLICATION_CREDENTIALS", raising=False)


def payload():
    now = datetime.now(timezone.utc)
    return {"hotel_name": "Hotel Laboratório", "guest_name": "Pessoa Teste",
            "room_number": "207", "checkin": now.isoformat(),
            "checkout": (now + timedelta(days=2)).isoformat()}


def test_create_and_read_credentials():
    assert client.post("/v1/credentials", json=payload()).status_code == 401
    result = client.post("/v1/credentials", json=payload(), headers=headers)
    assert result.status_code == 201
    data = result.json()
    assert len(data["opaque_token"]) > 32
    assert client.get(f"/v1/credentials/{data['id']}", headers=headers).json()["room_number"] == "207"


def test_wallet_fails_closed_without_issuer():
    record = client.post("/v1/credentials", json=payload(), headers=headers).json()
    response = client.post(f"/v1/credentials/{record['id']}/wallet", headers=headers)
    assert response.status_code == 503
    assert record["google_object_id"] is None


def test_revoke():
    record = client.post("/v1/credentials", json=payload(), headers=headers).json()
    result = client.patch(f"/v1/credentials/{record['id']}", json={"status": "revoked"}, headers=headers)
    assert result.status_code == 200
    assert result.json()["status"] == "revoked"
    assert client.post(f"/v1/credentials/{record['id']}/wallet", headers=headers).status_code == 409


def test_invalid_dates():
    data = payload()
    data["checkout"] = data["checkin"]
    assert client.post("/v1/credentials", json=data, headers=headers).status_code == 422


def test_payload_avoids_secrets():
    from app.wallet import object_payload
    data = payload()
    data.update({"id": "00000000-0000-0000-0000-000000000000",
                 "opaque_token": "opaque-test-token", "status": "active"})
    obj = object_payload(data, "123", "123.lab")
    assert obj["barcode"]["value"] == "opaque-test-token"
    assert "smartTapRedemptionValue" not in obj
    assert obj["classId"] == "123.lab"
