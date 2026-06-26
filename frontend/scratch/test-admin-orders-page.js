const puppeteer = require('puppeteer-core');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const brainDir = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\5dce3fd6-af3d-41f6-90ff-59641d4ff46c';

async function run() {
  console.log('Launching browser...');
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 950 });

  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  console.log('Navigating to login page...');
  await page.goto('http://localhost:5500/frontend/pages/login.html', {
    waitUntil: 'networkidle0'
  });

  // Type login credentials
  await page.type('#email', 'admin@movehome.vn');
  await page.type('#password', 'Admin@2026');
  
  await page.screenshot({ path: path.join(brainDir, 'admin_login_filled_orders.png') });
  console.log('Saved login filled screenshot.');

  // Click login
  await page.click('button[type="submit"]');
  console.log('Submitted login form. Waiting for navigation...');

  // Wait for redirect
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected to:', page.url());

  // Navigate to orders page
  const ordersUrl = 'http://localhost:5500/frontend/pages/admin/orders.html';
  console.log('Navigating to orders page:', ordersUrl);
  await page.goto(ordersUrl, {
    waitUntil: 'networkidle0'
  });

  // Wait for the table data to load (no spinner / has data rows)
  console.log('Waiting for orders data to load...');
  await page.waitForFunction(() => {
    const rows = document.querySelectorAll('#ordersTableBody tr');
    if (rows.length === 0) return false;
    const firstRowText = rows[0].textContent;
    return !firstRowText.includes('Đang tải');
  }, { timeout: 10000 });

  // Extra wait to let CSS/fonts load completely
  await new Promise(resolve => setTimeout(resolve, 3000));

  // Take screenshot of the orders page
  await page.screenshot({ path: path.join(brainDir, 'admin_orders_redesign.png') });
  console.log('Saved admin orders page screenshot.');

  await browser.close();
  console.log('Test run finished successfully.');
}

run().catch(console.error);
