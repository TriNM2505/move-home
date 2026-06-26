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

  try {
    // 1. Get user ID for customer1@test.com
    const userRes = await client.query("SELECT id, email FROM app_user WHERE email = 'customer1@test.com'");
    if (userRes.rows.length === 0) {
      console.error('User customer1@test.com not found.');
      return;
    }
    const user = userRes.rows[0];
    const userId = user.id;
    console.log(`Found user: ${user.email} with ID: ${userId}`);

    // 2. Clear existing tokens to avoid conflict
    await client.query("DELETE FROM password_reset_token WHERE user_id = $1", [userId]);
    console.log('Cleared old reset tokens for user.');

    // 3. Create raw token and token hash
    const rawToken = 'my-custom-test-token';
    const tokenHash = crypto.createHash('sha256').update(rawToken, 'utf-8').digest('hex');
    const expiresAt = new Date(Date.now() + 30 * 60 * 1000); // 30 minutes from now

    // 4. Insert token into database
    // The id is UUID generated automatically or we can generate one.
    // The table schema has: user_id, token_hash, expires_at, created_at
    const insertQuery = `
      INSERT INTO password_reset_token (id, user_id, token_hash, expires_at, created_at)
      VALUES (gen_random_uuid(), $1, $2, $3, NOW())
    `;
    await client.query(insertQuery, [userId, tokenHash, expiresAt]);
    console.log(`Successfully inserted reset token.`);
    console.log(`Raw token: ${rawToken}`);
    console.log(`Test URL: http://localhost:5500/frontend/pages/reset-password.html?token=${rawToken}`);

  } catch (err) {
    console.error('Error executing query:', err);
  } finally {
    await client.end();
    console.log('Database connection closed.');
  }
}

main().catch(console.error);
