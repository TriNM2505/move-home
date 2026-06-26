const puppeteer = require('puppeteer-core');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const brainDir = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\98c78e03-bd53-4e85-92ec-480545061646';

async function run() {
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1020, height: 740 });

  // Monitor console logs in the page
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  // 1. TEST EMPTY STATE (No token in URL)
  console.log('Testing Empty State...');
  await page.goto('http://localhost:5500/frontend/pages/verify-email-success.html', {
    waitUntil: 'networkidle0'
  });
  await page.screenshot({ path: path.join(brainDir, 'verify_empty_state.png') });
  console.log('Saved empty state screenshot.');

  // 2. TEST ERROR STATE (Invalid token in URL)
  console.log('Testing Error State (Invalid Token)...');
  await page.goto('http://localhost:5500/frontend/pages/verify-email-success.html?token=invalid-verification-token-12345', {
    waitUntil: 'networkidle0'
  });
  // Wait for the error state to activate
  await page.waitForFunction(() => {
    return document.getElementById('error-state').classList.contains('active');
  }, { timeout: 5000 });
  await page.screenshot({ path: path.join(brainDir, 'verify_error_state.png') });
  console.log('Saved error state screenshot.');

  // 3. TEST SUCCESS STATE (Valid token)
  console.log('Testing Success State (Valid Token)...');
  // Navigate with the known token set up in the DB
  await page.goto('http://localhost:5500/frontend/pages/verify-email-success.html?token=test-email-verification-token', {
    waitUntil: 'networkidle0'
  });
  // Wait for the success state to activate
  await page.waitForFunction(() => {
    return document.getElementById('success-state').classList.contains('active');
  }, { timeout: 5000 });
  await page.screenshot({ path: path.join(brainDir, 'verify_success_state.png') });
  console.log('Saved success state screenshot.');

  // Wait 3 seconds to check the countdown progression
  await new Promise(r => setTimeout(r, 3000));
  await page.screenshot({ path: path.join(brainDir, 'verify_success_countdown.png') });
  console.log('Saved success state countdown screenshot.');

  await browser.close();
  console.log('Browser tests completed successfully.');
}

run().catch(console.error);
