# FM HuB 24 Moviebox API deployment guide

This guide deploys the FastAPI service in `backend/moviebox-api`. The Android application must be configured with the public HTTPS URL produced by the selected hosting provider.

## Important limitation

The upstream repository is archived and depends on external MovieBox services. A successful deployment proves that the FastAPI container starts; it does not guarantee that upstream catalogue or stream endpoints remain available. Validate `/home`, `/search?q=...`, and `/detail/...` after deployment before connecting the APK.

## Option A: Render with Docker (recommended)

Render runs the included `Dockerfile` and supplies the listening port through the `PORT` environment variable. The Docker command already consumes that variable.

### Dashboard deployment

1. Push the FM HuB repository to GitHub.
2. In Render, create **New → Web Service** and select the repository.
3. Set **Root Directory** to `backend/moviebox-api`.
4. Set **Runtime** to `Docker`.
5. Leave the Dockerfile path as `Dockerfile` and use the free plan for testing.
6. Add `ALLOWED_ORIGINS` as an environment variable. For a native Android client, use the backend URL or a comma-separated list of approved web origins. Do not use `*` in a production browser-facing deployment.
7. Deploy and wait for the service to become healthy.
8. Copy the Render HTTPS URL, for example `https://fmhub24-moviebox-api.onrender.com`.

The repository also contains `render.yaml`. Render Blueprint deployment can use it directly; when prompted, provide the value for the `ALLOWED_ORIGINS` variable.

### Local Docker test

```bash
cd backend/moviebox-api
docker build -t fmhub24-moviebox-api .
docker run --rm -p 8000:8000 -e ALLOWED_ORIGINS=http://localhost:3000 fmhub24-moviebox-api
curl -i http://127.0.0.1:8000/
curl -i 'http://127.0.0.1:8000/search?q=avatar&page=1'
```

## Option B: Vercel Python functions

Vercel does not use the Dockerfile for this deployment mode. It runs the existing `main.py` FastAPI entrypoint as a Python function using `vercel.json`.

1. In Vercel, import the GitHub repository.
2. Set **Root Directory** to `backend/moviebox-api`.
3. Keep the framework preset as **Other**.
4. Vercel detects `main.py`, `requirements.txt`, and `vercel.json`.
5. Add `ALLOWED_ORIGINS` as an environment variable.
6. Deploy and open the generated HTTPS URL.

Vercel functions have execution and response-time limits. Stream discovery can exceed those limits because the backend performs several upstream requests. Use Render Docker for the more predictable long-running API service; use Vercel for lightweight metadata/search testing.

## Android configuration

In FM HuB 24 open:

```text
Settings → Provider source
```

Enter the deployed HTTPS base URL without a trailing slash. Do not put the upstream MovieBox URL in the APK. The Android app calls only the deployed FastAPI service.

## Production checklist

- Use HTTPS only.
- Set an explicit `ALLOWED_ORIGINS` value instead of `*`.
- Do not commit `.env` files or tokens.
- Configure a health check for `/`.
- Test `/home`, `/search`, `/detail`, `/api/stream`, and `/api/stream/.../captions`.
- Review Render logs or Vercel function logs for upstream failures and rate limits.
- Treat direct stream URLs as short-lived and do not cache them indefinitely.
- Confirm that the upstream service and all returned media are lawful to access in your jurisdiction.

## Required files

- `backend/moviebox-api/Dockerfile` — Render container image.
- `backend/moviebox-api/.dockerignore` — build context exclusions.
- `backend/moviebox-api/render.yaml` — optional Render Blueprint.
- `backend/moviebox-api/vercel.json` — Vercel function configuration.
- `backend/moviebox-api/main.py` — Vercel/FastAPI entrypoint.
