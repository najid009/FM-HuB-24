# FM HuB 24 Provider Gateway Contract

FM HuB 24 intentionally separates the Android client from upstream provider implementation details. The Android app accepts an HTTPS provider gateway URL and calls only the stable routes below. The gateway must be operated or authorized by the project owner; upstream credentials, session tokens, signing keys, and provider-specific headers must remain server-side.

## Required routes

| Route | Method | Purpose |
|---|---:|---|
| `/home?page=1` | GET | Curated sections and catalogue items |
| `/search?q=term&page=1` | GET | Search results |
| `/details/{id}` | GET | Metadata, genres, seasons, and episodes |
| `/details/{id}/season/{season}?page=1` | GET | Paginated episodes |
| `/streams/{episodeId}` | GET | Resolved playable stream sources |
| `/subtitles/{episodeId}` | GET | Subtitle tracks |

## Response envelope

Every response should use this envelope:

```json
{
  "ok": true,
  "data": {},
  "error": null
}
```

For failures:

```json
{
  "ok": false,
  "data": null,
  "error": {
    "kind": "unavailable",
    "message": "Provider is temporarily unavailable"
  }
}
```

## Media item

```json
{
  "id": "subject-id",
  "title": "Title",
  "poster_url": "https://...",
  "backdrop_url": "https://...",
  "year": 2025,
  "kind": "movie",
  "rating": 8.4,
  "description": "..."
}
```

`kind` may be `movie`, `series`, `episode`, or `unknown`.

## Playback safety

A stream response may include `url`, `quality`, `mime_type`, `referer`, and request headers required by the authorized provider. The Android app treats these as short-lived playback metadata. It does not contain or derive upstream signing secrets, bypass anti-bot controls, or impersonate a third-party mobile client.

## User disclosure

The app's Settings → About us page tells users that external content providers supply catalogue, playback, and subtitle information, and that availability depends on authorization and regional restrictions.
