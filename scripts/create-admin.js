#!/usr/bin/env node
// ============================================================
// INFOLD News - create the first admin user
//
// Usage:
//   npm run create-admin
//
// It prints the exact SQL to insert the admin user. Run it against D1:
//
//   Local:
//     wrangler d1 execute infold-news-db --local  --command "<SQL>"
//   Remote:
//     wrangler d1 execute infold-news-db --remote --command "<SQL>"
//
// NOTE: the PBKDF2 parameters below MUST match worker/src/auth.js
// (PBKDF2_ITERATIONS = 100000, 16-byte salt, SHA-256, 256-bit key).
// ============================================================

import crypto from 'node:crypto';
import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

const PBKDF2_ITERATIONS = 100000;

const rl = readline.createInterface({ input, output });

function question(prompt, { required = false } = {}) {
  return rl.question(prompt).then((answer) => {
    const value = answer.trim();
    if (required && !value) {
      console.error('This field is required. Please try again.');
      process.exit(1);
    }
    return value;
  });
}

try {
  console.log('');
  console.log('=== INFOLD News: create admin user ===');
  console.log('');

  const email = await question('Admin email address: ', { required: true });
  const password = await question('Password (at least 8 characters): ', { required: true });
  if (password.length < 8) {
    console.error('Error: password must be at least 8 characters.');
    process.exit(1);
  }
  const name = (await question(`Display name [${email.split('@')[0]}]: `)) || email.split('@')[0];

  const salt = crypto.randomBytes(16);
  const derivedKey = crypto.pbkdf2Sync(password, salt, PBKDF2_ITERATIONS, 32, 'sha256');
  const hash = `pbkdf2$${PBKDF2_ITERATIONS}$${salt.toString('base64')}$${derivedKey.toString('base64')}`;

  const escapedEmail = email.replace(/'/g, "''");
  const escapedName = name.replace(/'/g, "''");
  const sql = `INSERT INTO users (email, password_hash, name, role) VALUES ('${escapedEmail}', '${hash}', '${escapedName}', 'admin');`;

  console.log('');
  console.log('Admin user prepared. Insert it into D1 with one of these commands:');
  console.log('');
  console.log('  Local:  npx wrangler d1 execute infold-news-db --local  --command "' + sql + '"');
  console.log('  Remote: npx wrangler d1 execute infold-news-db --remote --command "' + sql + '"');
  console.log('');
  console.log('Raw SQL:');
  console.log(sql);
  console.log('');
} finally {
  rl.close();
}
