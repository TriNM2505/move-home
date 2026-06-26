const puppeteer = require('puppeteer-core');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const brainDir = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\2b5efa69-8e57-4cbf-9382-d50640441f2c';

async function run() {
  console.log('Starting puppeteer browser...');
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });

  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  console.log('1. Navigating to login page...');
  await page.goto('http://localhost:5500/frontend/pages/login.html', {
    waitUntil: 'networkidle0'
  });

  // Login as driver
  console.log('2. Entering driver credentials...');
  await page.type('#email', 'driver1@movehome.vn');
  await page.type('#password', 'Admin@2026');

  // Submit login
  console.log('3. Submitting login form...');
  await page.click('button[type="submit"]');

  // Wait for redirect to driver/home.html
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected to:', page.url());

  // Navigate to Withdrawal Request page
  console.log('4. Navigating to withdrawal-request.html...');
  await page.goto('http://localhost:5500/frontend/pages/driver/withdrawal-request.html', {
    waitUntil: 'networkidle0'
  });
  console.log('Withdrawal Request page loaded.');

  // Give it a moment to fetch balance
  console.log('5. Waiting for balance text...');
  await page.waitForFunction(
    () => {
      const val = document.getElementById('balance-value').textContent;
      return val && !val.includes('spinner');
    },
    { timeout: 8000 }
  );

  const balanceText = await page.evaluate(() => document.getElementById('balance-value').textContent);
  console.log('Driver wallet balance displayed:', balanceText);

  // Take screenshot of filled request page
  console.log('6. Typing withdrawal amount...');
  // Clear field and type 100000
  await page.click('#amount', { clickCount: 3 });
  await page.keyboard.press('Backspace');
  await page.type('#amount', '100000');

  // Screenshot form
  const formScreenshotPath = path.join(brainDir, 'withdrawal_form_redesign.png');
  await page.screenshot({ path: formScreenshotPath });
  console.log('Saved form screenshot to:', formScreenshotPath);

  // Submit form
  console.log('7. Submitting withdrawal request...');
  await page.click('#submit-btn');

  // Wait for redirect to withdrawal-history.html
  console.log('Waiting for redirection to withdrawal-history.html...');
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected to:', page.url());

  // Take screenshot of history page
  const historyScreenshotPath = path.join(brainDir, 'withdrawal_history_redirect.png');
  await page.screenshot({ path: historyScreenshotPath });
  console.log('Saved history page screenshot to:', historyScreenshotPath);

  await browser.close();
  console.log('Withdrawal redesign test completed successfully!');
}

run().catch(console.error);
