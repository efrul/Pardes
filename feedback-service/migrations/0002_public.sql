-- Whether the sender agreed to publish the package ("publish": true): its searches' and grades' words are then shown
-- to everyone on the site. Packages from before, and any without it, stay 0: their words are the maintainer's alone.
ALTER TABLE packages ADD COLUMN public INTEGER NOT NULL DEFAULT 0;
