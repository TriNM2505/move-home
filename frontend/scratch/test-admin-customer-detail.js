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
  
  await page.screenshot({ path: path.join(brainDir, 'admin_login_filled_customer_detail.png') });
  console.log('Saved login filled screenshot.');

  // Click login
  await page.click('button[type="submit"]');
  console.log('Submitted login form. Waiting for navigation...');

  // Wait for redirect
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected to:', page.url());

  // Retrieve a valid customer user ID from localStorage/API
  console.log('Fetching customer list from backend to get a valid user ID...');
  const customerUserId = await page.evaluate(async () => {
    const token = localStorage.getItem('accessToken');
    const res = await fetch('http://localhost:8080/api/admin/dashboard/customers', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const data = await res.json();
    console.log('Fetched customers count:', data.length);
    console.log('First customer object:', JSON.stringify(data[0]));
    // Find customer1's email or any active customer user ID
    const customer = data.find(c => c.email === 'customer1@movehome.vn') || data[0];
    return customer ? (customer.userId || customer.id) : null;
  });

  console.log('Selected customer user ID:', customerUserId);
  if (!customerUserId) {
    throw new Error('No customer user ID found!');
  }

  // Navigate to customer detail page with ID parameter
  const detailUrl = `http://localhost:5500/frontend/pages/admin/customer-detail.html?id=${customerUserId}`;
  console.log('Navigating to customer detail page:', detailUrl);
  await page.goto(detailUrl, {
    waitUntil: 'networkidle0'
  });

  // Wait a moment for dynamic status button loading to complete
  console.log('Waiting for toggle status button to render...');
  await page.waitForSelector('#account-status-toggle', { timeout: 10000 });
  
  // Extra wait to let CSS/fonts load completely
  await new Promise(resolve => setTimeout(resolve, 3000));

  // Take screenshot of the customer detail page
  await page.screenshot({ path: path.join(brainDir, 'admin_customer_detail_redesign.png') });
  console.log('Saved admin customer detail page screenshot.');

  await browser.close();
  console.log('Test run finished successfully.');
}

run().catch(console.error);
