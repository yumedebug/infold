#!/usr/bin/env node
// Generate the admin INSERT SQL for D1 (PBKDF2 100000 iters, SHA-256, 16-byte salt)
// Usage: node scripts/make-admin-sql.mjs <email> <password> [name]
import crypto from 'node:crypto';

const [, , emailArg, passwordArg, nameArg] = process.argv;
const email = String(emailArg || '').trim().toLowerCase();
const password = String(passwordArg || '');
const name = String(nameArg || '').trim() || email.split('@')[0] || 'admin';

if (!email || !password) {
  console.error('Usage: node scripts/make-admin-sql.mjs <email> <password> [name]');
  process.exit(1);
}
if (password.length < 8) {
  console.error('Error: password must be at least 8 characters.');
  process.exit(1);
}

const PBKDF2_ITERATIONS = 100000; // must match worker/src/auth.js
const salt = crypto.randomBytes(16);
const derivedKey = crypto.pbkdf2Sync(password, salt, PBKDF2_ITERATIONS, 32, 'sha256');
const hash = `pbkdf2$${PBKDF2_ITERATIONS}$${salt.toString('base64')}$${derivedKey.toString('base64')}`;

const escapedEmail = email.replace(/'/g, "''");
const escapedName = name.replace(/'/g, "''");
const sql = `INSERT INTO users (email, password_hash, name, role) VALUES ('${escapedEmail}', '${hash}', '${escapedName}', 'admin');`;

console.log(sql);
