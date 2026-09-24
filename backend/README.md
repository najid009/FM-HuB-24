# FM HuB 24 backend integration

The vendored `moviebox-api` directory is the FastAPI backend selected for the FM HuB 24 Android frontend. It is deployed separately from the APK.

## Run locally

```bash
cd backend/moviebox-api
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn api:app --host 0.0.0.0 --port 8000
```

The Android app is configured from **Settings → Provider source** with the HTTPS URL of this backend, for example `https://api.example.com`.

## Frontend contract

The Android adapter calls:

- `GET /home`
- `GET /search?q=...&page=...`
- `GET /detail/{slug}`
- `GET /api/stream/{subject_id}?detail_path=...`
- `GET /api/stream/{subject_id}/captions?detail_path=...`

The backend remains a separate process and must not be packaged into the Android APK. Before production deployment, configure a restricted CORS allowlist, HTTPS, request timeouts, health monitoring, and a deployment-specific secret management strategy.

The app discloses external provider usage in **Settings → About us**.
