-- User profile: optional about text (username remains the account identity key).

ALTER TABLE user_accounts
    ADD COLUMN about VARCHAR(200) NOT NULL DEFAULT '';
