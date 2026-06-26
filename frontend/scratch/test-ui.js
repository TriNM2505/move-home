const puppeteer = require('puppeteer-core');
const { Client } = require('pg');
const crypto = require('crypto');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const dbConnectionString = 'postgresql://neondb_owner:npg_Ob7cIU2MKjEf@ep-long-fire-aonicy5s-pooler.c-2.ap-southeast-1.aws.neon.tech/neondb';

async function setupToken(rawToken) {
  const client = new Client({
    connectionString: dbConnectionString,
    ssl: { rejectUnauthorized: false }
  });
  await client.connect();
  try {
    const userRes = await client.query("SELECT id FROM app_user WHERE email = 'customer1@test.com'");
    const userId = userRes.rows[0].id;
    await client.query("DELETE FROM password_reset_token WHERE user_id = $1", [userId]);
    const tokenHash = crypto.createHash('sha256').update(rawToken, 'utf-8').digest('hex');
    const expiresAt = new Date(Date.now() + 30 * 60 * 1000);
    await client.query(`
      INSERT INTO password_reset_token (id, user_id, token_hash, expires_at, created_at)
      VALUES (gen_random_uuid(), $1, $2, $3, NOW())
    `, [userId, tokenHash, expiresAt]);
    console.log(`Mock token '${rawToken}' configured in DB.`);
  } finally {
    await client.end();
  }
}

async function run() {
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 800 });

  // --- TEST 1: SUCCESSFUL RESET ---
  await setupToken('ui-test-success-token');

  console.log('Opening page...');
  await page.goto('http://localhost:5500/frontend/pages/reset-password.html?token=ui-test-success-token', {
    waitUntil: 'networkidle0'
  });

  // Verify elements are visible and styled properly
  await page.screenshot({ path: 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\035eaca8-d140-4dc6-ade1-5adb34859a97\\reset_password_initial.png' });
  console.log('Saved initial state screenshot.');

  // Type new passwords
  await page.type('#password', 'Admin@2026');
  await page.type('#confirm', 'Admin@2026');
  await page.screenshot({ path: 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\035eaca8-d140-4dc6-ade1-5adb34859a97\\reset_password_filled.png' });
  console.log('Saved filled state screenshot.');

  // Submit form
  await page.click('#submitButton');
  console.log('Submitted form.');

  // Wait for success message to show (hidden attribute removed)
  await page.waitForFunction(() => {
    const successMsg = document.getElementById('successMessage');
    return successMsg && !successMsg.hasAttribute('hidden') && successMsg.textContent.trim().length > 0;
  }, { timeout: 5000 });

  await page.screenshot({ path: 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\035eaca8-d140-4dc6-ade1-5adb34859a97\\reset_password_success.png' });
  console.log('Saved success state screenshot.');

  // --- TEST 2: ERROR STATE (Invalid/Used Token) ---
  console.log('Opening page with used token...');
  await page.goto('http://localhost:5500/frontend/pages/reset-password.html?token=ui-test-success-token', {
    waitUntil: 'networkidle0'
  });

  // Type new passwords
  await page.type('#password', 'Admin@2026');
  await page.type('#confirm', 'Admin@2026');

  // Submit form
  await page.click('#submitButton');
  console.log('Submitted form with invalid token.');

  // Wait for error message to show (hidden attribute removed)
  await page.waitForFunction(() => {
    const errorMsg = document.getElementById('errorMessage');
    return errorMsg && !errorMsg.hasAttribute('hidden') && errorMsg.textContent.trim().length > 0;
  }, { timeout: 5000 });

  await page.screenshot({ path: 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\035eaca8-d140-4dc6-ade1-5adb34859a97\\reset_password_error.png' });
  console.log('Saved error state screenshot.');

  await browser.close();
}

run().catch(console.error);
