const puppeteer = require('puppeteer-core');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const brainDir = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\fda2c0bf-4b87-46f2-920d-9f03863caf0e';

async function run() {
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 800 });

  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  console.log('1. Navigating to login page...');
  await page.goto('http://localhost:5500/frontend/pages/login.html', {
    waitUntil: 'networkidle0'
  });

  // Type credentials
  await page.type('#email', 'customer1@test.com');
  await page.type('#password', 'Admin@2026');

  // Submit login
  console.log('2. Submitting login form...');
  await page.click('button[type="submit"]');

  // Wait for redirect to customer/home.html
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected to:', page.url());

  // Navigate to Wallet page
  console.log('3. Navigating to my-wallet.html...');
  await page.goto('http://localhost:5500/frontend/pages/customer/my-wallet.html', {
    waitUntil: 'networkidle0'
  });
  console.log('Wallet page loaded.');

  // Give a moment for wallet balance fetch to complete
  await page.waitForSelector('#wallet-balance-val');
  await new Promise(resolve => setTimeout(resolve, 3000));

  // Retrieve wallet balance displayed
  const walletText = await page.evaluate(() => document.getElementById('wallet-balance-val').textContent);
  console.log('Wallet balance displayed:', walletText);

  // Take main wallet page screenshot
  const walletScreenshotPath = path.join(brainDir, 'wallet_dashboard.png');
  await page.screenshot({ path: walletScreenshotPath });
  console.log('Saved wallet dashboard screenshot:', walletScreenshotPath);

  // Click open modal button
  console.log('4. Opening top-up modal...');
  await page.click('#open-topup-btn');
  await new Promise(resolve => setTimeout(resolve, 500));

  // Enter amount
  console.log('5. Entering amount 500,000 in modal...');
  await page.type('#topup-amount-input', '500000');
  
  // Take screenshot of modal
  const modalScreenshotPath = path.join(brainDir, 'wallet_topup_modal.png');
  await page.screenshot({ path: modalScreenshotPath });
  console.log('Saved top-up modal screenshot:', modalScreenshotPath);

  // Click confirm
  console.log('6. Confirming top-up request...');
  await page.click('#confirm-topup-btn');

  // Wait for redirect to VNPay
  console.log('Waiting for VNPay redirect...');
  await page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 10000 });
  console.log('Redirected VNPay URL:', page.url());

  const vnpayScreenshotPath = path.join(brainDir, 'vnpay_redirect.png');
  await page.screenshot({ path: vnpayScreenshotPath });
  console.log('Saved VNPay redirect screenshot:', vnpayScreenshotPath);

  await browser.close();
  console.log('Wallet verification completed successfully.');
}

run().catch(console.error);
