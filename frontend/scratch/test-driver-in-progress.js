const puppeteer = require('puppeteer-core');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const brainDir = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\bae3f7d1-6297-4f6e-8ff1-7c93285eca2d';

async function run() {
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

  console.log('2. Logging in as driver...');
  await page.type('#email', 'driver1@movehome.vn');
  await page.type('#password', 'Admin@2026');
  await page.click('button[type="submit"]');

  console.log('3. Waiting for redirect to driver home...');
  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 8000 });
  console.log('Redirected to:', page.url());

  console.log('4. Navigating to in-progress.html...');
  await page.goto('http://localhost:5500/frontend/pages/driver/in-progress.html', {
    waitUntil: 'networkidle0'
  });
  
  // Wait additional time for Leaflet map to load and render
  await page.waitForTimeout ? await page.waitForTimeout(4000) : await new Promise(r => setTimeout(r, 4000));
  
  const inProgressPath = path.join(brainDir, 'driver_in_progress_accepted.png');
  await page.screenshot({ path: inProgressPath });
  console.log('Saved driver_in_progress_accepted.png');

  // Verify the "Đã đến điểm đón" button is present and click it
  console.log('5. Clicking "Đã đến điểm đón"...');
  await page.click('#btn-start');
  
  // Wait for state update reload
  await page.waitForTimeout ? await page.waitForTimeout(4000) : await new Promise(r => setTimeout(r, 4000));
  
  const inProgressStartedPath = path.join(brainDir, 'driver_in_progress_started.png');
  await page.screenshot({ path: inProgressStartedPath });
  console.log('Saved driver_in_progress_started.png');

  // Verify the "Đã hoàn thành" button is present and click it
  console.log('6. Clicking "Đã hoàn thành"...');
  await page.click('#btn-complete');
  
  // Wait for redirect to history.html
  await page.waitForTimeout ? await page.waitForTimeout(4000) : await new Promise(r => setTimeout(r, 4000));
  console.log('Current URL after completion:', page.url());
  
  const driverHistoryPath = path.join(brainDir, 'driver_history.png');
  await page.screenshot({ path: driverHistoryPath });
  console.log('Saved driver_history.png');

  await browser.close();
  console.log('Driver flow test completed successfully!');
}

run().catch(console.error);
