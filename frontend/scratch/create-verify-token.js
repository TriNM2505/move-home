const { Client } = require('pg');
const crypto = require('crypto');

async function main() {
  const connectionString = 'postgresql://neondb_owner:npg_Ob7cIU2MKjEf@ep-long-fire-aonicy5s-pooler.c-2.ap-southeast-1.aws.neon.tech/neondb';
  const client = new Client({
    connectionString,
    ssl: { rejectUnauthorized: false }
  });

  await client.connect();
  console.log('Connected to database.');

  const email = 'customertest_verify@test.com';
  const password = 'Admin@2026';
  const fullName = 'Customer Verify Test';
  const phone = '0987654321';

  try {
    // 1. Delete existing user from app_user if exists
    // Due to constraints, let's delete tokens first, then user
    const checkUser = await client.query("SELECT id FROM app_user WHERE email = $1", [email]);
    if (checkUser.rows.length > 0) {
      const existingUserId = checkUser.rows[0].id;
      console.log(`Found existing user ${email} (${existingUserId}). Cleaning up old data...`);
      await client.query("DELETE FROM email_verification_token WHERE user_id = $1", [existingUserId]);
      await client.query("DELETE FROM app_user WHERE id = $1", [existingUserId]);
      console.log('Cleaned up old user data.');
    }

    // 2. Register user via HTTP API
    console.log('Registering user via HTTP API...');
    const registerRes = await fetch('http://localhost:8080/api/auth/register/customer', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email,
        password,
        fullName,
        phone,
        termsAccepted: true
      })
    });

    if (!registerRes.ok) {
      const errText = await registerRes.text();
      throw new Error(`Registration failed: ${registerRes.status} - ${errText}`);
    }
    const registerData = await registerRes.json();
    console.log('User registered successfully:', registerData.user || registerData);

    // 3. Get the registered user's ID
    const userRes = await client.query("SELECT id, status FROM app_user WHERE email = $1", [email]);
    if (userRes.rows.length === 0) {
      throw new Error('Registered user not found in database.');
    }
    const user = userRes.rows[0];
    const userId = user.id;
    console.log(`User ID in database: ${userId}, Status: ${user.status}`);

    // 4. Delete the auto-generated token to avoid confusion
    await client.query("DELETE FROM email_verification_token WHERE user_id = $1", [userId]);
    console.log('Deleted auto-generated token.');

    // 5. Generate custom token
    const rawToken = 'test-email-verification-token';
    const tokenHash = crypto.createHash('sha256').update(rawToken, 'utf-8').digest('hex');
    const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000); // 24 hours expiry
    
    // Insert custom token
    const insertQuery = `
      INSERT INTO email_verification_token (id, user_id, token, expires_at, created_at, used_at)
      VALUES (gen_random_uuid(), $1, $2, $3, NOW(), NULL)
    `;
    await client.query(insertQuery, [userId, tokenHash, expiresAt]);
    console.log('Successfully inserted custom verification token.');
    console.log(`Raw token: ${rawToken}`);
    console.log(`Verification URL: http://localhost:5500/frontend/pages/verify-email-success.html?token=${rawToken}`);

  } catch (err) {
    console.error('Error:', err.message || err);
  } finally {
    await client.end();
    console.log('Database connection closed.');
  }
}

main().catch(console.error);
