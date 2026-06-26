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
  await page.setViewport({ width: 1280, height: 800 });

  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  console.log('Navigating to login page...');
  await page.goto('http://localhost:5500/frontend/pages/login.html', {
    waitUntil: 'networkidle0'
  });

  // Type login credentials
  await page.type('#email', 'customer1@test.com');
  await page.type('#password', 'Admin@2026');
  
  await page.screenshot({ path: path.join(brainDir, 'login_filled.png') });
  console.log('Saved login filled screenshot.');

  // Click login
  await page.click('button[type="submit"]');
  console.log('Submitted login form. Waiting for navigation...');

  // Wait for redirect to customer/home.html
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected to:', page.url());

  await page.screenshot({ path: path.join(brainDir, 'customer_home_dashboard.png') });
  console.log('Saved customer home dashboard screenshot.');

  await browser.close();
  console.log('Login verification test completed.');
}

run().catch(console.error);
