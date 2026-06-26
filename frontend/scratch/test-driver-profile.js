const puppeteer = require('puppeteer-core');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const brainDir = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\c8bb160a-4345-45a0-980f-1d82926c1404';

async function testDriver(email, password, screenshotName) {
  console.log(`\n--- Testing with ${email} ---`);
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 950 });

  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  console.log('1. Navigating to login page...');
  await page.goto('http://localhost:5500/frontend/pages/login.html', {
    waitUntil: 'networkidle0'
  });

  console.log('2. Entering credentials...');
  await page.type('#email', email);
  await page.type('#password', password);

  console.log('3. Clicking submit...');
  await page.click('button[type="submit"]');

  console.log('4. Waiting for redirect...');
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected URL:', page.url());

  console.log('5. Navigating to profile.html...');
  await page.goto('http://localhost:5500/frontend/pages/driver/profile.html', {
    waitUntil: 'networkidle0'
  });
  console.log('Profile page loaded.');

  // Wait for loading overlay to hide and content to display
  console.log('6. Waiting for profile data to load...');
  await page.waitForSelector('#profile-content', { visible: true, timeout: 8000 });
  
  // Extra wait to let rendering finish
  await new Promise(resolve => setTimeout(resolve, 2000));

  // Retrieve values displayed to verify
  const displayedName = await page.evaluate(() => document.getElementById('val-fullName').textContent);
  const displayedEmail = await page.evaluate(() => document.getElementById('val-email').textContent);
  const displayedPhone = await page.evaluate(() => document.getElementById('val-phone').textContent);
  const displayedLicense = await page.evaluate(() => document.getElementById('val-licenseNumber').textContent);
  const displayedPlate = await page.evaluate(() => document.getElementById('val-vehiclePlate').textContent);
  const totalOrders = await page.evaluate(() => document.getElementById('kpi-orders-count').textContent);
  const totalRevenue = await page.evaluate(() => document.getElementById('kpi-revenue-count').textContent);
  const averageRating = await page.evaluate(() => document.getElementById('kpi-rating-count').textContent);

  console.log('--- Displayed Data Summary ---');
  console.log('Name:', displayedName);
  console.log('Email:', displayedEmail);
  console.log('Phone:', displayedPhone);
  console.log('License Number:', displayedLicense);
  console.log('Vehicle Plate:', displayedPlate);
  console.log('KPI Orders:', totalOrders);
  console.log('KPI Revenue:', totalRevenue);
  console.log('KPI Rating:', averageRating);
  console.log('------------------------------');

  // Take screenshot
  const destPath = path.join(brainDir, screenshotName);
  await page.screenshot({ path: destPath });
  console.log('Saved screenshot:', destPath);

  await browser.close();
}

async function run() {
  // Test active driver
  await testDriver('driver1@movehome.vn', 'Admin@2026', 'driver_profile_active.png');
  
  // Test pending driver
  await testDriver('driver_pending@movehome.vn', 'Admin@2026', 'driver_profile_pending.png');

  console.log('\nAll driver UI tests finished.');
}

run().catch(err => {
  console.error('Test script crashed:', err);
});
