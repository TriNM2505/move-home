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
  
  await page.screenshot({ path: path.join(brainDir, 'admin_login_filled_detail.png') });
  console.log('Saved login filled screenshot.');

  // Click login
  await page.click('button[type="submit"]');
  console.log('Submitted login form. Waiting for navigation...');

  // Wait for redirect
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected to:', page.url());

  // Retrieve a valid driver user ID from localStorage or by executing a fetch call from page context
  console.log('Fetching driver list from backend to get a valid user ID...');
  const driverUserId = await page.evaluate(async () => {
    const token = localStorage.getItem('accessToken');
    const res = await fetch('http://localhost:8080/api/admin/dashboard/drivers', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const data = await res.json();
    console.log('Fetched drivers count:', data.length);
    // Find driver1's email or any active driver user ID
    const driver = data.find(d => d.email === 'driver1@movehome.vn') || data[0];
    return driver ? driver.userId : null;
  });

  console.log('Selected driver user ID:', driverUserId);
  if (!driverUserId) {
    throw new Error('No driver user ID found!');
  }

  // Navigate to driver detail page with ID parameter
  const detailUrl = `http://localhost:5500/frontend/pages/admin/driver-detail.html?id=${driverUserId}`;
  console.log('Navigating to driver detail page:', detailUrl);
  await page.goto(detailUrl, {
    waitUntil: 'networkidle0'
  });

  // Wait a moment for dynamic status button loading to complete
  console.log('Waiting for toggle status button to render...');
  await page.waitForSelector('#account-status-toggle', { timeout: 10000 });
  
  // Extra wait to let CSS/fonts load completely
  await new Promise(resolve => setTimeout(resolve, 3000));

  // Take screenshot of the driver detail page
  await page.screenshot({ path: path.join(brainDir, 'admin_driver_detail_redesign.png') });
  console.log('Saved admin driver detail page screenshot.');

  await browser.close();
  console.log('Test run finished successfully.');
}

run().catch(console.error);
