const puppeteer = require('puppeteer-core');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const brainDir = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\16d080c2-dd37-4e59-a3b9-9a8e5d98b805';

async function run() {
  console.log('Starting profile page automated UI test...');
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

  // Navigate to Profile page
  console.log('3. Navigating to my-profile.html...');
  await page.goto('http://localhost:5500/frontend/pages/customer/my-profile.html', {
    waitUntil: 'networkidle0'
  });
  console.log('Profile page loaded.');

  // Wait for profile content to load and display
  await page.waitForSelector('#profile-content');
  
  // Extra wait to ensure all async content fetches are completed and rendered
  await new Promise(resolve => setTimeout(resolve, 2000));

  // Retrieve values displayed to double check
  const displayedName = await page.evaluate(() => document.getElementById('info-name').textContent);
  const displayedEmail = await page.evaluate(() => document.getElementById('info-email').textContent);
  const displayedPhone = await page.evaluate(() => document.getElementById('info-phone').textContent);
  const displayedBalance = await page.evaluate(() => document.getElementById('info-balance').textContent);

  console.log('--- Displayed Data Summary ---');
  console.log('Name:', displayedName);
  console.log('Email:', displayedEmail);
  console.log('Phone:', displayedPhone);
  console.log('Balance:', displayedBalance);
  console.log('------------------------------');

  // Take screenshot of the profile page
  const screenshotPath = path.join(brainDir, 'my_profile_dashboard.png');
  await page.screenshot({ path: screenshotPath });
  console.log('Saved profile page screenshot:', screenshotPath);

  await browser.close();
  console.log('Profile page verification completed successfully.');
}

run().catch(console.error);
