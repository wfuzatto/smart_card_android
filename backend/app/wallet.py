"""Official Google Wallet REST integration. No synthetic success paths."""
import json
import os
import time
from datetime import timezone
from pathlib import Path

import jwt
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

SCOPE = "https://www.googleapis.com/auth/wallet_object.issuer"
BASE = "https://walletobjects.googleapis.com/walletobjects/v1"


class WalletConfigurationError(RuntimeError):
    pass


class WalletApiError(RuntimeError):
    pass


def settings():
    issuer = os.getenv("WALLET_ISSUER_ID", "").strip()
    key_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "").strip()
    if not issuer.isdecimal() or not key_path or not Path(key_path).is_file():
        raise WalletConfigurationError(
            "Configure WALLET_ISSUER_ID and GOOGLE_APPLICATION_CREDENTIALS on the server"
        )
    key = json.loads(Path(key_path).read_text(encoding="utf-8"))
    if key.get("type") != "service_account" or not key.get("private_key") or not key.get("client_email"):
        raise WalletConfigurationError("Invalid Google service account file")
    suffix = os.getenv("WALLET_CLASS_SUFFIX", "hotel_nfc_lab")
    if not suffix.replace("_", "").isalnum():
        raise WalletConfigurationError("Invalid WALLET_CLASS_SUFFIX")
    return issuer, key, f"{issuer}.{suffix}"


def session():
    issuer, key, class_id = settings()
    credentials = service_account.Credentials.from_service_account_info(key, scopes=[SCOPE])
    return AuthorizedSession(credentials), issuer, key, class_id


def checked(response):
    if not response.ok:
        raise WalletApiError(f"Google Wallet REST HTTP {response.status_code}: {response.text[:600]}")
    return response.json()


def localized(text):
    return {"defaultValue": {"language": "pt-BR", "value": text}}


def to_google_state(status):
    return {"active": "ACTIVE", "expired": "EXPIRED", "revoked": "INACTIVE"}[status]


def object_payload(credential, issuer, class_id):
    return {
        "id": f"{issuer}.hotel_{credential['id'].replace('-', '_')}",
        "classId": class_id,
        "state": to_google_state(credential["status"]),
        "cardTitle": localized(credential["hotel_name"]),
        "header": localized(f"Quarto {credential['room_number']}"),
        "subheader": localized(credential["guest_name"]),
        "hexBackgroundColor": "#123B4B",
        "barcode": {
            "type": "QR_CODE",
            "value": credential["opaque_token"],
            "alternateText": "Identificador experimental"
        },
        "validTimeInterval": {
            "start": {"date": credential["checkin"]},
            "end": {"date": credential["checkout"]}
        },
        "textModulesData": [
            {"id": "guest", "header": "Hóspede", "body": credential["guest_name"]},
            {"id": "room", "header": "Quarto", "body": credential["room_number"]},
            {"id": "checkin", "header": "Check-in", "body": credential["checkin"]},
            {"id": "checkout", "header": "Checkout", "body": credential["checkout"]}
        ]
    }


def provision(credential):
    """Create REAL GenericClass and GenericObject via REST, then signed save JWT."""
    client, issuer, key, class_id = session()
    class_response = client.get(f"{BASE}/genericClass/{class_id}", timeout=25)
    if class_response.status_code == 404:
        checked(client.post(f"{BASE}/genericClass", json={"id": class_id}, timeout=25))
    else:
        checked(class_response)
    obj = object_payload(credential, issuer, class_id)
    object_response = client.get(f"{BASE}/genericObject/{obj['id']}", timeout=25)
    if object_response.status_code == 404:
        checked(client.post(f"{BASE}/genericObject", json=obj, timeout=25))
    else:
        checked(object_response)
        # Reconcile a retry with the current local status.
        checked(client.patch(f"{BASE}/genericObject/{obj['id']}",
                             json={"state": obj["state"]}, timeout=25))
    token = jwt.encode({
        "iss": key["client_email"], "aud": "google", "typ": "savetowallet",
        "iat": int(time.time()), "origins": [],
        "payload": {"genericObjects": [{"id": obj["id"]}]}
    }, key["private_key"], algorithm="RS256",
        headers={"kid": key.get("private_key_id", "")})
    return {"object_id": obj["id"], "class_id": class_id,
            "jwt": token, "save_url": f"https://pay.google.com/gp/v/save/{token}",
            "google_object_created": True, "saved_to_user_wallet": None}


def patch_state(object_id, status):
    client, _, _, _ = session()
    return checked(client.patch(f"{BASE}/genericObject/{object_id}",
                                json={"state": to_google_state(status)}, timeout=25))


def lookup(object_id):
    client, _, _, _ = session()
    return checked(client.get(f"{BASE}/genericObject/{object_id}", timeout=25))
