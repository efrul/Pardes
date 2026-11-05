-- Pardes's feedback service (README.md): each package a user sent, as it came (its JSON), with what the list shows of
-- it. Nothing of the request is kept: no IP address, no user agent, no header.
CREATE TABLE packages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender TEXT NOT NULL,
    received_at INTEGER NOT NULL,
    app_version TEXT,
    library TEXT,
    grades INTEGER NOT NULL,
    searches INTEGER NOT NULL,
    daf_marks INTEGER NOT NULL,
    bytes INTEGER NOT NULL,
    body TEXT NOT NULL
);

CREATE INDEX packages_sender ON packages (sender, received_at);

-- The senders who had what they sent deleted (DELETE /v1/senders/:id), so that the copies fetched before go too
CREATE TABLE deletions (
    sender TEXT PRIMARY KEY,
    deleted_at INTEGER NOT NULL
);
